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
    // 취소 API 응답 코드 (LAB 합의)
    private static final String LAB_NOT_RECEIVED_CODE = "LAB117";
    private static final String LAB_CANCEL_ALL_CODE = "LAB118";
    private static final String LAB_CANCEL_PARTIAL_CODE = "LAB119";
    private static final String LAB_CANCEL_REFUSED_CODE = "LAB120";

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

    // 검사서비스 REST 취소 API 호출. 반환값은 "LAB이 요청을 받아 판정했는가"이다.
    // LAB118(전체 취소)·LAB119(일부)·LAB120(전부 거절)은 LAB이 판정까지 마친 것이라 true — 거절/일부 건은
    // LAB이 검사실 화면 경고로 사람이 처리하게 하므로 외래는 로그만 남긴다(이번 단계는 회신/안내 없음).
    // LAB117(오더 미접수)·통신 오류는 LAB이 처리하지 못한 것이라 false. REST는 자동 재시도가 없다.
    @Override
    public boolean cancel(LabOrderApiDto.LabOrderCancelRequestDto request) {
        try {
            LabOrderApiDto.LabOrderCancelResult result = labClient.cancelOrder(request);
            String code = result == null ? null : result.code();

            if (LAB_CANCEL_ALL_CODE.equals(code)) {
                log.info("[LAB 취소] 전체 취소 완료 prescriptionId={}", request.prescriptionId());
                return true;
            }
            if (LAB_CANCEL_PARTIAL_CODE.equals(code) || LAB_CANCEL_REFUSED_CODE.equals(code)) {
                log.warn("[LAB 취소] 취소하지 못한 항목 있음 prescriptionId={}, code={}, message={}, items={}",
                        request.prescriptionId(), code, result.message(), result.items());
                return true;
            }
            if (LAB_NOT_RECEIVED_CODE.equals(code)) {
                log.warn("[LAB 취소] 오더 미접수 — 접수 완료 후 재호출 필요 prescriptionId={}, message={}",
                        request.prescriptionId(), result.message());
                return false;
            }
            log.warn("[LAB 취소] 알 수 없는 응답 prescriptionId={}, code={}, message={}",
                    request.prescriptionId(), code, result == null ? null : result.message());
            return false;
        } catch (Exception e) {
            log.error("[LAB 취소 연동 실패] prescriptionId={}, message={}", request.prescriptionId(), e.getMessage());
            return false;
        }
    }
}
