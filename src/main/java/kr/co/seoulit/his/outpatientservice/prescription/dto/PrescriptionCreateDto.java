package kr.co.seoulit.his.outpatientservice.prescription.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class PrescriptionCreateDto {
    private String serviceType;              // 진료구분
    private String orderMethod;               // 처방유형
    private String priorityCode;               // 우선순위코드 (ROUTINE/URGENT/STAT)
    private String timingCode;                 // 처방패턴코드 (ADM 공통코드 ORDER_TIMING_CD: 01 Scheduled/02 PRN/03 Once)
    private List<PrescriptionItemDto> items;    // 처방 상세 아이템 목록

    // 입원(admission)/응급(emergency) 경로 전용 — 외래는 encounterId로 PAT/담당의를 조회하지만,
    // 병동·응급 쪽은 로그인 세션이 없는 서버 간 호출이라 요청 바디로 직접 받는다.
    private String patientId;     // 환자 ID
    private String prescribedBy;  // 처방의사 ID
    private String departmentCode; // 처방과 코드 (약제실 전송 시 Encounter 대신 사용)

    // 응급(emergency) 경로 전용 — 구두처방 여부 (Y/N). 확정은 별도 verbal-confirm API로 처리한다.
    private String verbalYn;

    // 응급(emergency) 경로 전용 — true면 등록 직후 약제실로 자동 전송한다(약품 항목이 있을 때만).
    // 전송이 실패해도 등록은 성공하고 응답의 pharmacySendStatus가 FAILED로 내려가며, 이후 dispatch-pharmacy로 재전송한다.
    private Boolean dispatchNow;
}