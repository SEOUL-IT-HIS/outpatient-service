package kr.co.seoulit.his.outpatientservice.prescription.service;

import kr.co.seoulit.his.outpatientservice.common.client.lab.LabOrderDispatcher;
import kr.co.seoulit.his.outpatientservice.common.client.patient.PatientClient;
import kr.co.seoulit.his.outpatientservice.common.client.pharmacy.PharmacyClient;
import kr.co.seoulit.his.outpatientservice.common.client.pharmacy.PharmacyPublisher;
import kr.co.seoulit.his.outpatientservice.outpatientcare.repository.EncounterRepository;
import kr.co.seoulit.his.outpatientservice.prescription.entity.PrescriptionItem;
import kr.co.seoulit.his.outpatientservice.prescription.mapper.PrescriptionMapper;
import kr.co.seoulit.his.outpatientservice.prescription.repository.PrescriptionItemRepository;
import kr.co.seoulit.his.outpatientservice.prescription.repository.PrescriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

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

    private PrescriptionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PrescriptionServiceImpl(
                prescriptionRepository, prescriptionItemRepository, prescriptionMapper,
                patientClient, encounterRepository, labOrderDispatcher, pharmacyPublisher, pharmacyClient
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
}
