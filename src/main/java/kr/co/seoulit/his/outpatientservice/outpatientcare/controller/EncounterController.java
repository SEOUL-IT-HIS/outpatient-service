package kr.co.seoulit.his.outpatientservice.outpatientcare.controller;

import kr.co.seoulit.his.outpatientservice.common.ApiResponse;
import kr.co.seoulit.his.outpatientservice.outpatientcare.dto.EncounterDto;
import kr.co.seoulit.his.outpatientservice.outpatientcare.dto.EncounterStatusDto;
import kr.co.seoulit.his.outpatientservice.outpatientcare.dto.VisitHistoryDto;
import kr.co.seoulit.his.outpatientservice.outpatientcare.service.OutpatientCareService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

//웹요청을 받아 json형태로 데이터를 반환하는컨트롤러임을 스프링에게 알려줌
@RestController
@RequestMapping("/api/outpatient/encounters")
//service를 가져와서 연결해주도록 만드는 롬복기능
@RequiredArgsConstructor
public class EncounterController {

    private final OutpatientCareService outpatientCareService;

    // 당일 외래 환자/진료 목록 조회 GET /api/outpatient/encounters
    @GetMapping
    public ApiResponse<List<EncounterDto>> getEncounters() {
        List<EncounterDto> response = outpatientCareService.getTodayEncounters();
        return ApiResponse.success(response);
    }

    // 환자 외래 진료 이력 조회 GET /api/outpatient/encounters/visit-history?patientId=&departmentCode=&withinDays=
    // 접수(RCP)가 초진/재진을 판정할 때 서버 간 호출한다. 조회만 하며 환자정보/진단내용은 응답에 없다.
    @GetMapping("/visit-history")
    public ApiResponse<VisitHistoryDto> getVisitHistory(
            @RequestParam(required = false) String patientId,
            @RequestParam(required = false) String departmentCode,
            @RequestParam(required = false) Integer withinDays
    ) {
        return ApiResponse.success(outpatientCareService.getVisitHistory(patientId, departmentCode, withinDays));
    }

    // 진료 시작 POST /api/outpatient/encounters/{encounterId}/start (대기중 -> 진료중)
    @PostMapping("/{encounterId}/start")
    public ApiResponse<EncounterDto> startConsultation(@PathVariable String encounterId) {
        return ApiResponse.success(outpatientCareService.startConsultation(encounterId));
    }

    // 진료 시작 취소 POST /api/outpatient/encounters/{encounterId}/cancel-start (진료중 -> 대기중)
    @PostMapping("/{encounterId}/cancel-start")
    public ApiResponse<EncounterDto> cancelStartConsultation(@PathVariable String encounterId) {
        return ApiResponse.success(outpatientCareService.cancelStartConsultation(encounterId));
    }

    // 접수 ID로 진료 상태 조회 GET /api/outpatient/encounters/by-reception/{receptionId}/status
    // 접수(RCP)가 접수 취소 직전에 진료 중인지(inProgress) 확인할 때 서버 간 호출한다. 외래에 없는 접수면 404.
    @GetMapping("/by-reception/{receptionId}/status")
    public ApiResponse<EncounterStatusDto> getEncounterStatusByReception(@PathVariable String receptionId) {
        return ApiResponse.success(outpatientCareService.getEncounterStatusByReception(receptionId));
    }
}
