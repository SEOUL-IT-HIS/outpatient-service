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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Patient(PAT) Consumer.
 * - GET /api/v1/patients/{patientId}
 * - POST /api/v1/patients/batch-query  (N+1 방지, 목록 화면용)
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.services.patient.stub-enabled", havingValue = "false", matchIfMissing = true)
public class PatientClientImpl implements PatientClient {

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
                    .uri("/api/v1/patients/{patientId}", patientId)
                    .retrieve()
                    .body(JsonNode.class);
            return Optional.ofNullable(extractOne(body));
        } catch (RestClientException ex) {
            log.warn("[PAT] getPatient failed patientId={}, message={}", patientId, ex.getMessage());
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "환자 서비스 단건 조회에 실패했습니다.");
        }
    }

    @Override
    public Map<String, PatientApiDto.PatientSummary> getPatients(Collection<String> patientIds) {
        if (patientIds == null || patientIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> distinctIds = patientIds.stream().distinct().toList();
        try {
            JsonNode body = patientRestClient.post()
                    .uri("/api/v1/patients/batch-query")
                    .body(new PatientApiDto.BatchQueryRequest(distinctIds))
                    .retrieve()
                    .body(JsonNode.class);

            List<PatientApiDto.PatientSummary> patients = extractList(body);
            return patients.stream()
                    .filter(p -> p.patientId() != null)
                    .collect(Collectors.toMap(PatientApiDto.PatientSummary::patientId, Function.identity(), (a, b) -> a));
        } catch (RestClientException ex) {
            log.warn("[PAT] batch-query failed ids={}, message={}", distinctIds, ex.getMessage());
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "환자 서비스 일괄 조회에 실패했습니다.");
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
