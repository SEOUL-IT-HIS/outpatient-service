package kr.co.seoulit.his.outpatientservice.outpatientcare.service;

import kr.co.seoulit.his.outpatientservice.common.client.admin.AuditLogClient;
import kr.co.seoulit.his.outpatientservice.common.client.admin.PersonalInfoAccessRequest;
import kr.co.seoulit.his.outpatientservice.common.client.patient.PatientApiDto;
import kr.co.seoulit.his.outpatientservice.common.client.patient.PatientClient;
import kr.co.seoulit.his.outpatientservice.common.exception.BusinessException;
import kr.co.seoulit.his.outpatientservice.common.exception.ErrorCode;
import kr.co.seoulit.his.outpatientservice.outpatientcare.dto.EncounterCreateDto;
import kr.co.seoulit.his.outpatientservice.outpatientcare.dto.EncounterDto;
import kr.co.seoulit.his.outpatientservice.outpatientcare.dto.EncounterSearchDto;
import kr.co.seoulit.his.outpatientservice.outpatientcare.dto.MedicalRecordDto;
import kr.co.seoulit.his.outpatientservice.outpatientcare.dto.ReceptionDto;
import kr.co.seoulit.his.outpatientservice.outpatientcare.entity.Encounter;
import kr.co.seoulit.his.outpatientservice.outpatientcare.entity.MedicalRecord;
import kr.co.seoulit.his.outpatientservice.outpatientcare.mapper.OutpatientCareMapper;
import kr.co.seoulit.his.outpatientservice.outpatientcare.repository.EncounterRepository;
import kr.co.seoulit.his.outpatientservice.outpatientcare.repository.MedicalRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutpatientCareServiceImpl implements OutpatientCareService {

    private final EncounterRepository encounterRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final OutpatientCareMapper outpatientCareMapper;

    // 1. 공통 모듈에 선언되어 있던 PatientClient 주입 (PAT 연동용)
    private final PatientClient patientClient;

    // 2. 공통 모듈에 선언되어 있던 AuditLogClient 주입 (개인정보 열람 감사 로그 -> ADM 연동용)
    private final AuditLogClient auditLogClient;

    private final RestTemplate restTemplate = new RestTemplate();

    private static final String SERVICE_CODE = "OPD"; // application.yml의 app.service.code 와 동일

    // --- [당일 대기 환자 목록 조회 - RCP(접수) + PAT(환자) API 연동] ---
    // RCP는 전체 목록 API가 없고 GET /receptions/waiting(대기 환자)만 제공한다(2026-07-29 확인).
    // OUTPATIENT_ENCOUNTER 테이블(EncounterRepository)은 OPD가 담당의/진료과 배정과 진료상태를 관리하는
    // 별도 장부라 이 목록 조회에는 아직 관여하지 않는다 — 추후 두 데이터를 합치는 단계가 별도로 필요하다.
    @Override
    public List<EncounterDto> getEncounters(EncounterSearchDto request) {
        // [단계 1] 검색 파라미터 처리 (진료과 코드는 RCP 쪽에서 필터링해서 내려주도록 쿼리로 전달)
        String deptCode = (request != null && request.getDepartmentCode() != null) ? request.getDepartmentCode() : "";

        // [단계 2] RCP(접수) 마이크로서비스 API 호출 - 대기 환자 목록 조회
        // RCP가 죽어있어도 목록 조회 자체는 막히지 않도록 PAT 연동과 동일하게 감싼다.
        List<ReceptionDto> waitingList;
        try {
            String url = "http://localhost:8081/receptions/waiting?deptCode=" + deptCode;
            ReceptionDto[] response = restTemplate.getForObject(url, ReceptionDto[].class);
            waitingList = (response != null) ? Arrays.asList(response) : Collections.emptyList();
        } catch (Exception e) {
            log.error("[RCP 연동 실패] 대기 환자 목록 조회 실패: {}", e.getMessage());
            waitingList = Collections.emptyList();
        }

        // [단계 3] 상태 필터 (status) - 주의: RCP /waiting은 대기 상태만 내려주므로, WAITING이 아닌
        // 상태로 검색하면 항상 빈 목록이 된다. RCP에 전체 상태 조회 API가 생기기 전까지는 알려진 제약이다.
        if (request != null && request.getStatus() != null && !request.getStatus().isBlank()) {
            String status = request.getStatus().trim();
            waitingList = waitingList.stream()
                    .filter(r -> status.equalsIgnoreCase(r.getStatus()))
                    .collect(Collectors.toList());
        }

        // [단계 4] PAT(환자) 마이크로서비스 API 연동 - patientId 수집 후 일괄조회 (batch-query)
        Map<String, PatientApiDto.PatientSummary> patientMap = Collections.emptyMap();
        List<String> patientIds = waitingList.stream()
                .map(ReceptionDto::getPatientId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        if (!patientIds.isEmpty()) {
            try {
                patientMap = patientClient.getPatients(patientIds);
            } catch (Exception e) {
                log.error("[PAT 연동 실패] 환자 기본정보 일괄조회 실패: {}", e.getMessage());
            }
        }

        // [단계 5] 감사 로그 남기기 (ADM 연동, GR2-28)
        log.info("[AUDIT LOG] 당일 대기 환자 목록 조회 완료 - RCP 목록: {}건, PAT 환자정보: {}건",
                waitingList.size(), patientMap.size());
        recordAudit("ENCOUNTER", deptCode.isBlank() ? "ALL" : deptCode, "LIST");

        // [단계 6] MapStruct로 RCP → EncounterDto 변환 후, PAT 환자정보(환자번호/환자명) 채우기
        // encounterId는 아직 OPD에서 담당의/진료과 배정 전이라 null로 남는다
        // (예전처럼 접수ID를 진료ID 자리에 대신 넣지 않는다 — 실제로 그런 진료 건이 아직 없기 때문).
        List<EncounterDto> result = outpatientCareMapper.toEncounterDtoListFromReception(waitingList);
        for (EncounterDto dto : result) {
            PatientApiDto.PatientSummary patient = patientMap.get(dto.getPatientId());
            if (patient != null) {
                dto.setPatientNo(patient.patientNo());
                dto.setPatientName(patient.patientName());
            }
        }

        // [단계 7] 정렬 (sort 예: status,asc / visitDate,desc)
        if (request != null && request.getSort() != null && !request.getSort().isBlank()) {
            String[] parts = request.getSort().split(",");
            String field = parts[0].trim();
            boolean desc = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim());
            Comparator<EncounterDto> comparator;
            if ("status".equals(field)) {
                comparator = Comparator.comparing(EncounterDto::getStatus, Comparator.nullsLast(String::compareTo));
            } else if ("visitDate".equals(field)) {
                comparator = Comparator.comparing(EncounterDto::getVisitDate, Comparator.nullsLast(LocalDate::compareTo));
            } else if ("createdAt".equals(field)) {
                comparator = Comparator.comparing(EncounterDto::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo));
            } else {
                comparator = Comparator.comparing(EncounterDto::getEncounterId, Comparator.nullsLast(String::compareTo));
            }
            if (desc) {
                comparator = comparator.reversed();
            }
            result = result.stream().sorted(comparator).collect(Collectors.toList());
        }

        return result;
    }

    // --- [RCP 대기 환자 -> OPD 진료 배정 등록] ---
    // RCP는 대기 환자 정보만 갖고 있고, 담당의/진료과 배정과 진료상태 관리는 OPD가 OUTPATIENT_ENCOUNTER에서 직접 한다.
    @Override
    @Transactional
    public EncounterDto createEncounter(EncounterCreateDto request) {
        // 같은 접수가 중복으로 배정되는 것을 막는다
        if (encounterRepository.existsByReceptionId(request.getReceptionId())) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 배정된 접수입니다. receptionId=" + request.getReceptionId());
        }

        Encounter encounter = new Encounter();
        encounter.setId(UUID.randomUUID().toString());
        encounter.setPatientId(request.getPatientId());
        encounter.setReceptionId(request.getReceptionId());
        encounter.setDepartmentId(request.getDepartmentId());
        encounter.setDoctorId(request.getDoctorId());
        encounter.setStatus("WAITING"); // 배정 직후엔 아직 진료 시작 전이라 대기 상태로 시작
        encounter.setVisitDate(LocalDate.now());
        // CREATED_AT/UPDATED_AT은 DB 기본값(SYSTIMESTAMP)이 있어도, JPA가 insert 시 null을 명시적으로
        // 보내면 그 기본값이 적용되지 않아 NOT NULL 제약에 걸린다 -> 애플리케이션에서 직접 채운다.
        LocalDateTime now = LocalDateTime.now();
        encounter.setCreatedAt(now);
        encounter.setUpdatedAt(now);

        Encounter saved = encounterRepository.save(encounter);
        log.info("[진료 배정 등록] encounterId={}, receptionId={}, doctorId={}",
                saved.getId(), saved.getReceptionId(), saved.getDoctorId());

        EncounterDto dto = outpatientCareMapper.toEncounterDto(saved);

        // PAT(환자) 마이크로서비스 API 연동 - 단건조회로 환자번호/환자명 채우기
        try {
            patientClient.getPatient(dto.getPatientId()).ifPresent(patient -> {
                dto.setPatientNo(patient.patientNo());
                dto.setPatientName(patient.patientName());
            });
        } catch (Exception e) {
            log.error("[PAT 연동 실패] 환자 기본정보 단건조회 실패: {}", e.getMessage());
        }

        return dto;
    }


    // --- [진료기록 목록 조회] ---
    @Override
    public List<MedicalRecordDto> getRecords(String encounterId) {
        List<MedicalRecord> records = medicalRecordRepository.findByEncounter_Id(encounterId);

        // PAT(환자) 마이크로서비스 API 연동 - patientId 수집 후 일괄조회 (batch-query)
        // MedicalRecord 엔티티엔 환자번호/환자명이 없어서(값 복제 금지, docs/conventions.md 5장) 여기서 채워야 한다
        Map<String, PatientApiDto.PatientSummary> patientMap = Collections.emptyMap();
        List<String> patientIds = records.stream()
                .map(MedicalRecord::getPatientId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        if (!patientIds.isEmpty()) {
            try {
                patientMap = patientClient.getPatients(patientIds);
            } catch (Exception e) {
                log.error("[PAT 연동 실패] 환자 기본정보 일괄조회 실패: {}", e.getMessage());
            }
        }

        // 감사 로그 남기기 (ADM 연동, GR2-36)
        log.info("[AUDIT LOG] 진료 기록 목록 열람 - encounterId={}, {}건", encounterId, records.size());
        recordAudit("MEDICAL_RECORD", String.valueOf(encounterId), "LIST");

        // MapStruct로 MedicalRecord → MedicalRecordDto 변환 후, PAT 환자정보(환자번호/환자명) 채우기
        List<MedicalRecordDto> result = outpatientCareMapper.toRecordDtoList(records);
        for (MedicalRecordDto dto : result) {
            PatientApiDto.PatientSummary patient = patientMap.get(dto.getPatientId());
            if (patient != null) {
                dto.setPatientNo(patient.patientNo());
                dto.setPatientName(patient.patientName());
            }
        }

        return result;
    }

    // --- [진료기록 상세 조회] ---
    @Override
    public MedicalRecordDto getRecord(String recordId) {
        // ErrorCode.NOT_FOUND(OPD004, 404)로 던져야 GlobalExceptionHandler가 500이 아니라 404로 응답한다
        MedicalRecord record = medicalRecordRepository.findById(recordId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "진료 기록을 찾을 수 없습니다. recordId=" + recordId));

        // 감사 로그 남기기 (ADM 연동, GR2-36)
        log.info("[AUDIT LOG] 진료 기록 상세 열람 - recordId={}", recordId);
        recordAudit("MEDICAL_RECORD", String.valueOf(recordId), "VIEW");

        MedicalRecordDto dto = outpatientCareMapper.toMedicalRecordDto(record);

        // PAT(환자) 마이크로서비스 API 연동 - 단건조회로 환자번호/환자명 채우기
        if (dto.getPatientId() != null) {
            try {
                patientClient.getPatient(dto.getPatientId()).ifPresent(patient -> {
                    dto.setPatientNo(patient.patientNo());
                    dto.setPatientName(patient.patientName());
                });
            } catch (Exception e) {
                log.error("[PAT 연동 실패] 환자 기본정보 단건조회 실패: {}", e.getMessage());
            }
        }

        return dto;
    }

    // --- [개인정보 열람 감사 로그 - ADM personalInfoAccessHistories 연동] ---
    private void recordAudit(String resourceType, String resourceId, String action) {
        auditLogClient.recordPersonalInfoAccess(new PersonalInfoAccessRequest(
                SERVICE_CODE,
                "SYSTEM", // TODO: 인증 체계 도입 후 로그인한 사용자(의료진) ID로 교체
                resourceType,
                resourceId,
                action,
                "외래 진료 업무 조회"
        ));
    }
}
