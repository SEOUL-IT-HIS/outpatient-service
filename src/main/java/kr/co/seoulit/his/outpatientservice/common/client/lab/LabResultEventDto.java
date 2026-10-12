package kr.co.seoulit.his.outpatientservice.common.client.lab;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

public final class LabResultEventDto {

    private LabResultEventDto() {}

    public static final String EVENT_TYPE_REPORTED = "LabResultReported";
    public static final String RESULT_STATUS_FINAL = "FINAL";
    public static final String ABNORMAL_FLAG_NORMAL = "N";
    public static final String ABNORMAL_FLAG_HIGH = "H"; // 참고범위 상한 초과
    public static final String ABNORMAL_FLAG_LOW = "L";  // 참고범위 하한 미만 (판정 불가는 null)
    public static final String RESULT_TYPE_GENERAL = "GENERAL"; // 수치형 결과만 우선 처리, 나머지(CULTURE/PATHOLOGY)는 스킵

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(name = "LabResultReportedEvent")
    public record LabResultReportedEvent(
            String eventId,
            String eventType,
            String version,
            OffsetDateTime occurredAt,
            String source,
            String correlationId,
            ResultData data
    ) {}

    // receptionNo/systemCode/confirmedItemCount 등 부가 필드는 안 쓰므로 매핑 안 함 (ignoreUnknown이 알아서 무시)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResultData(
            String prescriptionId,
            String labOrderId,
            String resultStatus,
            OffsetDateTime reportedAt,
            List<ResultItem> items
    ) {}

    // 일반검사(01~04) 신형식은 결과가 details에 담기고 최상위 resultValue/unit/referenceRange/abnormalFlag는 null이다.
    // 구형식(details 없음)은 최상위 값에 결과가 담긴다 — 신·구 동시 수용 기간 동안 둘 다 받는다.
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResultItem(
            String itemCode,
            String resultValue,
            String unit,
            String referenceRange,
            String abnormalFlag,
            String resultType,
            String labOrderItemId,
            String resultId,
            String confirmedById,
            List<ResultDetail> details
    ) {}

    // 결과항목 1개 (detailCode는 공통코드 RESULT_ITEM_CD 값, seq는 검사별 고정 순번)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResultDetail(
            Integer seq,
            String detailCode,
            String resultValue,
            String unit,
            String referenceRange,
            String abnormalFlag
    ) {}
}