package kr.co.seoulit.his.outpatientservice.admission.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import kr.co.seoulit.his.outpatientservice.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(schema = "OUTPATIENT", name = "ADMISSION_REQUEST")
@Getter
@Setter
public class AdmissionRequest extends BaseEntity {

    @Id
    @Column(name = "ADMISSION_REQUEST_ID", length = 36)
    private String admissionRequestId; // 입원요청 ID (UUID, 병동 메시지의 requestId)

    @Column(name = "PATIENT_ID", nullable = false, length = 36)
    private String patientId; // 환자 ID (PAT 참조)

    @Column(name = "ENCOUNTER_ID", nullable = false, length = 36)
    private String encounterId; // 외래진료 ID

    @Column(name = "ADMISSION_DEPT_CD", nullable = false, length = 20)
    private String admissionDeptCode; // 입원 진료과 코드 (ADM 공통코드 DEPT_CD)

    @Column(name = "REQUEST_NOTE", length = 500)
    private String requestNote; // 요청 메모 (병동 note, 500자 이내)

    @Column(name = "REQUEST_STATUS", nullable = false, length = 20)
    private String requestStatus; // 요청 상태 (ADM 공통코드 ADMISSION_REQUEST_STATUS_CD: 01 요청됨 / 02 병상 배정 / 03 거부)

    @Column(name = "REQUESTED_BY", nullable = false, length = 36)
    private String requestedBy; // 요청 의사 ID

    @Column(name = "REQUESTED_AT", nullable = false)
    private LocalDateTime requestedAt; // 요청 일시

    // 아래는 병동 결과 회신(병상 배정/거부)이 오기 전까지 null이다.
    @Column(name = "ADMISSION_ID", length = 36)
    private String admissionId; // 입원 ID (병동이 병상 배정 시 생성, 외래 입원 처방 API의 admissionId와 같은 값)

    @Column(name = "ASSIGNED_WARD_CD", length = 20)
    private String assignedWardCode; // 배정 병동 코드 (ADM 공통코드 WARD_CD)

    @Column(name = "REJECT_REASON", length = 1000)
    private String rejectReason; // 거부 사유

    @Column(name = "RESPONDED_AT")
    private LocalDateTime respondedAt; // 회신 일시

    @Column(name = "SEND_STATUS", length = 20)
    private String sendStatus; // 요청 이벤트 전송 상태 (PENDING / SENT / FAILED)

    @Column(name = "SENT_AT")
    private LocalDateTime sentAt; // 요청 이벤트 전송 일시
}
