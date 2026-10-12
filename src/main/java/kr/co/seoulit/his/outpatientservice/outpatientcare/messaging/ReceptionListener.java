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
            //접수서비스가 등록할때 메시지를보낸 토픽을 구독
            topics = "${app.kafka.topics.reception-registered}",
            //메시지를받는 소비자그룹(같은 그룹에 속한 서비스끼리 메시지를 분산처리함)
            groupId = "${app.kafka.reception-consumer-group}",
            //설정파일에서 false일때는 메시지를 받지않음
            autoStartup = "${app.services.reception.messaging-enabled:false}"
    )

    //토픽으로 메시지가 들어오면 스프링이 자동으로 이메서드 실행함
    public void onReceptionRegistered(String payload) throws Exception {
        //json텍스트를 자바객체로 변환(역직렬화)하는 과정
        ReceptionEventDto.ReceptionRegisteredEvent event =
                objectMapper.readValue(payload, ReceptionEventDto.ReceptionRegisteredEvent.class);

        //변환된 이벤트 객체안에서 필요한 데이터(접수id,환자id 등)만 꺼내 data에 담음
        ReceptionEventDto.ReceptionData data = event.data();

        //콘솔창에 이벤트잘받았다고 기록남김
        log.info("[접수 수신] receptionId={}, patientId={}", data.receptionId(), data.patientId());

        outpatientCareService.registerEncounter(data);
    }
}