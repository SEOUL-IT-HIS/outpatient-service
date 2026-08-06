package kr.co.seoulit.his.outpatientservice.outpatientcare.service;

import kr.co.seoulit.his.outpatientservice.common.client.admin.AuditLogClient;
import kr.co.seoulit.his.outpatientservice.common.client.admin.PersonalInfoAccessRequest;
import kr.co.seoulit.his.outpatientservice.common.client.patient.PatientApiDto;
import kr.co.seoulit.his.outpatientservice.common.client.patient.PatientClient;
// 의사 정보 조회를 위한 Client/Dto 가 있다면 import (예시)
// import kr.co.seoulit.his.outpatientservice.common.client.doctor.DoctorClient;
import kr.co.seoulit.his.outpatientservice.common.exception.BusinessException;
import kr.co.seoulit.his.outpatientservice.common.exception.ErrorCode;
import kr.co.seoulit.his.outpatientservice.outpatientcare.dto.*;
import kr.co.seoulit.his.outpatientservice.outpatientcare.entity.Encounter;
import kr.co.seoulit.his.outpatientservice.outpatientcare.entity.MedicalRecord;
import kr.co.seoulit.his.outpatientservice.outpatientcare.mapper.OutpatientCareMapper;
import kr.co.seoulit.his.outpatientservice.outpatientcare.repository.EncounterRepository;
import kr.co.seoulit.his.outpatientservice.outpatientcare.repository.MedicalRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
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

    // 3. (필요 시) DoctorClient 주입 - 의사명, 진료과명 연동용
    // private final DoctorClient doctorClient;

    private final RestTemplate restTemplate = new RestTemplate();

    private static final String SERVICE_CODE = "OPD";

    // --- [당일 대기 환자 목록 조회 - RCP(접수) + PAT(환자) API 연동] ---
    @Override
    public List<EncounterDto> getEncounters(EncounterSearchDto request) {
        String deptCode = (request != null && request.getDepartmentCode() != null) ? request.getDepartmentCode() : "";

        List<ReceptionDto> waitingList;
        try {
            String url = "http://localhost:8081/receptions/waiting?deptCode=" + deptCode;
            ReceptionDto[] response = restTemplate.getForObject(url, ReceptionDto[].class);
            waitingList = (response != null) ? Arrays.asList(response) : Collections.emptyList();
        } catch (Exception e) {
            log.error("[RCP 연동 실패] 대기 환자 목록 조회 실패: {}", e.getMessage());
            waitingList = Collections.emptyList();
        }

        if (request != null && request.getStatus() != null && !request.getStatus().isBlank()) {
            String status = request.getStatus().trim();
            waitingList = waitingList.stream()
                    .filter(r -> status.equalsIgnoreCase(r.getStatus()))
                    .collect(Collectors.toList());
        }

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

        log.info("[AUDIT LOG] 당일 대기 환자 목록 조회 완료 - RCP 목록: {}건, PAT 환자정보: {}건",
                waitingList.size(), patientMap.size());
        recordAudit("ENCOUNTER", deptCode.isBlank() ? "ALL" : deptCode, "LIST");

        List<EncounterDto> result = outpatientCareMapper.toEncounterDtoListFromReception(waitingList);
        for (EncounterDto dto : result) {
            PatientApiDto.PatientSummary patient = patientMap.get(dto.getPatientId());
            if (patient != null) {
                dto.setPatientNo(patient.patientNo());
                //환자명앞에 환자라는 단어가 붙어서 제거
                String rawName = patient.patientName();
                dto.setPatientName(rawName != null && rawName.startsWith("환자") ? rawName.substring(2) : rawName);
            }
        }

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
    @Override
    @Transactional
    public EncounterDto createEncounter(EncounterCreateDto request) {
        if (encounterRepository.existsByReceptionId(request.getReceptionId())) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 배정된 접수입니다. receptionId=" + request.getReceptionId());
        }

        Encounter encounter = new Encounter();
        encounter.setId(UUID.randomUUID().toString());
        encounter.setPatientId(request.getPatientId());
        encounter.setReceptionId(request.getReceptionId());
        encounter.setDepartmentId(request.getDepartmentId());
        encounter.setDoctorId(request.getDoctorId());
        encounter.setStatus("WAITING");
        encounter.setVisitDate(LocalDate.now());

        LocalDateTime now = LocalDateTime.now();
        encounter.setCreatedAt(now);
        encounter.setUpdatedAt(now);

        Encounter saved = encounterRepository.save(encounter);
        log.info("[진료 배정 등록] encounterId={}, receptionId={}, doctorId={}",
                saved.getId(), saved.getReceptionId(), saved.getDoctorId());

        EncounterDto dto = outpatientCareMapper.toEncounterDto(saved);

        try {
            patientClient.getPatient(dto.getPatientId()).ifPresent(patient -> {
                dto.setPatientNo(patient.patientNo());
                String rawName = patient.patientName();
                dto.setPatientName(rawName != null && rawName.startsWith("환자") ? rawName.substring(2) : rawName);
            });
        } catch (Exception e) {
            log.error("[PAT 연동 실패] 환자 기본정보 단건조회 실패: {}", e.getMessage());
        }

        return dto;
    }

    // --- [진료기록 목록 조회] ---
    @Override
    @Transactional(readOnly = true)
    public List<MedicalRecordDto> getRecords(String keyword) {
        log.info(">>>>>> [최종확인] 컨트롤러로부터 전달된 keyword : [{}]", keyword);
        // 1. 키워드가 없으면 최근 50건만, 키워드가 있으면 전체에서 필터링하기 위해 전체 조회
        List<MedicalRecord> records = (keyword == null || keyword.isBlank())
                ? medicalRecordRepository.findByOrderByCreatedAtDesc(PageRequest.of(0, 50))
                : medicalRecordRepository.findAll();

        // 2. 환자 정보 일괄 조회를 위한 patientId 추출
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

        log.info("[AUDIT LOG] 진료 기록 목록 열람 - keyword={}, DB조회: {}건", keyword, records.size());
        recordAudit("MEDICAL_RECORD", (keyword == null || keyword.isBlank()) ? "ALL" : keyword, "LIST");

        // 3. DTO 변환 및 외부 API(PAT) 결과로 환자명/환자번호 세팅 (필터링 전 완료 필수!)
        List<MedicalRecordDto> result = outpatientCareMapper.toRecordDtoList(records);
        for (MedicalRecordDto dto : result) {
            PatientApiDto.PatientSummary patient = patientMap.get(dto.getPatientId());
            if (patient != null) {
                dto.setPatientNo(patient.patientNo());
                String rawName = patient.patientName();
                if (rawName != null && rawName.startsWith("환자")) {
                    rawName = rawName.substring(2);
                }
                dto.setPatientName(rawName);
            }
        }

        // 4. [핵심] 환자명/환자번호/주호소 세팅이 완료된 후 자바 Stream 필터링 수행
        if (keyword != null && !keyword.isBlank()) {
            String trimmedKeyword = keyword.trim();
            result = result.stream()
                    .filter(dto -> (dto.getPatientName() != null && dto.getPatientName().contains(trimmedKeyword))
                            || (dto.getPatientNo() != null && dto.getPatientNo().contains(trimmedKeyword))
                            || (dto.getChiefComplaint() != null && dto.getChiefComplaint().contains(trimmedKeyword)))
                    .collect(Collectors.toList());

            log.info("[AUDIT LOG] 필터링 적용 완료 - keyword={}, 최종 결과: {}건", trimmedKeyword, result.size());
        }

        return result;
    }

    // --- [진료기록 상세 조회] ---
    @Override
    @Transactional(readOnly = true)
    public MedicalRecordDto getRecord(String recordId) {
        MedicalRecord record = medicalRecordRepository.findById(recordId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "진료 기록을 찾을 수 없습니다. recordId=" + recordId));

        log.info("[AUDIT LOG] 진료 기록 상세 열람 - recordId={}", recordId);
        recordAudit("MEDICAL_RECORD", String.valueOf(recordId), "VIEW");

        MedicalRecordDto dto = outpatientCareMapper.toMedicalRecordDto(record);

        // 1. 환자 정보 세팅
        if (dto.getPatientId() != null) {
            try {
                patientClient.getPatient(dto.getPatientId()).ifPresent(patient -> {
                    dto.setPatientNo(patient.patientNo());
                    String rawName = patient.patientName();
                    dto.setPatientName(rawName != null && rawName.startsWith("환자") ? rawName.substring(2) : rawName);
                });
            } catch (Exception e) {
                log.error("[PAT 연동 실패] 환자 기본정보 단건조회 실패: {}", e.getMessage());
            }
        }

        // 2. 의사/진료과 정보 세팅 (추가)
        if (dto.getDoctorId() != null) {
            try {
                // TODO: DoctorClient / RestTemplate 연동에 맞게 호출
                // DoctorDto doctor = doctorClient.getDoctor(dto.getDoctorId());
                // dto.setDoctorName(doctor.getDoctorName());
                // dto.setDepartmentName(doctor.getDepartmentName());
            } catch (Exception e) {
                log.error("[DOCTOR 연동 실패] 의사 정보 단건조회 실패 doctorId={}: {}", dto.getDoctorId(), e.getMessage());
            }
        }

        return dto;
    }

    // --- [개인정보 열람 감사 로그 - ADM personalInfoAccessHistories 연동] ---
    private void recordAudit(String resourceType, String resourceId, String action) {
        auditLogClient.recordPersonalInfoAccess(new PersonalInfoAccessRequest(
                SERVICE_CODE,
                "SYSTEM",
                resourceType,
                resourceId,
                action,
                "외래 진료 업무 조회"
        ));
    }
}