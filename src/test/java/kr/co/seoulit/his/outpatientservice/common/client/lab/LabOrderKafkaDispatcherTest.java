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
                "RX-1", "ENC-1", "PAT-1", "DOC-1",
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
    void 발행_자체가_실패하면_FAILED를_반환한다() {
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker down"));
        when(kafkaTemplate.send(eq(TOPIC), eq("RX-1"), anyString())).thenReturn(failed);

        LabOrderApiDto.DispatchOutcome outcome = dispatcher.dispatch(sampleRequest());

        assertThat(outcome.status()).isEqualTo("FAILED");
    }
}
