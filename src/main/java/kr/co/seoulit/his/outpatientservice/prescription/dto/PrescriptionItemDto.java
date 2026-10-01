package kr.co.seoulit.his.outpatientservice.prescription.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

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

    //검사결과조회
    private String resultValue;        // 검사결과값 (수치/텍스트)
    private String resultUnit;         // 결과값 단위 (예: mmol/L)
    private String referenceRange;     // 정상 참고범위 (예: 3.9-6.1)
    private String abnormalFlag;       // 이상 여부 (N=정상, H=높음, L=낮음, null=판정불가)
    private LocalDateTime resultReportedAt; // 검사결과 보고(확정) 일시

    // 검사결과 항목별 상세 (신형식). 구형식으로 저장된 결과는 seq=1 한 건으로 합쳐서 내려간다
    private List<ResultDetail> resultDetails;

    @Getter
    @Setter
    public static class ResultDetail {
        private Integer seq;               // 결과 순번
        private String detailCode;         // 결과항목 코드 (공통코드 RESULT_ITEM_CD)
        private String detailName;         // 결과항목명 (ADM 공통코드 연동, 미등록/캐시 미적재면 null)
        private String resultValue;        // 결과값
        private String resultUnit;         // 결과값 단위
        private String referenceRange;     // 참고범위
        private String abnormalFlag;       // 이상 여부 (N=정상, H=높음, L=낮음, null=판정불가)
    }
}