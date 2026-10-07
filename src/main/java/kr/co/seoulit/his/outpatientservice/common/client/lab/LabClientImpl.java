package kr.co.seoulit.his.outpatientservice.common.client.lab;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.seoulit.his.outpatientservice.common.exception.BusinessException;
import kr.co.seoulit.his.outpatientservice.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.services.lab.stub-enabled", havingValue = "false", matchIfMissing = true)
public class LabClientImpl implements LabClient {

    private final RestClient labRestClient;
    private final ObjectMapper objectMapper;

    public LabClientImpl(
            @Qualifier("labRestClient") RestClient labRestClient,
            ObjectMapper objectMapper
    ) {
        this.labRestClient = labRestClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public LabOrderApiDto.LabOrderResult sendOrder(LabOrderApiDto.LabOrderCreateRequestDto request) {
        try {
            JsonNode body = labRestClient.post()
                    .uri("/api/lab-imaging/lab-orders/intake")
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode data = (body != null && body.has("data")) ? body.get("data") : body;
            return objectMapper.convertValue(data, LabOrderApiDto.LabOrderResult.class);
        } catch (RestClientException ex) {
            log.warn("[LAB] sendOrder failed prescriptionId={}, message={}", request.prescriptionId(), ex.getMessage());
            // 검사실 전송에 실패했습니다.
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "Failed to send the order to the lab.");
        }
    }

    // 응답 code: LAB118(전체 취소)/LAB119(일부)/LAB120(전부 거절)은 HTTP 200, 오더 미접수는 HTTP 404 + LAB117.
    // 404는 업무상 "처리 대상 없음"이라 예외로 올리지 않고 결과(LAB117)로 돌려준다 — 그 외 통신 오류만 예외.
    @Override
    public LabOrderApiDto.LabOrderCancelResult cancelOrder(LabOrderApiDto.LabOrderCancelRequestDto request) {
        try {
            JsonNode body = labRestClient.post()
                    .uri("/api/lab-imaging/lab-orders/cancel")
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);
            return toCancelResult(body);
        } catch (HttpClientErrorException.NotFound ex) {
            LabOrderApiDto.LabOrderCancelResult result = toCancelResult(ex.getResponseBodyAs(JsonNode.class));
            if (result != null && result.code() != null) {
                return result;
            }
            log.warn("[LAB] cancelOrder 404 응답 본문 해석 실패 prescriptionId={}", request.prescriptionId());
            // 검사오더 취소에 실패했습니다.
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "Failed to cancel the lab order.");
        } catch (RestClientException ex) {
            log.warn("[LAB] cancelOrder failed prescriptionId={}, message={}", request.prescriptionId(), ex.getMessage());
            // 검사오더 취소에 실패했습니다.
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "Failed to cancel the lab order.");
        }
    }

    // sendOrder와 같이 data 래핑이 있으면 벗기고, 없으면 최상위를 그대로 읽는다
    private LabOrderApiDto.LabOrderCancelResult toCancelResult(JsonNode body) {
        if (body == null || body.isNull()) {
            return null;
        }
        JsonNode data = body.has("data") && body.get("data").isObject() ? body.get("data") : body;
        return objectMapper.convertValue(data, LabOrderApiDto.LabOrderCancelResult.class);
    }

    // 검사서비스 확인 완료(LAB104) — name 생략 시 전체 목록, 코드/이름 부분일치(대소문자 무관) 검색
    @Override
    public List<LabOrderApiDto.LabItem> searchLabItem(String name) {
        try {
            LabOrderApiDto.LabItemSearchResponse response = labRestClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/api/lab-imaging/lab-items");
                        if (name != null && !name.isBlank()) {
                            uriBuilder.queryParam("name", name);
                        }
                        return uriBuilder.build();
                    })
                    .retrieve()
                    .body(LabOrderApiDto.LabItemSearchResponse.class);

            return response == null ? List.of() : response.data();
        } catch (RestClientException ex) {
            log.warn("[LAB] searchLabItem failed name={}, message={}", name, ex.getMessage());
            // 검사항목 검색에 실패했습니다.
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "Failed to search lab items.");
        }
    }
}