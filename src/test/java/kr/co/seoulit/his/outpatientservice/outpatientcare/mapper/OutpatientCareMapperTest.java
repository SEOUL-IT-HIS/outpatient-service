package kr.co.seoulit.his.outpatientservice.outpatientcare.mapper;

import kr.co.seoulit.his.outpatientservice.outpatientcare.dto.MedicalRecordDto;
import kr.co.seoulit.his.outpatientservice.outpatientcare.entity.Encounter;
import kr.co.seoulit.his.outpatientservice.outpatientcare.entity.MedicalRecord;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutpatientCareMapperTest {

    private final OutpatientCareMapper mapper = new OutpatientCareMapperImpl();

    private MedicalRecord recordWithVisitType(String visitType) {
        Encounter encounter = new Encounter();
        encounter.setEncounterId("ENC-1");
        encounter.setPatientId("PAT-1");
        encounter.setVisitType(visitType);

        MedicalRecord record = new MedicalRecord();
        record.setRecordId("REC-1");
        record.setEncounter(encounter);
        return record;
    }

    @Test
    void 진료기록_응답에_진료건의_초진재진이_담긴다() {
        MedicalRecordDto dto = mapper.toMedicalRecordDto(recordWithVisitType("REVISIT"));

        assertThat(dto.getVisitType()).isEqualTo("REVISIT");
        assertThat(dto.getEncounterId()).isEqualTo("ENC-1");
    }

    @Test
    void 진료기록_응답에_진료건의_진료과코드가_담기고_진료과명은_서비스에서_채운다() {
        MedicalRecord record = recordWithVisitType("INITIAL");
        record.getEncounter().setDepartmentCode("01");

        MedicalRecordDto dto = mapper.toMedicalRecordDto(record);

        assertThat(dto.getDepartmentCode()).isEqualTo("01");
        assertThat(dto.getDepartmentName()).isNull();
    }

    @Test
    void 기존_데이터처럼_초진재진이_없으면_null이다() {
        MedicalRecordDto dto = mapper.toMedicalRecordDto(recordWithVisitType(null));

        assertThat(dto.getVisitType()).isNull();
    }
}
