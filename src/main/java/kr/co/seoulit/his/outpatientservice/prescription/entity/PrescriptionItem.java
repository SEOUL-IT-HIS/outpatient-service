package kr.co.seoulit.his.outpatientservice.prescription.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(schema = "OUTPATIENT", name = "PRESCRIPTION_ITEM")
@Getter
@Setter
public class PrescriptionItem {

    @Id
    @Column(name = "ITEM_ID", length = 36)
    private String itemId; // 상세 아이템 ID

    @Column(name = "PRESCRIPTION_ID", length = 36)
    private String prescriptionId; // 처방 ID

    @Column(name = "PRESCRIPTION_TYPE", length = 36)
    private String prescriptionType; // 처방 유형 (약품, 검사)

    @Column(name = "ITEM_CODE", length = 36)
    private String itemCode; // 약품/검사 코드

    @Column(name = "ITEM_NAME", length = 100)
    private String itemName; // 항목 명칭

    @Column(name = "DOSAGE", length = 36)
    private Double dosage; // 용량

    @Column(name = "FREQUENCY", length = 36)
    private String frequency; // 투여 횟수

    @Column(name = "DURATION_DAYS", length = 36)
    private String durationDays; // 투약 일수

    @Column(name = "DETAIL_INFO", length = 2000)
    private String detailInfo; // 상세 정보

    @Column(name = "SEND_STATUS", length = 20)
    private String sendStatus; // 검사실(LIS) 등 외부 전송상태 - PENDING/SENT/FAILED, 대상 아니면 null

    @Column(name = "SENT_AT")
    private LocalDateTime sentAt; // 외부 전송 완료(또는 마지막 시도) 일시

    @Column(name = "LAB_ORDER_ID", length = 36)
    private String labOrderId; // 검사실(LIS)에서 채번한 검사오더 ID (SENT일 때만 값 존재)

    @Column(name = "REJECT_REASON", length = 800)
    private String rejectReason; // 검사오더 거절/실패 사유 (FAILED일 때만 값 존재)

    private String dosageFormCd;

    @Column(name = "RESULT_VALUE", length = 200)
    private String resultValue;

    @Column(name = "RESULT_UNIT", length = 50)
    private String resultUnit;

    @Column(name = "REFERENCE_RANGE", length = 100)
    private String referenceRange;

    @Column(name = "ABNORMAL_FLAG", length = 10)
    private String abnormalFlag;

    @Column(name = "RESULT_REPORTED_AT")
    private LocalDateTime resultReportedAt;

    @Column(name = "RESULT_EVENT_ID", length = 36)
    private String resultEventId; // 같은 eventId 재수신이면 중복으로 보고 스킵
}
