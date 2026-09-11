package kr.co.seoulit.his.outpatientservice.common.client.pharmacy;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

public final class PharmacyApiDto {

    private PharmacyApiDto() {
    }

    @Schema(name = "Medication")
    public record Medication(
            Long medicationId,
            String medicationName,   // 약품명
            String itemSeq,          // 품목기준코드
            String itemEngName,      // 영문 약품명
            String entpName,         // 제조사명
            String etcOtcName,       // 전문/일반 구분
            String classNo,          // 약효분류번호
            String className,        // 약효분류명
            String formCodeName,     // 제형
            String chart,            // 성상
            LocalDate itemPermitDate, // 허가일자
            String ediCode,          // 약가코드 (처방 항목 itemCode로 매핑)
            String stdCd              // 표준코드
    ) {
    }

    @Schema(name = "MedicationSearchResponse")
    public record MedicationSearchResponse(
            int code,
            String message,
            List<Medication> data
    ) {
    }
}
