package kr.co.seoulit.his.outpatientservice.outpatientcare.service;

import kr.co.seoulit.his.outpatientservice.outpatientcare.dto.ConsultationSaveRequestDto;
import kr.co.seoulit.his.outpatientservice.outpatientcare.dto.ConsultationSaveResponseDto;
import kr.co.seoulit.his.outpatientservice.outpatientcare.dto.MedicalRecordCreateDto;
import kr.co.seoulit.his.outpatientservice.outpatientcare.dto.MedicalRecordDto;
import kr.co.seoulit.his.outpatientservice.prescription.dto.PrescriptionCreateDto;
import kr.co.seoulit.his.outpatientservice.prescription.dto.PrescriptionDto;
import kr.co.seoulit.his.outpatientservice.prescription.dto.PrescriptionItemDto;
import kr.co.seoulit.his.outpatientservice.prescription.service.PrescriptionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsultationServiceImplTest {

    @Mock
    private OutpatientCareService outpatientCareService;
    @Mock
    private PrescriptionService prescriptionService;

    @InjectMocks
    private ConsultationServiceImpl consultationService;

    @Test
    void 진료기록과_처방을_저장하되_약제실_전송은_하지_않는다() {
        // 약제 전송은 커밋 이후 ConsultationController가 1회만 수행한다 (서비스에서도 하면 2회 발행된다)
        ConsultationSaveRequestDto request = new ConsultationSaveRequestDto();
        request.setMedicalRecord(new MedicalRecordCreateDto());
        PrescriptionCreateDto prescriptionRequest = new PrescriptionCreateDto();
        prescriptionRequest.setItems(List.of(new PrescriptionItemDto()));
        request.setPrescription(prescriptionRequest);

        MedicalRecordDto record = new MedicalRecordDto();
        PrescriptionDto prescription = new PrescriptionDto();
        prescription.setPrescriptionId("RX-1");
        when(outpatientCareService.createRecord(any())).thenReturn(record);
        when(prescriptionService.createPrescription(eq("ENC-1"), any())).thenReturn(prescription);

        ConsultationSaveResponseDto response = consultationService.saveRecordAndPrescription("ENC-1", request);

        assertThat(response.getMedicalRecord()).isSameAs(record);
        assertThat(response.getPrescription()).isSameAs(prescription);
        assertThat(request.getMedicalRecord().getEncounterId()).isEqualTo("ENC-1");
        verify(prescriptionService, never()).dispatchPharmacyOrders(any());
    }

    @Test
    void 처방_항목이_없으면_빈_처방을_만들지_않고_진료기록만_저장한다() {
        ConsultationSaveRequestDto request = new ConsultationSaveRequestDto();
        request.setMedicalRecord(new MedicalRecordCreateDto());
        PrescriptionCreateDto prescriptionRequest = new PrescriptionCreateDto();
        prescriptionRequest.setItems(List.of());
        request.setPrescription(prescriptionRequest);

        MedicalRecordDto record = new MedicalRecordDto();
        when(outpatientCareService.createRecord(any())).thenReturn(record);

        ConsultationSaveResponseDto response = consultationService.saveRecordAndPrescription("ENC-1", request);

        assertThat(response.getMedicalRecord()).isSameAs(record);
        assertThat(response.getPrescription()).isNull();
        verify(prescriptionService, never()).createPrescription(any(), any());
    }
}
