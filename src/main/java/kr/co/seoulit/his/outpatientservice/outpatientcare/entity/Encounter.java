package kr.co.seoulit.his.outpatientservice.outpatientcare.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * JPA Entity — OUTPATIENT.OUTPATIENT_ENCOUNTER 테이블 매핑 (실제 DB 컬럼 기준, 2026-07-28 확인)
 * 지금은 getEncounters()가 RCP GET /receptions/waiting만 사용해서 이 엔티티는 조회에 관여하지 않는다.
 * OPD가 담당의/진료과 배정, 진료상태를 관리하는 장부로 쓸 예정이라(추후 스프린트) 지우지 않고 남겨둔다.
 */
@Entity
@Table(schema = "OUTPATIENT", name = "OUTPATIENT_ENCOUNTER")
@Getter
@Setter
public class Encounter {

    @Id
    @Column(name = "ENCOUNTER_ID", length = 36)
    private String id; // 외래진료 ID (UUID, DB에서 채번)

    @Column(name = "PATIENT_ID", nullable = false, length = 36)
    private String patientId; // 환자 ID (PAT 참조)

    @Column(name = "RECEPTION_ID", nullable = false, length = 36)
    private String receptionId; // 접수 ID (RCP 참조)

    @Column(name = "DEPARTMENT_ID", nullable = false, length = 20)
    private String departmentId; // 진료과 코드

    @Column(name = "DOCTOR_ID", nullable = false, length = 36)
    private String doctorId; // 담당의 ID

    @Column(name = "STATUS", nullable = false, length = 20)
    private String status; // 진료 상태 (WAITING, IN_PROGRESS, COMPLETED, CANCELLED)

    @Column(name = "VISIT_DATE", nullable = false)
    private LocalDate visitDate; // 내원일

    @Column(name = "STARTED_AT")
    private LocalDateTime startedAt; // 진료 시작 일시

    @Column(name = "ENDED_AT")
    private LocalDateTime endedAt; // 진료 종료 일시

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt; // 등록 일시

    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt; // 수정 일시
}
