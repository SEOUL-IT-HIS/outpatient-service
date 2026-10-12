package kr.co.seoulit.his.outpatientservice.prescription.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.seoulit.his.outpatientservice.common.client.lab.LabResultEventDto;
import kr.co.seoulit.his.outpatientservice.prescription.service.PrescriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 검사서비스가 lab.lab-result.reported.v1에 발행하는 검사결과를 수신한다.
 * 검사 쪽은 검사항목 1건이 확정될 때마다 이벤트를 하나씩 보내지만(같은 labOrderId로 여러 번 수신),
 * items가 배열이라 나중에 묶어서 보내도 그대로 처리되도록 반복 처리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LabResultReportedListener {

    private final PrescriptionService prescriptionService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${app.kafka.topics.lab-result-reported}",
            groupId = "${app.kafka.lab-result-consumer-group}",
            autoStartup = "${app.services.lab.messaging-enabled:false}"
    )
    public void onLabResultReported(String payload) throws Exception {
        LabResultEventDto.LabResultReportedEvent event =
                objectMapper.readValue(payload, LabResultEventDto.LabResultReportedEvent.class);
        LabResultEventDto.ResultData data = event.data();

        // 재시도해도 성공할 수 없는 형식 오류는 예외로 던져 DLT까지 보내지 않고 경고만 남기고 스킵한다
        if (data == null || data.prescriptionId() == null || data.items() == null || data.items().isEmpty()) {
            log.warn("[LAB 결과 수신] 필수 값 누락으로 스킵 eventId={}", event.eventId());
            return;
        }

        log.info("[LAB 결과 수신] prescriptionId={}, labOrderId={}, itemCount={}",
                data.prescriptionId(), data.labOrderId(), data.items().size());

        for (LabResultEventDto.ResultItem item : data.items()) {
            prescriptionService.applyLabResult(event.eventId(), data, item);
        }
    }
}
