package kr.co.seoulit.his.outpatientservice.common.client.lab;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LabOrderKafkaDispatcherTest {

    private static final String TOPIC = "opd.lab-order.requested.v1";

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private LabOrderKafkaDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new LabOrderKafkaDispatcher(kafkaTemplate, objectMapper, TOPIC);
    }

    private LabOrderApiDto.LabOrderCreateRequestDto sampleRequest() {
        return new LabOrderApiDto.LabOrderCreateRequestDto(
                "RX-1", "ENC-1", "PAT-1", "DOC-1", "OPD", "N", null, null,
                List.of(new LabOrderApiDto.LabOrderItemRequestDto("CBC", "일반혈액검사"))
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void 브로커_발행에_성공하면_PENDING을_반환하고_prescriptionId를_key로_보낸다() throws Exception {
        SendResult<String, String> sendResult = mock(SendResult.class);
        when(kafkaTemplate.send(eq(TOPIC), eq("RX-1"), anyString()))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        LabOrderApiDto.DispatchOutcome outcome = dispatcher.dispatch(sampleRequest());

        assertThat(outcome.status()).isEqualTo("PENDING");
        assertThat(outcome.labOrderId()).isNull();

        verify(kafkaTemplate).send(eq(TOPIC), eq("RX-1"), anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void 발행한_이벤트_data에_채널구분_응급여부_입원ID가_포함된다() throws Exception {
        SendResult<String, String> sendResult = mock(SendResult.class);
        when(kafkaTemplate.send(eq(TOPIC), eq("RX-1"), anyString()))
                .thenReturn(CompletableFuture.completedFuture(sendResult));
        LabOrderApiDto.LabOrderCreateRequestDto request = new LabOrderApiDto.LabOrderCreateRequestDto(
                "RX-1", "ENC-1", "PAT-1", "DOC-1", "IP", "Y", "ADM-1", null,
                List.of(new LabOrderApiDto.LabOrderItemRequestDto("01", "Blood Glucose Test")));

        dispatcher.dispatch(request);

        org.mockito.ArgumentCaptor<String> payload = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(TOPIC), eq("RX-1"), payload.capture());
        var data = objectMapper.readTree(payload.getValue()).get("data");
        assertThat(data.get("encounterType").asText()).isEqualTo("IP");
        assertThat(data.get("urgencyYn").asText()).isEqualTo("Y");
        assertThat(data.get("admissionId").asText()).isEqualTo("ADM-1");
        assertThat(data.get("encounterId").asText()).isEqualTo("ENC-1"); // 기존 필드는 그대로
    }

    @Test
    @SuppressWarnings("unchecked")
    void 발행한_이벤트_data에_접수ID가_포함된다() throws Exception {
        SendResult<String, String> sendResult = mock(SendResult.class);
        when(kafkaTemplate.send(eq(TOPIC), eq("RX-2"), anyString()))
                .thenReturn(CompletableFuture.completedFuture(sendResult));
        LabOrderApiDto.LabOrderCreateRequestDto request = new LabOrderApiDto.LabOrderCreateRequestDto(
                "RX-2", null, "PAT-1", "DOC-1", "ER", "Y", null, "RCP-1",
                List.of(new LabOrderApiDto.LabOrderItemRequestDto("LAB001", "CBC")));

        dispatcher.dispatch(request);

        org.mockito.ArgumentCaptor<String> payload = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(TOPIC), eq("RX-2"), payload.capture());
        var data = objectMapper.readTree(payload.getValue()).get("data");
        assertThat(data.get("encounterType").asText()).isEqualTo("ER");
        assertThat(data.get("receptionId").asText()).isEqualTo("RCP-1");
        assertThat(data.get("admissionId").isNull()).isTrue();
    }

    @Test
    void 발행_자체가_실패하면_FAILED를_반환한다() {
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker down"));
        when(kafkaTemplate.send(eq(TOPIC), eq("RX-1"), anyString())).thenReturn(failed);

        LabOrderApiDto.DispatchOutcome outcome = dispatcher.dispatch(sampleRequest());

        assertThat(outcome.status()).isEqualTo("FAILED");
    }
}
