package kr.co.seoulit.his.outpatientservice.common.client.patient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * PAT Provider 계약 DTO (카탈로그: POST /api/patient/batch, GET /api/patient/{id}).
 * 실제 PAT 응답 필드명이 다르면 이 클래스만 맞추면 된다.
 */
public final class PatientApiDto {

    private PatientApiDto() {
    }

    @Schema(name = "PatientBatchQueryRequest")
    public record BatchQueryRequest(List<String> patientIds) {
    }

    // 응답에 birthDate/genderCd/statusCd 등 우리가 안 쓰는 필드가 더 와도 무시하고 파싱
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(name = "PatientSummary")
    public record PatientSummary(
            String patientId,
            String patientName
    ) {
    }
}
