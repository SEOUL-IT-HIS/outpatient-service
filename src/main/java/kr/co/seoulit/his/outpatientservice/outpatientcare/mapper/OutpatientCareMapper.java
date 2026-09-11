package kr.co.seoulit.his.outpatientservice.outpatientcare.mapper;

import kr.co.seoulit.his.outpatientservice.outpatientcare.dto.EncounterDto;
import kr.co.seoulit.his.outpatientservice.outpatientcare.dto.MedicalRecordDto;
import kr.co.seoulit.his.outpatientservice.outpatientcare.entity.Encounter;
import kr.co.seoulit.his.outpatientservice.outpatientcare.entity.MedicalRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface OutpatientCareMapper {

    // 진료기록 차트 변환
    @Mapping(target = "encounterId", source = "encounter.encounterId")
    @Mapping(target = "patientId", source = "encounter.patientId")
    @Mapping(target = "doctorName", ignore = true)
    @Mapping(target = "departmentName", ignore = true)
    @Mapping(target = "patientName", ignore = true)
    MedicalRecordDto toMedicalRecordDto(MedicalRecord entity);

    List<MedicalRecordDto> toRecordDtoList(List<MedicalRecord> entityList);

    // 당일 환자 목록 변환 (환자명은 PAT 조회, 진료과명은 ADM 공통코드 조회 후 서비스 레이어에서 채움)
    @Mapping(target = "patientName", ignore = true)
    @Mapping(target = "departmentName", ignore = true)
    EncounterDto toEncounterDto(Encounter entity);

    List<EncounterDto> toEncounterDtoList(List<Encounter> entityList);
}