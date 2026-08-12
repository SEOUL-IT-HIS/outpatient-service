package kr.co.seoulit.his.outpatientservice.outpatientcare.controller;

import jakarta.validation.Valid;
import kr.co.seoulit.his.outpatientservice.common.ApiResponse;
import kr.co.seoulit.his.outpatientservice.outpatientcare.dto.EncounterCreateDto;
import kr.co.seoulit.his.outpatientservice.outpatientcare.dto.EncounterDto;
import kr.co.seoulit.his.outpatientservice.outpatientcare.dto.EncounterSearchDto;
import kr.co.seoulit.his.outpatientservice.outpatientcare.service.OutpatientCareService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/outpatient/encounters")
@RequiredArgsConstructor
public class EncounterController {

    private final OutpatientCareService outpatientCareService;

    // --- [외래 환자/진료 목록 조회] GET /api/outpatient/encounters ---
    @GetMapping
    public ApiResponse<List<EncounterDto>> getEncounters(@ModelAttribute EncounterSearchDto request) {
        List<EncounterDto> response = outpatientCareService.getEncounters(request);
        return ApiResponse.success(response);
    }

    // --- [RCP 대기 환자 -> OPD 진료 배정 등록] POST /api/outpatient/encounters ---
    @PostMapping
    public ApiResponse<EncounterDto> createEncounter(@Valid @RequestBody EncounterCreateDto request) {
        EncounterDto response = outpatientCareService.createEncounter(request);
        return ApiResponse.success(response);
    }
}