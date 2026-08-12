package kr.co.seoulit.his.outpatientservice.outpatientcare.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReceptionDto {
    private Long receptionId;
    private String patientId;
    private String status;
    private String deptCode;
}