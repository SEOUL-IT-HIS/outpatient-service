package kr.co.seoulit.his.outpatientservice.prescription.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class PrescriptionDto {
    private String prescriptionId;      // 처방 ID
    private String encounterId;         // 진료 ID
    private String patientId;           // 환자 ID
    private String patientName;         // 환자명
    private String serviceType;         // 진료구분
    private String status;              // 처방상태
    private LocalDateTime prescribedAt; // 처방일시
    private String prescribedBy;        // 처방자 ID
    private LocalDateTime cancelledAt;  // 취소일시
    private String cancelReason;        // 취소사유
    private String orderMethod;         // 처방유형
    private String admissionId;         // 입원 ID

    private String priorityCode;        // 우선순위코드 (ROUTINE/URGENT/STAT)
    private String timingCode;          // 처방패턴코드 (SCHEDULED/PRN/ONCE)
    private String verbalYn;            // 구두처방여부 (Y/N)
    private LocalDateTime verbalConfirmedAt; // 구두처방확정일시
    private String verbalConfirmedBy;   // 구두처방확정자 ID
    private String recorderId;          // 입력자 ID
    private String holdReason;          // 보류사유
    private String holdBy;              // 보류자 ID
    private LocalDateTime holdAt;       // 보류일시
    private String discontinuedReason;  // 중단사유
    private String discontinuedBy;      // 중단자 ID
    private LocalDateTime discontinuedAt; // 중단일시

    // 1:N 관계로 매핑될 처방 상세 아이템 목록
    private List<PrescriptionItemDto> items;

    private String pharmacySendStatus;       // 약제실 전송 상태 (PENDING/SENT/FAILED)
    private LocalDateTime pharmacySentAt;    // 약제실 전송 일시
}