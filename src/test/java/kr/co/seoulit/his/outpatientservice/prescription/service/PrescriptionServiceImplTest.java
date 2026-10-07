package kr.co.seoulit.his.outpatientservice.prescription.service;

import kr.co.seoulit.his.outpatientservice.common.cache.CommonCodeCache;
import kr.co.seoulit.his.outpatientservice.common.client.lab.LabClient;
import kr.co.seoulit.his.outpatientservice.common.client.lab.LabOrderApiDto;
import kr.co.seoulit.his.outpatientservice.common.client.lab.LabOrderDispatcher;
import kr.co.seoulit.his.outpatientservice.common.client.lab.LabResultEventDto;
import kr.co.seoulit.his.outpatientservice.common.client.patient.PatientClient;
import kr.co.seoulit.his.outpatientservice.common.client.pharmacy.PharmacyApiDto;
import kr.co.seoulit.his.outpatientservice.common.client.pharmacy.PharmacyClient;
import kr.co.seoulit.his.outpatientservice.common.client.pharmacy.PharmacyEventDto;
import kr.co.seoulit.his.outpatientservice.common.client.pharmacy.PharmacyPublisher;
import kr.co.seoulit.his.outpatientservice.common.exception.BusinessException;
import kr.co.seoulit.his.outpatientservice.common.exception.ErrorCode;
import kr.co.seoulit.his.outpatientservice.outpatientcare.entity.Encounter;
import kr.co.seoulit.his.outpatientservice.outpatientcare.repository.EncounterRepository;
import kr.co.seoulit.his.outpatientservice.prescription.dto.PrescriptionCreateDto;
import kr.co.seoulit.his.outpatientservice.prescription.dto.PrescriptionDto;
import kr.co.seoulit.his.outpatientservice.prescription.dto.PrescriptionItemDto;
import kr.co.seoulit.his.outpatientservice.prescription.entity.ExamResultRef;
import kr.co.seoulit.his.outpatientservice.prescription.entity.Prescription;
import kr.co.seoulit.his.outpatientservice.prescription.entity.PrescriptionItem;
import kr.co.seoulit.his.outpatientservice.prescription.mapper.PrescriptionMapper;
import kr.co.seoulit.his.outpatientservice.prescription.mapper.PrescriptionMapperImpl;
import kr.co.seoulit.his.outpatientservice.prescription.repository.ExamResultRefRepository;
import kr.co.seoulit.his.outpatientservice.prescription.repository.PrescriptionItemRepository;
import kr.co.seoulit.his.outpatientservice.prescription.repository.PrescriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * applyLabOrderResult()는 LabOrderResultListener가 Kafka 결과 이벤트를 받았을 때 호출하는
 * 반영 로직이라, 실제 브로커 없이도 PENDING/중복 이벤트 처리가 맞는지 여기서 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class PrescriptionServiceImplTest {

    @Mock
    private PrescriptionRepository prescriptionRepository;
    @Mock
    private PrescriptionItemRepository prescriptionItemRepository;
    @Mock
    private PrescriptionMapper prescriptionMapper;
    @Mock
    private PatientClient patientClient;
    @Mock
    private EncounterRepository encounterRepository;
    @Mock
    private LabOrderDispatcher labOrderDispatcher;
    @Mock
    private PharmacyPublisher pharmacyPublisher;
    @Mock
    private PharmacyClient pharmacyClient;
    @Mock
    private CommonCodeCache commonCodeCache;
    @Mock
    private ExamResultRefRepository examResultRefRepository;
    @Mock
    private LabClient labClient;

    private PrescriptionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PrescriptionServiceImpl(
                prescriptionRepository, prescriptionItemRepository, prescriptionMapper,
                patientClient, encounterRepository, labOrderDispatcher, pharmacyPublisher, pharmacyClient,
                commonCodeCache, examResultRefRepository, labClient
        );
    }

    private PrescriptionItem labItem(String itemId, String sendStatus) {
        PrescriptionItem item = new PrescriptionItem();
        item.setItemId(itemId);
        item.setPrescriptionId("RX-1");
        item.setPrescriptionType("검사");
        item.setSendStatus(sendStatus);
        return item;
    }

    @Test
    void 수락_결과를_받으면_PENDING_항목이_SENT로_바뀌고_검사오더ID가_저장된다() {
        PrescriptionItem pending = labItem("ITEM-1", "PENDING");
        when(prescriptionItemRepository.findByPrescriptionId("RX-1")).thenReturn(List.of(pending));

        service.applyLabOrderResult("RX-1", "ACCEPTED", "LAB-ORDER-999", null);

        assertThat(pending.getSendStatus()).isEqualTo("SENT");
        assertThat(pending.getLabOrderId()).isEqualTo("LAB-ORDER-999");
        assertThat(pending.getRejectReason()).isNull();
        verify(prescriptionItemRepository).saveAll(List.of(pending));
    }

    @Test
    void 거절_결과를_받으면_PENDING_항목이_FAILED로_바뀌고_사유가_저장된다() {
        PrescriptionItem pending = labItem("ITEM-1", "PENDING");
        when(prescriptionItemRepository.findByPrescriptionId("RX-1")).thenReturn(List.of(pending));

        service.applyLabOrderResult("RX-1", "REJECTED", null, "중복 검사 오더");

        assertThat(pending.getSendStatus()).isEqualTo("FAILED");
        assertThat(pending.getLabOrderId()).isNull();
        assertThat(pending.getRejectReason()).isEqualTo("중복 검사 오더");
    }

    @Test
    void 이미_확정된_항목이면_중복_이벤트로_보고_반영하지_않는다() {
        PrescriptionItem alreadySent = labItem("ITEM-1", "SENT");
        when(prescriptionItemRepository.findByPrescriptionId("RX-1")).thenReturn(List.of(alreadySent));

        service.applyLabOrderResult("RX-1", "ACCEPTED", "LAB-ORDER-999", null);

        assertThat(alreadySent.getLabOrderId()).isNull(); // 그대로 유지, 덮어쓰지 않음
        verify(prescriptionItemRepository, never()).saveAll(anyList());
    }

    // ---- 처방 비활성화 시 검사오더 취소 통보 ----

    private Prescription prescriptionWithStatus(String status) {
        Prescription prescription = new Prescription();
        prescription.setPrescriptionId("RX-1");
        prescription.setStatus(status);
        return prescription;
    }

    @Test
    void 비활성화하면_LAB에_전달된_검사항목만_취소_통보한다() {
        when(prescriptionRepository.findById("RX-1")).thenReturn(Optional.of(prescriptionWithStatus("ORDERED")));
        PrescriptionItem sent = labItem("ITEM-1", "SENT");
        sent.setItemCode("CBC");
        sent.setLabOrderId("LO-1");
        PrescriptionItem pending = labItem("ITEM-2", "PENDING");
        pending.setItemCode("CRP");
        PrescriptionItem failed = labItem("ITEM-3", "FAILED");
        PrescriptionItem unsent = labItem("ITEM-4", null);
        PrescriptionItem drug = labItem("ITEM-5", "SENT");
        drug.setPrescriptionType("약품");
        when(prescriptionItemRepository.findByPrescriptionId("RX-1"))
                .thenReturn(List.of(sent, pending, failed, unsent, drug));
        when(labOrderDispatcher.cancel(any())).thenReturn(true);

        service.deactivatePrescription("RX-1", "오처방", "DOC-1");

        ArgumentCaptor<LabOrderApiDto.LabOrderCancelRequestDto> captor =
                ArgumentCaptor.forClass(LabOrderApiDto.LabOrderCancelRequestDto.class);
        verify(labOrderDispatcher).cancel(captor.capture());
        assertThat(captor.getValue().prescriptionId()).isEqualTo("RX-1");
        assertThat(captor.getValue().cancelReason()).isEqualTo("오처방");
        assertThat(captor.getValue().cancelledBy()).isEqualTo("DOC-1");
        assertThat(captor.getValue().cancelledItems()).extracting(LabOrderApiDto.LabOrderCancelItemDto::itemCode)
                .containsExactly("CBC", "CRP");
    }

    @Test
    void LAB에_전달된_검사항목이_없으면_취소_통보하지_않는다() {
        when(prescriptionRepository.findById("RX-1")).thenReturn(Optional.of(prescriptionWithStatus("ORDERED")));
        when(prescriptionItemRepository.findByPrescriptionId("RX-1"))
                .thenReturn(List.of(labItem("ITEM-1", "FAILED"), labItem("ITEM-2", null)));

        service.deactivatePrescription("RX-1", "오처방", "DOC-1");

        verify(labOrderDispatcher, never()).cancel(any());
    }

    @Test
    void 취소_통보가_실패해도_처방은_비활성화된다() {
        Prescription prescription = prescriptionWithStatus("ORDERED");
        when(prescriptionRepository.findById("RX-1")).thenReturn(Optional.of(prescription));
        when(prescriptionItemRepository.findByPrescriptionId("RX-1")).thenReturn(List.of(labItem("ITEM-1", "SENT")));
        when(labOrderDispatcher.cancel(any())).thenReturn(false);

        service.deactivatePrescription("RX-1", "오처방", "DOC-1");

        assertThat(prescription.getStatus()).isEqualTo("CANCELLED");
        verify(prescriptionRepository).save(prescription);
    }

    @Test
    void 이미_취소된_처방은_다시_비활성화할_수_없고_통보도_하지_않는다() {
        when(prescriptionRepository.findById("RX-1")).thenReturn(Optional.of(prescriptionWithStatus("CANCELLED")));

        assertThatThrownBy(() -> service.deactivatePrescription("RX-1", "오처방", "DOC-1"))
                .isInstanceOf(BusinessException.class);

        verify(labOrderDispatcher, never()).cancel(any());
        verify(prescriptionRepository, never()).save(any());
    }

    // ---- 처방 비활성화 시 약제 취소 통보 (처방 전체 단위, 단방향) ----

    private Prescription prescriptionWithPharmacyStatus(String pharmacySendStatus) {
        Prescription prescription = prescriptionWithStatus("ORDERED");
        prescription.setPharmacySendStatus(pharmacySendStatus);
        return prescription;
    }

    @Test
    void 약제에_SENT인_처방을_비활성화하면_약품_항목_전체를_취소_통보한다() {
        when(prescriptionRepository.findById("RX-1")).thenReturn(Optional.of(prescriptionWithPharmacyStatus("SENT")));
        PrescriptionItem drug1 = drugItem("RX-1");
        PrescriptionItem drug2 = drugItem("RX-1");
        drug2.setItemCode("195700021");
        drug2.setItemName("부루펜정");
        PrescriptionItem lab = labItem("ITEM-9", "FAILED"); // 검사 항목은 약제 취소 대상이 아니다
        when(prescriptionItemRepository.findByPrescriptionId("RX-1")).thenReturn(List.of(drug1, drug2, lab));
        when(pharmacyPublisher.publishCancel(any())).thenReturn(true);

        service.deactivatePrescription("RX-1", "오처방", "DOC-1");

        ArgumentCaptor<PharmacyEventDto.PharmacyOrderCancelledData> captor =
                ArgumentCaptor.forClass(PharmacyEventDto.PharmacyOrderCancelledData.class);
        verify(pharmacyPublisher).publishCancel(captor.capture());
        assertThat(captor.getValue().prescriptionId()).isEqualTo("RX-1");
        assertThat(captor.getValue().cancelReason()).isEqualTo("오처방");
        assertThat(captor.getValue().cancelledBy()).isEqualTo("DOC-1");
        assertThat(captor.getValue().cancelledItems()).extracting(PharmacyEventDto.PharmacyCancelledItem::ediCode)
                .containsExactly("195700020", "195700021");
    }

    @Test
    void 약제에_발행하지_않았거나_약품이_없는_처방은_취소_통보하지_않는다() {
        for (String status : new String[]{"PENDING", "FAILED", null}) {
            reset(prescriptionRepository, prescriptionItemRepository);
            when(prescriptionRepository.findById("RX-1")).thenReturn(Optional.of(prescriptionWithPharmacyStatus(status)));
            when(prescriptionItemRepository.findByPrescriptionId("RX-1")).thenReturn(List.of());

            service.deactivatePrescription("RX-1", "오처방", "DOC-1");
        }

        verify(pharmacyPublisher, never()).publishCancel(any());
    }

    @Test
    void 약제_취소_통보가_실패해도_처방은_비활성화된다() {
        Prescription prescription = prescriptionWithPharmacyStatus("SENT");
        when(prescriptionRepository.findById("RX-1")).thenReturn(Optional.of(prescription));
        when(prescriptionItemRepository.findByPrescriptionId("RX-1")).thenReturn(List.of(drugItem("RX-1")));
        when(pharmacyPublisher.publishCancel(any())).thenReturn(false);

        service.deactivatePrescription("RX-1", "오처방", "DOC-1");

        assertThat(prescription.getStatus()).isEqualTo("CANCELLED");
        verify(prescriptionRepository).save(prescription);
    }

    // ---- 약품 목록 (약제 카탈로그 페이지 조회) ----

    private PharmacyApiDto.Medication medication(long id, String name, String ediCode) {
        return new PharmacyApiDto.Medication(id, name, null, null, null, null, null, null, null, "01", null, null, ediCode, null);
    }

    @Test
    void 약품_목록에서_약가코드가_없는_약은_빼고_페이지_정보는_약제_값_그대로_둔다() {
        PharmacyApiDto.MedicationPage page = new PharmacyApiDto.MedicationPage(
                List.of(medication(1, "코드있음", "653500890"), medication(2, "코드null", null), medication(3, "코드빈값", " ")),
                109, 2, 0, 100, true, false);
        when(pharmacyClient.listMedications(null, 0, 100)).thenReturn(page);

        PharmacyApiDto.MedicationPage result = service.listMedications(null, 0, 100);

        assertThat(result.content()).extracting(PharmacyApiDto.Medication::medicationName).containsExactly("코드있음");
        assertThat(result.totalElements()).isEqualTo(109);
        assertThat(result.totalPages()).isEqualTo(2);
        assertThat(result.last()).isFalse();
    }

    @Test
    void 약품_목록의_page와_size는_약제가_받는_범위로_보정한다() {
        when(pharmacyClient.listMedications(any(), anyInt(), anyInt()))
                .thenReturn(new PharmacyApiDto.MedicationPage(List.of(), 0, 0, 0, 100, true, true));

        service.listMedications("모빅", -3, 5000);
        verify(pharmacyClient).listMedications("모빅", 0, 100);

        service.listMedications("모빅", 2, 0);
        verify(pharmacyClient).listMedications("모빅", 2, 1);
    }

    @Test
    void 약제가_content를_주지_않아도_빈_목록으로_돌려준다() {
        when(pharmacyClient.listMedications(null, 0, 100))
                .thenReturn(new PharmacyApiDto.MedicationPage(null, 0, 0, 0, 100, true, true));

        assertThat(service.listMedications(null, 0, 100).content()).isEmpty();
    }

    // ---- 검사결과 수신(applyLabResult) / 처방 상세 조회 시 결과 채우기 ----

    private PrescriptionItem sentLabItem(String itemId, String itemCode) {
        PrescriptionItem item = labItem(itemId, "SENT");
        item.setItemCode(itemCode);
        return item;
    }

    // 신형식: 일반검사 결과항목이 details에 담기고 최상위 값은 null
    private LabResultEventDto.ResultItem detailsItem(String itemCode, List<LabResultEventDto.ResultDetail> details) {
        return new LabResultEventDto.ResultItem(itemCode, null, null, null, null, "GENERAL",
                "LOI-1", "RES-1", "DOC-1", details);
    }

    // 구형식: 최상위 값에 결과가 담기고 details 없음
    private LabResultEventDto.ResultItem legacyItem(String itemCode, String value) {
        return new LabResultEventDto.ResultItem(itemCode, value, "mmol/L", "3.9-6.1", "N", "GENERAL",
                null, null, null, null);
    }

    private LabResultEventDto.ResultData resultData(LabResultEventDto.ResultItem item) {
        return new LabResultEventDto.ResultData("RX-1", "LAB-1", "FINAL",
                OffsetDateTime.parse("2026-09-30T10:00:00+09:00"), List.of(item));
    }

    @Test
    void 신형식_details를_받으면_결과행이_저장되고_항목에_eventId와_확정시각이_기록된다() {
        PrescriptionItem sent = sentLabItem("ITEM-1", "02");
        when(prescriptionItemRepository.findByPrescriptionId("RX-1")).thenReturn(List.of(sent));
        LabResultEventDto.ResultItem item = detailsItem("02", List.of(
                new LabResultEventDto.ResultDetail(1, "02", "6.2", "x10^3/uL", "4.0-10.0", "N"),
                new LabResultEventDto.ResultDetail(2, "03", "4.6", "x10^6/uL", "4.35-5.65", "H")));

        service.applyLabResult("EVT-1", resultData(item), item);

        ArgumentCaptor<List<ExamResultRef>> captor = ArgumentCaptor.forClass(List.class);
        verify(examResultRefRepository).saveAll(captor.capture());
        List<ExamResultRef> saved = captor.getValue();
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getItemId()).isEqualTo("ITEM-1");
        assertThat(saved.get(0).getLabResultId()).isEqualTo("RES-1");
        assertThat(saved.get(0).getResultStatus()).isEqualTo("FINAL");
        assertThat(saved.get(0).getSeq()).isEqualTo(1);
        assertThat(saved.get(0).getDetailCode()).isEqualTo("02");
        assertThat(saved.get(0).getResultValue()).isEqualTo("6.2");
        assertThat(saved.get(0).getResultedAt()).isEqualTo(LocalDateTime.of(2026, 9, 30, 10, 0));
        assertThat(saved.get(1).getAbnormalFlag()).isEqualTo("H");
        assertThat(sent.getResultEventId()).isEqualTo("EVT-1");
        assertThat(sent.getResultReportedAt()).isEqualTo(LocalDateTime.of(2026, 9, 30, 10, 0));
        assertThat(sent.getResultValue()).isNull(); // 신형식은 PRESCRIPTION_ITEM의 결과 컬럼을 쓰지 않는다
        verify(prescriptionItemRepository).save(sent);
    }

    @Test
    void 구형식_최상위값을_받으면_처방항목_결과컬럼에_저장되고_결과행은_만들지_않는다() {
        PrescriptionItem sent = sentLabItem("ITEM-1", "01");
        when(prescriptionItemRepository.findByPrescriptionId("RX-1")).thenReturn(List.of(sent));
        LabResultEventDto.ResultItem item = legacyItem("01", "5.4");

        service.applyLabResult("EVT-1", resultData(item), item);

        assertThat(sent.getResultValue()).isEqualTo("5.4");
        assertThat(sent.getResultUnit()).isEqualTo("mmol/L");
        assertThat(sent.getResultEventId()).isEqualTo("EVT-1");
        verify(prescriptionItemRepository).save(sent);
        verify(examResultRefRepository, never()).saveAll(anyList());
    }

    @Test
    void details와_최상위값이_모두_없으면_빈_결과를_저장하지_않고_스킵한다() {
        LabResultEventDto.ResultItem item = detailsItem("02", List.of());

        service.applyLabResult("EVT-1", resultData(item), item);

        verifyNoInteractions(prescriptionItemRepository, examResultRefRepository);
    }

    @Test
    void 같은_eventId가_다시_오면_중복으로_보고_스킵한다() {
        PrescriptionItem alreadyApplied = sentLabItem("ITEM-1", "02");
        alreadyApplied.setResultEventId("EVT-1");
        when(prescriptionItemRepository.findByPrescriptionId("RX-1")).thenReturn(List.of(alreadyApplied));
        LabResultEventDto.ResultItem item = detailsItem("02", List.of(
                new LabResultEventDto.ResultDetail(1, "02", "6.2", "x10^3/uL", "4.0-10.0", "N")));

        service.applyLabResult("EVT-1", resultData(item), item);

        verify(examResultRefRepository, never()).saveAll(anyList());
        verify(prescriptionItemRepository, never()).save(any());
    }

    @Test
    void 이미_결과가_있는_항목에_다른_eventId가_오면_덮어쓰지_않는다() {
        PrescriptionItem sent = sentLabItem("ITEM-1", "02");
        when(prescriptionItemRepository.findByPrescriptionId("RX-1")).thenReturn(List.of(sent));
        when(examResultRefRepository.existsByItemId("ITEM-1")).thenReturn(true);
        LabResultEventDto.ResultItem item = detailsItem("02", List.of(
                new LabResultEventDto.ResultDetail(1, "02", "6.2", "x10^3/uL", "4.0-10.0", "N")));

        service.applyLabResult("EVT-2", resultData(item), item);

        verify(examResultRefRepository, never()).saveAll(anyList());
        assertThat(sent.getResultEventId()).isNull();
    }

    @Test
    void GENERAL이_아닌_결과는_스킵한다() {
        LabResultEventDto.ResultItem item = new LabResultEventDto.ResultItem(
                "05", null, null, null, null, "MICROBIOLOGY", null, null, null, null);

        service.applyLabResult("EVT-1", resultData(item), item);

        verifyNoInteractions(prescriptionItemRepository, examResultRefRepository);
    }

    // 실제 매퍼를 써서 MapStruct 매핑(결과 필드, 중첩 DTO)까지 함께 검증한다
    @Test
    void 처방_상세조회시_검사항목에_결과상세가_채워지고_구형식_결과는_1건으로_합쳐진다() {
        PrescriptionServiceImpl realMapperService = new PrescriptionServiceImpl(
                prescriptionRepository, prescriptionItemRepository, new PrescriptionMapperImpl(),
                patientClient, encounterRepository, labOrderDispatcher, pharmacyPublisher, pharmacyClient,
                commonCodeCache, examResultRefRepository, labClient
        );

        Prescription prescription = new Prescription();
        prescription.setPrescriptionId("RX-1");
        when(prescriptionRepository.findById("RX-1")).thenReturn(Optional.of(prescription));

        PrescriptionItem cbc = sentLabItem("ITEM-1", "02");      // 신형식: EXAM_RESULT_REF에 저장
        PrescriptionItem glucose = sentLabItem("ITEM-2", "01");  // 구형식: PRESCRIPTION_ITEM에 저장
        glucose.setResultValue("5.4");
        glucose.setResultUnit("mmol/L");
        PrescriptionItem drug = new PrescriptionItem();          // 약품: 결과 상세 없음
        drug.setItemId("ITEM-3");
        drug.setPrescriptionId("RX-1");
        drug.setPrescriptionType("약품");
        when(prescriptionItemRepository.findByPrescriptionId("RX-1")).thenReturn(List.of(cbc, glucose, drug));

        ExamResultRef ref = new ExamResultRef();
        ref.setItemId("ITEM-1");
        ref.setSeq(1);
        ref.setDetailCode("02");
        ref.setResultValue("6.2");
        ref.setAbnormalFlag("N");
        when(examResultRefRepository.findByItemIdInOrderBySeqAsc(anyCollection())).thenReturn(List.of(ref));
        // fillPriorityName도 같은 메서드를 다른 인자로 호출하므로 strict stubs에 걸리지 않게 lenient로 둔다
        lenient().when(commonCodeCache.findCodeName("RESULT_ITEM_CD", "02")).thenReturn(Optional.of("백혈구"));

        PrescriptionDto dto = realMapperService.getPrescription("RX-1");

        assertThat(dto.getItems()).hasSize(3);

        PrescriptionItemDto cbcDto = dto.getItems().get(0);
        assertThat(cbcDto.getResultDetails()).hasSize(1);
        assertThat(cbcDto.getResultDetails().get(0).getDetailName()).isEqualTo("백혈구");
        assertThat(cbcDto.getResultDetails().get(0).getResultValue()).isEqualTo("6.2");
        assertThat(cbcDto.getResultDetails().get(0).getAbnormalFlag()).isEqualTo("N");

        PrescriptionItemDto glucoseDto = dto.getItems().get(1);
        assertThat(glucoseDto.getResultDetails()).hasSize(1);
        assertThat(glucoseDto.getResultDetails().get(0).getSeq()).isEqualTo(1);
        assertThat(glucoseDto.getResultDetails().get(0).getResultValue()).isEqualTo("5.4");

        assertThat(dto.getItems().get(2).getResultDetails()).isNull();
    }

    // ---- 입원(admission) 처방 등록 ----

    private PrescriptionCreateDto admissionRequest(String patientId, String prescribedBy, String departmentCode) {
        PrescriptionCreateDto request = new PrescriptionCreateDto();
        request.setPatientId(patientId);
        request.setPrescribedBy(prescribedBy);
        request.setDepartmentCode(departmentCode);
        request.setServiceType("ADMISSION");
        request.setOrderMethod("EMR");
        request.setPriorityCode("ROUTINE");
        request.setTimingCode("01");

        PrescriptionItemDto item = new PrescriptionItemDto();
        item.setPrescriptionType("약품");
        item.setItemCode("195700020");
        item.setItemName("타이레놀정500mg");
        item.setDosage(1.0);
        item.setDosageFormCd("01");
        item.setFrequency("TID");
        item.setDurationDays("3");
        request.setItems(List.of(item));

        return request;
    }

    @Test
    void 입원_처방을_등록하면_Encounter_조회없이_요청값으로_저장된다() {
        when(prescriptionRepository.save(any(Prescription.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(prescriptionMapper.toPrescriptionDto(any(Prescription.class))).thenReturn(new PrescriptionDto());
        when(prescriptionMapper.toItemDtoList(anyList())).thenReturn(List.of());

        PrescriptionCreateDto request = admissionRequest("P0001", "DR0001", "IM");

        service.createPrescriptionForAdmission("ADM-1", request);

        ArgumentCaptor<Prescription> prescriptionCaptor = ArgumentCaptor.forClass(Prescription.class);
        verify(prescriptionRepository).save(prescriptionCaptor.capture());
        Prescription saved = prescriptionCaptor.getValue();
        assertThat(saved.getAdmissionId()).isEqualTo("ADM-1");
        assertThat(saved.getEncounterId()).isNull();
        assertThat(saved.getPatientId()).isEqualTo("P0001");
        assertThat(saved.getPrescribedBy()).isEqualTo("DR0001");
        assertThat(saved.getDepartmentCode()).isEqualTo("IM");
        assertThat(saved.getStatus()).isEqualTo("ORDERED");
        assertThat(saved.getPharmacySendStatus()).isEqualTo("PENDING");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PrescriptionItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(prescriptionItemRepository).saveAll(itemsCaptor.capture());
        PrescriptionItem savedItem = itemsCaptor.getValue().get(0);
        assertThat(savedItem.getPrescriptionId()).isEqualTo(saved.getPrescriptionId());
        assertThat(savedItem.getPrescriptionType()).isEqualTo("약품");
        assertThat(savedItem.getItemCode()).isEqualTo("195700020");

        verifyNoInteractions(encounterRepository); // 입원 경로는 Encounter를 조회하지 않는다
    }

    @Test
    void 입원_처방_등록시_patientId가_없으면_INVALID_INPUT_예외를_던진다() {
        PrescriptionCreateDto request = admissionRequest(null, "DR0001", "IM");

        assertThatThrownBy(() -> service.createPrescriptionForAdmission("ADM-1", request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);

        verifyNoInteractions(prescriptionRepository, prescriptionItemRepository);
    }

    @Test
    void 입원_처방_등록시_prescribedBy가_없으면_INVALID_INPUT_예외를_던진다() {
        PrescriptionCreateDto request = admissionRequest("P0001", null, "IM");

        assertThatThrownBy(() -> service.createPrescriptionForAdmission("ADM-1", request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);

        verifyNoInteractions(prescriptionRepository, prescriptionItemRepository);
    }

    @Test
    void 약품_항목의_투약형태코드가_문자값이면_INVALID_INPUT_예외를_던지고_저장하지_않는다() {
        PrescriptionCreateDto request = admissionRequest("P0001", "DR0001", "IM");
        request.getItems().get(0).setDosageFormCd("TAB");

        assertThatThrownBy(() -> service.createPrescriptionForAdmission("ADM-1", request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);

        verifyNoInteractions(prescriptionRepository, prescriptionItemRepository);
    }

    @Test
    void 약품_항목의_투약형태코드가_없으면_INVALID_INPUT_예외를_던지고_저장하지_않는다() {
        PrescriptionCreateDto request = admissionRequest("P0001", "DR0001", "IM");
        request.getItems().get(0).setDosageFormCd(null);

        assertThatThrownBy(() -> service.createPrescriptionForAdmission("ADM-1", request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);

        verifyNoInteractions(prescriptionRepository, prescriptionItemRepository);
    }

    // ---- 약제실 전송(dispatchPharmacyOrders) — 외래/입원 경로 분기 ----

    private PrescriptionItem drugItem(String prescriptionId) {
        PrescriptionItem item = new PrescriptionItem();
        item.setItemId("ITEM-1");
        item.setPrescriptionId(prescriptionId);
        item.setPrescriptionType("약품");
        item.setItemCode("195700020");
        item.setItemName("타이레놀정500mg");
        return item;
    }

    @Test
    void 외래_처방은_기존대로_Encounter에서_처방과_코드를_가져와_전송한다() {
        Prescription prescription = new Prescription();
        prescription.setPrescriptionId("RX-1");
        prescription.setEncounterId("ENC-1");
        prescription.setPrescribedAt(LocalDateTime.now());
        when(prescriptionRepository.findById("RX-1")).thenReturn(Optional.of(prescription));

        Encounter encounter = new Encounter();
        encounter.setDepartmentCode("DEPT-A");
        when(encounterRepository.findById("ENC-1")).thenReturn(Optional.of(encounter));

        when(prescriptionItemRepository.findByPrescriptionId("RX-1")).thenReturn(List.of(drugItem("RX-1")));
        when(pharmacyPublisher.publish(any())).thenReturn(true);

        service.dispatchPharmacyOrders("RX-1");

        ArgumentCaptor<PharmacyEventDto.PharmacyOrderData> dataCaptor = ArgumentCaptor.forClass(PharmacyEventDto.PharmacyOrderData.class);
        verify(pharmacyPublisher).publish(dataCaptor.capture());
        assertThat(dataCaptor.getValue().departmentId()).isEqualTo("DEPT-A");
        assertThat(prescription.getPharmacySendStatus()).isEqualTo("SENT");
    }

    @Test
    void 입원_처방은_Encounter_대신_저장된_departmentCode로_약제실에_전송된다() {
        Prescription prescription = new Prescription();
        prescription.setPrescriptionId("RX-2");
        prescription.setAdmissionId("ADM-1");
        prescription.setDepartmentCode("IM");
        prescription.setPrescribedAt(LocalDateTime.now());
        when(prescriptionRepository.findById("RX-2")).thenReturn(Optional.of(prescription));

        when(prescriptionItemRepository.findByPrescriptionId("RX-2")).thenReturn(List.of(drugItem("RX-2")));
        when(pharmacyPublisher.publish(any())).thenReturn(true);

        service.dispatchPharmacyOrders("RX-2");

        ArgumentCaptor<PharmacyEventDto.PharmacyOrderData> dataCaptor = ArgumentCaptor.forClass(PharmacyEventDto.PharmacyOrderData.class);
        verify(pharmacyPublisher).publish(dataCaptor.capture());
        assertThat(dataCaptor.getValue().departmentId()).isEqualTo("IM");
        assertThat(prescription.getPharmacySendStatus()).isEqualTo("SENT");

        verifyNoInteractions(encounterRepository); // encounterId가 없으니 Encounter는 조회하지 않는다
    }

    @Test
    void 입원_처방에_departmentCode도_없으면_INVALID_INPUT_예외를_던지고_전송하지_않는다() {
        Prescription prescription = new Prescription();
        prescription.setPrescriptionId("RX-3");
        prescription.setAdmissionId("ADM-1");
        when(prescriptionRepository.findById("RX-3")).thenReturn(Optional.of(prescription));

        assertThatThrownBy(() -> service.dispatchPharmacyOrders("RX-3"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);

        verifyNoInteractions(encounterRepository, pharmacyPublisher, prescriptionItemRepository);
    }

    @Test
    void 이미_SENT인_처방은_약제실에_다시_발행하지_않는다() {
        Prescription prescription = new Prescription();
        prescription.setPrescriptionId("RX-4");
        prescription.setEncounterId("ENC-1");
        prescription.setPharmacySendStatus("SENT");
        when(prescriptionRepository.findById("RX-4")).thenReturn(Optional.of(prescription));

        service.dispatchPharmacyOrders("RX-4");

        verifyNoInteractions(pharmacyPublisher, encounterRepository, prescriptionItemRepository);
        verify(prescriptionRepository, never()).save(any());
    }

    @Test
    void FAILED였던_처방은_약제실에_다시_발행한다() {
        Prescription prescription = new Prescription();
        prescription.setPrescriptionId("RX-5");
        prescription.setAdmissionId("ADM-1");
        prescription.setDepartmentCode("IM");
        prescription.setPrescribedAt(LocalDateTime.now());
        prescription.setPharmacySendStatus("FAILED");
        when(prescriptionRepository.findById("RX-5")).thenReturn(Optional.of(prescription));
        when(prescriptionItemRepository.findByPrescriptionId("RX-5")).thenReturn(List.of(drugItem("RX-5")));
        when(pharmacyPublisher.publish(any())).thenReturn(true);

        service.dispatchPharmacyOrders("RX-5");

        verify(pharmacyPublisher).publish(any());
        assertThat(prescription.getPharmacySendStatus()).isEqualTo("SENT");
    }

    @Test
    void 취소된_처방은_약제실에_전송하지_않고_CONFLICT_예외를_던진다() {
        Prescription prescription = new Prescription();
        prescription.setPrescriptionId("RX-6");
        prescription.setStatus("CANCELLED");
        prescription.setPharmacySendStatus("PENDING");
        when(prescriptionRepository.findById("RX-6")).thenReturn(Optional.of(prescription));

        assertThatThrownBy(() -> service.dispatchPharmacyOrders("RX-6"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CONFLICT);

        verifyNoInteractions(pharmacyPublisher, encounterRepository, prescriptionItemRepository);
        verify(prescriptionRepository, never()).save(any());
    }

    @Test
    void 약제_이벤트에_진료채널_우선순위코드_구두여부가_담기고_처리후_전송상태를_반환한다() {
        Prescription prescription = new Prescription();
        prescription.setPrescriptionId("RX-7");
        prescription.setReceptionId("RCP-1");
        prescription.setServiceType("ER");
        prescription.setPriorityCode("01");
        prescription.setVerbalYn("Y");
        prescription.setDepartmentCode("ER-01");
        prescription.setPrescribedAt(LocalDateTime.now());
        when(prescriptionRepository.findById("RX-7")).thenReturn(Optional.of(prescription));
        when(prescriptionItemRepository.findByPrescriptionId("RX-7")).thenReturn(List.of(drugItem("RX-7")));
        when(pharmacyPublisher.publish(any())).thenReturn(true);

        String status = service.dispatchPharmacyOrders("RX-7");

        ArgumentCaptor<PharmacyEventDto.PharmacyOrderData> captor = ArgumentCaptor.forClass(PharmacyEventDto.PharmacyOrderData.class);
        verify(pharmacyPublisher).publish(captor.capture());
        assertThat(captor.getValue().encounterType()).isEqualTo("ER");
        assertThat(captor.getValue().priorityCode()).isEqualTo("01");
        assertThat(captor.getValue().verbalYn()).isEqualTo("Y");
        assertThat(status).isEqualTo("SENT");
    }

    @Test
    void 발행에_실패하면_FAILED를_반환한다() {
        Prescription prescription = new Prescription();
        prescription.setPrescriptionId("RX-8");
        prescription.setReceptionId("RCP-1");
        prescription.setDepartmentCode("ER-01");
        prescription.setPrescribedAt(LocalDateTime.now());
        when(prescriptionRepository.findById("RX-8")).thenReturn(Optional.of(prescription));
        when(prescriptionItemRepository.findByPrescriptionId("RX-8")).thenReturn(List.of(drugItem("RX-8")));
        when(pharmacyPublisher.publish(any())).thenReturn(false);

        assertThat(service.dispatchPharmacyOrders("RX-8")).isEqualTo("FAILED");
    }

    @Test
    void 우선순위_코드는_옛_문자열을_공통코드로_바꾸고_나머지는_그대로_둔다() {
        assertThat(PrescriptionServiceImpl.normalizePriorityCode("STAT")).isEqualTo("01");
        assertThat(PrescriptionServiceImpl.normalizePriorityCode("urgent")).isEqualTo("02");
        assertThat(PrescriptionServiceImpl.normalizePriorityCode("ROUTINE")).isEqualTo("03");
        assertThat(PrescriptionServiceImpl.normalizePriorityCode(" 02 ")).isEqualTo("02");
        assertThat(PrescriptionServiceImpl.normalizePriorityCode("99")).isEqualTo("99");
        assertThat(PrescriptionServiceImpl.normalizePriorityCode(null)).isNull();
    }

    // ---- 검사오더 전송 payload (채널 구분 / 응급 여부 / 입원ID) ----

    @Test
    void 진료구분을_검사서비스_채널구분으로_변환한다() {
        assertThat(PrescriptionServiceImpl.toEncounterType("OP")).isEqualTo("OPD");
        assertThat(PrescriptionServiceImpl.toEncounterType("외래")).isEqualTo("OPD");
        assertThat(PrescriptionServiceImpl.toEncounterType("ADMISSION")).isEqualTo("IP");
        assertThat(PrescriptionServiceImpl.toEncounterType("er")).isEqualTo("ER");
        assertThat(PrescriptionServiceImpl.toEncounterType("UNKNOWN")).isNull(); // 모르는 값은 null (검사서비스가 OPD로 처리)
        assertThat(PrescriptionServiceImpl.toEncounterType(null)).isNull();
    }

    @Test
    void 응급여부는_ER채널이거나_우선순위가_STAT이면_Y이다() {
        assertThat(PrescriptionServiceImpl.isUrgent("ER", "03")).isTrue();
        assertThat(PrescriptionServiceImpl.isUrgent("OPD", "01")).isTrue();   // ADM 공통코드 STAT
        assertThat(PrescriptionServiceImpl.isUrgent("IP", "STAT")).isTrue();  // 옛 데이터 문자열
        assertThat(PrescriptionServiceImpl.isUrgent("OPD", "03")).isFalse();  // Routine
        assertThat(PrescriptionServiceImpl.isUrgent("OPD", "02")).isFalse();  // Urgent는 STAT이 아님
        assertThat(PrescriptionServiceImpl.isUrgent("OPD", "URGENT")).isFalse();
        assertThat(PrescriptionServiceImpl.isUrgent(null, null)).isFalse();
    }

    private LabOrderApiDto.LabOrderCreateRequestDto dispatchAndCaptureLabRequest(Prescription prescription) {
        when(prescriptionRepository.findById("RX-1")).thenReturn(Optional.of(prescription));
        PrescriptionItem item = labItem("ITEM-1", null);
        item.setItemCode("01");
        when(prescriptionItemRepository.findByPrescriptionId("RX-1")).thenReturn(List.of(item));
        when(labOrderDispatcher.dispatch(any())).thenReturn(new LabOrderApiDto.DispatchOutcome("PENDING", null, null));

        service.dispatchLabOrders("RX-1");

        ArgumentCaptor<LabOrderApiDto.LabOrderCreateRequestDto> captor =
                ArgumentCaptor.forClass(LabOrderApiDto.LabOrderCreateRequestDto.class);
        verify(labOrderDispatcher).dispatch(captor.capture());
        return captor.getValue();
    }

    @Test
    void 외래_처방의_검사오더는_OPD_비응급_입원ID없음으로_전송된다() {
        Prescription prescription = new Prescription();
        prescription.setPrescriptionId("RX-1");
        prescription.setServiceType("OP");
        prescription.setPriorityCode("03");

        LabOrderApiDto.LabOrderCreateRequestDto request = dispatchAndCaptureLabRequest(prescription);

        assertThat(request.encounterType()).isEqualTo("OPD");
        assertThat(request.urgencyYn()).isEqualTo("N");
        assertThat(request.admissionId()).isNull();
    }

    @Test
    void 입원_STAT_처방의_검사오더는_IP_응급_입원ID가_담겨_전송된다() {
        Prescription prescription = new Prescription();
        prescription.setPrescriptionId("RX-1");
        prescription.setServiceType("ADMISSION");
        prescription.setPriorityCode("01");
        prescription.setAdmissionId("ADM-1");

        LabOrderApiDto.LabOrderCreateRequestDto request = dispatchAndCaptureLabRequest(prescription);

        assertThat(request.encounterType()).isEqualTo("IP");
        assertThat(request.urgencyYn()).isEqualTo("Y");
        assertThat(request.admissionId()).isEqualTo("ADM-1");
    }

    @Test
    void 응급_STAT_처방의_검사오더는_ER_응급_접수ID가_담겨_전송된다() {
        Prescription prescription = new Prescription();
        prescription.setPrescriptionId("RX-1");
        prescription.setServiceType("ER");
        prescription.setPriorityCode("01");
        prescription.setReceptionId("RCP-1");

        LabOrderApiDto.LabOrderCreateRequestDto request = dispatchAndCaptureLabRequest(prescription);

        assertThat(request.encounterType()).isEqualTo("ER");
        assertThat(request.urgencyYn()).isEqualTo("Y");
        assertThat(request.receptionId()).isEqualTo("RCP-1");
        assertThat(request.admissionId()).isNull();
    }

    // ---- dispatch-lab 재호출 멱등성 — SENT/PENDING 항목은 다시 보내지 않는다 ----

    @Test
    void 이미_SENT된_항목은_재전송_대상에서_빠지고_LAB을_다시_호출하지_않는다() {
        Prescription prescription = new Prescription();
        prescription.setPrescriptionId("RX-1");
        when(prescriptionRepository.findById("RX-1")).thenReturn(Optional.of(prescription));

        PrescriptionItem sentItem = labItem("ITEM-1", "SENT");
        when(prescriptionItemRepository.findByPrescriptionId("RX-1")).thenReturn(List.of(sentItem));

        service.dispatchLabOrders("RX-1");

        verifyNoInteractions(labOrderDispatcher);
        verify(prescriptionItemRepository, never()).saveAll(anyList());
        assertThat(sentItem.getSendStatus()).isEqualTo("SENT"); // 그대로 유지, 덮어쓰지 않음
    }

    @Test
    void PENDING인_항목도_재전송_대상에서_빠진다_LAB이_이미_받았을_수_있어서() {
        Prescription prescription = new Prescription();
        prescription.setPrescriptionId("RX-1");
        when(prescriptionRepository.findById("RX-1")).thenReturn(Optional.of(prescription));

        PrescriptionItem pendingItem = labItem("ITEM-1", "PENDING");
        when(prescriptionItemRepository.findByPrescriptionId("RX-1")).thenReturn(List.of(pendingItem));

        service.dispatchLabOrders("RX-1");

        verifyNoInteractions(labOrderDispatcher);
        assertThat(pendingItem.getSendStatus()).isEqualTo("PENDING");
    }

    @Test
    void FAILED_또는_미전송_항목은_그대로_재전송된다() {
        Prescription prescription = new Prescription();
        prescription.setPrescriptionId("RX-1");
        when(prescriptionRepository.findById("RX-1")).thenReturn(Optional.of(prescription));

        PrescriptionItem failedItem = labItem("ITEM-1", "FAILED");
        PrescriptionItem neverSentItem = labItem("ITEM-2", null);
        when(prescriptionItemRepository.findByPrescriptionId("RX-1"))
                .thenReturn(List.of(failedItem, neverSentItem));
        when(labOrderDispatcher.dispatch(any())).thenReturn(new LabOrderApiDto.DispatchOutcome("SENT", "LAB-ORD-1", null));

        service.dispatchLabOrders("RX-1");

        ArgumentCaptor<LabOrderApiDto.LabOrderCreateRequestDto> captor =
                ArgumentCaptor.forClass(LabOrderApiDto.LabOrderCreateRequestDto.class);
        verify(labOrderDispatcher).dispatch(captor.capture());
        assertThat(captor.getValue().orderItems()).hasSize(2); // FAILED/미전송 둘 다 재전송 대상에 포함
        assertThat(failedItem.getSendStatus()).isEqualTo("SENT");
        assertThat(neverSentItem.getSendStatus()).isEqualTo("SENT");
    }

    // ---- 응급(emergency) 처방 등록 ----

    private PrescriptionCreateDto emergencyRequest(String patientId, String prescribedBy, String departmentCode) {
        PrescriptionCreateDto request = new PrescriptionCreateDto();
        request.setPatientId(patientId);
        request.setPrescribedBy(prescribedBy);
        request.setDepartmentCode(departmentCode);
        request.setServiceType("ER");
        request.setOrderMethod("01");
        request.setPriorityCode("01");
        request.setTimingCode("03");
        return request;
    }

    @Test
    void 응급_처방을_등록하면_Encounter_조회없이_요청값으로_저장된다() {
        when(prescriptionRepository.save(any(Prescription.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(prescriptionMapper.toPrescriptionDto(any(Prescription.class))).thenReturn(new PrescriptionDto());
        when(prescriptionMapper.toItemDtoList(anyList())).thenReturn(List.of());

        PrescriptionCreateDto request = emergencyRequest("P0001", "DR0001", "10");

        service.createPrescriptionForEmergency("RCP-1", request);

        ArgumentCaptor<Prescription> prescriptionCaptor = ArgumentCaptor.forClass(Prescription.class);
        verify(prescriptionRepository).save(prescriptionCaptor.capture());
        Prescription saved = prescriptionCaptor.getValue();
        assertThat(saved.getReceptionId()).isEqualTo("RCP-1");
        assertThat(saved.getEncounterId()).isNull();
        assertThat(saved.getPatientId()).isEqualTo("P0001");
        assertThat(saved.getPrescribedBy()).isEqualTo("DR0001");
        assertThat(saved.getDepartmentCode()).isEqualTo("10");
        assertThat(saved.getStatus()).isEqualTo("ORDERED");
        assertThat(saved.getPharmacySendStatus()).isNull(); // 약품 항목이 없으므로 PENDING이 아니라 null

        verifyNoInteractions(encounterRepository); // 응급 경로는 Encounter를 조회하지 않는다
    }

    @Test
    void 검사_항목만_있으면_pharmacySendStatus는_null이고_약품_항목이_있으면_PENDING이다() {
        when(prescriptionRepository.save(any(Prescription.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(prescriptionMapper.toPrescriptionDto(any(Prescription.class))).thenReturn(new PrescriptionDto());
        when(prescriptionMapper.toItemDtoList(anyList())).thenReturn(List.of());

        PrescriptionCreateDto labOnlyRequest = emergencyRequest("P0001", "DR0001", "10");
        PrescriptionItemDto labItem = new PrescriptionItemDto();
        labItem.setPrescriptionType("검사");
        labItem.setItemCode("02");
        labItem.setItemName("CBC");
        labOnlyRequest.setItems(List.of(labItem));

        service.createPrescriptionForEmergency("RCP-1", labOnlyRequest);

        ArgumentCaptor<Prescription> captor = ArgumentCaptor.forClass(Prescription.class);
        verify(prescriptionRepository).save(captor.capture());
        assertThat(captor.getValue().getPharmacySendStatus()).isNull();

        reset(prescriptionRepository, prescriptionMapper);
        when(prescriptionRepository.save(any(Prescription.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(prescriptionMapper.toPrescriptionDto(any(Prescription.class))).thenReturn(new PrescriptionDto());
        when(prescriptionMapper.toItemDtoList(anyList())).thenReturn(List.of());

        PrescriptionCreateDto withPharmacyRequest = emergencyRequest("P0001", "DR0001", "10");
        PrescriptionItemDto pharmacyItem = new PrescriptionItemDto();
        pharmacyItem.setPrescriptionType("약품");
        pharmacyItem.setItemCode("195700020");
        pharmacyItem.setDosageFormCd("01");
        withPharmacyRequest.setItems(List.of(labItem, pharmacyItem));

        service.createPrescriptionForEmergency("RCP-2", withPharmacyRequest);

        ArgumentCaptor<Prescription> captor2 = ArgumentCaptor.forClass(Prescription.class);
        verify(prescriptionRepository).save(captor2.capture());
        assertThat(captor2.getValue().getPharmacySendStatus()).isEqualTo("PENDING");
    }

    private PrescriptionItemDto drugItemRequest() {
        PrescriptionItemDto item = new PrescriptionItemDto();
        item.setPrescriptionType("약품");
        item.setItemCode("195700020");
        item.setDosageFormCd("01");
        return item;
    }

    @Test
    void dispatchNow가_true이고_약품이_있는데_departmentCode가_없으면_저장_전에_INVALID_INPUT_예외를_던진다() {
        PrescriptionCreateDto request = emergencyRequest("P0001", "DR0001", null);
        request.setDispatchNow(true);
        request.setItems(List.of(drugItemRequest()));

        assertThatThrownBy(() -> service.createPrescriptionForEmergency("RCP-1", request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);

        verifyNoInteractions(prescriptionRepository, prescriptionItemRepository);
    }

    @Test
    void dispatchNow가_true여도_약품이_없으면_departmentCode_없이_등록된다() {
        when(prescriptionRepository.save(any(Prescription.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(prescriptionMapper.toPrescriptionDto(any(Prescription.class))).thenReturn(new PrescriptionDto());
        when(prescriptionMapper.toItemDtoList(anyList())).thenReturn(List.of());

        PrescriptionCreateDto request = emergencyRequest("P0001", "DR0001", null);
        request.setDispatchNow(true);
        PrescriptionItemDto lab = new PrescriptionItemDto();
        lab.setPrescriptionType("검사");
        request.setItems(List.of(lab));

        service.createPrescriptionForEmergency("RCP-1", request);

        verify(prescriptionRepository).save(any(Prescription.class));
    }

    @Test
    void 응급_처방_등록시_patientId가_없으면_INVALID_INPUT_예외를_던진다() {
        PrescriptionCreateDto request = emergencyRequest(null, "DR0001", "10");

        assertThatThrownBy(() -> service.createPrescriptionForEmergency("RCP-1", request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);

        verifyNoInteractions(prescriptionRepository, prescriptionItemRepository);
    }

    @Test
    void 응급_처방_등록시_prescribedBy가_없으면_INVALID_INPUT_예외를_던진다() {
        PrescriptionCreateDto request = emergencyRequest("P0001", null, "10");

        assertThatThrownBy(() -> service.createPrescriptionForEmergency("RCP-1", request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);

        verifyNoInteractions(prescriptionRepository, prescriptionItemRepository);
    }

    // ---- 구두처방 확정(confirmVerbalOrder) ----

    @Test
    void 구두처방을_확정하면_확정일시와_확정자가_기록된다() {
        Prescription prescription = new Prescription();
        prescription.setPrescriptionId("RX-1");
        prescription.setVerbalYn("Y");
        when(prescriptionRepository.findById("RX-1")).thenReturn(Optional.of(prescription));
        when(prescriptionRepository.save(any(Prescription.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(prescriptionMapper.toPrescriptionDto(any(Prescription.class))).thenReturn(new PrescriptionDto());
        when(prescriptionItemRepository.findByPrescriptionId("RX-1")).thenReturn(List.of());
        when(prescriptionMapper.toItemDtoList(anyList())).thenReturn(List.of());

        service.confirmVerbalOrder("RX-1", "DR0001");

        assertThat(prescription.getVerbalConfirmedBy()).isEqualTo("DR0001");
        assertThat(prescription.getVerbalConfirmedAt()).isNotNull();
    }

    @Test
    void 구두처방이_아닌_처방을_확정하려하면_INVALID_INPUT_예외를_던진다() {
        Prescription prescription = new Prescription();
        prescription.setPrescriptionId("RX-1");
        prescription.setVerbalYn("N");
        when(prescriptionRepository.findById("RX-1")).thenReturn(Optional.of(prescription));

        assertThatThrownBy(() -> service.confirmVerbalOrder("RX-1", "DR0001"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void 이미_확정된_구두처방을_다시_확정하려하면_CONFLICT_예외를_던진다() {
        Prescription prescription = new Prescription();
        prescription.setPrescriptionId("RX-1");
        prescription.setVerbalYn("Y");
        prescription.setVerbalConfirmedAt(LocalDateTime.now());
        when(prescriptionRepository.findById("RX-1")).thenReturn(Optional.of(prescription));

        assertThatThrownBy(() -> service.confirmVerbalOrder("RX-1", "DR0001"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    // ---- 검사항목 검색(searchLabItem) ----

    @Test
    void 검사항목_검색은_LabClient에_위임한다() {
        when(labClient.searchLabItem("CBC")).thenReturn(List.of(
                new LabOrderApiDto.LabItem("LAB001", "CBC(일반혈액검사)", "GENERAL", List.of("혈액"))));

        List<LabOrderApiDto.LabItem> result = service.searchLabItem("CBC");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).itemCode()).isEqualTo("LAB001");
        verify(labClient).searchLabItem("CBC");
    }

    // ---- 처방 목록 조회 — receptionId 필터 ----

    @Test
    void receptionId로_목록을_조회하면_같은_접수건_처방만_남는다() {
        when(prescriptionRepository.findAll(any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of(new Prescription(), new Prescription()));

        PrescriptionDto own = new PrescriptionDto();
        own.setPrescriptionId("RX-1");
        own.setReceptionId("RCP-1");
        PrescriptionDto other = new PrescriptionDto();
        other.setPrescriptionId("RX-2");
        other.setReceptionId("RCP-2");
        when(prescriptionMapper.toPrescriptionDtoList(anyList())).thenReturn(List.of(own, other));

        List<PrescriptionDto> result = service.getPrescriptions(null, "RCP-1", null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPrescriptionId()).isEqualTo("RX-1");
    }

    @Test
    void receptionId가_없으면_전체_목록을_반환한다() {
        when(prescriptionRepository.findAll(any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of(new Prescription(), new Prescription()));

        PrescriptionDto a = new PrescriptionDto();
        a.setPrescriptionId("RX-1");
        PrescriptionDto b = new PrescriptionDto();
        b.setPrescriptionId("RX-2");
        when(prescriptionMapper.toPrescriptionDtoList(anyList())).thenReturn(List.of(a, b));

        List<PrescriptionDto> result = service.getPrescriptions(null, null, null);

        assertThat(result).hasSize(2);
    }

    @Test
    void encounterId로_목록을_조회하면_같은_진료건_처방만_남고_labSendStatus도_채워진다() {
        when(prescriptionRepository.findAll(any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of(new Prescription(), new Prescription()));

        PrescriptionDto own = new PrescriptionDto();
        own.setPrescriptionId("RX-1");
        own.setEncounterId("ENC-1");
        PrescriptionDto other = new PrescriptionDto();
        other.setPrescriptionId("RX-2");
        other.setEncounterId("ENC-2");
        when(prescriptionMapper.toPrescriptionDtoList(anyList())).thenReturn(List.of(own, other));
        when(prescriptionItemRepository.findByPrescriptionIdIn(List.of("RX-1"))).thenReturn(List.of(
                labItemWithStatus("RX-1", "SENT")));

        List<PrescriptionDto> result = service.getPrescriptions(null, null, "ENC-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPrescriptionId()).isEqualTo("RX-1");
        assertThat(result.get(0).getLabSendStatus()).isEqualTo("SENT"); // encounterId로 좁혀졌으니 요약도 채워짐
    }

    @Test
    void keyword로_좁혀져도_labSendStatus가_채워진다() {
        when(prescriptionRepository.findAll(any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of(new Prescription()));

        PrescriptionDto dto = new PrescriptionDto();
        dto.setPrescriptionId("RX-1");
        dto.setPatientName("정대훈");
        when(prescriptionMapper.toPrescriptionDtoList(anyList())).thenReturn(List.of(dto));
        when(prescriptionItemRepository.findByPrescriptionIdIn(List.of("RX-1"))).thenReturn(List.of(
                labItemWithStatus("RX-1", "FAILED")));

        List<PrescriptionDto> result = service.getPrescriptions("정대훈", null, null);

        assertThat(result.get(0).getLabSendStatus()).isEqualTo("FAILED");
    }

    @Test
    void 아무_조건_없이_전체_조회해도_검사_상태_요약이_채워진다() {
        when(prescriptionRepository.findAll(any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of(new Prescription(), new Prescription()));

        PrescriptionDto withLab = new PrescriptionDto();
        withLab.setPrescriptionId("RX-1");
        PrescriptionDto noLab = new PrescriptionDto();
        noLab.setPrescriptionId("RX-2");
        when(prescriptionMapper.toPrescriptionDtoList(anyList())).thenReturn(List.of(withLab, noLab));
        when(prescriptionItemRepository.findByPrescriptionIdIn(List.of("RX-1", "RX-2"))).thenReturn(List.of(
                labItemWithStatus("RX-1", "SENT")));

        List<PrescriptionDto> result = service.getPrescriptions(null, null, null);

        assertThat(result.get(0).getLabSendStatus()).isEqualTo("SENT");
        assertThat(result.get(0).getLabResultStatus()).isEqualTo("WAITING"); // 결과 보고 전
        assertThat(result.get(1).getLabResultStatus()).isNull(); // 검사 항목 자체가 없음
    }

    @Test
    void 전체_조회_시_처방이_많으면_항목_조회를_500건씩_나눠서_한다() {
        when(prescriptionRepository.findAll(any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of(new Prescription()));

        List<PrescriptionDto> dtos = new java.util.ArrayList<>();
        for (int i = 0; i < 1200; i++) {
            PrescriptionDto dto = new PrescriptionDto();
            dto.setPrescriptionId("RX-" + i);
            dtos.add(dto);
        }
        when(prescriptionMapper.toPrescriptionDtoList(anyList())).thenReturn(dtos);

        service.getPrescriptions(null, null, null);

        org.mockito.ArgumentCaptor<List<String>> captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(prescriptionItemRepository, times(3)).findByPrescriptionIdIn(captor.capture());
        assertThat(captor.getAllValues()).extracting(List::size).containsExactly(500, 500, 200); // Oracle IN 1000개 제한 회피
    }

    // ---- 처방 목록 조회 — receptionId 지정 시 검사 전송상태 요약(labSendStatus) ----

    private PrescriptionItem labItemWithStatus(String prescriptionId, String sendStatus) {
        PrescriptionItem item = new PrescriptionItem();
        item.setItemId("ITEM-" + prescriptionId + "-" + sendStatus);
        item.setPrescriptionId(prescriptionId);
        item.setPrescriptionType("검사");
        item.setSendStatus(sendStatus);
        return item;
    }

    @Test
    void 검사항목_중_하나라도_FAILED면_labSendStatus는_FAILED다() {
        when(prescriptionRepository.findAll(any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of(new Prescription()));
        PrescriptionDto dto = new PrescriptionDto();
        dto.setPrescriptionId("RX-1");
        dto.setReceptionId("RCP-1");
        when(prescriptionMapper.toPrescriptionDtoList(anyList())).thenReturn(List.of(dto));
        when(prescriptionItemRepository.findByPrescriptionIdIn(List.of("RX-1"))).thenReturn(List.of(
                labItemWithStatus("RX-1", "SENT"),
                labItemWithStatus("RX-1", "FAILED")
        ));

        List<PrescriptionDto> result = service.getPrescriptions(null, "RCP-1", null);

        assertThat(result.get(0).getLabSendStatus()).isEqualTo("FAILED");
    }

    @Test
    void 검사항목이_FAILED없이_하나라도_미전송이면_labSendStatus는_PENDING이다() {
        when(prescriptionRepository.findAll(any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of(new Prescription()));
        PrescriptionDto dto = new PrescriptionDto();
        dto.setPrescriptionId("RX-1");
        dto.setReceptionId("RCP-1");
        when(prescriptionMapper.toPrescriptionDtoList(anyList())).thenReturn(List.of(dto));
        when(prescriptionItemRepository.findByPrescriptionIdIn(List.of("RX-1"))).thenReturn(List.of(
                labItemWithStatus("RX-1", "SENT"),
                labItemWithStatus("RX-1", null)
        ));

        List<PrescriptionDto> result = service.getPrescriptions(null, "RCP-1", null);

        assertThat(result.get(0).getLabSendStatus()).isEqualTo("PENDING");
    }

    @Test
    void 검사항목이_전부_SENT면_labSendStatus는_SENT다() {
        when(prescriptionRepository.findAll(any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of(new Prescription()));
        PrescriptionDto dto = new PrescriptionDto();
        dto.setPrescriptionId("RX-1");
        dto.setReceptionId("RCP-1");
        when(prescriptionMapper.toPrescriptionDtoList(anyList())).thenReturn(List.of(dto));
        when(prescriptionItemRepository.findByPrescriptionIdIn(List.of("RX-1"))).thenReturn(List.of(
                labItemWithStatus("RX-1", "SENT"),
                labItemWithStatus("RX-1", "SENT")
        ));

        List<PrescriptionDto> result = service.getPrescriptions(null, "RCP-1", null);

        assertThat(result.get(0).getLabSendStatus()).isEqualTo("SENT");
    }

    @Test
    void 검사항목이_없으면_labSendStatus는_null이다() {
        when(prescriptionRepository.findAll(any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of(new Prescription()));
        PrescriptionDto dto = new PrescriptionDto();
        dto.setPrescriptionId("RX-1");
        dto.setReceptionId("RCP-1");
        when(prescriptionMapper.toPrescriptionDtoList(anyList())).thenReturn(List.of(dto));
        when(prescriptionItemRepository.findByPrescriptionIdIn(List.of("RX-1"))).thenReturn(List.of());

        List<PrescriptionDto> result = service.getPrescriptions(null, "RCP-1", null);

        assertThat(result.get(0).getLabSendStatus()).isNull();
        assertThat(result.get(0).getLabResultStatus()).isNull();
    }

    // ---- 처방 목록 조회 — receptionId 지정 시 검사 결과도착상태 요약(labResultStatus) ----

    @Test
    void 결과가_하나도_안_왔으면_labResultStatus는_WAITING이다() {
        when(prescriptionRepository.findAll(any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of(new Prescription()));
        PrescriptionDto dto = new PrescriptionDto();
        dto.setPrescriptionId("RX-1");
        dto.setReceptionId("RCP-1");
        when(prescriptionMapper.toPrescriptionDtoList(anyList())).thenReturn(List.of(dto));

        PrescriptionItem noResultItem = labItemWithStatus("RX-1", "SENT");
        when(prescriptionItemRepository.findByPrescriptionIdIn(List.of("RX-1"))).thenReturn(List.of(noResultItem));

        List<PrescriptionDto> result = service.getPrescriptions(null, "RCP-1", null);

        assertThat(result.get(0).getLabResultStatus()).isEqualTo("WAITING");
    }

    @Test
    void 결과가_일부만_와도_labResultStatus는_COMPLETE다() {
        when(prescriptionRepository.findAll(any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of(new Prescription()));
        PrescriptionDto dto = new PrescriptionDto();
        dto.setPrescriptionId("RX-1");
        dto.setReceptionId("RCP-1");
        when(prescriptionMapper.toPrescriptionDtoList(anyList())).thenReturn(List.of(dto));

        PrescriptionItem withResult = labItemWithStatus("RX-1", "SENT");
        withResult.setResultReportedAt(LocalDateTime.now());
        PrescriptionItem withoutResult = labItemWithStatus("RX-1", "SENT");
        when(prescriptionItemRepository.findByPrescriptionIdIn(List.of("RX-1")))
                .thenReturn(List.of(withResult, withoutResult));

        List<PrescriptionDto> result = service.getPrescriptions(null, "RCP-1", null);

        assertThat(result.get(0).getLabResultStatus()).isEqualTo("COMPLETE"); // 일부만 와도 PARTIAL 아니라 COMPLETE
    }
}
