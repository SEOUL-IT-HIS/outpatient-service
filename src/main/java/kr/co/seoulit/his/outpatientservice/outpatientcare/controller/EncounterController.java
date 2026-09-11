package kr.co.seoulit.his.outpatientservice.outpatientcare.controller;

import kr.co.seoulit.his.outpatientservice.common.ApiResponse;
import kr.co.seoulit.his.outpatientservice.outpatientcare.dto.EncounterDto;
import kr.co.seoulit.his.outpatientservice.outpatientcare.service.OutpatientCareService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
