package kr.co.seoulit.his.outpatientservice.common.client.lab;

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
}