package kr.co.seoulit.his.outpatientservice.outpatientcare.service;

import kr.co.seoulit.his.outpatientservice.common.cache.CommonCodeCache;
import kr.co.seoulit.his.outpatientservice.common.client.patient.PatientApiDto;
import kr.co.seoulit.his.outpatientservice.common.client.patient.PatientClient;
import kr.co.seoulit.his.outpatientservice.common.client.reception.ReceptionEventDto;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class OutpatientCareServiceImpl implements OutpatientCareService {

    private final EncounterRepository encounterRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final OutpatientCareMapper outpatientCareMapper;
    private final PatientClient patientClient;
    private final CommonCodeCache commonCodeCache;


    // 접수(RCP) 이벤트 수신 시 Encounter 생성/갱신
    @Override
    public void registerEncounter(ReceptionEventDto.ReceptionData data) {
        Encounter encounter = encounterRepository.findByReceptionId(data.receptionId())
                .orElseGet(Encounter::new);
        boolean isNew = encounter.getEncounterId() == null;

        if (isNew) {
            encounter.setEncounterId(UUID.randomUUID().toString());
            encounter.setStatus(translateReceptionStatus(data.status()));
            encounter.setVisitDate(data.visitDate());
            encounter.setCreatedAt(LocalDateTime.now());
        } else if ("CANCELLED".equals(translateReceptionStatus(data.status()))
                && !"COMPLETED".equals(encounter.getStatus())) {
            // 접수 취소 이벤트는 기존 진료건에 반영한다. 이미 진료완료된 건은 취소로 되돌리지 않고,
            // 그 외 상태(접수/대기)는 OPD가 관리하므로 재수신해도 덮어쓰지 않는다.
            encounter.setStatus("CANCELLED");
        }

        encounter.setPatientId(data.patientId());
        encounter.setReceptionId(data.receptionId());
        encounter.setDepartmentCode(data.departmentCode());
        encounter.setDoctorId(data.doctorId());
        encounter.setVisitReason(data.visitReason());
        encounter.setVisitType(data.visitType());
        encounter.setReceptionType(data.receptionType());
        encounter.setUpdatedAt(LocalDateTime.now());

        encounterRepository.save(encounter);

        log.info("접수 이벤트로 Encounter {} encounterId={}, receptionId={}",
                isNew ? "생성" : "갱신", encounter.getEncounterId(), data.receptionId());
    }

    //접수서비스의 상태를 외래 서비스 용어로 바꿈(접수완료->대기중)
    private String translateReceptionStatus(String rcpStatus) {
        if (rcpStatus == null) {
            return "WAITING";
        }
        return switch (rcpStatus) {
            case "RECEPTION" -> "WAITING";
            case "CANCELLED" -> "CANCELLED";
            default -> rcpStatus;
        };
    }

    // 당일 외래 환자 목록 조회 (접수 순)
    @Override
    @Transactional(readOnly = true)
    public List<EncounterDto> getTodayEncounters() {
        List<Encounter> encounters = encounterRepository.findByVisitDateOrderByCreatedAtAsc(LocalDate.now());

        Map<String, PatientApiDto.PatientSummary> patientMap = Collections.emptyMap();
        List<String> patientIds = encounters.stream()
                .map(Encounter::getPatientId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        if (!patientIds.isEmpty()) {
            try {
                patientMap = patientClient.getPatients(patientIds);
            } catch (Exception e) {
                log.error("조회 실패: {}", e.getMessage());
            }
        }

        List<EncounterDto> result = outpatientCareMapper.toEncounterDtoList(encounters);
        for (EncounterDto dto : result) {
            PatientApiDto.PatientSummary patient = patientMap.get(dto.getPatientId());
            if (patient != null) {
                String rawName = patient.patientName();
                if (rawName != null && rawName.startsWith("환자")) {
                    rawName = rawName.substring(2);
                }
                dto.setPatientName(rawName);
            }

            // 진료과 코드 -> 진료과명 (ADM 공통코드 DEPT_CD)
            commonCodeCache.findCodeName("DEPT_CD", dto.getDepartmentCode())
                    .ifPresent(dto::setDepartmentName);
        }
        return result;
    }

    // 환자의 외래 진료 이력 조회 (접수 초진/재진 판정용)
    // 진료기록이 있는 외래건만 이력으로 보고(대기/취소 건·비활성화된 기록은 제외), 오늘 건도 포함한다
    @Override
    @Transactional(readOnly = true)
    public VisitHistoryDto getVisitHistory(String patientId, String departmentCode, Integer withinDays) {
        if (patientId == null || patientId.isBlank()) {
            // 환자 ID는 필수입니다.
            throw new BusinessException(ErrorCode.INVALID_INPUT, "patientId is required.");
        }
        if (withinDays != null && withinDays <= 0) {
            // 조회 기간(일)은 1 이상이어야 합니다.
            throw new BusinessException(ErrorCode.INVALID_INPUT, "withinDays must be greater than 0.");
        }

        LocalDate from = withinDays == null ? LocalDate.of(1900, 1, 1) : LocalDate.now().minusDays(withinDays);
        LocalDate lastVisitDate = (departmentCode == null || departmentCode.isBlank())
                ? medicalRecordRepository.findLastVisitDate(patientId, from)
                : medicalRecordRepository.findLastVisitDateByDepartment(patientId, departmentCode, from);

        return new VisitHistoryDto(lastVisitDate != null, lastVisitDate);
    }

    // 진료기록 목록 조회
    @Override
    @Transactional(readOnly = true)
    public List<MedicalRecordDto> getRecords(String keyword) {
        // 키워드가 없으면 최근 50건만, 키워드가 있으면 전체에서 필터링하기 위해 전체 조회
        List<MedicalRecord> records = (keyword == null || keyword.isBlank())
                ? medicalRecordRepository.findByOrderByCreatedAtDesc(PageRequest.of(0, 50))
                : medicalRecordRepository.findAll();

        // 비활성화(DELETED)된 기록은 목록에서 제외
        records = records.stream()
                .filter(r -> !"DELETED".equals(r.getStatus()))
                .collect(Collectors.toList());

        // 환자 정보 일괄 조회를 위한 patientId 추출
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
                log.error("조회 실패: {}", e.getMessage());
            }
        }

        // DTO 변환 및 외부 API(PAT) 결과로 환자명/환자번호 세팅
        List<MedicalRecordDto> result = outpatientCareMapper.toRecordDtoList(records);
        for (MedicalRecordDto dto : result) {
            PatientApiDto.PatientSummary patient = patientMap.get(dto.getPatientId());
            if (patient != null) {
                String rawName = patient.patientName();
                if (rawName != null && rawName.startsWith("환자")) {
                    rawName = rawName.substring(2);
                }
                dto.setPatientName(rawName);
            }
            fillDepartmentName(dto);
        }

        // 환자명/환자번호/주호소 세팅이 완료된 후 자바 Stream 필터링 수행
        if (keyword != null && !keyword.isBlank()) {
            String trimmedKeyword = keyword.trim();
            result = result.stream()
                    .filter(dto -> (dto.getPatientName() != null && dto.getPatientName().contains(trimmedKeyword))
                            || (dto.getChiefComplaint() != null && dto.getChiefComplaint().contains(trimmedKeyword)))
                    .collect(Collectors.toList());
        }

        return result;
    }

    // 진료기록 상세 조회
    @Override
    @Transactional(readOnly = true)
    public MedicalRecordDto getRecord(String recordId) {
        MedicalRecord record = medicalRecordRepository.findById(recordId)
                // 진료 기록을 찾을 수 없습니다.
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Medical record not found. recordId=" + recordId));

        // 비활성화(DELETED)된 기록은 없는 것과 동일하게 취급
        if ("DELETED".equals(record.getStatus())) {
            // 진료 기록을 찾을 수 없습니다.
            throw new BusinessException(ErrorCode.NOT_FOUND, "Medical record not found. recordId=" + recordId);
        }

        MedicalRecordDto dto = outpatientCareMapper.toMedicalRecordDto(record);

        // 환자 정보 세팅
        if (dto.getPatientId() != null) {
            try {
                patientClient.getPatient(dto.getPatientId()).ifPresent(patient -> {
                    String rawName = patient.patientName();
                    dto.setPatientName(rawName != null && rawName.startsWith("환자") ? rawName.substring(2) : rawName);
                });
            } catch (Exception e) {
                log.error("조회 실패: {}", e.getMessage());
            }
        }

        fillDepartmentName(dto);

        return dto;
    }

    // 진료과 코드 -> 진료과명 (ADM 공통코드 DEPT_CD). 미등록이거나 캐시 적재 전이면 null로 둔다
    private void fillDepartmentName(MedicalRecordDto dto) {
        commonCodeCache.findCodeName("DEPT_CD", dto.getDepartmentCode())
                .ifPresent(dto::setDepartmentName);
    }

    // 진료기록 등록
    @Override
    public MedicalRecordDto createRecord(MedicalRecordCreateDto request) {
        String targetId = request.getEncounterId();

        // 임시 보완-encounterId로 먼저 찾고, 없으면 receptionId로 찾거나 엔티티를 가져옴
        Encounter encounter = encounterRepository.findById(targetId).orElse(null);

        // 만약 encounterId로 안 찾아지면, receptionId로 등록된 Encounter가 있는지 한 번 더 검색
        if (encounter == null) {
            encounter = encounterRepository.findByReceptionId(targetId).orElse(null);
        }

        // 그래도 없다면(아직 Encounter 테이블에 배정 안 된 상태라면) 예외 대신 임시로 생성해서 연결하거나 에러 메시지 명확화
        if (encounter == null) {
            // 접수 연동 완료 전까지 테스트를 위해 임시로 Encounter를 만들어야 한다면 여기서 생성할 수도 있습니다.
            // 존재하지 않는 외래 진료건입니다.
            throw new BusinessException(ErrorCode.NOT_FOUND, "Outpatient encounter not found. ID=" + targetId);
        }



        // 접수가 취소된 진료건에는 진료기록을 작성할 수 없다
        if ("CANCELLED".equals(encounter.getStatus())) {
            // 취소된 접수 건에는 진료기록을 작성할 수 없습니다.
            throw new BusinessException(ErrorCode.CONFLICT,
                    "Cannot create a medical record for a cancelled encounter. encounterId=" + encounter.getEncounterId());
        }

        // 이미 진료기록을 작성해 진료완료된 건에는 다시 작성할 수 없다 (내용 변경은 진료기록 수정 API 사용)
        if ("COMPLETED".equals(encounter.getStatus())) {
            // 이미 진료가 완료된 건입니다.
            throw new BusinessException(ErrorCode.CONFLICT,
                    "Medical record already exists for a completed encounter. encounterId=" + encounter.getEncounterId());
        }

        // 요청 DTO를 엔티티로 변환 및 값 세팅
        MedicalRecord record = new MedicalRecord();
        //고유식별자를 생성해서 넣어줌
        record.setRecordId(UUID.randomUUID().toString()); // UUID 수동 할당
        record.setEncounter(encounter);

        //Encounter에 있는 담당 의사 ID를 가져와서 세팅
        record.setDoctorId(encounter.getDoctorId());

        record.setChiefComplaint(request.getChiefComplaint());
        record.setExaminationNote(request.getExaminationNote());

        //누락되었던 진료소견 및 치료계획 매핑 추가
        record.setAssessmentNote(request.getAssessmentNote());
        record.setPlanNote(request.getPlanNote());

        record.setStatus("COMPLETED"); // 필요에 따라 수정

        LocalDateTime now = LocalDateTime.now();
        record.setCreatedAt(now);
        record.setUpdatedAt(now);

        // DB에 저장
        MedicalRecord savedRecord = medicalRecordRepository.save(record);

        // 진료기록을 저장하면 외래 진료건을 진료완료로 변경
        encounter.setStatus("COMPLETED");
        encounter.setEndedAt(now);
        encounter.setUpdatedAt(now);
        encounterRepository.save(encounter);

        log.info("진료기록 등록 완료 recordId={}, encounterId={}",
                savedRecord.getRecordId(), encounter.getEncounterId());

        // MapStruct 맵퍼를 사용하여 화면에 전달할 DTO로 변환
        MedicalRecordDto dto = outpatientCareMapper.toMedicalRecordDto(savedRecord);

        // 화자id를 이용해 환자서비스클라이언트 호출
        if (dto.getPatientId() != null) {
            try {
                patientClient.getPatient(dto.getPatientId()).ifPresent(patient -> {
                    String rawName = patient.patientName();
                    dto.setPatientName(rawName != null && rawName.startsWith("환자") ? rawName.substring(2) : rawName);
                });
            } catch (Exception e) {
                log.error("조회 실패: {}", e.getMessage());
            }
        }

        return dto;
    }

    // 진료기록 수정
    @Override
    public MedicalRecordDto updateRecord(String recordId, MedicalRecordCreateDto request) {
        // 기존 진료기록 조회
        MedicalRecord record = medicalRecordRepository.findById(recordId)
                // 수정할 진료 기록을 찾을 수 없습니다.
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Medical record to update not found. recordId=" + recordId));

        // 비활성화(DELETED)된 기록은 수정 불가
        if ("DELETED".equals(record.getStatus())) {
            // 비활성화된 진료 기록은 수정할 수 없습니다.
            throw new BusinessException(ErrorCode.CONFLICT, "Cannot update a deactivated medical record. recordId=" + recordId);
        }

        // 전달받은 수정 요청 데이터(DTO) 값 반영
        record.setChiefComplaint(request.getChiefComplaint());
        record.setExaminationNote(request.getExaminationNote());
        record.setAssessmentNote(request.getAssessmentNote());
        record.setPlanNote(request.getPlanNote());

        // 수정 일시 갱신
        record.setUpdatedAt(LocalDateTime.now());

        log.info("진료기록 수정 완료 recordId={}", record.getRecordId());

        // 엔티티를 DTO로 변환
        MedicalRecordDto dto = outpatientCareMapper.toMedicalRecordDto(record);

        // 환자 정보(PAT API) 연동하여 이름/번호 채우기
        if (dto.getPatientId() != null) {
            try {
                patientClient.getPatient(dto.getPatientId()).ifPresent(patient -> {
                    String rawName = patient.patientName();
                    dto.setPatientName(rawName != null && rawName.startsWith("환자") ? rawName.substring(2) : rawName);
                });
            } catch (Exception e) {
                log.error("조회 실패: {}", e.getMessage());
            }
        }

        return dto;
    }

    // 진료기록 비활성화
    @Override
    public void deactivateRecord(String recordId, String userId) {
        // 기존 진료 기록 조회
        MedicalRecord record = medicalRecordRepository.findById(recordId)
                // 비활성화할 진료 기록을 찾을 수 없습니다.
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Medical record to deactivate not found. recordId=" + recordId));

        // 상태값을 'DELETED'로 변경하고 수정 일시 갱신
        record.setStatus("DELETED");
        record.setUpdatedAt(LocalDateTime.now());

        // 저장
        medicalRecordRepository.save(record);

        log.info("진료기록 비활성화 완료 recordId={}, 처리자={}", recordId, userId);
    }

}
