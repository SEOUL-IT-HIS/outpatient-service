package kr.co.seoulit.his.outpatientservice.admission.service;

import kr.co.seoulit.his.outpatientservice.admission.dto.AdmissionRequestCreateDto;
import kr.co.seoulit.his.outpatientservice.admission.dto.AdmissionRequestDto;
import kr.co.seoulit.his.outpatientservice.admission.dto.AdmissionRequestPageDto;
import kr.co.seoulit.his.outpatientservice.admission.entity.AdmissionRequest;
import kr.co.seoulit.his.outpatientservice.admission.mapper.AdmissionRequestMapper;
import kr.co.seoulit.his.outpatientservice.admission.repository.AdmissionRequestRepository;
import kr.co.seoulit.his.outpatientservice.common.cache.CommonCodeCache;
import kr.co.seoulit.his.outpatientservice.common.client.admission.AdmissionEventDto;
import kr.co.seoulit.his.outpatientservice.common.client.admission.AdmissionRequestPublisher;
import kr.co.seoulit.his.outpatientservice.common.client.patient.PatientApiDto;
import kr.co.seoulit.his.outpatientservice.common.client.patient.PatientClient;
import kr.co.seoulit.his.outpatientservice.common.exception.BusinessException;
import kr.co.seoulit.his.outpatientservice.common.exception.ErrorCode;
import kr.co.seoulit.his.outpatientservice.outpatientcare.entity.Encounter;
import kr.co.seoulit.his.outpatientservice.outpatientcare.repository.EncounterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AdmissionRequestServiceImpl implements AdmissionRequestService {

    private final AdmissionRequestRepository admissionRequestRepository;
    private final EncounterRepository encounterRepository;
    private final AdmissionRequestMapper admissionRequestMapper;
    private final AdmissionRequestPublisher admissionRequestPublisher;
    private final CommonCodeCache commonCodeCache;
    private final PatientClient patientClient;

    private static final String DEPT_CODE_GROUP = "DEPT_CD";
    private static final String WARD_CODE_GROUP = "WARD_CD";
    private static final String REQUEST_STATUS_CODE_GROUP = "ADMISSION_REQUEST_STATUS_CD";

    private static final String SEND_STATUS_PENDING = "PENDING";
    private static final String SEND_STATUS_SENT = "SENT";
    private static final String SEND_STATUS_FAILED = "FAILED";

    private static final int NOTE_MAX_LENGTH = 500; // 병동 note 제한
    private static final int IN_CLAUSE_CHUNK_SIZE = 500; // Oracle IN 절 1000개 제한 회피
    private static final int PAGE_MAX_SIZE = 100; // 입원요청 목록 API의 한 번에 최대 건수

    private static final List<String> ALL_STATUSES = List.of(
            AdmissionEventDto.STATUS_REQUESTED, AdmissionEventDto.STATUS_BED_ASSIGNED, AdmissionEventDto.STATUS_REJECTED);

    // 진행 중인 요청: 병동 회신 대기(01)와 병상 배정(02). 거부(03)된 요청만 같은 진료에서 다시 요청할 수 있다.
    private static final List<String> ACTIVE_STATUSES =
            List.of(AdmissionEventDto.STATUS_REQUESTED, AdmissionEventDto.STATUS_BED_ASSIGNED);

    // 입원 요청 등록
    @Override
    public AdmissionRequestDto createAdmissionRequest(String encounterId, AdmissionRequestCreateDto request) {
        Encounter encounter = encounterRepository.findById(encounterId)
                // 존재하지 않는 외래 진료 건입니다.
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "Outpatient encounter not found. encounterId=" + encounterId));

        // 진료 시작 전(WAITING/PENDING)이거나 취소된 진료에서는 입원을 요청할 수 없다
        String encounterStatus = encounter.getStatus();
        if (!"IN_PROGRESS".equals(encounterStatus) && !"COMPLETED".equals(encounterStatus)) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "An admission cannot be requested for this encounter status. encounterId=" + encounterId
                            + ", status=" + encounterStatus);
        }

        String note = request == null ? null : trimToNull(request.getRequestNote());
        if (note != null && note.length() > NOTE_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "requestNote must be " + NOTE_MAX_LENGTH + " characters or less.");
        }

        // 입원 진료과를 따로 고르지 않으면 진료를 본 진료과로 요청한다
        String deptCode = request == null ? null : trimToNull(request.getAdmissionDeptCode());
        if (deptCode == null) {
            deptCode = trimToNull(encounter.getDepartmentCode());
        }
        if (deptCode == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "admissionDeptCode is required.");
        }

        // 병동 회신 대기·병상 배정 상태의 요청이 있으면 같은 진료에서 다시 요청할 수 없다 (거부된 경우만 재요청 가능)
        if (admissionRequestRepository.existsByEncounterIdAndRequestStatusIn(encounterId, ACTIVE_STATUSES)) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "An admission request is already in progress for this encounter. encounterId=" + encounterId);
        }

        AdmissionRequest admissionRequest = new AdmissionRequest();
        admissionRequest.setAdmissionRequestId(UUID.randomUUID().toString());
        admissionRequest.setPatientId(encounter.getPatientId());
        admissionRequest.setEncounterId(encounterId);
        admissionRequest.setAdmissionDeptCode(deptCode);
        admissionRequest.setRequestNote(note);
        admissionRequest.setRequestStatus(AdmissionEventDto.STATUS_REQUESTED);
        admissionRequest.setRequestedBy(encounter.getDoctorId());
        admissionRequest.setRequestedAt(LocalDateTime.now());
        admissionRequest.setSendStatus(SEND_STATUS_PENDING);
        AdmissionRequest saved = admissionRequestRepository.save(admissionRequest);

        return toResponse(saved);
    }

    // 입원 요청 병동 전송
    // Kafka 발행 중에 DB 커넥션을 잡고 있지 않도록 트랜잭션 없이 실행한다(조회/저장은 각각 짧은 트랜잭션).
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AdmissionRequestDto dispatchAdmissionRequest(String admissionRequestId) {
        AdmissionRequest admissionRequest = findAdmissionRequest(admissionRequestId);

        // 이미 전송됐거나 병동이 회신까지 마친 요청은 다시 보내지 않는다
        if (SEND_STATUS_SENT.equals(admissionRequest.getSendStatus())
                || !AdmissionEventDto.STATUS_REQUESTED.equals(admissionRequest.getRequestStatus())) {
            log.info("[병동 전송] 이미 전송됐거나 회신 처리된 요청이라 재발행 스킵 admissionRequestId={}, sendStatus={}, requestStatus={}",
                    admissionRequestId, admissionRequest.getSendStatus(), admissionRequest.getRequestStatus());
            return toResponse(admissionRequest);
        }

        boolean sent = admissionRequestPublisher.publish(new AdmissionEventDto.RequestedData(
                admissionRequest.getAdmissionRequestId(),
                admissionRequest.getPatientId(),
                admissionRequest.getAdmissionDeptCode(),
                admissionRequest.getEncounterId(),
                admissionRequest.getRequestedBy(),
                admissionRequest.getRequestNote()
        ));

        // 발행하는 사이에 병동 회신이 먼저 반영됐을 수 있으니, 최신 상태를 다시 읽어 전송 상태만 바꾼다
        AdmissionRequest latest = findAdmissionRequest(admissionRequestId);
        latest.setSendStatus(sent ? SEND_STATUS_SENT : SEND_STATUS_FAILED);
        latest.setSentAt(LocalDateTime.now());
        AdmissionRequest saved = admissionRequestRepository.save(latest);

        return toResponse(saved);
    }

    // 진료별 입원요청 목록 조회
    @Override
    @Transactional(readOnly = true)
    public List<AdmissionRequestDto> getAdmissionRequests(String encounterId) {
        List<AdmissionRequestDto> result = admissionRequestMapper.toDtoList(
                admissionRequestRepository.findByEncounterIdOrderByRequestedAtDesc(encounterId));
        result.forEach(this::fillCodeNames);
        return result;
    }

    // 입원요청 목록 조회 (모든 의사의 요청, 요청일시 최신순)
    // 환자명은 PAT 소유 데이터라 저장하지 않고 일괄 조회로 채운다. PAT 호출 중에 DB 커넥션을 잡고 있지 않도록
    // 트랜잭션 없이 실행한다(목록 조회는 각각 짧은 읽기 트랜잭션).
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AdmissionRequestPageDto getAdmissionRequestPage(String requestStatus, int page, int size) {
        String status = trimToNull(requestStatus);
        if (status != null && !ALL_STATUSES.contains(status)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "requestStatus must be one of " + ALL_STATUSES + ". requestStatus=" + requestStatus);
        }

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), PAGE_MAX_SIZE);
        // 요청일시가 같은 건이 페이지 경계에서 뒤섞이지 않도록 ID를 두 번째 정렬 기준으로 쓴다
        Pageable pageable = PageRequest.of(safePage, safeSize,
                Sort.by(Sort.Direction.DESC, "requestedAt").and(Sort.by(Sort.Direction.ASC, "admissionRequestId")));

        Page<AdmissionRequest> result = status == null
                ? admissionRequestRepository.findAll(pageable)
                : admissionRequestRepository.findByRequestStatus(status, pageable);

        List<AdmissionRequestDto> content = admissionRequestMapper.toDtoList(result.getContent());
        content.forEach(this::fillCodeNames);
        fillPatientNames(content);

        return new AdmissionRequestPageDto(content, safePage, safeSize, result.getTotalElements(), result.getTotalPages());
    }

    // 환자 정보(PAT API) 일괄 연동하여 환자명 채우기 — 실패해도 목록은 환자명 없이 내려준다
    private void fillPatientNames(List<AdmissionRequestDto> dtos) {
        List<String> patientIds = dtos.stream()
                .map(AdmissionRequestDto::getPatientId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (patientIds.isEmpty()) {
            return;
        }

        Map<String, PatientApiDto.PatientSummary> patientMap = Map.of();
        try {
            patientMap = patientClient.getPatients(patientIds);
        } catch (Exception e) {
            log.error("[PAT 연동 실패] 입원요청 목록 환자명 일괄조회 실패: {}", e.getMessage());
        }

        for (AdmissionRequestDto dto : dtos) {
            PatientApiDto.PatientSummary patient = patientMap.get(dto.getPatientId());
            if (patient != null) {
                String rawName = patient.patientName();
                dto.setPatientName(rawName != null && rawName.startsWith("환자") ? rawName.substring(2) : rawName);
            }
        }
    }

    // 여러 진료의 최신 입원요청 조회 (환자 목록 배지용)
    // 진료당 요청이 여러 건(거부 후 재요청)일 수 있어 가장 최근 요청 하나만 남긴다.
    // Oracle IN 절은 1000개를 넘으면 오류라서 IN_CLAUSE_CHUNK_SIZE 개씩 나눠서 조회한다.
    @Override
    @Transactional(readOnly = true)
    public Map<String, AdmissionRequestDto> getLatestAdmissionRequests(Collection<String> encounterIds) {
        if (encounterIds == null || encounterIds.isEmpty()) {
            return Map.of();
        }

        List<String> ids = encounterIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<String, AdmissionRequest> latestByEncounterId = new HashMap<>();
        for (int from = 0; from < ids.size(); from += IN_CLAUSE_CHUNK_SIZE) {
            List<String> chunk = ids.subList(from, Math.min(from + IN_CLAUSE_CHUNK_SIZE, ids.size()));
            for (AdmissionRequest request : admissionRequestRepository.findByEncounterIdIn(chunk)) {
                latestByEncounterId.merge(request.getEncounterId(), request,
                        (current, candidate) -> isLater(candidate, current) ? candidate : current);
            }
        }

        Map<String, AdmissionRequestDto> result = new HashMap<>();
        latestByEncounterId.forEach((encounterId, request) -> result.put(encounterId, toResponse(request)));
        return result;
    }

    // requestedAt이 더 늦은 쪽이 최신 (값이 없으면 오래된 것으로 본다)
    private static boolean isLater(AdmissionRequest candidate, AdmissionRequest current) {
        if (candidate.getRequestedAt() == null) {
            return false;
        }
        return current.getRequestedAt() == null || candidate.getRequestedAt().isAfter(current.getRequestedAt());
    }

    // 병동이 회신한 병상 배정/거부 결과를 요청에 반영한다.
    // 이미 02/03으로 확정된 요청이면 중복 이벤트로 보고 건너뛴다(병동 회신이 재전달돼도 상태가 뒤집히지 않게).
    @Override
    public void applyAdmissionResult(String admissionRequestId, String status, String admissionId,
                                     String assignedWardCode, String rejectReason) {
        if (admissionRequestId == null || admissionRequestId.isBlank()) {
            log.warn("[병동 회신 수신] requestId 누락으로 스킵 status={}", status);
            return;
        }
        if (!AdmissionEventDto.STATUS_BED_ASSIGNED.equals(status) && !AdmissionEventDto.STATUS_REJECTED.equals(status)) {
            log.warn("[병동 회신 수신] 알 수 없는 status={} 스킵 admissionRequestId={}", status, admissionRequestId);
            return;
        }

        AdmissionRequest admissionRequest = admissionRequestRepository.findById(admissionRequestId).orElse(null);
        if (admissionRequest == null) {
            log.warn("[병동 회신 수신] 해당 입원요청 없음 스킵 admissionRequestId={}", admissionRequestId);
            return;
        }
        if (!AdmissionEventDto.STATUS_REQUESTED.equals(admissionRequest.getRequestStatus())) {
            log.info("[병동 회신 수신] 이미 처리된 요청(중복 이벤트로 추정) 스킵 admissionRequestId={}, requestStatus={}",
                    admissionRequestId, admissionRequest.getRequestStatus());
            return;
        }

        admissionRequest.setRequestStatus(status);
        admissionRequest.setRespondedAt(LocalDateTime.now());
        if (AdmissionEventDto.STATUS_BED_ASSIGNED.equals(status)) {
            admissionRequest.setAdmissionId(admissionId);
            admissionRequest.setAssignedWardCode(assignedWardCode);
        } else {
            admissionRequest.setRejectReason(rejectReason);
        }
        admissionRequestRepository.save(admissionRequest);
    }

    private AdmissionRequest findAdmissionRequest(String admissionRequestId) {
        return admissionRequestRepository.findById(admissionRequestId)
                // 입원 요청 정보를 찾을 수 없습니다.
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "Admission request not found. admissionRequestId=" + admissionRequestId));
    }

    private AdmissionRequestDto toResponse(AdmissionRequest entity) {
        AdmissionRequestDto dto = admissionRequestMapper.toDto(entity);
        fillCodeNames(dto);
        return dto;
    }

    // 진료과/요청상태/배정병동 코드 -> 이름 채우기 (ADM 공통코드, 캐시에 없으면 null로 둔다)
    private void fillCodeNames(AdmissionRequestDto dto) {
        commonCodeCache.findCodeName(DEPT_CODE_GROUP, dto.getAdmissionDeptCode())
                .ifPresent(dto::setAdmissionDeptName);
        commonCodeCache.findCodeName(REQUEST_STATUS_CODE_GROUP, dto.getRequestStatus())
                .ifPresent(dto::setRequestStatusName);
        commonCodeCache.findCodeName(WARD_CODE_GROUP, dto.getAssignedWardCode())
                .ifPresent(dto::setAssignedWardName);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
