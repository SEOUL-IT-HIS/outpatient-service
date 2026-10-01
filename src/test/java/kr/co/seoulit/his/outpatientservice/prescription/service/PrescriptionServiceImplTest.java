package kr.co.seoulit.his.outpatientservice.prescription.service;

import kr.co.seoulit.his.outpatientservice.common.cache.CommonCodeCache;
import kr.co.seoulit.his.outpatientservice.common.client.lab.LabOrderDispatcher;
import kr.co.seoulit.his.outpatientservice.common.client.lab.LabResultEventDto;
import kr.co.seoulit.his.outpatientservice.common.client.patient.PatientClient;
import kr.co.seoulit.his.outpatientservice.common.client.pharmacy.PharmacyClient;
import kr.co.seoulit.his.outpatientservice.common.client.pharmacy.PharmacyPublisher;
import kr.co.seoulit.his.outpatientservice.outpatientcare.repository.EncounterRepository;
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

    private PrescriptionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PrescriptionServiceImpl(
                prescriptionRepository, prescriptionItemRepository, prescriptionMapper,
                patientClient, encounterRepository, labOrderDispatcher, pharmacyPublisher, pharmacyClient,
                commonCodeCache, examResultRefRepository
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
                commonCodeCache, examResultRefRepository
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
}
