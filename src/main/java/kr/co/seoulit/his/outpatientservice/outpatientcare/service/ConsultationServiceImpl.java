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

        // 처방 항목이 하나도 없으면 빈 처방을 만들지 않는다 (진료기록만 저장되고 처방 목록에도 안 나온다)
        PrescriptionDto prescription = hasPrescriptionItems(request)
                ? prescriptionService.createPrescription(encounterId, request.getPrescription())
                : null;

        // 약제서비스 전송(Kafka)은 여기서 하지 않는다 — 이 메서드의 트랜잭션이 커밋되기 전에 발행하면
        // 커밋 실패 시 존재하지 않는 처방이 약제서비스로 나가고, ConsultationController의 호출과 겹쳐 2회 발행된다.
        // 검사실·약제실 전송은 커밋 이후 ConsultationController가 순서대로 수행한다.

        ConsultationSaveResponseDto response = new ConsultationSaveResponseDto();
        response.setMedicalRecord(record);
        response.setPrescription(prescription);
        return response;
    }

    private static boolean hasPrescriptionItems(ConsultationSaveRequestDto request) {
        return request.getPrescription() != null
                && request.getPrescription().getItems() != null
                && !request.getPrescription().getItems().isEmpty();
    }
}
