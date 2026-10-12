package kr.co.seoulit.his.outpatientservice.admission.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.seoulit.his.outpatientservice.admission.service.AdmissionRequestService;
import kr.co.seoulit.his.outpatientservice.common.client.admission.AdmissionEventDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 병동이 inpatient.opd-admission.result.v1에 발행하는 입원 요청 결과(병상 배정/거부)를 수신한다.
 * 병동이 배정/거부의 eventType 문자열을 확정하기 전이라 eventType이 아니라 data.status(02/03)로 판단한다.
 * 역직렬화 실패 등 예외는 그대로 던져서 컨테이너의 재시도/DLT 정책을 타게 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdmissionResultListener {

    private final AdmissionRequestService admissionRequestService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${app.kafka.topics.admission-resulted}",
            groupId = "${app.kafka.admission-result-consumer-group}",
            autoStartup = "${app.services.admission.messaging-enabled:false}"
    )
    public void onAdmissionResulted(String payload) throws Exception {
        AdmissionEventDto.AdmissionResultEvent event =
                objectMapper.readValue(payload, AdmissionEventDto.AdmissionResultEvent.class);
        AdmissionEventDto.ResultData data = event.data();

        if (data == null) {
            log.warn("[병동 회신 수신] data 없음 스킵 eventId={}", event.eventId());
            return;
        }

        log.info("[병동 회신 수신] requestId={}, status={}, admissionId={}, assignedWardCode={}",
                data.requestId(), data.status(), data.admissionId(), data.assignedWardCode());

        admissionRequestService.applyAdmissionResult(
                data.requestId(), data.status(), data.admissionId(), data.assignedWardCode(), data.rejectReason()
        );
    }
}
