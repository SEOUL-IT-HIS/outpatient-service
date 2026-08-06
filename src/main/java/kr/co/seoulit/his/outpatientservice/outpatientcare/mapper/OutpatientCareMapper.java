package kr.co.seoulit.his.outpatientservice.outpatientcare.mapper;

import kr.co.seoulit.his.outpatientservice.outpatientcare.dto.EncounterDto;
import kr.co.seoulit.his.outpatientservice.outpatientcare.dto.MedicalRecordDto;
import kr.co.seoulit.his.outpatientservice.outpatientcare.dto.ReceptionDto;
import kr.co.seoulit.his.outpatientservice.outpatientcare.entity.Encounter;
import kr.co.seoulit.his.outpatientservice.outpatientcare.entity.MedicalRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface OutpatientCareMapper {

    // --- Encounter (진료 Entity) 변환 ---
    @Mapping(target = "encounterId", source = "id")
    @Mapping(target = "departmentCode", source = "departmentId")
    @Mapping(target = "patientNo", ignore = true)
    @Mapping(target = "patientName", ignore = true)
    EncounterDto toEncounterDto(Encounter entity);

    List<EncounterDto> toEncounterDtoList(List<Encounter> entityList);

    // --- ReceptionDto (RCP 대기환자) 변환 ---
    @Mapping(target = "encounterId", ignore = true)
    @Mapping(target = "departmentCode", source = "deptCode")
    @Mapping(target = "doctorId", ignore = true)
    @Mapping(target = "visitDate", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "patientNo", ignore = true)
    @Mapping(target = "patientName", ignore = true)
    EncounterDto toEncounterDto(ReceptionDto receptionDto);

    List<EncounterDto> toEncounterDtoListFromReception(List<ReceptionDto> receptionList);

    // --- MedicalRecord (진료기록 차트) 변환 ---
    @Mapping(target = "recordId", source = "id")
    @Mapping(target = "encounterId", source = "encounter.id")
    @Mapping(target = "patientId", source = "encounter.patientId")
    // doctorId는 엔티티와 DTO의 필드명이 동일하므로 MapStruct가 자동 매핑함
    @Mapping(target = "doctorName", ignore = true)     // 서비스나 SQL에서 별도 세팅
    @Mapping(target = "departmentName", ignore = true) // 서비스나 SQL에서 별도 세팅
    @Mapping(target = "patientNo", ignore = true)
    @Mapping(target = "patientName", ignore = true)
    MedicalRecordDto toMedicalRecordDto(MedicalRecord entity);

    List<MedicalRecordDto> toRecordDtoList(List<MedicalRecord> entityList);
}