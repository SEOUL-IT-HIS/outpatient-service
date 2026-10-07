package kr.co.seoulit.his.outpatientservice.common.client.pharmacy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PharmacyPublisherTest {

    private static final String REQUESTED_TOPIC = "opd.pharmacy-order.requested.v1";
    private static final String CANCELLED_TOPIC = "opd.pharmacy-order.cancelled.v1";

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private PharmacyPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new PharmacyPublisher(kafkaTemplate, objectMapper, REQUESTED_TOPIC, CANCELLED_TOPIC);
    }

    private PharmacyEventDto.PharmacyOrderCancelledData sampleCancel() {
        return new PharmacyEventDto.PharmacyOrderCancelledData("RX-1", "오처방", "DOC-1",
                List.of(new PharmacyEventDto.PharmacyCancelledItem("195700020", "타이레놀정500mg")));
    }

    @SuppressWarnings("unchecked")
    private void stubSendSuccess(String topic) {
        SendResult<String, String> sendResult = mock(SendResult.class);
        when(kafkaTemplate.send(eq(topic), eq("RX-1"), anyString()))
                .thenReturn(CompletableFuture.completedFuture(sendResult));
    }

    @Test
    void 취소_이벤트를_취소토픽에_prescriptionId를_key로_발행한다() throws Exception {
        stubSendSuccess(CANCELLED_TOPIC);

        boolean delivered = publisher.publishCancel(sampleCancel());

        assertThat(delivered).isTrue();
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(CANCELLED_TOPIC), eq("RX-1"), payload.capture());
        var json = objectMapper.readTree(payload.getValue());
        assertThat(json.get("eventType").asText()).isEqualTo("PharmacyOrderCancelled");
        assertThat(json.get("source").asText()).isEqualTo("OPD");
        assertThat(json.get("data").get("prescriptionId").asText()).isEqualTo("RX-1");
        assertThat(json.get("data").get("cancelReason").asText()).isEqualTo("오처방");
        assertThat(json.get("data").get("cancelledBy").asText()).isEqualTo("DOC-1");
        assertThat(json.get("data").get("cancelledItems").get(0).get("ediCode").asText()).isEqualTo("195700020");
    }

    @Test
    void 취소_발행이_실패하면_false를_반환한다() {
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker down"));
        when(kafkaTemplate.send(eq(CANCELLED_TOPIC), eq("RX-1"), anyString())).thenReturn(failed);

        assertThat(publisher.publishCancel(sampleCancel())).isFalse();
    }

    @Test
    void 요청_이벤트는_요청토픽에_진료채널_우선순위코드_구두여부를_담아_발행한다() throws Exception {
        stubSendSuccess(REQUESTED_TOPIC);
        PharmacyEventDto.PharmacyOrderData data = new PharmacyEventDto.PharmacyOrderData(
                "RX-1", "P0001", "DR0001", "ER-01", OffsetDateTime.now(),
                "ER", "01", "Y",
                List.of(new PharmacyEventDto.PharmacyOrderItem("195700020", "타이레놀정500mg", 1.0, "01", "1", "1", null)));

        boolean delivered = publisher.publish(data);

        assertThat(delivered).isTrue();
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(REQUESTED_TOPIC), eq("RX-1"), payload.capture());
        var json = objectMapper.readTree(payload.getValue());
        assertThat(json.get("eventType").asText()).isEqualTo("PharmacyOrderRequested");
        var body = json.get("data");
        assertThat(body.get("encounterType").asText()).isEqualTo("ER");
        assertThat(body.get("priorityCode").asText()).isEqualTo("01");
        assertThat(body.get("verbalYn").asText()).isEqualTo("Y");
        assertThat(body.get("items").get(0).get("ediCode").asText()).isEqualTo("195700020"); // 기존 필드는 그대로
    }

    @Test
    void 요청_발행이_실패하면_false를_반환한다() {
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker down"));
        when(kafkaTemplate.send(eq(REQUESTED_TOPIC), eq("RX-1"), anyString())).thenReturn(failed);
        PharmacyEventDto.PharmacyOrderData data = new PharmacyEventDto.PharmacyOrderData(
                "RX-1", "P0001", "DR0001", "ER-01", OffsetDateTime.now(), null, null, null, List.of());

        assertThat(publisher.publish(data)).isFalse();
    }
}
