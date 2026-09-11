package kr.co.seoulit.his.outpatientservice.outpatientcare.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;


@Entity
@Table(schema = "OUTPATIENT", name = "OUTPATIENT_ENCOUNTER")
@Getter
@Setter
public class Encounter {

    @Id
    @Column(name = "ENCOUNTER_ID", length = 36)
    private String encounterId; // 외래진료 ID (UUID, DB에서 채번)

    @Column(name = "PATIENT_ID", nullable = false, length = 36)
    private String patientId; // 환자 ID (PAT 참조)

    @Column(name = "RECEPTION_ID", nullable = false, length = 36)
    private String receptionId; // 접수 ID (RCP 참조)

    @Column(name = "DEPARTMENT_ID", nullable = false, length = 20)
    private String departmentCode; // 진료과 코드

    @Column(name = "DOCTOR_ID", nullable = false, length = 36)
    private String doctorId; // 담당의 ID

    @Column(name = "STATUS", nullable = false, length = 20)
    private String status; // 진료 상태 (WAITING, IN_PROGRESS, COMPLETED, CANCELLED)

    @Column(name = "VISIT_DATE", nullable = false)
    private LocalDate visitDate; // 내원일

    @Column(name = "VISIT_REASON", length = 500)
    private String visitReason; // 방문 사유 (RCP 접수 시 입력)

    @Column(name = "STARTED_AT")
    private LocalDateTime startedAt; // 진료 시작 일시

    @Column(name = "ENDED_AT")
    private LocalDateTime endedAt; // 진료 종료 일시

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt; // 등록 일시

    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt; // 수정 일시
}
