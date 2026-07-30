package kr.co.seoulit.his.outpatientservice.outpatientcare.controller;

import kr.co.seoulit.his.outpatientservice.common.ApiResponse;
import kr.co.seoulit.his.outpatientservice.outpatientcare.dto.MedicalRecordDto;
import kr.co.seoulit.his.outpatientservice.outpatientcare.service.OutpatientCareService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/outpatient/records")
@RequiredArgsConstructor
public class MedicalRecordController {

    private final OutpatientCareService outpatientCareService;

    // --- [진료기록 목록 조회] GET /api/outpatient/records?encounterId={id} ---
    @GetMapping
    public ApiResponse<List<MedicalRecordDto>> getRecords(
            @RequestParam String encounterId
    ) {
        List<MedicalRecordDto> response = outpatientCareService.getRecords(encounterId);
        return ApiResponse.success(response);
    }

    // --- [진료기록 상세 조회] GET /api/outpatient/records/{recordId} ---
    @GetMapping("/{recordId}")
    public ApiResponse<MedicalRecordDto> getRecord(
            @PathVariable String recordId
    ) {
        MedicalRecordDto response = outpatientCareService.getRecord(recordId);
        return ApiResponse.success(response);
    }
}