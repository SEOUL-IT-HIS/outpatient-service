package kr.co.seoulit.his.outpatientservice.common.client.reception;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public final class ReceptionEventDto {

    private ReceptionEventDto() {}

    public static final String EVENT_TYPE_REGISTERED = "ReceptionRegistered";
    public static final String SCHEMA_VERSION = "1.1";
    public static final String SOURCE_RCP = "RCP";

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(name = "ReceptionRegisteredEvent")
    public record ReceptionRegisteredEvent(
            String eventId,
            String eventType,
            String version,
            OffsetDateTime occurredAt,
            String source,
            ReceptionData data
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ReceptionData(
            String receptionId,      // RCP 접수 ID
            String patientId,        // PAT 환자 ID
            String departmentCode,   // 배정된 진료과
            String doctorId,         // 담당의 ID
            LocalDate visitDate,     // 내원일
            String status,           // 초기 상태 (보통 WAITING)
            String visitReason,      // 방문 사유
            String visitType,        // 초진/재진 (INITIAL / REVISIT)
            String receptionType     // 예약/당일 (RESERVATION / WALK_IN)
    ) {}
}
