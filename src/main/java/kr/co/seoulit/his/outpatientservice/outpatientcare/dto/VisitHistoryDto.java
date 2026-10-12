package kr.co.seoulit.his.outpatientservice.outpatientcare.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

// [환자 외래 진료 이력 조회 응답] GET /api/outpatient/encounters/visit-history (접수 초진/재진 판정용)
@Getter
@AllArgsConstructor
public class VisitHistoryDto {

    private boolean hasVisitRecord;  // 외래 진료기록 있음(true) / 없음(false)
    private LocalDate lastVisitDate; // 가장 최근 진료일 (없으면 null)
}
