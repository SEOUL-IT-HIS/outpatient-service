package kr.co.seoulit.his.outpatientservice.common.client.pharmacy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

public class PharmacyEventDto {

    private PharmacyEventDto() {}

    public static final String EVENT_TYPE_REQUESTED = "PharmacyOrderRequested";
    public static final String EVENT_TYPE_CANCELLED = "PharmacyOrderCancelled";
    public static final String SCHEMA_VERSION = "1.0";
    public static final String SOURCE_OPD = "OPD";

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(name = "PharmacyOrderRequestedEvent")
    public record PharmacyOrderRequestedEvent(
            String eventId,
            String eventType,
            String version,
            OffsetDateTime occurredAt,
            String source,
            PharmacyOrderData data
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PharmacyOrderData(
            String prescriptionId,    // 처방전 번호
            String patientId,         // 환자 ID
            String physicianId,       // 처방 작성 의사 ID
            String departmentId,      // 처방 작성 진료과 ID
            OffsetDateTime createdAt, // 처방전 작성 일시
            String encounterType,     // 진료 채널 OPD/ER/IP (알 수 없는 진료구분이면 null)
            String priorityCode,      // 우선순위 코드 (ADM ORDER_PRIORITY_CD: 01 STAT, 02 Urgent, 03 Routine)
            String verbalYn,          // 구두처방 여부 Y/N (응급 경로만 값, 그 외 null)
            List<PharmacyOrderItem> items
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PharmacyOrderItem(
            String ediCode,       // 약품 코드
            String itemName,      // 약품명
            Double dosageQty,     // 1회 투여량
            String dosageFormCd,  // 투약 형태 코드
            String frequency,     // 투여 횟수
            String durationDays,  // 투약 일수
            String detailInfo     // 복약 안내
    ) {}

    // 처방 비활성화로 이미 보낸 처방을 철회할 때 (단방향 통보 — 약제의 회신은 받지 않는다)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(name = "PharmacyOrderCancelledEvent")
    public record PharmacyOrderCancelledEvent(
            String eventId,
            String eventType,
            String version,
            OffsetDateTime occurredAt,
            String source,
            PharmacyOrderCancelledData data
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PharmacyOrderCancelledData(
            String prescriptionId,
            String cancelReason,
            String cancelledBy,                 // 취소한 사용자 ID
            List<PharmacyCancelledItem> cancelledItems // 처방 전체 단위 취소 — 약제는 참고용으로만 본다
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PharmacyCancelledItem(
            String ediCode,
            String itemName
    ) {}

}
