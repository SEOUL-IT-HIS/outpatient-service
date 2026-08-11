package kr.co.seoulit.his.outpatientservice.outpatientcare.controller;

import kr.co.seoulit.his.outpatientservice.common.ApiResponse;
import kr.co.seoulit.his.outpatientservice.outpatientcare.dto.MedicalRecordCreateDto;
import kr.co.seoulit.his.outpatientservice.outpatientcare.dto.MedicalRecordDto;
import kr.co.seoulit.his.outpatientservice.outpatientcare.service.OutpatientCareService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/outpatient/records")
@RequiredArgsConstructor
public class MedicalRecordController {

    private final OutpatientCareService outpatientCareService;

    // 진료기록 목록 조회 GET /api/outpatient/records?keyword={keyword}
    @GetMapping
    public ApiResponse<List<MedicalRecordDto>> getRecords(
            @RequestParam(name = "keyword", required = false) String keyword) { // name="keyword" 명시!

        List<MedicalRecordDto> response = outpatientCareService.getRecords(keyword);
        return ApiResponse.success(response);
    }

    // 진료기록 상세 조회 GET /api/outpatient/records/{recordId}
    @GetMapping("/{recordId}")
    public ApiResponse<MedicalRecordDto> getRecord(@PathVariable String recordId) {
        MedicalRecordDto response = outpatientCareService.getRecord(recordId);
        return ApiResponse.success(response);
    }

    // 진료기록 등록 POST /api/outpatient/records
    @PostMapping
    public ApiResponse<MedicalRecordDto> postRecord(@RequestBody MedicalRecordCreateDto request) {
        MedicalRecordDto response = outpatientCareService.createRecord(request);
        return ApiResponse.success(response);
    }

    // 진료기록 수정 PUT /api/outpatient/records/{recordId}
    @PutMapping("/{recordId}")
    public ApiResponse<MedicalRecordDto> updateRecord(
            @PathVariable String recordId,
            @RequestBody MedicalRecordCreateDto request) { // 기존 MedicalRecordCreateDto 재사용!

        MedicalRecordDto response = outpatientCareService.updateRecord(recordId, request);
        return ApiResponse.success(response);
    }

    // 진료기록 비활성화 PATCH /api/outpatient/records/{recordId}/deactivate
    @PatchMapping("/{recordId}/deactivate")
    public ApiResponse<String> deactivateMedicalRecord(
            @PathVariable("recordId") String recordId,
            @RequestParam("userId") String userId) {

        // 주입받은 서비스 변수명(outpatientCareService)과 인터페이스 메서드명(deactivateRecord)에 맞춤
        outpatientCareService.deactivateRecord(recordId, userId);

        return ApiResponse.success("진료기록이 성공적으로 비활성화되었습니다.");
    }

    // 진료기록 첨부파일

}