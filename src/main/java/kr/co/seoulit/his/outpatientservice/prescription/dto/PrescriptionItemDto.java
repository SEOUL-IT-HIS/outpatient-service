package kr.co.seoulit.his.outpatientservice.prescription.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class PrescriptionItemDto {
    private String itemId;            // 상세 아이템 ID
    private String prescriptionId;    // 처방 ID
    private String prescriptionType;  // 처방 유형 (약품, 검사, 수술/처치 등)
    private String itemCode;          // 약품/검사/수술 코드
    private String itemName;          // 항목 명칭
    private Double dosage;           // 용량
    private String frequency;         // 투여 횟수
    private String durationDays;     // 투약 일수
    private String detailInfo;        // 상세 정보
    private String sendStatus;        // 검사실(LIS) 등 외부 전송상태 - PENDING/SENT/FAILED
    private LocalDateTime sentAt;     // 외부 전송 완료 일시
    private String labOrderId;        // 검사실(LIS)에서 채번한 검사오더 ID (SENT일 때만 값 존재)
    private String rejectReason;      // 검사오더 거절/실패 사유 (FAILED일 때만 값 존재)
    private String dosageFormCd;      // 투약 형태 코드 (약제실 전송용)
}