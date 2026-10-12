package kr.co.seoulit.his.outpatientservice.common.client.admission;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdmissionRequestPublisherTest {

    private static final String TOPIC = "opd.admission.requested.v1";

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    // Spring Boot ObjectMapper 설정(날짜를 문자열로 직렬화)에 기대지 않고, 기본 설정에서도 형식이 지켜지는지 본다
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private AdmissionRequestPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new AdmissionRequestPublisher(kafkaTemplate, objectMapper, TOPIC, true);
    }

    private AdmissionEventDto.RequestedData sampleData() {
        return new AdmissionEventDto.RequestedData("REQ-1", "PAT-1", "IM", "ENC-1", "DOC-1", "입원 치료 필요");
    }

    @Test
    @SuppressWarnings("unchecked")
    void 입원요청을_요청토픽에_requestId를_key로_발행한다() throws Exception {
        SendResult<String, String> sendResult = mock(SendResult.class);
        when(kafkaTemplate.send(eq(TOPIC), eq("REQ-1"), anyString()))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        boolean sent = publisher.publish(sampleData());

        assertThat(sent).isTrue();
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(TOPIC), eq("REQ-1"), payload.capture());

        JsonNode json = objectMapper.readTree(payload.getValue());
        assertThat(json.get("eventType").asText()).isEqualTo("AdmissionRequested");
        assertThat(json.get("version").asText()).isEqualTo("1.0");
        assertThat(json.get("source").asText()).isEqualTo("01");
        assertThat(json.get("eventId").asText()).isNotBlank();
        assertThat(json.get("data").get("requestId").asText()).isEqualTo("REQ-1");
        assertThat(json.get("data").get("patientId").asText()).isEqualTo("PAT-1");
        assertThat(json.get("data").get("targetDeptCode").asText()).isEqualTo("IM");
        assertThat(json.get("data").get("encounterId").asText()).isEqualTo("ENC-1");
        assertThat(json.get("data").get("requestedBy").asText()).isEqualTo("DOC-1");
        assertThat(json.get("data").get("note").asText()).isEqualTo("입원 치료 필요");
    }

    @Test
    @SuppressWarnings("unchecked")
    void occurredAt은_타임존_없는_초_단위_ISO로_보낸다() throws Exception {
        SendResult<String, String> sendResult = mock(SendResult.class);
        when(kafkaTemplate.send(eq(TOPIC), eq("REQ-1"), anyString()))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        publisher.publish(sampleData());

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(TOPIC), eq("REQ-1"), payload.capture());
        String occurredAt = objectMapper.readTree(payload.getValue()).get("occurredAt").asText();

        // 병동 요구 형식: 2026-10-12T10:29:00 (오프셋/소수초 없음)
        assertThat(occurredAt).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}");
    }

    @Test
    void 격리여부_등_합의하지_않은_필드는_보내지_않는다() throws Exception {
        @SuppressWarnings("unchecked")
        SendResult<String, String> sendResult = mock(SendResult.class);
        when(kafkaTemplate.send(eq(TOPIC), eq("REQ-1"), anyString()))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        publisher.publish(sampleData());

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(TOPIC), eq("REQ-1"), payload.capture());
        JsonNode data = objectMapper.readTree(payload.getValue()).get("data");

        assertThat(data.has("isolationYn")).isFalse();
        assertThat(data.has("preferredAdmitDate")).isFalse();
        assertThat(data.has("wardPrefer")).isFalse();
    }

    @Test
    void 브로커_발행이_실패하면_false를_반환한다() {
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker down"));
        when(kafkaTemplate.send(eq(TOPIC), eq("REQ-1"), anyString())).thenReturn(failed);

        assertThat(publisher.publish(sampleData())).isFalse();
    }

    @Test
    void messaging이_꺼져_있으면_브로커에_접속하지_않고_false를_반환한다() {
        AdmissionRequestPublisher disabled = new AdmissionRequestPublisher(kafkaTemplate, objectMapper, TOPIC, false);

        assertThat(disabled.publish(sampleData())).isFalse();
        verifyNoInteractions(kafkaTemplate);
    }
}
