package kr.co.seoulit.his.outpatientservice.prescription.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(schema = "OUTPATIENT", name = "PRESCRIPTION")
@Getter
@Setter
public class Prescription {

    @Id
    @Column(name = "PRESCRIPTION_ID", length = 36)
    private String prescriptionId; // 처방 ID

    // 입원(admission)/응급(emergency) 경로로 등록된 처방은 외래 진료(encounter)가 없어 null일 수 있다
    // — admissionId 또는 receptionId가 대신 채워진다.
    @Column(name = "ENCOUNTER_ID", length = 36)
    private String encounterId; // 진료 ID

    @Column(name = "PATIENT_ID", nullable = false, length = 36)
    private String patientId; // 환자 ID

    @Column(name = "SERVICE_TYPE", length = 36)
    private String serviceType; // 진료구분

    @Column(name = "STATUS", length = 36)
    private String status; // 처방상태

    @Column(name = "PRESCRIBED_AT")
    private LocalDateTime prescribedAt; // 처방일시

    @Column(name = "PRESCRIBED_BY", length = 36)
    private String prescribedBy; // 처방자 ID

    @Column(name = "CANCELLED_AT")
    private LocalDateTime cancelledAt; // 취소일시

    @Column(name = "CANCEL_REASON", length = 2000)
    private String cancelReason; // 취소사유

    @Column(name = "ORDER_METHOD", length = 36)
    private String orderMethod; // 처방유형

    @Column(name = "ADMISSION_ID", length = 36)
    private String admissionId; // 입원 ID

    // 응급(emergency) 경로 전용 — encounterId가 없을 때 접수ID를 담는다.
    @Column(name = "RECEPTION_ID", length = 36)
    private String receptionId; // 접수 ID (응급)

    @Column(name = "PRIORITY_CODE", length = 80)
    private String priorityCode; // 우선순위코드 (ROUTINE/URGENT/STAT)

    @Column(name = "TIMING_CODE", length = 80)
    private String timingCode; // 처방패턴코드 (ADM 공통코드 ORDER_TIMING_CD: 01 Scheduled/02 PRN/03 Once)

    @Column(name = "VERBAL_YN", length = 1)
    private String verbalYn; // 구두처방여부 (Y/N)

    @Column(name = "VERBAL_CONFIRMED_AT")
    private LocalDateTime verbalConfirmedAt; // 구두처방확정일시

    @Column(name = "VERBAL_CONFIRMED_BY", length = 36)
    private String verbalConfirmedBy; // 구두처방확정자 ID

    @Column(name = "RECORDER_ID", length = 36)
    private String recorderId; // 입력자 ID

    @Column(name = "HOLD_REASON", length = 800)
    private String holdReason; // 보류사유

    @Column(name = "HOLD_BY", length = 36)
    private String holdBy; // 보류자 ID

    @Column(name = "HOLD_AT")
    private LocalDateTime holdAt; // 보류일시

    @Column(name = "DISCONTINUED_REASON", length = 800)
    private String discontinuedReason; // 중단사유

    @Column(name = "DISCONTINUED_BY", length = 36)
    private String discontinuedBy; // 중단자 ID

    @Column(name = "DISCONTINUED_AT")
    private LocalDateTime discontinuedAt; // 중단일시

    @Column(name = "PHARMACY_SEND_STATUS", length = 20)
    private String pharmacySendStatus;

    @Column(name = "PHARMACY_SENT_AT")
    private LocalDateTime pharmacySentAt;

    // 입원 경로 전용 — encounterId가 없을 때 dispatchPharmacyOrders가 Encounter 대신 이 값을 쓴다.
    @Column(name = "DEPARTMENT_CODE", length = 36)
    private String departmentCode; // 처방과 코드
}
