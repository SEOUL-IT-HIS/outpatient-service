package kr.co.seoulit.his.outpatientservice.common.client.pharmacy;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class PharmacyPublisher {

    private static final long PUBLISH_ACK_TIMEOUT_SECONDS = 3;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String requestedTopic;
    private final String cancelledTopic;

    public PharmacyPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${app.kafka.topics.pharmacy-order-requested}") String requestedTopic,
            @Value("${app.kafka.topics.pharmacy-order-cancelled}") String cancelledTopic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.requestedTopic = requestedTopic;
        this.cancelledTopic = cancelledTopic;
    }

    // 처방(약품 항목 포함)을 카프카 이벤트로 발행. 브로커 적재까지 성공하면 true.
    public boolean publish(PharmacyEventDto.PharmacyOrderData data) {
        PharmacyEventDto.PharmacyOrderRequestedEvent event = new PharmacyEventDto.PharmacyOrderRequestedEvent(
                UUID.randomUUID().toString(),
                PharmacyEventDto.EVENT_TYPE_REQUESTED,
                PharmacyEventDto.SCHEMA_VERSION,
                OffsetDateTime.now(),
                PharmacyEventDto.SOURCE_OPD,
                data
        );
        return send(requestedTopic, data.prescriptionId(), event, "처방 요청");
    }

    // 처방 비활성화로 이미 약제에 보낸 처방을 철회한다. 브로커 적재까지만 확인하고 약제의 회신은 받지 않는다.
    // 같은 key(prescriptionId)로 발행해야 요청 이벤트와 같은 파티션이라 순서가 유지된다.
    public boolean publishCancel(PharmacyEventDto.PharmacyOrderCancelledData data) {
        PharmacyEventDto.PharmacyOrderCancelledEvent event = new PharmacyEventDto.PharmacyOrderCancelledEvent(
                UUID.randomUUID().toString(),
                PharmacyEventDto.EVENT_TYPE_CANCELLED,
                PharmacyEventDto.SCHEMA_VERSION,
                OffsetDateTime.now(),
                PharmacyEventDto.SOURCE_OPD,
                data
        );
        return send(cancelledTopic, data.prescriptionId(), event, "처방 취소");
    }

    private boolean send(String topic, String prescriptionId, Object event, String label) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(topic, prescriptionId, payload)
                    .get(PUBLISH_ACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            log.error("[약제 Kafka] {} 이벤트 발행 실패 prescriptionId={}, message={}",
                    label, prescriptionId, e.getMessage());
            return false;
        }
    }
}
