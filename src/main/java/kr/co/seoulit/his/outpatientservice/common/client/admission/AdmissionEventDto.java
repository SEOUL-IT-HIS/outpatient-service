package kr.co.seoulit.his.outpatientservice.common.client.admission;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 외래 -> 병동 입원 요청(opd.admission.requested.v1)과 병동 -> 외래 결과 회신(inpatient.opd-admission.result.v1) 이벤트.
 * 취소/수정은 병동과 합의해 이번 범위에서 제외했다(응급과 동일하게 요청과 결과 회신만 주고받는다).
 */
public final class AdmissionEventDto {

    private AdmissionEventDto() {}

    public static final String EVENT_TYPE_REQUESTED = "AdmissionRequested";
    public static final String SCHEMA_VERSION = "1.0";
    // SYSTEM_SOURCE_CD 공통코드 기준: 01 Outpatient System(외래), 03 Ward System(병동)
    public static final String SOURCE_OPD = "01";

    // ADM 공통코드 ADMISSION_REQUEST_STATUS_CD — 병동이 회신으로 발행하는 값은 02, 03뿐이다(01은 외래 쪽 상태)
    public static final String STATUS_REQUESTED = "01";
    public static final String STATUS_BED_ASSIGNED = "02";
    public static final String STATUS_REJECTED = "03";

    // 병동이 요구한 형식: 타임존 없는 ISO(초 단위, KST 기준) 예) 2026-10-12T10:29:00
    private static final String OCCURRED_AT_PATTERN = "yyyy-MM-dd'T'HH:mm:ss";

    // 외래에서 병동으로 입원을 요청할 때
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(name = "AdmissionRequestedEvent")
    public record AdmissionRequestedEvent(
            String eventId,
            String eventType,
            String version,
            @JsonFormat(pattern = OCCURRED_AT_PATTERN) LocalDateTime occurredAt,
            String source,
            RequestedData data
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RequestedData(
            String requestId,        // 외래 입원요청 ID (필수, 병동이 회신 key로 사용)
            String patientId,        // 환자 ID (필수)
            String targetDeptCode,   // 입원 진료과 DEPT_CD (필수)
            String encounterId,      // 외래 진료 ID
            String requestedBy,      // 요청 의사 ID
            String note              // 요청 메모 (500자 이내, 선택)
    ) {}

    // 병동이 병상 배정 또는 거부 결과를 회신할 때 — occurredAt 등 쓰지 않는 필드는 무시한다
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(name = "AdmissionResultEvent")
    public record AdmissionResultEvent(
            String eventId,
            String eventType,
            String version,
            String source,
            ResultData data
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResultData(
            String requestId,          // 요청 ID
            String status,             // 02 병상 배정 / 03 거부
            String admissionId,        // 병상 배정 시에만 값 존재
            String assignedWardCode,   // 병상 배정 시에만 값 존재 (WARD_CD)
            String rejectReason        // 거부일 때만 값 존재
    ) {}
}
