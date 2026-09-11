package kr.co.seoulit.his.outpatientservice.common.client.lab;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 기존 동기 REST(/api/lab-imaging/lab-orders/intake) 경로. Kafka 전환 이후에도
 * 검사서비스가 롤백 경로로 유지하기로 해서 그대로 남겨두고, 기본값(messaging-enabled=false)으로 계속 사용된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.services.lab.messaging-enabled", havingValue = "false", matchIfMissing = true)
public class LabOrderRestDispatcher implements LabOrderDispatcher {

    private static final String LAB_SUCCESS_CODE = "LAB001";

    private final LabClient labClient;

    @Override
    public LabOrderApiDto.DispatchOutcome dispatch(LabOrderApiDto.LabOrderCreateRequestDto request) {
        try {
            LabOrderApiDto.LabOrderResult result = labClient.sendOrder(request);
            if (LAB_SUCCESS_CODE.equals(result.code())) {
                return new LabOrderApiDto.DispatchOutcome("SENT", result.labOrderId(), null);
            }
            log.warn("[LAB 업무 실패] prescriptionId={}, code={}, message={}",
                    request.prescriptionId(), result.code(), result.message());
            return new LabOrderApiDto.DispatchOutcome("FAILED", null, result.message());
        } catch (Exception e) {
            log.error("[LAB 연동 실패] prescriptionId={}, message={}", request.prescriptionId(), e.getMessage());
            return new LabOrderApiDto.DispatchOutcome("FAILED", null, null);
        }
    }
}
