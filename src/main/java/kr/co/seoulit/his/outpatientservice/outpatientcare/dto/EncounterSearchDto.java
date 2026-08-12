package kr.co.seoulit.his.outpatientservice.outpatientcare.dto;

import lombok.Getter;
import lombok.Setter;

//[목록 조회 요청] GET /api/outpatient/encounters
@Getter
@Setter
public class EncounterSearchDto {
        private String recordId;       // 진료기록 ID 검색 (화면 검색창용)
        private String encounterId;    // 외래진료 ID 검색
        private String patientName;    // 환자명 검색
        private String patientNo;      // 환자번호 검색
        private String doctorId;       // 담당의 ID 검색
        private String status;         // 진료 상태 (WAITING, IN_PROGRESS, COMPLETED, CANCELLED)
        private String departmentCode; // 진료과 코드
        private String sort;           // 정렬
}