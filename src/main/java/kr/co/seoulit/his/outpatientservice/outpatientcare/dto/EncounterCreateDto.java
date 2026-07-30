package kr.co.seoulit.his.outpatientservice.outpatientcare.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * [진료 배정 등록 요청] POST /api/outpatient/encounters
 * RCP 대기 환자(receptionId) 한 명을 OPD가 담당의/진료과에 배정해서 OUTPATIENT_ENCOUNTER에 등록할 때 사용
 */
@Getter
@Setter
public class EncounterCreateDto {

    @NotBlank(message = "receptionId는 필수입니다.")
    private String receptionId; // RCP 접수 ID

    @NotBlank(message = "patientId는 필수입니다.")
    private String patientId;   // 환자 ID (RCP 대기 목록 응답에서 그대로 가져옴)

    @NotBlank(message = "departmentId는 필수입니다.")
    private String departmentId; // 배정할 진료과 코드

    @NotBlank(message = "doctorId는 필수입니다.")
    private String doctorId;    // 배정할 담당의 ID
}
