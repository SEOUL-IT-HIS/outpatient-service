package kr.co.seoulit.his.outpatientservice.common.client.patient;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * PAT Provider 계약 DTO (카탈로그: POST /api/v1/patients/batch-query, GET /api/v1/patients/{id}).
 * 실제 PAT 응답 필드명이 다르면 이 클래스만 맞추면 된다.
 */
public final class PatientApiDto {

    private PatientApiDto() {
    }

    @Schema(name = "PatientBatchQueryRequest")
    public record BatchQueryRequest(List<String> patientIds) {
    }

    @Schema(name = "PatientSummary")
    public record PatientSummary(
            String patientId,
            String patientNo,
            String patientName
    ) {
    }
}
