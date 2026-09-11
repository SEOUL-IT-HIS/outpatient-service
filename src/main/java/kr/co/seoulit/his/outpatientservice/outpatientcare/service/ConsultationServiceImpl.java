package kr.co.seoulit.his.outpatientservice.outpatientcare.service;

import kr.co.seoulit.his.outpatientservice.outpatientcare.dto.ConsultationSaveRequestDto;
import kr.co.seoulit.his.outpatientservice.outpatientcare.dto.ConsultationSaveResponseDto;
import kr.co.seoulit.his.outpatientservice.outpatientcare.dto.MedicalRecordDto;
import kr.co.seoulit.his.outpatientservice.prescription.dto.PrescriptionDto;
import kr.co.seoulit.his.outpatientservice.prescription.service.PrescriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConsultationServiceImpl implements ConsultationService {

    private final OutpatientCareService outpatientCareService;
    private final PrescriptionService prescriptionService;

    // 진료기록 저장 + 처방/처방아이템 저장
    @Override
    //이메서드 안에서 일어나는 모든 db저장작업은 하나로 묶임
    @Transactional
    public ConsultationSaveResponseDto saveRecordAndPrescription(String encounterId, ConsultationSaveRequestDto request) {
        request.getMedicalRecord().setEncounterId(encounterId);

        MedicalRecordDto record = outpatientCareService.createRecord(request.getMedicalRecord());
        PrescriptionDto prescription = prescriptionService.createPrescription(encounterId, request.getPrescription());

        // 처방 저장 즉시 약품 항목을 약제서비스로 전송(Kafka). 약품 항목이 없으면 내부에서 바로 종료됨.
        prescriptionService.dispatchPharmacyOrders(prescription.getPrescriptionId());

        ConsultationSaveResponseDto response = new ConsultationSaveResponseDto();
        response.setMedicalRecord(record);
        response.setPrescription(prescription);
        return response;
    }
}
