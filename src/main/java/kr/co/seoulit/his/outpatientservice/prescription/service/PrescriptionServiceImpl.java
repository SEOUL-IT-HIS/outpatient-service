package kr.co.seoulit.his.outpatientservice.prescription.service;

import kr.co.seoulit.his.outpatientservice.common.client.patient.PatientApiDto;
import kr.co.seoulit.his.outpatientservice.common.client.patient.PatientClient;
import kr.co.seoulit.his.outpatientservice.common.client.pharmacy.PharmacyApiDto;
import kr.co.seoulit.his.outpatientservice.common.client.pharmacy.PharmacyClient;
import kr.co.seoulit.his.outpatientservice.common.client.pharmacy.PharmacyEventDto;
import kr.co.seoulit.his.outpatientservice.common.client.pharmacy.PharmacyPublisher;
import kr.co.seoulit.his.outpatientservice.common.exception.BusinessException;
import kr.co.seoulit.his.outpatientservice.common.exception.ErrorCode;
import kr.co.seoulit.his.outpatientservice.prescription.dto.PrescriptionDto;
import kr.co.seoulit.his.outpatientservice.prescription.entity.Prescription;
import kr.co.seoulit.his.outpatientservice.prescription.entity.PrescriptionItem;
import kr.co.seoulit.his.outpatientservice.prescription.mapper.PrescriptionMapper;
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

    // 처방 목록 조회
    @Override
    @Transactional(readOnly = true)
    public List<PrescriptionDto> getPrescriptions(String keyword) {
        List<Prescription> prescriptions = prescriptionRepository.findAll(Sort.by(Sort.Direction.DESC, "prescribedAt"));

        List<PrescriptionDto> result = prescriptionMapper.toPrescriptionDtoList(prescriptions);
        fillPatientInfo(result);

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

        fillPatientInfo(List.of(dto));

        return dto;
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

        List<PrescriptionItem> items = request.getItems() == null
                ? List.of()
                : request.getItems().stream().map(req -> {
            PrescriptionItem item = new PrescriptionItem();
            item.setItemId(UUID.randomUUID().toString());
            item.setPrescriptionId(saved.getPrescriptionId());
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
        prescriptionItemRepository.saveAll(items);

        PrescriptionDto dto = prescriptionMapper.toPrescriptionDto(saved);
        dto.setItems(prescriptionMapper.toItemDtoList(items));
        fillPatientInfo(List.of(dto));
        return dto;
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

            LabOrderApiDto.LabOrderCreateRequestDto request = new LabOrderApiDto.LabOrderCreateRequestDto(
                    prescription.getPrescriptionId(),
                    prescription.getEncounterId(),
                    prescription.getPatientId(),
                    prescription.getPrescribedBy(),
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

    // 처방 아이템 중 약품 항목만 모아서 약제실로 전송
    @Override
    @Transactional
    public void dispatchPharmacyOrders(String prescriptionId) {
        Prescription prescription = prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "Prescription not found. prescriptionId=" + prescriptionId));

        Encounter encounter = encounterRepository.findById(prescription.getEncounterId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "Outpatient encounter not found. encounterId=" + prescription.getEncounterId()));

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
                encounter.getDepartmentCode(),
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

}
