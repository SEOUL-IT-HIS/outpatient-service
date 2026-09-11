package kr.co.seoulit.his.outpatientservice.outpatientcare.dto;

import kr.co.seoulit.his.outpatientservice.prescription.dto.PrescriptionCreateDto;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConsultationSaveRequestDto {
    private MedicalRecordCreateDto medicalRecord;
    private PrescriptionCreateDto prescription;
}
