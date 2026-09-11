package kr.co.seoulit.his.outpatientservice.outpatientcare.controller;

import kr.co.seoulit.his.outpatientservice.common.ApiResponse;
import kr.co.seoulit.his.outpatientservice.outpatientcare.dto.ConsultationSaveRequestDto;
import kr.co.seoulit.his.outpatientservice.outpatientcare.dto.ConsultationSaveResponseDto;
import kr.co.seoulit.his.outpatientservice.outpatientcare.service.ConsultationService;
import kr.co.seoulit.his.outpatientservice.prescription.dto.PrescriptionItemDto;
import kr.co.seoulit.his.outpatientservice.prescription.service.PrescriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/outpatient/encounters")
@RequiredArgsConstructor
public class ConsultationController {

    private final ConsultationService consultationService;
    private final PrescriptionService prescriptionService;

    // 진료기록+처방 동시 등록 후 검사실 전송 POST /api/outpatient/encounters/{encounterId}/consultation
    @PostMapping("/{encounterId}/consultation")
    public ApiResponse<ConsultationSaveResponseDto> saveConsultation(
            @PathVariable String encounterId,
            @RequestBody ConsultationSaveRequestDto request) {

        // 진료기록 + 처방 저장(서비스를 불러서 진료기록과처방내용을 db에 저장)
        ConsultationSaveResponseDto response = consultationService.saveRecordAndPrescription(encounterId, request);

        // 검사실 전송
        List<PrescriptionItemDto> items = prescriptionService.dispatchLabOrders(response.getPrescription().getPrescriptionId());
        response.getPrescription().setItems(items);

        // 약제실 전송
        prescriptionService.dispatchPharmacyOrders(response.getPrescription().getPrescriptionId());

        return ApiResponse.success(response);
    }


}
