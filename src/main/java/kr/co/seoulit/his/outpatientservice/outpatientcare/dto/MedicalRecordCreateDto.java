package kr.co.seoulit.his.outpatientservice.outpatientcare.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MedicalRecordCreateDto {
    private String encounterId;       // 외래 접수 ID
    private String chiefComplaint;    // 주호소
    private String examinationNote;   // 진찰내용
    private String assessmentNote;    // 진료소견 (필요시 추가)
    private String planNote;          // 치료계획 (필요시 추가)
}
