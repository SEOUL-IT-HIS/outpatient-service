package kr.co.seoulit.his.outpatientservice.common.client.patient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import kr.co.seoulit.his.outpatientservice.common.exception.BusinessException;
import kr.co.seoulit.his.outpatientservice.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PatientClientImplTest {

    private static final String BATCH_URL = "http://pat/api/patient/batch";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockRestServiceServer server;
    private PatientClientImpl client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://pat");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new PatientClientImpl(builder.build(), objectMapper);
    }

    private static List<String> ids(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(i -> "P" + i).toList();
    }

    // 요청 바디의 patientIds를 그대로 환자 목록으로 돌려주는 PAT 흉내 — 호출별 요청 건수를 sizes에 기록한다
    private void expectBatchCalls(int callCount, List<Integer> sizes) {
        server.expect(times(callCount), requestTo(BATCH_URL))
                .andRespond(request -> {
                    String requestBody = ((MockClientHttpRequest) request).getBodyAsString();
                    JsonNode requested = objectMapper.readTree(requestBody).get("patientIds");
                    sizes.add(requested.size());

                    ArrayNode data = objectMapper.createArrayNode();
                    requested.forEach(id -> {
                        ObjectNode patient = objectMapper.createObjectNode();
                        patient.put("patientId", id.asText());
                        patient.put("patientName", "이름" + id.asText());
                        data.add(patient);
                    });
                    ObjectNode responseBody = objectMapper.createObjectNode();
                    responseBody.put("code", "SUCCESS");
                    responseBody.set("data", data);
                    return withSuccess(objectMapper.writeValueAsString(responseBody), MediaType.APPLICATION_JSON)
                            .createResponse(request);
                });
    }

    @Test
    void 환자ID가_100건_이하면_batch를_1번만_호출한다() {
        List<Integer> sizes = new ArrayList<>();
        expectBatchCalls(1, sizes);

        Map<String, PatientApiDto.PatientSummary> result = client.getPatients(ids(100));

        assertThat(sizes).containsExactly(100);
        assertThat(result).hasSize(100);
        server.verify();
    }

    @Test
    void 환자ID가_100건을_넘으면_100건씩_나눠서_호출하고_결과를_합친다() {
        List<Integer> sizes = new ArrayList<>();
        expectBatchCalls(3, sizes);

        Map<String, PatientApiDto.PatientSummary> result = client.getPatients(ids(250));

        assertThat(sizes).containsExactly(100, 100, 50);
        assertThat(result).hasSize(250);
        assertThat(result.get("P250").patientName()).isEqualTo("이름P250");
        server.verify();
    }

    @Test
    void 중복된_환자ID는_제거하고_조회한다() {
        List<Integer> sizes = new ArrayList<>();
        expectBatchCalls(1, sizes);

        Map<String, PatientApiDto.PatientSummary> result = client.getPatients(List.of("P1", "P1", "P2"));

        assertThat(sizes).containsExactly(2);
        assertThat(result).containsOnlyKeys("P1", "P2");
        server.verify();
    }

    @Test
    void 일부_구간_호출이_실패하면_EXTERNAL_API_ERROR를_던진다() {
        server.expect(requestTo(BATCH_URL)).andRespond(withServerError());

        assertThatThrownBy(() -> client.getPatients(ids(150)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.EXTERNAL_API_ERROR);
    }

    @Test
    void 환자ID가_비어있으면_호출하지_않는다() {
        assertThat(client.getPatients(List.of())).isEmpty();
        assertThat(client.getPatients(null)).isEmpty();
        server.verify(); // 기대 호출이 없으므로 요청이 나갔다면 실패한다
    }
}
