package kr.co.seoulit.his.outpatientservice.admission.controller;

import kr.co.seoulit.his.outpatientservice.admission.dto.AdmissionRequestCreateDto;
import kr.co.seoulit.his.outpatientservice.admission.dto.AdmissionRequestDto;
import kr.co.seoulit.his.outpatientservice.admission.dto.AdmissionRequestPageDto;
import kr.co.seoulit.his.outpatientservice.admission.service.AdmissionRequestService;
import kr.co.seoulit.his.outpatientservice.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/outpatient")
@RequiredArgsConstructor
public class AdmissionRequestController {

    private final AdmissionRequestService admissionRequestService;

    // 입원 요청 등록 POST /api/outpatient/encounters/{encounterId}/admission-requests
    // 요청 저장(커밋) 이후 병동으로 발행한다. 발행이 실패해도 요청은 남고 sendStatus=FAILED로 알려주며, 이후 dispatch로 재전송한다.
    @PostMapping("/encounters/{encounterId}/admission-requests")
    public ApiResponse<AdmissionRequestDto> createAdmissionRequest(
            @PathVariable String encounterId,
            @RequestBody(required = false) AdmissionRequestCreateDto request) {
        AdmissionRequestDto response = admissionRequestService.createAdmissionRequest(encounterId, request);

        try {
            response = admissionRequestService.dispatchAdmissionRequest(response.getAdmissionRequestId());
        } catch (Exception e) {
            // 등록은 이미 커밋됐다. 전송 상태는 PENDING 그대로 두고 재전송하게 한다.
            log.error("[입원요청 자동 전송 실패] admissionRequestId={}, message={}", response.getAdmissionRequestId(), e.getMessage());
        }
        return ApiResponse.success(response);
    }

    // 진료별 입원 요청 목록 조회 GET /api/outpatient/encounters/{encounterId}/admission-requests
    @GetMapping("/encounters/{encounterId}/admission-requests")
    public ApiResponse<List<AdmissionRequestDto>> getAdmissionRequests(@PathVariable String encounterId) {
        return ApiResponse.success(admissionRequestService.getAdmissionRequests(encounterId));
    }

    // 입원 요청 목록 조회 GET /api/outpatient/admission-requests?requestStatus={01|02|03}&page={page}&size={size}
    // 모든 의사의 요청을 요청일시 최신순으로 페이지 단위로 내려준다. requestStatus를 생략하면 전체, page는 0부터, size는 최대 100.
    @GetMapping("/admission-requests")
    public ApiResponse<AdmissionRequestPageDto> getAdmissionRequestPage(
            @RequestParam(name = "requestStatus", required = false) String requestStatus,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ApiResponse.success(admissionRequestService.getAdmissionRequestPage(requestStatus, page, size));
    }

    // 입원 요청 재전송 POST /api/outpatient/admission-requests/{admissionRequestId}/dispatch
    // 전송상태가 FAILED/PENDING인 요청만 다시 발행한다(이미 SENT이거나 병동이 회신한 요청은 그대로 돌려준다).
    @PostMapping("/admission-requests/{admissionRequestId}/dispatch")
    public ApiResponse<AdmissionRequestDto> dispatchAdmissionRequest(@PathVariable String admissionRequestId) {
        return ApiResponse.success(admissionRequestService.dispatchAdmissionRequest(admissionRequestId));
    }
}
