package kr.co.seoulit.his.outpatientservice.outpatientcare.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * [진료기록 조회 응답] GET /api/outpatient/records, GET /api/outpatient/records/{recordId}
 * 실제 MEDICAL_RECORD 테이블 컬럼 기준(2026-07-28 확인) — SUBJECTIVE_TEXT 컬럼이 없어 SOAP의 S는 존재하지 않는다.
 * recordId/encounterId는 실제 PK/FK가 VARCHAR2(UUID)라 String으로 맞춤
 */
@Getter
@Setter
public class MedicalRecordDto {

        private String recordId;          // 진료기록 ID
        private String encounterId;       // 외래진료 ID
        private String patientId;         // 환자 ID (encounter 조인으로 채움, PAT 참조)
        private String patientNo;         // 환자 번호 (PAT)
        private String patientName;       // 환자명 (PAT)
        private String chiefComplaint;    // 주호소
        private String examinationNote;   // 진찰내용
        private String assessmentNote;    // 진료소견
        private String planNote;          // 치료계획
        private String status;            // 기록 상태
        private String authorId;          // 작성자 ID
        private LocalDateTime createdAt;  // 등록 일시
        private LocalDateTime updatedAt;  // 수정 일시
}
