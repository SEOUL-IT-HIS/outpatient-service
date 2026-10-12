package kr.co.seoulit.his.outpatientservice.common.client.lab;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
            String encounterType,    // 채널 구분 OPD/ER/IP (알 수 없으면 null — 검사서비스가 OPD로 처리)
            String urgencyYn,        // 응급 여부 Y/N (ER 채널이거나 우선순위가 STAT이면 Y)
            String admissionId,      // 입원 건일 때만 값, 그 외는 null
            String receptionId,      // 응급 건일 때만 값(접수ID), 그 외는 null
            List<LabOrderItemRequestDto> orderItems // 검사 항목 코드 리스트
    ) {}

    public record LabOrderItemRequestDto(
            String itemCode,         // 검사 항목 코드 (예: CBC 등)
            String itemName          // 검사 항목 명
    ) {}

    // 처방 비활성화 시 이미 전송된 검사오더 취소 요청.
    // REST 본문은 Kafka 취소 이벤트의 data와 필드가 같다(LAB 합의) — 필드명을 바꾸면 LAB 계약이 깨진다.
    @Schema(name = "LabOrderCancelRequestDto")
    public record LabOrderCancelRequestDto(
            String prescriptionId,
            String cancelReason,
            String cancelledBy,
            List<LabOrderCancelItemDto> cancelledItems
    ) {}

    public record LabOrderCancelItemDto(
            String itemCode,
            String itemName,
            String labOrderId        // 확보된 경우에만 값 존재
    ) {}

    // REST 취소 응답. code: LAB118(전체 취소)/LAB119(일부 취소)/LAB120(전부 거절)/LAB117(오더 미접수, HTTP 404).
    // outcome은 CANCELLED/PARTIAL/REFUSED, LAB117일 땐 없을 수 있다.
    @Schema(name = "LabOrderCancelResult")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LabOrderCancelResult(
            String prescriptionId,
            String code,
            String message,
            String outcome,
            List<CancelItemResult> items
    ) {}

    // 항목별 결과. result: CANCELLED / ALREADY / REFUSED_DONE(결과 이미 있음) / REFUSED_PROG(검체 이미 등록됨)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CancelItemResult(
            String itemCode,
            String result,
            String message
    ) {}

    // 검사 항목 카탈로그 검색 — 약품의 medications/search에 대응. 검사서비스 확인 완료(LAB104).
    @Schema(name = "LabItem")
    public record LabItem(
            String itemCode,                 // 검사 항목 코드 (처방 itemCode로 매핑)
            String itemName,                 // 검사명
            String testClassification,       // 검사 분류 (GENERAL/MICROBIOLOGY/PATHOLOGY), 참고용
            List<String> specimenTypes       // 허용 검체종류 목록, 참고용(규칙 없으면 빈 배열)
    ) {}

    @Schema(name = "LabItemSearchResponse")
    public record LabItemSearchResponse(
            String code,   // LAB 응답 코드 (예: "LAB104") — 숫자 아님
            String message,
            List<LabItem> data
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