package kr.co.seoulit.his.outpatientservice.prescription.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.seoulit.his.outpatientservice.common.client.lab.LabOrderEventDto;
import kr.co.seoulit.his.outpatientservice.prescription.service.PrescriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 검사서비스가 lab.lab-order.resulted.v1에 발행하는 검사오더 접수 결과를 수신한다.
 * 역직렬화 실패 등 예외는 그대로 던져서 KafkaConfig의 재시도(3회)+DLT 정책을 타게 한다 -
 * 업무 거절(REJECTED)은 정상 메시지라 여기서 예외가 나지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LabOrderResultListener {

    private final PrescriptionService prescriptionService;
    private final ObjectMapper objectMapper;

    //토픽을 보고 검사실에서 새로운 결과 메시지가 들어오면 onLabOrderResulted 메서드를 자동으로 호출
    @KafkaListener(
            topics = "${app.kafka.topics.lab-order-resulted}",
            groupId = "${app.kafka.consumer-group}",
            autoStartup = "${app.services.lab.messaging-enabled:false}"
    )
    //결과 메시지 수신
    public void onLabOrderResulted(String payload) throws Exception {
        LabOrderEventDto.LabOrderResultedEvent event =
                objectMapper.readValue(payload, LabOrderEventDto.LabOrderResultedEvent.class);
        LabOrderEventDto.ResultedData data = event.data();

        log.info("[LAB 결과 수신] prescriptionId={}, status={}, labOrderId={}",
                data.prescriptionId(), data.status(), data.labOrderId());

        prescriptionService.applyLabOrderResult(
                data.prescriptionId(), data.status(), data.labOrderId(), data.reason()
        );
    }
}
