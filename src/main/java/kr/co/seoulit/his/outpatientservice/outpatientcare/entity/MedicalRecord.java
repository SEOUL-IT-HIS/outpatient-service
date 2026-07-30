package kr.co.seoulit.his.outpatientservice.outpatientcare.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * JPA Entity — OUTPATIENT.MEDICAL_RECORD 테이블 매핑 (실제 DB 컬럼 기준, 2026-07-28 확인)
 * MedicalRecordRepository / OutpatientCareServiceImpl에서 사용
 * 실제 테이블엔 PATIENT_ID 컬럼이 없어서 encounter 연관관계를 통해서만 환자를 특정할 수 있다.
 */
@Entity
@Table(schema = "OUTPATIENT", name = "MEDICAL_RECORD")
@Getter
@Setter
public class MedicalRecord {

    @Id
    @Column(name = "RECORD_ID", length = 36)
    private String id; // 진료기록 ID (UUID, DB에서 채번)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ENCOUNTER_ID", nullable = false)
    private Encounter encounter; // 외래진료 연관관계 매핑

    @Column(name = "CHIEF_COMPLAINT", length = 500)
    private String chiefComplaint; // 주호소

    @Lob
    @Column(name = "EXAMINATION_NOTE")
    private String examinationNote; // 진찰내용

    @Lob
    @Column(name = "ASSESSMENT_NOTE")
    private String assessmentNote; // 진료소견

    @Lob
    @Column(name = "PLAN_NOTE")
    private String planNote; // 치료계획

    @Column(name = "STATUS", nullable = false, length = 20)
    private String status; // 기록 상태

    @Column(name = "AUTHOR_ID", nullable = false, length = 36)
    private String authorId; // 작성자 ID

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt; // 등록 일시

    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt; // 수정 일시

    // 편의 메서드 - 실제 테이블에 PATIENT_ID 컬럼이 없어서 연관된 Encounter를 통해서만 얻을 수 있다
    public String getPatientId() {
        return encounter != null ? encounter.getPatientId() : null;
    }
}
