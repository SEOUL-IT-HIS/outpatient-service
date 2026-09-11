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
    private String timingCode;                 // 처방패턴코드 (SCHEDULED/PRN/ONCE)
    private List<PrescriptionItemDto> items;    // 처방 상세 아이템 목록
}