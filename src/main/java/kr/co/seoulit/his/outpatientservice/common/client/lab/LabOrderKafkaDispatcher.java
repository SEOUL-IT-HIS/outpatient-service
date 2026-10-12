package kr.co.seoulit.his.outpatientservice.common.client.lab;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 검사오더 요청을 opd.lab-order.requested.v1 토픽으로 발행한다.
 * 발행 성공 = Kafka 브로커까지 전달 확인(PENDING). 검사서비스의 실제 접수 결과는
 * LabOrderResultListener가 lab.lab-order.resulted.v1 결과 이벤트를 받아 별도로 반영한다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.services.lab.messaging-enabled", havingValue = "true")
public class LabOrderKafkaDispatcher implements LabOrderDispatcher {

    private static final long PUBLISH_ACK_TIMEOUT_SECONDS = 3;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String requestedTopic;
    private final String cancelledTopic;

    public LabOrderKafkaDispatcher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${app.kafka.topics.lab-order-requested}") String requestedTopic,
            @Value("${app.kafka.topics.lab-order-cancelled}") String cancelledTopic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.requestedTopic = requestedTopic;
        this.cancelledTopic = cancelledTopic;
    }

    //검사오더를 넘겨주면 이거를 카프카이벤트로 만들어 발송함
    @Override
    public LabOrderApiDto.DispatchOutcome dispatch(LabOrderApiDto.LabOrderCreateRequestDto request) {
        String eventId = UUID.randomUUID().toString();
        //처방에 포함된 검사 항목들을 카프카규격에 맞는 형태로 변환
        List<LabOrderEventDto.OrderItem> orderItems = request.orderItems().stream()
                .map(item -> new LabOrderEventDto.OrderItem(item.itemCode(), item.itemName()))
                .toList();

        LabOrderEventDto.LabOrderRequestedEvent event = new LabOrderEventDto.LabOrderRequestedEvent(
                eventId,
                LabOrderEventDto.EVENT_TYPE_REQUESTED,
                LabOrderEventDto.SCHEMA_VERSION,
                OffsetDateTime.now(),
                LabOrderEventDto.SOURCE_OPD,
                eventId,
                new LabOrderEventDto.RequestedData(
                        request.prescriptionId(),
                        request.encounterId(),
                        request.patientId(),
                        request.doctorId(),
                        request.encounterType(),
                        request.urgencyYn(),
                        request.admissionId(),
                        request.receptionId(),
                        orderItems
                )
        );

        try {
            String payload = objectMapper.writeValueAsString(event);
            // 브로커 적재까지만 확인한다 — 검사서비스 처리 속도/장애와는 분리된다.
            kafkaTemplate.send(requestedTopic, request.prescriptionId(), payload)
                    .get(PUBLISH_ACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return new LabOrderApiDto.DispatchOutcome("PENDING", null, null);
        } catch (Exception e) {
            log.error("[LAB Kafka] 검사오더 요청 이벤트 발행 실패 prescriptionId={}, message={}",
                    request.prescriptionId(), e.getMessage());
            return new LabOrderApiDto.DispatchOutcome("FAILED", null, null);
        }
    }

    //처방 비활성화로 검사오더 취소 이벤트를 opd.lab-order.cancelled.v1 토픽으로 발행 (브로커 적재 확인까지만)
    @Override
    public boolean cancel(LabOrderApiDto.LabOrderCancelRequestDto request) {
        String eventId = UUID.randomUUID().toString();
        List<LabOrderEventDto.CancelledItem> cancelledItems = request.cancelledItems().stream()
                .map(item -> new LabOrderEventDto.CancelledItem(item.itemCode(), item.itemName(), item.labOrderId()))
                .toList();

        LabOrderEventDto.LabOrderCancelledEvent event = new LabOrderEventDto.LabOrderCancelledEvent(
                eventId,
                LabOrderEventDto.EVENT_TYPE_CANCELLED,
                LabOrderEventDto.SCHEMA_VERSION,
                OffsetDateTime.now(),
                LabOrderEventDto.SOURCE_OPD,
                eventId,
                new LabOrderEventDto.CancelledData(
                        request.prescriptionId(),
                        request.cancelReason(),
                        request.cancelledBy(),
                        cancelledItems
                )
        );

        try {
            String payload = objectMapper.writeValueAsString(event);
            // 요청 이벤트와 같은 key(prescriptionId)를 써서 같은 파티션 안에서 요청 → 취소 순서가 유지된다.
            kafkaTemplate.send(cancelledTopic, request.prescriptionId(), payload)
                    .get(PUBLISH_ACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            log.error("[LAB Kafka] 검사오더 취소 이벤트 발행 실패 prescriptionId={}, message={}",
                    request.prescriptionId(), e.getMessage());
            return false;
        }
    }
}
