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

    public PharmacyPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${app.kafka.topics.pharmacy-order-requested}") String requestedTopic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.requestedTopic = requestedTopic;
    }

    // 처방(약품 항목 포함)을 카프카 이벤트로 발행. 브로커 적재까지 성공하면 true.
    public boolean publish(PharmacyEventDto.PharmacyOrderData data) {
        String eventId = UUID.randomUUID().toString();
        PharmacyEventDto.PharmacyOrderRequestedEvent event = new PharmacyEventDto.PharmacyOrderRequestedEvent(
                eventId,
                PharmacyEventDto.EVENT_TYPE_REQUESTED,
                PharmacyEventDto.SCHEMA_VERSION,
                OffsetDateTime.now(),
                PharmacyEventDto.SOURCE_OPD,
                data
        );

        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(requestedTopic, data.prescriptionId(), payload)
                    .get(PUBLISH_ACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            log.error("[약제 Kafka] 처방 요청 이벤트 발행 실패 prescriptionId={}, message={}",
                    data.prescriptionId(), e.getMessage());
            return false;
        }
    }
}