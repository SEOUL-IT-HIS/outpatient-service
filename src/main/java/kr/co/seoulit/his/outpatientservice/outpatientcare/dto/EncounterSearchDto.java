package kr.co.seoulit.his.outpatientservice.outpatientcare.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * [목록 조회 요청] GET /api/outpatient/encounters
 * 당일 진료 목록 조회 조건 DTO [GR2-25/27]
 * RCP GET /receptions/waiting에는 날짜 개념이 없어 date 필드는 제거함(2026-07-29)
 */
@Getter
@Setter
public class EncounterSearchDto {

        private String status;         // 진료 상태 (WAITING, IN_PROGRESS, COMPLETED, CANCELLED)
        private String departmentCode; // 진료과 코드 (예: IM)
        private String sort;           // 정렬 (예: visitDate,asc / status,desc)
}