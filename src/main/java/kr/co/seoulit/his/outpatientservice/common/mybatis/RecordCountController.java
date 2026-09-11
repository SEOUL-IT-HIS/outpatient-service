package kr.co.seoulit.his.outpatientservice.common.mybatis;

import kr.co.seoulit.his.outpatientservice.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/outpatient")
@RequiredArgsConstructor
public class RecordCountController {

    private final RecordCountService recordCountService;

    @GetMapping("/record-count/{encounterId}")
    public ApiResponse<Map<String, Object>> countMedicalRecords(@PathVariable String encounterId) {
        //서비스를 호출하여 프로시저 결과가 담긴 map을 받아옴
        Map<String, Object> result = recordCountService.countByEncounterId(encounterId);

        //클라이언트에게 반환할 json응답구조를 순서가 보장되는 LinkedHashMap으로 생성
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("encounterId", encounterId);
        //서비스 결과에서 out파라미터로 들어온 진료건수를 추출하여 넣음
        response.put("count", result.get("recordCount"));

        //응답객체(ApiResponse)에 담아 json으로 프론트에 전달
        return ApiResponse.success(response);
    }
}
