package kr.co.seoulit.his.outpatientservice.outpatientcare.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.seoulit.his.outpatientservice.common.client.reception.ReceptionEventDto;
import kr.co.seoulit.his.outpatientservice.outpatientcare.service.OutpatientCareService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReceptionListener {

    private final OutpatientCareService outpatientCareService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${app.kafka.topics.reception-registered}",
            groupId = "${app.kafka.reception-consumer-group}",
            autoStartup = "${app.services.reception.messaging-enabled:false}"
    )
    public void onReceptionRegistered(String payload) throws Exception {
        ReceptionEventDto.ReceptionRegisteredEvent event =
                objectMapper.readValue(payload, ReceptionEventDto.ReceptionRegisteredEvent.class);
        ReceptionEventDto.ReceptionData data = event.data();

        log.info("[접수 수신] receptionId={}, patientId={}", data.receptionId(), data.patientId());

        outpatientCareService.registerEncounter(data);
    }
}