package kr.co.seoulit.his.outpatientservice.common.client.lab;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public final class LabOrderApiDto {

    private LabOrderApiDto() {}

    @Schema(name = "LabOrderCreateRequestDto")
    public record LabOrderCreateRequestDto(
            String prescriptionId,   // 처방 ID (어떤 처방에서 나간 오더인지)
            String encounterId,      // 진료 건 ID
            String patientId,        // 환자 UUID
            String doctorId,         // 오더를 낸 의사 ID
            List<LabOrderItemRequestDto> orderItems // 검사 항목 코드 리스트
    ) {}

    public record LabOrderItemRequestDto(
            String itemCode,         // 검사 항목 코드 (예: CBC 등)
            String itemName          // 검사 항목 명
    ) {}

    @Schema(name = "LabOrderResult")
    public record LabOrderResult(
            String code,
            String message,
            String labOrderId        // 연동 후 생성된 검사 오더 ID
    ) {}

    // REST(동기)/Kafka(비동기) 두 발송 경로를 동일한 모양으로 다루기 위한 결과값.
    // REST는 이 시점에 SENT/FAILED가 확정되고, Kafka는 발행 성공 시 PENDING만 확정되고
    // 실제 SENT/FAILED는 LabOrderResultListener가 결과 이벤트를 받은 뒤 채운다.
    @Schema(name = "LabOrderDispatchOutcome")
    public record DispatchOutcome(
            String status,       // SENT / FAILED / PENDING
            String labOrderId,   // 확보된 경우에만 값 존재 (REST는 즉시, Kafka는 결과 이벤트 수신 후)
            String rejectReason  // 실패/거절 사유
    ) {}
}