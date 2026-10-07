package kr.co.seoulit.his.outpatientservice.prescription.service;

import kr.co.seoulit.his.outpatientservice.common.cache.CommonCodeCache;
import kr.co.seoulit.his.outpatientservice.common.client.lab.LabClient;
import kr.co.seoulit.his.outpatientservice.common.client.lab.LabResultEventDto;
import kr.co.seoulit.his.outpatientservice.common.client.patient.PatientApiDto;
import kr.co.seoulit.his.outpatientservice.common.client.patient.PatientClient;
import kr.co.seoulit.his.outpatientservice.common.client.pharmacy.PharmacyApiDto;
import kr.co.seoulit.his.outpatientservice.common.client.pharmacy.PharmacyClient;
import kr.co.seoulit.his.outpatientservice.common.client.pharmacy.PharmacyEventDto;
import kr.co.seoulit.his.outpatientservice.common.client.pharmacy.PharmacyPublisher;
import kr.co.seoulit.his.outpatientservice.common.exception.BusinessException;
import kr.co.seoulit.his.outpatientservice.common.exception.ErrorCode;
import kr.co.seoulit.his.outpatientservice.prescription.dto.PrescriptionDto;
import kr.co.seoulit.his.outpatientservice.prescription.entity.ExamResultRef;
import kr.co.seoulit.his.outpatientservice.prescription.entity.Prescription;
import kr.co.seoulit.his.outpatientservice.prescription.entity.PrescriptionItem;
import kr.co.seoulit.his.outpatientservice.prescription.mapper.PrescriptionMapper;
import kr.co.seoulit.his.outpatientservice.prescription.repository.ExamResultRefRepository;
import kr.co.seoulit.his.outpatientservice.prescription.repository.PrescriptionItemRepository;
import kr.co.seoulit.his.outpatientservice.prescription.repository.PrescriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import kr.co.seoulit.his.outpatientservice.outpatientcare.entity.Encounter;
import kr.co.seoulit.his.outpatientservice.outpatientcare.repository.EncounterRepository;
import kr.co.seoulit.his.outpatientservice.prescription.dto.PrescriptionCreateDto;
import kr.co.seoulit.his.outpatientservice.prescription.dto.PrescriptionItemDto;
import kr.co.seoulit.his.outpatientservice.common.client.lab.LabOrderApiDto;
import kr.co.seoulit.his.outpatientservice.common.client.lab.LabOrderDispatcher;
import kr.co.seoulit.his.outpatientservice.common.client.lab.LabOrderEventDto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PrescriptionServiceImpl implements PrescriptionService {

    private final PrescriptionRepository prescriptionRepository;
    private final PrescriptionItemRepository prescriptionItemRepository;
    private final PrescriptionMapper prescriptionMapper;
    private final PatientClient patientClient;
    private final EncounterRepository encounterRepository;
    private final LabOrderDispatcher labOrderDispatcher;
    private final PharmacyPublisher pharmacyPublisher;
    private final PharmacyClient pharmacyClient;
    private final CommonCodeCache commonCodeCache;
    private final ExamResultRefRepository examResultRefRepository;
    private final LabClient labClient;

    private static final String PRIORITY_CODE_GROUP = "ORDER_PRIORITY_CD";
    private static final String TIMING_CODE_GROUP = "ORDER_TIMING_CD";
    private static final String ORDER_METHOD_CODE_GROUP = "ORDER_METHOD_CD";
    private static final String RESULT_ITEM_CODE_GROUP = "RESULT_ITEM_CD";
    private static final Set<String> VALID_DOSAGE_FORM_CDS = Set.of("01", "02", "03"); // ADM 공통코드 DOSAGE_FORM_CD: 01 알약/캡슐, 02 수액, 03 주사
    private static final int IN_CLAUSE_CHUNK_SIZE = 500; // Oracle IN 절 1000개 제한 회피
    private static final int MEDICATION_PAGE_MAX_SIZE = 100; // 약제 약품 목록 API의 한 번에 최대 건수

    // 처방 목록 조회
    // 목록은 N+1 방지를 위해 items[](검사결과 등)를 포함하지 않는다 — 응급은 orderId 선택용으로만 쓰고,
    // 검사결과/조제상태는 원래 정한 대로 Kafka 구독으로 받는다(처방코어를 다시 거치지 않기 위함).
    @Override
    @Transactional(readOnly = true)
    public List<PrescriptionDto> getPrescriptions(String keyword, String receptionId, String encounterId) {
        List<Prescription> prescriptions = prescriptionRepository.findAll(Sort.by(Sort.Direction.DESC, "prescribedAt"));

        List<PrescriptionDto> result = prescriptionMapper.toPrescriptionDtoList(prescriptions);
        fillPatientInfo(result);
        fillPriorityName(result);
        fillTimingName(result);
        fillOrderMethodName(result);

        // 응급이 자기 접수건(receptionId)의 처방만 찾을 때 사용 — 다른 사람이 만든 처방도 ID 없이 찾아야 해서 필요
        if (receptionId != null && !receptionId.isBlank()) {
            String trimmedReceptionId = receptionId.trim();
            result = result.stream()
                    .filter(dto -> trimmedReceptionId.equals(dto.getReceptionId()))
                    .collect(Collectors.toList());
        }

        // 외래가 자기 진료건(encounterId)의 처방만 찾을 때 사용
        if (encounterId != null && !encounterId.isBlank()) {
            String trimmedEncounterId = encounterId.trim();
            result = result.stream()
                    .filter(dto -> trimmedEncounterId.equals(dto.getEncounterId()))
                    .collect(Collectors.toList());
        }

        // 환자명/환자번호/환자ID 세팅이 끝난 후에 키워드로 필터링
        if (keyword != null && !keyword.isBlank()) {
            String trimmedKeyword = keyword.trim();
            result = result.stream()
                    .filter(dto -> (dto.getPatientName() != null && dto.getPatientName().contains(trimmedKeyword))
                            || (dto.getPatientId() != null && dto.getPatientId().contains(trimmedKeyword)))
                    .collect(Collectors.toList());
        }

        // 검사 상태 요약(labSendStatus/labResultStatus)은 조건 유무와 상관없이 항상 채운다
        // (처방 항목은 IN 조회로 한 번에 가져오므로 N+1 은 아님, 건수가 많으면 fill 안에서 나눠서 조회)
        fillLabSendStatusSummary(result);

        return result;
    }

    // 처방 상세 조회
    @Override
    @Transactional(readOnly = true)
    public PrescriptionDto getPrescription(String prescriptionId) {
        Prescription prescription = prescriptionRepository.findById(prescriptionId)
                // 처방 정보를 찾을 수 없습니다.
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Prescription not found. prescriptionId=" + prescriptionId));

        PrescriptionDto dto = prescriptionMapper.toPrescriptionDto(prescription);

        // 처방 상세 아이템 목록 조회 (목록 조회 API에는 N+1 방지를 위해 포함하지 않음)
        List<PrescriptionItem> items = prescriptionItemRepository.findByPrescriptionId(prescriptionId);
        dto.setItems(prescriptionMapper.toItemDtoList(items));
        fillResultDetails(dto.getItems());

        fillPatientInfo(List.of(dto));
        fillPriorityName(List.of(dto));
        fillTimingName(List.of(dto));
        fillOrderMethodName(List.of(dto));

        return dto;
    }

    // 검사 항목별 결과 상세(EXAM_RESULT_REF)를 한 번에 조회해서 채운다 (N+1 방지).
    // 구형식으로 저장된 결과(PRESCRIPTION_ITEM.RESULT_VALUE)는 seq=1 한 건으로 합쳐서 내려준다.
    private void fillResultDetails(List<PrescriptionItemDto> items) {
        List<String> labItemIds = items.stream()
                .filter(i -> "검사".equals(i.getPrescriptionType()))
                .map(PrescriptionItemDto::getItemId)
                .collect(Collectors.toList());
        if (labItemIds.isEmpty()) {
            return;
        }

        Map<String, List<ExamResultRef>> refsByItemId =
                examResultRefRepository.findByItemIdInOrderBySeqAsc(labItemIds).stream()
                        .collect(Collectors.groupingBy(ExamResultRef::getItemId));

        for (PrescriptionItemDto item : items) {
            if (!"검사".equals(item.getPrescriptionType())) {
                continue;
            }
            List<ExamResultRef> refs = refsByItemId.get(item.getItemId());
            if (refs != null) {
                List<PrescriptionItemDto.ResultDetail> details = prescriptionMapper.toResultDetailList(refs);
                // 결과항목명은 ADM 공통코드(RESULT_ITEM_CD). 미등록이거나 캐시 적재 전이면 null로 둔다
                details.forEach(d -> commonCodeCache.findCodeName(RESULT_ITEM_CODE_GROUP, d.getDetailCode())
                        .ifPresent(d::setDetailName));
                item.setResultDetails(details);
            } else if (item.getResultValue() != null) {
                PrescriptionItemDto.ResultDetail legacy = new PrescriptionItemDto.ResultDetail();
                legacy.setSeq(1);
                legacy.setResultValue(item.getResultValue());
                legacy.setResultUnit(item.getResultUnit());
                legacy.setReferenceRange(item.getReferenceRange());
                legacy.setAbnormalFlag(item.getAbnormalFlag());
                item.setResultDetails(List.of(legacy));
            }
        }
    }

    // 우선순위코드(ADM 공통코드 ORDER_PRIORITY_CD) -> 우선순위명 채우기
    private void fillPriorityName(List<PrescriptionDto> dtos) {
        for (PrescriptionDto dto : dtos) {
            commonCodeCache.findCodeName(PRIORITY_CODE_GROUP, dto.getPriorityCode())
                    .ifPresent(dto::setPriorityName);
        }
    }

    // 처방패턴코드(ADM 공통코드 ORDER_TIMING_CD) -> 처방패턴명 채우기
    private void fillTimingName(List<PrescriptionDto> dtos) {
        for (PrescriptionDto dto : dtos) {
            commonCodeCache.findCodeName(TIMING_CODE_GROUP, dto.getTimingCode())
                    .ifPresent(dto::setTimingName);
        }
    }

    // 처방유형코드(ADM 공통코드 ORDER_METHOD_CD) -> 처방유형명 채우기
    private void fillOrderMethodName(List<PrescriptionDto> dtos) {
        for (PrescriptionDto dto : dtos) {
            commonCodeCache.findCodeName(ORDER_METHOD_CODE_GROUP, dto.getOrderMethod())
                    .ifPresent(dto::setOrderMethodName);
        }
    }

    // 검사 전송상태(labSendStatus) + 결과도착상태(labResultStatus) 요약.
    // labSendStatus: 하나라도 FAILED면 FAILED, 아니면 하나라도 PENDING/미전송(null)이면 PENDING, 전부 SENT면 SENT.
    // labResultStatus: 결과가 하나도 안 왔으면 WAITING, 하나라도 왔으면 COMPLETE(전체 도착까진 구분 안 함 — 일부만 온 경우도 COMPLETE).
    // 둘 다 검사 항목이 없는 처방은 null로 둔다.
    // Oracle IN 절은 1000개를 넘으면 오류라서 IN_CLAUSE_CHUNK_SIZE 개씩 나눠서 조회한다.
    private void fillLabSendStatusSummary(List<PrescriptionDto> dtos) {
        List<String> prescriptionIds = dtos.stream()
                .map(PrescriptionDto::getPrescriptionId)
                .collect(Collectors.toList());
        if (prescriptionIds.isEmpty()) {
            return;
        }

        Map<String, List<PrescriptionItem>> labItemsByPrescriptionId = new HashMap<>();
        for (int from = 0; from < prescriptionIds.size(); from += IN_CLAUSE_CHUNK_SIZE) {
            List<String> chunk = new ArrayList<>(
                    prescriptionIds.subList(from, Math.min(from + IN_CLAUSE_CHUNK_SIZE, prescriptionIds.size())));
            for (PrescriptionItem item : prescriptionItemRepository.findByPrescriptionIdIn(chunk)) {
                if ("검사".equals(item.getPrescriptionType())) {
                    labItemsByPrescriptionId
                            .computeIfAbsent(item.getPrescriptionId(), k -> new ArrayList<>())
                            .add(item);
                }
            }
        }

        for (PrescriptionDto dto : dtos) {
            List<PrescriptionItem> labItems = labItemsByPrescriptionId.get(dto.getPrescriptionId());
            if (labItems == null || labItems.isEmpty()) {
                continue;
            }

            boolean anyFailed = labItems.stream().anyMatch(i -> "FAILED".equals(i.getSendStatus()));
            boolean anyPending = labItems.stream()
                    .anyMatch(i -> !"SENT".equals(i.getSendStatus()) && !"FAILED".equals(i.getSendStatus()));

            if (anyFailed) {
                dto.setLabSendStatus("FAILED");
            } else if (anyPending) {
                dto.setLabSendStatus("PENDING");
            } else {
                dto.setLabSendStatus("SENT");
            }

            boolean anyResultArrived = labItems.stream().anyMatch(i -> i.getResultReportedAt() != null);
            dto.setLabResultStatus(anyResultArrived ? "COMPLETE" : "WAITING");
        }
    }

    // 환자 정보(PAT API) 일괄 연동하여 환자명/환자번호 채우기
    private void fillPatientInfo(List<PrescriptionDto> dtos) {
        List<String> patientIds = dtos.stream()
                .map(PrescriptionDto::getPatientId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        if (patientIds.isEmpty()) {
            return;
        }

        Map<String, PatientApiDto.PatientSummary> patientMap = Collections.emptyMap();
        try {
            patientMap = patientClient.getPatients(patientIds);
        } catch (Exception e) {
            log.error("[PAT 연동 실패] 환자 기본정보 일괄조회 실패: {}", e.getMessage());
        }

        for (PrescriptionDto dto : dtos) {
            PatientApiDto.PatientSummary patient = patientMap.get(dto.getPatientId());
            if (patient != null) {
                String rawName = patient.patientName();
                dto.setPatientName(rawName != null && rawName.startsWith("환자") ? rawName.substring(2) : rawName);
            }
        }
    }

    // 처방 등록
    @Override
    public PrescriptionDto createPrescription(String encounterId, PrescriptionCreateDto request) {
        validateDosageFormCd(request.getItems());
        Encounter encounter = encounterRepository.findById(encounterId)
                // 존재하지 않는 외래 진료 건입니다.
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "Outpatient encounter not found. encounterId=" + encounterId));

        Prescription prescription = new Prescription();
        prescription.setPrescriptionId(UUID.randomUUID().toString());
        prescription.setEncounterId(encounterId);
        prescription.setPatientId(encounter.getPatientId());
        prescription.setPrescribedBy(encounter.getDoctorId());
        prescription.setServiceType(request.getServiceType());
        prescription.setStatus("ORDERED");
        prescription.setPrescribedAt(LocalDateTime.now());
        prescription.setOrderMethod(request.getOrderMethod());
        prescription.setPriorityCode(request.getPriorityCode());
        prescription.setTimingCode(request.getTimingCode());
        prescription.setPharmacySendStatus(hasPharmacyItems(request.getItems()) ? "PENDING" : null);
        Prescription saved = prescriptionRepository.save(prescription);

        List<PrescriptionItem> items = buildItems(saved.getPrescriptionId(), request.getItems());
        prescriptionItemRepository.saveAll(items);

        PrescriptionDto dto = prescriptionMapper.toPrescriptionDto(saved);
        dto.setItems(prescriptionMapper.toItemDtoList(items));
        fillPatientInfo(List.of(dto));
        fillPriorityName(List.of(dto));
        fillTimingName(List.of(dto));
        fillOrderMethodName(List.of(dto));
        return dto;
    }

    // 입원(admission) 처방 등록 — 병동은 서버 간 호출(로그인 세션 없음)이라 patientId/prescribedBy를 요청 바디로 직접 받는다
    @Override
    public PrescriptionDto createPrescriptionForAdmission(String admissionId, PrescriptionCreateDto request) {
        if (request.getPatientId() == null || request.getPrescribedBy() == null) {
            // 입원 경로는 Encounter가 없어 patientId/prescribedBy를 세션/encounter로 채울 수 없습니다.
            throw new BusinessException(ErrorCode.INVALID_INPUT, "patientId and prescribedBy are required for admission prescriptions.");
        }
        validateDosageFormCd(request.getItems());

        Prescription prescription = new Prescription();
        prescription.setPrescriptionId(UUID.randomUUID().toString());
        prescription.setAdmissionId(admissionId);
        prescription.setPatientId(request.getPatientId());
        prescription.setPrescribedBy(request.getPrescribedBy());
        prescription.setDepartmentCode(request.getDepartmentCode());
        prescription.setServiceType(request.getServiceType());
        prescription.setStatus("ORDERED");
        prescription.setPrescribedAt(LocalDateTime.now());
        prescription.setOrderMethod(request.getOrderMethod());
        prescription.setPriorityCode(request.getPriorityCode());
        prescription.setTimingCode(request.getTimingCode());
        prescription.setPharmacySendStatus(hasPharmacyItems(request.getItems()) ? "PENDING" : null);
        Prescription saved = prescriptionRepository.save(prescription);

        List<PrescriptionItem> items = buildItems(saved.getPrescriptionId(), request.getItems());
        prescriptionItemRepository.saveAll(items);

        PrescriptionDto dto = prescriptionMapper.toPrescriptionDto(saved);
        dto.setItems(prescriptionMapper.toItemDtoList(items));
        fillPatientInfo(List.of(dto));
        fillPriorityName(List.of(dto));
        fillTimingName(List.of(dto));
        fillOrderMethodName(List.of(dto));
        return dto;
    }

    // 응급(emergency) 처방 등록 — 병동과 동일하게 서버 간 호출(로그인 세션 없음)이라 patientId/prescribedBy를 요청 바디로 직접 받는다
    @Override
    public PrescriptionDto createPrescriptionForEmergency(String receptionId, PrescriptionCreateDto request) {
        if (request.getPatientId() == null || request.getPrescribedBy() == null) {
            // 응급 경로는 Encounter가 없어 patientId/prescribedBy를 세션/encounter로 채울 수 없습니다.
            throw new BusinessException(ErrorCode.INVALID_INPUT, "patientId and prescribedBy are required for emergency prescriptions.");
        }
        validateDosageFormCd(request.getItems());
        // 자동 전송은 Encounter가 없는 응급 경로라 등록 시 받은 departmentCode가 있어야 약제 이벤트를 만들 수 있다 — 저장 전에 막는다.
        if (Boolean.TRUE.equals(request.getDispatchNow()) && hasPharmacyItems(request.getItems())
                && (request.getDepartmentCode() == null || request.getDepartmentCode().isBlank())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "departmentCode is required when dispatchNow=true and the prescription has medication items.");
        }

        Prescription prescription = new Prescription();
        prescription.setPrescriptionId(UUID.randomUUID().toString());
        prescription.setReceptionId(receptionId);
        prescription.setPatientId(request.getPatientId());
        prescription.setPrescribedBy(request.getPrescribedBy());
        prescription.setDepartmentCode(request.getDepartmentCode());
        prescription.setServiceType(request.getServiceType());
        prescription.setStatus("ORDERED");
        prescription.setPrescribedAt(LocalDateTime.now());
        prescription.setOrderMethod(request.getOrderMethod());
        prescription.setPriorityCode(request.getPriorityCode());
        prescription.setTimingCode(request.getTimingCode());
        prescription.setVerbalYn(request.getVerbalYn());
        prescription.setPharmacySendStatus(hasPharmacyItems(request.getItems()) ? "PENDING" : null);
        Prescription saved = prescriptionRepository.save(prescription);

        List<PrescriptionItem> items = buildItems(saved.getPrescriptionId(), request.getItems());
        prescriptionItemRepository.saveAll(items);

        PrescriptionDto dto = prescriptionMapper.toPrescriptionDto(saved);
        dto.setItems(prescriptionMapper.toItemDtoList(items));
        fillPatientInfo(List.of(dto));
        fillPriorityName(List.of(dto));
        fillTimingName(List.of(dto));
        fillOrderMethodName(List.of(dto));
        return dto;
    }

    // 구두처방 확정 — verbalYn=Y인 처방에만 허용, 이미 확정된 처방은 CONFLICT
    @Override
    @Transactional
    public PrescriptionDto confirmVerbalOrder(String prescriptionId, String confirmedBy) {
        if (confirmedBy == null || confirmedBy.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "confirmedBy is required.");
        }

        Prescription prescription = prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "Prescription not found. prescriptionId=" + prescriptionId));

        if (!"Y".equals(prescription.getVerbalYn())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "This prescription is not a verbal order. prescriptionId=" + prescriptionId);
        }
        if (prescription.getVerbalConfirmedAt() != null) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "This verbal order has already been confirmed. prescriptionId=" + prescriptionId);
        }

        prescription.setVerbalConfirmedAt(LocalDateTime.now());
        prescription.setVerbalConfirmedBy(confirmedBy);
        Prescription saved = prescriptionRepository.save(prescription);

        PrescriptionDto dto = prescriptionMapper.toPrescriptionDto(saved);
        List<PrescriptionItem> items = prescriptionItemRepository.findByPrescriptionId(prescriptionId);
        dto.setItems(prescriptionMapper.toItemDtoList(items));
        fillPatientInfo(List.of(dto));
        fillPriorityName(List.of(dto));
        fillTimingName(List.of(dto));
        fillOrderMethodName(List.of(dto));
        return dto;
    }

    // 약품 항목이 하나라도 있는지 — 없으면 pharmacySendStatus를 PENDING이 아니라 null로 둔다(약제실 전송 대상 자체가 아님을 구분)
    private static boolean hasPharmacyItems(List<PrescriptionItemDto> itemDtos) {
        return itemDtos != null && itemDtos.stream().anyMatch(item -> "약품".equals(item.getPrescriptionType()));
    }

    // 약품 항목의 투약형태코드가 PHM이 받는 숫자코드(01/02/03)인지 검증 — 문자값/미입력은 PHM에서 PHM009로 거절되고 재시도 없이 유실된다
    private static void validateDosageFormCd(List<PrescriptionItemDto> itemDtos) {
        if (itemDtos == null) {
            return;
        }
        for (PrescriptionItemDto item : itemDtos) {
            if (!"약품".equals(item.getPrescriptionType())) {
                continue;
            }
            if (item.getDosageFormCd() == null || !VALID_DOSAGE_FORM_CDS.contains(item.getDosageFormCd())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "dosageFormCd must be one of " + VALID_DOSAGE_FORM_CDS + " for medication items. itemCode="
                                + item.getItemCode() + ", dosageFormCd=" + item.getDosageFormCd());
            }
        }
    }

    private List<PrescriptionItem> buildItems(String prescriptionId, List<PrescriptionItemDto> itemDtos) {
        if (itemDtos == null) {
            return List.of();
        }
        return itemDtos.stream().map(req -> {
            PrescriptionItem item = new PrescriptionItem();
            item.setItemId(UUID.randomUUID().toString());
            item.setPrescriptionId(prescriptionId);
            item.setPrescriptionType(req.getPrescriptionType());
            item.setItemCode(req.getItemCode());
            item.setItemName(req.getItemName());
            item.setDosage(req.getDosage());
            item.setDosageFormCd(req.getDosageFormCd());
            item.setFrequency(req.getFrequency());
            item.setDurationDays(req.getDurationDays());
            item.setDetailInfo(req.getDetailInfo());
            return item;
        }).collect(Collectors.toList());
    }

    // 처방의 진료구분(serviceType) -> 검사서비스 채널 구분(OPD/ER/IP).
    // 데이터에 OP/외래/ADMISSION 등이 섞여 있어 알려진 값만 변환하고, 모르는 값은 null로 보낸다(검사서비스가 OPD로 처리).
    static String toEncounterType(String serviceType) {
        if (serviceType == null) {
            return null;
        }
        return switch (serviceType.trim().toUpperCase()) {
            case "OP", "OPD", "외래" -> LabOrderEventDto.ENCOUNTER_TYPE_OPD;
            case "ER", "EMERGENCY", "응급" -> LabOrderEventDto.ENCOUNTER_TYPE_ER;
            case "IP", "IPD", "ADMISSION", "입원" -> LabOrderEventDto.ENCOUNTER_TYPE_IP;
            default -> null;
        };
    }

    // 응급 여부: ER 채널이거나 우선순위가 STAT이면 Y (검사서비스와 합의).
    // 우선순위는 ADM 공통코드 ORDER_PRIORITY_CD 기준 STAT="01"이고, 옛 데이터에는 문자열 "STAT"이 있을 수 있어 둘 다 인정한다.
    static boolean isUrgent(String encounterType, String priorityCode) {
        if (LabOrderEventDto.ENCOUNTER_TYPE_ER.equals(encounterType)) {
            return true;
        }
        return priorityCode != null && ("01".equals(priorityCode.trim()) || "STAT".equalsIgnoreCase(priorityCode.trim()));
    }

    // 처방 아이템 중 검사(Lab) 항목만 모아서 검사실로 한 번에 전송
    @Override
    @Transactional
    public List<PrescriptionItemDto> dispatchLabOrders(String prescriptionId) {
        Prescription prescription = prescriptionRepository.findById(prescriptionId)
                // 처방 정보를 찾을 수 없습니다.
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "Prescription not found. prescriptionId=" + prescriptionId));

        List<PrescriptionItem> items = prescriptionItemRepository.findByPrescriptionId(prescriptionId);
        // SENT(이미 성공)·PENDING(응답 대기 중 — LAB이 이미 받았을 수 있음)인 항목은 재전송 대상에서 뺀다.
        // dispatch-lab이 여러 번 호출돼도(재시도 등) 이미 처리 중/완료된 항목을 또 보내서 LAB의 중복 거절로
        // 기존 성공 상태가 덮어써지는 사고를 막기 위함 — 아직 안 보냈거나(null) 확실히 실패한 항목만 재전송한다.
        List<PrescriptionItem> labItems = items.stream()
                .filter(item -> "검사".equals(item.getPrescriptionType()))
                .filter(item -> item.getSendStatus() == null || "FAILED".equals(item.getSendStatus()))
                .collect(Collectors.toList());

        if (!labItems.isEmpty()) {
            List<LabOrderApiDto.LabOrderItemRequestDto> orderItems = labItems.stream()
                    .map(item -> new LabOrderApiDto.LabOrderItemRequestDto(item.getItemCode(), item.getItemName()))
                    .collect(Collectors.toList());

            String encounterType = toEncounterType(prescription.getServiceType());
            LabOrderApiDto.LabOrderCreateRequestDto request = new LabOrderApiDto.LabOrderCreateRequestDto(
                    prescription.getPrescriptionId(),
                    prescription.getEncounterId(),
                    prescription.getPatientId(),
                    prescription.getPrescribedBy(),
                    encounterType,
                    isUrgent(encounterType, prescription.getPriorityCode()) ? "Y" : "N",
                    prescription.getAdmissionId(),
                    prescription.getReceptionId(),
                    orderItems
            );

            LabOrderApiDto.DispatchOutcome outcome = labOrderDispatcher.dispatch(request);

            LocalDateTime now = LocalDateTime.now();
            for (PrescriptionItem item : labItems) {
                item.setSendStatus(outcome.status());
                item.setSentAt(now);
                item.setLabOrderId(outcome.labOrderId());
                item.setRejectReason(outcome.rejectReason());
            }
            prescriptionItemRepository.saveAll(labItems);
        }

        return prescriptionMapper.toItemDtoList(items);
    }

    //검사실측에서 처리를끝내고 보낸 결과이벤트를 받아 처방항목상태를 확정함
    // 검사서비스가 결과 이벤트(ACCEPTED/REJECTED)로 회신한 내용을 PENDING 상태였던 항목에 반영
    // LabOrderResultListener가 호출 - 이미 SENT/FAILED로 확정된 항목이면 중복 이벤트로 보고 건너뛴다
    @Override
    @Transactional
    public void applyLabOrderResult(String prescriptionId, String status, String labOrderId, String rejectReason) {
        List<PrescriptionItem> items = prescriptionItemRepository.findByPrescriptionId(prescriptionId);
        List<PrescriptionItem> pendingLabItems = items.stream()
                .filter(item -> "검사".equals(item.getPrescriptionType()))
                .filter(item -> "PENDING".equals(item.getSendStatus()))
                .collect(Collectors.toList());

        if (pendingLabItems.isEmpty()) {
            log.info("[LAB 결과 수신] 반영할 PENDING 항목 없음(중복 이벤트로 추정) prescriptionId={}", prescriptionId);
            return;
        }

        String sendStatus = LabOrderEventDto.STATUS_ACCEPTED.equals(status) ? "SENT" : "FAILED";
        LocalDateTime now = LocalDateTime.now();
        for (PrescriptionItem item : pendingLabItems) {
            item.setSendStatus(sendStatus);
            item.setLabOrderId(labOrderId);
            item.setRejectReason(rejectReason);
            item.setSentAt(now);
        }
        prescriptionItemRepository.saveAll(pendingLabItems);
    }

    //검사결과조회
    @Override
    @Transactional
    public void applyLabResult(String eventId, LabResultEventDto.ResultData data, LabResultEventDto.ResultItem item) {
        String prescriptionId = data.prescriptionId();

        if (eventId == null || item.itemCode() == null) {
            log.warn("[LAB 결과 수신] eventId/itemCode 누락으로 스킵 prescriptionId={}", prescriptionId);
            return;
        }

        if (!LabResultEventDto.RESULT_TYPE_GENERAL.equals(item.resultType())) {
            log.info("[LAB 결과 수신] 미지원 resultType={} 스킵 prescriptionId={}, itemCode={}",
                    item.resultType(), prescriptionId, item.itemCode());
            return;
        }

        // 신형식은 details, 구형식은 최상위 resultValue가 채워진다. 둘 다 비면 빈 결과가 저장되므로 스킵
        boolean hasDetails = item.details() != null && !item.details().isEmpty();
        if (!hasDetails && item.resultValue() == null) {
            log.warn("[LAB 결과 수신] details/resultValue 모두 없음 스킵 prescriptionId={}, itemCode={}",
                    prescriptionId, item.itemCode());
            return;
        }

        List<PrescriptionItem> items = prescriptionItemRepository.findByPrescriptionId(prescriptionId);
        PrescriptionItem target = items.stream()
                .filter(i -> "검사".equals(i.getPrescriptionType()))
                .filter(i -> item.itemCode().equals(i.getItemCode()))
                .filter(i -> "SENT".equals(i.getSendStatus()))
                .findFirst()
                .orElse(null);

        if (target == null) {
            log.info("[LAB 결과 수신] 대상 검사항목 없음(중복/오류로 추정) prescriptionId={}, itemCode={}", prescriptionId, item.itemCode());
            return;
        }

        if (eventId.equals(target.getResultEventId())) {
            log.info("[LAB 결과 수신] 중복 eventId={} 스킵 prescriptionId={}, itemCode={}", eventId, prescriptionId, item.itemCode());
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        // 검사서비스가 보내는 결과 확정 시각(+09:00)을 KST로 맞춘다. 없으면 수신 시각으로 대체
        LocalDateTime resultedAt = data.reportedAt() != null
                ? data.reportedAt().atZoneSameInstant(java.time.ZoneId.of("Asia/Seoul")).toLocalDateTime()
                : now;

        if (hasDetails) {
            // 검사서비스에 정정 기능이 없어, 이미 결과가 있는 항목에 다른 eventId가 오면 비정상이다 → 덮어쓰지 않고 스킵
            if (examResultRefRepository.existsByItemId(target.getItemId())) {
                log.warn("[LAB 결과 수신] 이미 결과가 있는 항목에 다른 eventId={} 수신, 스킵 itemId={}", eventId, target.getItemId());
                return;
            }

            String patientId = prescriptionRepository.findById(prescriptionId)
                    .map(Prescription::getPatientId)
                    .orElse(null);

            List<ExamResultRef> refs = item.details().stream().map(d -> {
                ExamResultRef ref = new ExamResultRef();
                ref.setResultRefId(UUID.randomUUID().toString());
                ref.setItemId(target.getItemId());
                ref.setPatientId(patientId);
                ref.setLabResultId(item.resultId());
                ref.setResultStatus(data.resultStatus());
                ref.setResultedAt(resultedAt);
                ref.setCachedAt(now);
                ref.setSeq(d.seq());
                ref.setDetailCode(d.detailCode());
                ref.setResultValue(d.resultValue());
                ref.setResultUnit(d.unit());
                ref.setReferenceRange(d.referenceRange());
                ref.setAbnormalFlag(d.abnormalFlag());
                return ref;
            }).collect(Collectors.toList());
            examResultRefRepository.saveAll(refs);
        } else {
            // 구형식(최상위 값) 호환 — 검사서비스가 신형식으로 전환하기 전까지 유지
            target.setResultValue(item.resultValue());
            target.setResultUnit(item.unit());
            target.setReferenceRange(item.referenceRange());
            target.setAbnormalFlag(item.abnormalFlag());
        }

        target.setResultReportedAt(resultedAt);
        target.setResultEventId(eventId);
        prescriptionItemRepository.save(target);
    }

    // 처방 아이템 중 약품 항목만 모아서 약제실로 전송하고, 처리 후의 pharmacySendStatus를 돌려준다
    @Override
    @Transactional
    public String dispatchPharmacyOrders(String prescriptionId) {
        Prescription prescription = prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "Prescription not found. prescriptionId=" + prescriptionId));

        // 취소된 처방을 나중에 전송하면 약제가 취소 통보 없이 새 처방으로 접수해 버린다 (취소 이벤트는 SENT인 처방에만 나간다).
        if ("CANCELLED".equals(prescription.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "A cancelled prescription cannot be dispatched to the pharmacy. prescriptionId=" + prescriptionId);
        }

        // 이미 약제실로 발행된 처방은 재발행하지 않는다 — 재호출(재시도/중복 호출)로 같은 처방이 약제서비스에 중복 접수되는 것을 막는다.
        // PENDING(아직 안 보냄)·FAILED(발행 실패)만 발행 대상이다.
        if ("SENT".equals(prescription.getPharmacySendStatus())) {
            log.info("[약제 전송] 이미 SENT 상태라 재발행 스킵 prescriptionId={}", prescriptionId);
            return prescription.getPharmacySendStatus();
        }

        // 외래 경로는 Encounter에서 처방과 코드를 가져오고, 입원 경로는 Encounter가 없어 등록 시 받은 departmentCode를 그대로 쓴다.
        String departmentCode;
        if (prescription.getEncounterId() != null) {
            Encounter encounter = encounterRepository.findById(prescription.getEncounterId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                            "Outpatient encounter not found. encounterId=" + prescription.getEncounterId()));
            departmentCode = encounter.getDepartmentCode();
        } else {
            departmentCode = prescription.getDepartmentCode();
            if (departmentCode == null) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "Department code missing for admission prescription. prescriptionId=" + prescriptionId);
            }
        }

        List<PrescriptionItem> items = prescriptionItemRepository.findByPrescriptionId(prescriptionId);
        List<PrescriptionItem> pharmacyItems = items.stream()
                .filter(item -> "약품".equals(item.getPrescriptionType()))
                .collect(Collectors.toList());

        if (pharmacyItems.isEmpty()) {
            return prescription.getPharmacySendStatus();
        }

        List<PharmacyEventDto.PharmacyOrderItem> orderItems = pharmacyItems.stream()
                .map(item -> new PharmacyEventDto.PharmacyOrderItem(
                        item.getItemCode(),
                        item.getItemName(),
                        item.getDosage(),
                        item.getDosageFormCd(),
                        item.getFrequency(),
                        item.getDurationDays(),
                        item.getDetailInfo()
                ))
                .collect(Collectors.toList());

        PharmacyEventDto.PharmacyOrderData data = new PharmacyEventDto.PharmacyOrderData(
                prescription.getPrescriptionId(),
                prescription.getPatientId(),
                prescription.getPrescribedBy(),
                departmentCode,
                prescription.getPrescribedAt().atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime(),
                toEncounterType(prescription.getServiceType()),
                normalizePriorityCode(prescription.getPriorityCode()),
                prescription.getVerbalYn(),
                orderItems
        );

        boolean sent = pharmacyPublisher.publish(data);

        prescription.setPharmacySendStatus(sent ? "SENT" : "FAILED");
        prescription.setPharmacySentAt(LocalDateTime.now());
        prescriptionRepository.save(prescription);
        return prescription.getPharmacySendStatus();
    }

    // 우선순위 코드는 ADM 공통코드(ORDER_PRIORITY_CD: 01 STAT, 02 Urgent, 03 Routine)로 약제에 전달한다.
    // 옛 데이터에는 STAT/URGENT/ROUTINE 문자열이 섞여 있어 알려진 값만 코드로 바꾸고, 그 외는 그대로 둔다.
    static String normalizePriorityCode(String priorityCode) {
        if (priorityCode == null) {
            return null;
        }
        String trimmed = priorityCode.trim();
        return switch (trimmed.toUpperCase()) {
            case "STAT" -> "01";
            case "URGENT" -> "02";
            case "ROUTINE" -> "03";
            default -> trimmed;
        };
    }

    // 약품 검색 (약제서비스 카탈로그 조회)
    @Override
    @Transactional(readOnly = true)
    public List<PharmacyApiDto.Medication> searchMedication(String name) {
        return pharmacyClient.searchMedication(name);
    }

    // 약품 목록 (약제서비스 카탈로그를 이름 필터 + 페이지로 조회)
    // 약제는 약가코드(ediCode)가 없는 약의 처방을 접수하지 않으므로 코드 없는 약은 목록에서 뺀다.
    // 그래서 content가 요청한 size보다 적을 수 있고, totalElements/totalPages/last는 거르기 전 약제 값 그대로다.
    @Override
    @Transactional(readOnly = true)
    public PharmacyApiDto.MedicationPage listMedications(String name, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MEDICATION_PAGE_MAX_SIZE);

        PharmacyApiDto.MedicationPage result = pharmacyClient.listMedications(name, safePage, safeSize);

        List<PharmacyApiDto.Medication> prescribable = result.content() == null ? List.of()
                : result.content().stream()
                        .filter(med -> med.ediCode() != null && !med.ediCode().isBlank())
                        .collect(Collectors.toList());
        return new PharmacyApiDto.MedicationPage(prescribable, result.totalElements(), result.totalPages(),
                result.number(), result.size(), result.first(), result.last());
    }

    // 검사 항목 검색 (검사서비스 카탈로그 조회)
    @Override
    @Transactional(readOnly = true)
    public List<LabOrderApiDto.LabItem> searchLabItem(String name) {
        return labClient.searchLabItem(name);
    }

    //처방 비활성화
    @Override
    public void deactivatePrescription(String prescriptionId, String cancelReason, String userId) {
        Prescription prescription = prescriptionRepository.findById(prescriptionId)
                //비활성화 처방을 찾을 수 없음
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "Prescription to deactivate not found. prescriptionId=" + prescriptionId));

        // 이미 취소된 처방을 다시 비활성화하면 검사실로 취소 이벤트가 중복 발행되므로 막는다.
        if ("CANCELLED".equals(prescription.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "The prescription has already been deactivated. prescriptionId=" + prescriptionId);
        }

        prescription.setStatus("CANCELLED");
        prescription.setCancelledAt(LocalDateTime.now());
        prescription.setCancelReason(cancelReason);
        prescriptionRepository.save(prescription);

        cancelLabOrders(prescriptionId, cancelReason, userId);
        cancelPharmacyOrders(prescription, cancelReason, userId);
    }

    // 약제실에 이미 발행(SENT)한 처방이면 취소를 통보한다. 처방 전체 단위이고 약제의 회신(거부 등)은 받지 않는다.
    // 발행 전(PENDING/FAILED)·약품 없음(null) 처방은 약제가 받은 적이 없으므로 대상이 아니다.
    // 통보 실패가 처방 비활성화 자체를 막지는 않는다(재시도 없음 — 약제에 도착하지 않을 수 있다).
    private void cancelPharmacyOrders(Prescription prescription, String cancelReason, String userId) {
        if (!"SENT".equals(prescription.getPharmacySendStatus())) {
            return;
        }

        List<PharmacyEventDto.PharmacyCancelledItem> cancelledItems = prescriptionItemRepository
                .findByPrescriptionId(prescription.getPrescriptionId()).stream()
                .filter(item -> "약품".equals(item.getPrescriptionType()))
                .map(item -> new PharmacyEventDto.PharmacyCancelledItem(item.getItemCode(), item.getItemName()))
                .collect(Collectors.toList());

        boolean delivered = pharmacyPublisher.publishCancel(new PharmacyEventDto.PharmacyOrderCancelledData(
                prescription.getPrescriptionId(), cancelReason, userId, cancelledItems));
        if (!delivered) {
            log.error("[약제 취소 통보 실패] 처방은 비활성화됐으나 약제실에 전달되지 않음 prescriptionId={}",
                    prescription.getPrescriptionId());
        }
    }

    // 이미 검사실에 전달된(SENT/PENDING) 검사 항목이 있으면 검사오더 취소를 통보한다.
    // 미전송(null)/실패(FAILED) 항목은 검사실이 받은 적이 없으므로 대상이 아니다.
    // 통보 실패가 처방 비활성화 자체를 막지는 않는다(실패는 dispatcher가 로그로 남김).
    private void cancelLabOrders(String prescriptionId, String cancelReason, String userId) {
        List<LabOrderApiDto.LabOrderCancelItemDto> cancelItems = prescriptionItemRepository
                .findByPrescriptionId(prescriptionId).stream()
                .filter(item -> "검사".equals(item.getPrescriptionType()))
                .filter(item -> "SENT".equals(item.getSendStatus()) || "PENDING".equals(item.getSendStatus()))
                .map(item -> new LabOrderApiDto.LabOrderCancelItemDto(
                        item.getItemCode(), item.getItemName(), item.getLabOrderId()))
                .collect(Collectors.toList());

        if (cancelItems.isEmpty()) {
            return;
        }

        boolean delivered = labOrderDispatcher.cancel(new LabOrderApiDto.LabOrderCancelRequestDto(
                prescriptionId, cancelReason, userId, cancelItems));
        if (!delivered) {
            log.error("[LAB 취소 통보 실패] 처방은 비활성화됐으나 검사실에 전달되지 않음 prescriptionId={}", prescriptionId);
        }
    }
}
