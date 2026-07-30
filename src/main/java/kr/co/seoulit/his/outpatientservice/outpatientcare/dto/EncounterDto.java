package kr.co.seoulit.his.outpatientservice.outpatientcare.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * [목록 조회 응답] GET /api/outpatient/encounters
 * 외래 당일 진료 목록 항목 DTO (환자명/환자번호는 PAT API로 조회해 채움)
 * encounterId/receptionId/doctorId는 실제 OUTPATIENT_ENCOUNTER 테이블 PK/컬럼이 VARCHAR2(UUID)라 String으로 맞춤
 */
@Getter
@Setter
public class EncounterDto {

        private String encounterId;      // 외래진료 ID
        private String patientId;        // 환자 ID (PAT 참조)
        private String patientNo;        // 환자 번호 (PAT)
        private String patientName;      // 환자명 (PAT)
        private String receptionId;      // 접수 ID (RCP 연계)
        private String departmentCode;   // 진료과 코드
        private String doctorId;         // 담당의 ID
        private String status;           // 진료 상태 (WAITING, IN_PROGRESS, COMPLETED, CANCELLED)
        private LocalDate visitDate;     // 내원일
        private LocalDateTime createdAt; // 등록 일시
}
