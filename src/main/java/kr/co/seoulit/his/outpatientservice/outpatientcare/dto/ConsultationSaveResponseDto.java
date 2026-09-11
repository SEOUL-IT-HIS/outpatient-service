package kr.co.seoulit.his.outpatientservice.outpatientcare.dto;

import kr.co.seoulit.his.outpatientservice.prescription.dto.PrescriptionDto;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConsultationSaveResponseDto {
    private MedicalRecordDto medicalRecord;
    private PrescriptionDto prescription;
}
