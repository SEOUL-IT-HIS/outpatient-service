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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    // 처방 목록 조회
    // 목록은 N+1 방지를 위해 items[](검사결과 등)를 포함하지 않는다 — 응급은 orderId 선택용으로만 쓰고,
    // 검사결과/조제상태는 원래 정한 대로 Kafka 구독으로 받는다(처방코어를 다시 거치지 않기 위함).
    @Override
    @Transactional(readOnly = true)
    public List<PrescriptionDto> getPrescriptions(String keyword, String receptionId) {
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

        // 환자명/환자번호/환자ID 세팅이 끝난 후에 키워드로 필터링
        if (keyword != null && !keyword.isBlank()) {
            String trimmedKeyword = keyword.trim();
            result = result.stream()
                    .filter(dto -> (dto.getPatientName() != null && dto.getPatientName().contains(trimmedKeyword))
                            || (dto.getPatientId() != null && dto.getPatientId().contains(trimmedKeyword)))
                    .collect(Collectors.toList());
        }

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
        prescription.setPharmacySendStatus("PENDING");
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
        prescription.setPharmacySendStatus("PENDING");
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
        prescription.setPharmacySendStatus("PENDING");
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
        List<PrescriptionItem> labItems = items.stream()
                .filter(item -> "검사".equals(item.getPrescriptionType()))
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

    // 처방 아이템 중 약품 항목만 모아서 약제실로 전송
    @Override
    @Transactional
    public void dispatchPharmacyOrders(String prescriptionId) {
        Prescription prescription = prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "Prescription not found. prescriptionId=" + prescriptionId));

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
            return;
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
                orderItems
        );

        boolean sent = pharmacyPublisher.publish(data);

        prescription.setPharmacySendStatus(sent ? "SENT" : "FAILED");
        prescription.setPharmacySentAt(LocalDateTime.now());
        prescriptionRepository.save(prescription);
    }

    // 약품 검색 (약제서비스 카탈로그 조회)
    @Override
    @Transactional(readOnly = true)
    public List<PharmacyApiDto.Medication> searchMedication(String name) {
        return pharmacyClient.searchMedication(name);
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

        prescription.setStatus("CANCELLED");
        prescription.setCancelledAt(LocalDateTime.now());
        prescription.setCancelReason(cancelReason);
        prescriptionRepository.save(prescription);
    }
}
