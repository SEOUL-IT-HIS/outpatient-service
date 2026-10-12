package kr.co.seoulit.his.outpatientservice.common.client.patient;

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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Patient(PAT) Consumer.
 * - GET /api/patient/{patientId}
 * - POST /api/patient/batch  (N+1 방지, 목록 화면용)
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.services.patient.stub-enabled", havingValue = "false", matchIfMissing = true)
public class PatientClientImpl implements PatientClient {

    // PAT POST /api/patient/batch 1회 최대 건수 (통합 카탈로그 기준)
    private static final int BATCH_MAX_SIZE = 100;

    private final RestClient patientRestClient;
    private final ObjectMapper objectMapper;

    public PatientClientImpl(
            @Qualifier("patientRestClient") RestClient patientRestClient,
            ObjectMapper objectMapper
    ) {
        this.patientRestClient = patientRestClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<PatientApiDto.PatientSummary> getPatient(String patientId) {
        if (patientId == null) {
            return Optional.empty();
        }
        try {
            JsonNode body = patientRestClient.get()
                    .uri("/api/patient/{patientId}", patientId)
                    .retrieve()
                    .body(JsonNode.class);
            return Optional.ofNullable(extractOne(body));
        } catch (RestClientException ex) {
            log.warn("[PAT] getPatient failed patientId={}, message={}", patientId, ex.getMessage());
            // 환자 서비스 단건 조회에 실패했습니다.
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "Failed to fetch patient information.");
        }
    }

    @Override
    public Map<String, PatientApiDto.PatientSummary> getPatients(Collection<String> patientIds) {
        if (patientIds == null || patientIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> distinctIds = patientIds.stream().distinct().toList();
        Map<String, PatientApiDto.PatientSummary> result = new HashMap<>();
        // PAT batch는 1회 최대 100건 — 초과분은 나눠서 호출한다
        for (int from = 0; from < distinctIds.size(); from += BATCH_MAX_SIZE) {
            List<String> chunk = distinctIds.subList(from, Math.min(from + BATCH_MAX_SIZE, distinctIds.size()));
            fetchBatch(chunk).forEach(p -> result.putIfAbsent(p.patientId(), p));
        }
        return result;
    }

    private List<PatientApiDto.PatientSummary> fetchBatch(List<String> ids) {
        try {
            JsonNode body = patientRestClient.post()
                    .uri("/api/patient/batch")
                    .body(new PatientApiDto.BatchQueryRequest(List.copyOf(ids)))
                    .retrieve()
                    .body(JsonNode.class);

            return extractList(body).stream()
                    .filter(p -> p.patientId() != null)
                    .toList();
        } catch (RestClientException ex) {
            log.warn("[PAT] batch-query failed ids={}, message={}", ids, ex.getMessage());
            // 환자 서비스 일괄 조회에 실패했습니다.
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "Failed to batch-fetch patient information.");
        }
    }

    private PatientApiDto.PatientSummary extractOne(JsonNode body) {
        if (body == null) {
            return null;
        }
        JsonNode data = body.has("data") ? body.get("data") : body;
        return objectMapper.convertValue(data, PatientApiDto.PatientSummary.class);
    }

    private List<PatientApiDto.PatientSummary> extractList(JsonNode body) {
        if (body == null) {
            return List.of();
        }
        JsonNode data = body.has("data") ? body.get("data") : body;
        return objectMapper.convertValue(data, new TypeReference<>() {
        });
    }
}
