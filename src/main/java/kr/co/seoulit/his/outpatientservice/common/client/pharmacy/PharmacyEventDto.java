package kr.co.seoulit.his.outpatientservice.common.client.pharmacy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

public class PharmacyEventDto {

    private PharmacyEventDto() {}

    public static final String EVENT_TYPE_REQUESTED = "PharmacyOrderRequested";
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

}
