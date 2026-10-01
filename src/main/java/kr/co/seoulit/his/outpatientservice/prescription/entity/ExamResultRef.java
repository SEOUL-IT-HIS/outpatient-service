package kr.co.seoulit.his.outpatientservice.prescription.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(schema = "OUTPATIENT", name = "EXAM_RESULT_REF")
@Getter
@Setter
public class ExamResultRef {

    @Id
    @Column(name = "RESULT_REF_ID", length = 36)
    private String resultRefId; // 결과참조 ID

    @Column(name = "ITEM_ID", nullable = false, length = 36)
    private String itemId; // 처방 상세 아이템 ID

    @Column(name = "PATIENT_ID", length = 36)
    private String patientId; // 환자 ID

    @Column(name = "LAB_RESULT_ID", length = 36)
    private String labResultId; // 검사서비스 resultId

    @Column(name = "RESULT_SUMMARY", length = 500)
    private String resultSummary; // 결과 요약 (수치가 아닌 결과용)

    @Column(name = "RESULT_STATUS", length = 20)
    private String resultStatus; // 결과 상태 (FINAL)

    @Column(name = "RESULTED_AT")
    private LocalDateTime resultedAt; // 결과 확정 일시

    @Column(name = "CACHED_AT")
    private LocalDateTime cachedAt; // 수신 저장 일시

    @Column(name = "SEQ")
    private Integer seq; // 결과 순번 (검사별 고정 순번)

    @Column(name = "DETAIL_CODE", length = 20)
    private String detailCode; // 결과항목 코드 (공통코드 RESULT_ITEM_CD)

    @Column(name = "RESULT_VALUE", length = 200)
    private String resultValue; // 결과값

    @Column(name = "RESULT_UNIT", length = 50)
    private String resultUnit; // 결과 단위

    @Column(name = "REFERENCE_RANGE", length = 100)
    private String referenceRange; // 참고범위

    @Column(name = "ABNORMAL_FLAG", length = 10)
    private String abnormalFlag; // 이상 여부 (N=정상, H=높음, L=낮음, null=판정불가)
}
