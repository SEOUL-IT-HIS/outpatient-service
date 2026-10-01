package kr.co.seoulit.his.outpatientservice.common.client.lab;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;


public final class LabOrderEventDto {

    private LabOrderEventDto() {}

    //지금 주고받는 카프카메시지가 검사오더요청인지, 검사결과회신인지 구분
    public static final String EVENT_TYPE_REQUESTED = "LabOrderRequested";
    public static final String SCHEMA_VERSION = "1.0";
    // SYSTEM_SOURCE_CD 공통코드(ADM DB 등록값) 기준 "Outpatient System" 코드값. 개발표준가이드의 "OPD" 문자열과 다르니 주의
    public static final String SOURCE_OPD = "01";

    public static final String STATUS_ACCEPTED = "ACCEPTED";

    //외래에서 검사실로 보낼때
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(name = "LabOrderRequestedEvent")
    public record LabOrderRequestedEvent(
            String eventId,
            String eventType,
            String version,
            OffsetDateTime occurredAt,
            String source,
            String correlationId,
            RequestedData data
    ) {}

    //어떤 목록들이 있는지
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RequestedData(
            String prescriptionId,
            String encounterId,
            String patientId,
            String doctorId,
            List<OrderItem> orderItems
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OrderItem(
            String itemCode,
            String itemName
    ) {}

    //검사실에서 처리를 마친뒤 외래쪽으로 결과를 돌려줄때
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(name = "LabOrderResultedEvent")
    public record LabOrderResultedEvent(
            String eventId,
            String eventType,
            String version,
            OffsetDateTime occurredAt,
            String source,
            String correlationId,
            ResultedData data
    ) {}

    //접수가 수락되었는지 상태
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResultedData(
            String prescriptionId,
            String status,       // ACCEPTED / REJECTED
            String labOrderId,   // ACCEPTED일 때만 값 존재
            String reason        // REJECTED일 때만 값 존재
    ) {}
}
