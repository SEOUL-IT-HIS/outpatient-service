package kr.co.seoulit.his.outpatientservice.admission.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class AdmissionRequestDto {
    private String admissionRequestId;      // 입원요청 ID
    private String patientId;               // 환자 ID
    private String patientName;             // 환자명 (PAT 연동, 입원요청 목록 조회에서만 채움)
    private String encounterId;             // 외래진료 ID
    private String admissionDeptCode;       // 입원 진료과 코드 (ADM 공통코드 DEPT_CD 의 codeValue)
    private String admissionDeptName;       // 입원 진료과명 (ADM 공통코드 DEPT_CD 연동)
    private String requestNote;             // 요청 메모
    private String requestStatus;           // 요청상태 코드 (ADM 공통코드 ADMISSION_REQUEST_STATUS_CD: 01 요청됨/02 병상 배정/03 거부)
    private String requestStatusName;       // 요청상태명 (ADM 공통코드 ADMISSION_REQUEST_STATUS_CD 연동)
    private String requestedBy;             // 요청 의사 ID
    private LocalDateTime requestedAt;      // 요청일시

    // 병동 회신 전에는 null
    private String admissionId;             // 입원 ID
    private String assignedWardCode;        // 배정 병동 코드 (ADM 공통코드 WARD_CD 의 codeValue)
    private String assignedWardName;        // 배정 병동명 (ADM 공통코드 WARD_CD 연동)
    private String rejectReason;            // 거부 사유
    private LocalDateTime respondedAt;      // 회신일시

    private String sendStatus;              // 요청 이벤트 전송상태 (PENDING / SENT / FAILED)
    private LocalDateTime sentAt;           // 전송일시
}
