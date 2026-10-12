package kr.co.seoulit.his.outpatientservice.common.client.admission;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 입원 요청을 opd.admission.requested.v1 토픽으로 발행한다.
 * 발행 성공 = Kafka 브로커 적재 확인까지만이다. 병상 배정/거부 결과는 병동이 별도 회신 이벤트로 알려준다.
 */
@Slf4j
@Component
public class AdmissionRequestPublisher {

    private static final long PUBLISH_ACK_TIMEOUT_SECONDS = 3;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String requestedTopic;
    private final boolean messagingEnabled;

    public AdmissionRequestPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${app.kafka.topics.admission-requested}") String requestedTopic,
            @Value("${app.services.admission.messaging-enabled:false}") boolean messagingEnabled
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.requestedTopic = requestedTopic;
        this.messagingEnabled = messagingEnabled;
    }

    // 입원 요청을 병동으로 발행한다. 브로커 적재까지 성공하면 true.
    // messaging-enabled=false면 브로커에 접속하지 않고 false를 돌려준다(Kafka 미기동 환경에서 요청이 매달리지 않게).
    public boolean publish(AdmissionEventDto.RequestedData data) {
        if (!messagingEnabled) {
            log.warn("[병동 Kafka] app.services.admission.messaging-enabled=false라 입원 요청을 발행하지 않음 requestId={}",
                    data.requestId());
            return false;
        }

        String eventId = UUID.randomUUID().toString();
        AdmissionEventDto.AdmissionRequestedEvent event = new AdmissionEventDto.AdmissionRequestedEvent(
                eventId,
                AdmissionEventDto.EVENT_TYPE_REQUESTED,
                AdmissionEventDto.SCHEMA_VERSION,
                LocalDateTime.now(KST).truncatedTo(ChronoUnit.SECONDS),
                AdmissionEventDto.SOURCE_OPD,
                data
        );

        try {
            String payload = objectMapper.writeValueAsString(event);
            // 병동이 회신 key로 requestId를 쓰므로 요청도 같은 key로 보낸다
            kafkaTemplate.send(requestedTopic, data.requestId(), payload)
                    .get(PUBLISH_ACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            log.error("[병동 Kafka] 입원 요청 이벤트 발행 실패 requestId={}, message={}", data.requestId(), e.getMessage());
            return false;
        }
    }
}
