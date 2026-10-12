package kr.co.seoulit.his.outpatientservice.admission.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdmissionRequestCreateDto {
    private String admissionDeptCode;   // 입원 진료과 코드 (ADM 공통코드 DEPT_CD, 생략 시 진료의 진료과)
    private String requestNote;         // 요청 메모 (병동에 전달, 500자 이내)
}
