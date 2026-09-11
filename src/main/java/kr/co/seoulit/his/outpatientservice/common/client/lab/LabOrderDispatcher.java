package kr.co.seoulit.his.outpatientservice.common.client.lab;

/**
 * 검사오더 발송 경로 추상화. app.services.lab.messaging-enabled 값에 따라
 * REST(LabOrderRestDispatcher) 또는 Kafka(LabOrderKafkaDispatcher) 구현체가 선택된다.
 */
public interface LabOrderDispatcher {
    LabOrderApiDto.DispatchOutcome dispatch(LabOrderApiDto.LabOrderCreateRequestDto request);
}
