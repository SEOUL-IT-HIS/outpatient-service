package kr.co.seoulit.his.outpatientservice.prescription.controller;

import kr.co.seoulit.his.outpatientservice.common.ApiResponse;
import kr.co.seoulit.his.outpatientservice.common.client.lab.LabOrderApiDto;
import kr.co.seoulit.his.outpatientservice.common.client.pharmacy.PharmacyApiDto;
import kr.co.seoulit.his.outpatientservice.prescription.dto.PrescriptionCreateDto;
import kr.co.seoulit.his.outpatientservice.prescription.dto.PrescriptionDto;
import kr.co.seoulit.his.outpatientservice.prescription.dto.PrescriptionItemDto;
import kr.co.seoulit.his.outpatientservice.prescription.service.PrescriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/outpatient/prescriptions")
@RequiredArgsConstructor
public class PrescriptionController {

    private final PrescriptionService prescriptionService;

    // 처방 목록 조회 GET /api/outpatient/prescriptions?keyword={keyword}&receptionId={receptionId}
    @GetMapping
    public ApiResponse<List<PrescriptionDto>> getPrescriptions(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "receptionId", required = false) String receptionId) {
        List<PrescriptionDto> response = prescriptionService.getPrescriptions(keyword, receptionId);
        return ApiResponse.success(response);
    }

    // 처방 상세 조회 GET /api/outpatient/prescriptions/{prescriptionId}
    @GetMapping("/{prescriptionId}")
    public ApiResponse<PrescriptionDto> getPrescription(@PathVariable String prescriptionId) {
        PrescriptionDto response = prescriptionService.getPrescription(prescriptionId);
        return ApiResponse.success(response);
    }

    // 처방 등록 POST /api/outpatient/prescriptions/{encounterId}
    @PostMapping("/{encounterId}")
    public ApiResponse<PrescriptionDto> createPrescription(
            @PathVariable String encounterId,
            @RequestBody PrescriptionCreateDto request) {
        PrescriptionDto response = prescriptionService.createPrescription(encounterId, request);
        return ApiResponse.success(response);
    }

    // 입원 처방 등록 POST /api/outpatient/prescriptions/admission/{admissionId} (병동 서비스 서버 간 호출)
    @PostMapping("/admission/{admissionId}")
    public ApiResponse<PrescriptionDto> createPrescriptionForAdmission(
            @PathVariable String admissionId,
            @RequestBody PrescriptionCreateDto request) {
        PrescriptionDto response = prescriptionService.createPrescriptionForAdmission(admissionId, request);
        return ApiResponse.success(response);
    }

    // 응급 처방 등록 POST /api/outpatient/prescriptions/emergency/{receptionId} (응급 서비스 서버 간 호출)
    @PostMapping("/emergency/{receptionId}")
    public ApiResponse<PrescriptionDto> createPrescriptionForEmergency(
            @PathVariable String receptionId,
            @RequestBody PrescriptionCreateDto request) {
        PrescriptionDto response = prescriptionService.createPrescriptionForEmergency(receptionId, request);
        return ApiResponse.success(response);
    }

    // 구두처방 확정 PATCH /api/outpatient/prescriptions/{prescriptionId}/verbal-confirm?confirmedBy={의사ID}
    @PatchMapping("/{prescriptionId}/verbal-confirm")
    public ApiResponse<PrescriptionDto> confirmVerbalOrder(
            @PathVariable String prescriptionId,
            @RequestParam("confirmedBy") String confirmedBy) {
        PrescriptionDto response = prescriptionService.confirmVerbalOrder(prescriptionId, confirmedBy);
        return ApiResponse.success(response);
    }

    // 검사실 전송 POST /api/outpatient/prescriptions/{prescriptionId}/dispatch-lab
    @PostMapping("/{prescriptionId}/dispatch-lab")
    public ApiResponse<List<PrescriptionItemDto>> dispatchLabOrders(@PathVariable String prescriptionId) {
        List<PrescriptionItemDto> response = prescriptionService.dispatchLabOrders(prescriptionId);
        return ApiResponse.success(response);
    }

    // 약제실 전송 POST /api/outpatient/prescriptions/{prescriptionId}/dispatch-pharmacy
    @PostMapping("/{prescriptionId}/dispatch-pharmacy")
    public ApiResponse<Void> dispatchPharmacyOrders(@PathVariable String prescriptionId) {
        prescriptionService.dispatchPharmacyOrders(prescriptionId);
        return ApiResponse.success();
    }

    // 약품 검색 GET /api/outpatient/prescriptions/medications/search?name={name}
    @GetMapping("/medications/search")
    public ApiResponse<List<PharmacyApiDto.Medication>> searchMedication(
            @RequestParam("name") String name) {
        List<PharmacyApiDto.Medication> response = prescriptionService.searchMedication(name);
        return ApiResponse.success(response);
    }

    // 검사항목 검색 GET /api/outpatient/prescriptions/lab-items/search?name={name}
    @GetMapping("/lab-items/search")
    public ApiResponse<List<LabOrderApiDto.LabItem>> searchLabItem(
            @RequestParam("name") String name) {
        List<LabOrderApiDto.LabItem> response = prescriptionService.searchLabItem(name);
        return ApiResponse.success(response);
    }

    //처방 비활성화 PATCH /api/outpatient/prescriptions/{prescriptionId}/deactivate
    @PatchMapping("/{prescriptionId}/deactivate")
    public ApiResponse<String> deactivatePrescription(
            @PathVariable("prescriptionId") String prescriptionId,
            @RequestParam("cancelReason") String cancelReason,
            @RequestParam("userId") String userId) {

        prescriptionService.deactivatePrescription(prescriptionId, cancelReason, userId) ;

        return ApiResponse.success("The prescription has been deactivated successfully.");
    }
}