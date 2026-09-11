package kr.co.seoulit.his.outpatientservice.common.client.admin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.seoulit.his.outpatientservice.common.exception.BusinessException;
import kr.co.seoulit.his.outpatientservice.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * Admin(ADM) Consumer — 공통코드.
 * 카탈로그: GET /api/commonCodeGroup/list, GET /api/commonCodeItem/list?groupId=
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.services.admin.stub-enabled", havingValue = "false", matchIfMissing = true)
public class CommonCodeClientImpl implements CommonCodeClient {

    private final RestClient adminRestClient;
    private final ObjectMapper objectMapper;

    public CommonCodeClientImpl(
            @Qualifier("adminRestClient") RestClient adminRestClient,
            ObjectMapper objectMapper
    ) {
        this.adminRestClient = adminRestClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<CommonCodeApiDto.CommonCodeGroup> getGroups() {
        try {
            JsonNode body = adminRestClient.get()
                    .uri("/api/commonCodeGroup/list")
                    .retrieve()
                    .body(JsonNode.class);
            return extractList(body, new TypeReference<>() {
            });
        } catch (RestClientException ex) {
            log.warn("[ADM] commonCodeGroup/list failed message={}", ex.getMessage());
            // 공통코드 그룹 조회에 실패했습니다.
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "Failed to fetch common code groups.");
        }
    }

    @Override
    public List<CommonCodeApiDto.CommonCodeItem> getItems(String groupId) {
        try {
            JsonNode body = adminRestClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/api/commonCodeItem/list")
                            .queryParam("groupId", groupId)
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
            return extractList(body, new TypeReference<>() {
            });
        } catch (RestClientException ex) {
            log.warn("[ADM] commonCodeItem/list failed groupId={}, message={}", groupId, ex.getMessage());
            // 공통코드 항목 조회에 실패했습니다.
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "Failed to fetch common code items.");
        }
    }

    private <T> List<T> extractList(JsonNode body, TypeReference<List<T>> typeReference) {
        if (body == null) {
            return List.of();
        }
        JsonNode data = body.has("data") ? body.get("data") : body;
        return objectMapper.convertValue(data, typeReference);
    }
}
