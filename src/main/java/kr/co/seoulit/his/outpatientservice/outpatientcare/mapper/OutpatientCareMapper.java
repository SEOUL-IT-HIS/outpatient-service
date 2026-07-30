package kr.co.seoulit.his.outpatientservice.outpatientcare.mapper;

import kr.co.seoulit.his.outpatientservice.outpatientcare.dto.EncounterDto;
import kr.co.seoulit.his.outpatientservice.outpatientcare.dto.MedicalRecordDto;
import kr.co.seoulit.his.outpatientservice.outpatientcare.dto.ReceptionDto;
import kr.co.seoulit.his.outpatientservice.outpatientcare.entity.Encounter;
import kr.co.seoulit.his.outpatientservice.outpatientcare.entity.MedicalRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * MapStruct Mapper — Encounter / MedicalRecord / Reception ↔ DTO 변환
 * RCP는 GET /receptions/waiting(대기 환자)만 제공하고, OUTPATIENT_ENCOUNTER는 OPD가 담당의/진료과 배정 및
 * 진료상태를 관리하는 별도 장부라 지금은 RCP 대기 목록만 EncounterDto로 변환해서 쓴다(2026-07-29 확인).
 */
@Mapper(componentModel = "spring")
public interface OutpatientCareMapper {

    // --- Encounter (진료 Entity) 변환 - 현재는 getEncounters()에서 미사용, 추후 OPD 배정 기능에서 사용 예정 ---
    // receptionId/doctorId/status/visitDate/createdAt은 실제 컬럼명과 DTO 필드명이 같아 MapStruct가 자동 매핑한다
    @Mapping(target = "encounterId", source = "id")
    @Mapping(target = "departmentCode", source = "departmentId")
    // patientNo/patientName은 타 서비스(PAT) 소유 데이터라 값 복제 금지(docs/conventions.md 5장) — 항상 PAT 조회로 채운다
    @Mapping(target = "patientNo", ignore = true)
    @Mapping(target = "patientName", ignore = true)
    EncounterDto toEncounterDto(Encounter entity);

    List<EncounterDto> toEncounterDtoList(List<Encounter> entityList);

    // --- ReceptionDto (RCP 대기환자) 변환 ---
    // 아직 OPD에서 담당의/진료과를 배정하기 전이라 encounterId/doctorId/visitDate/createdAt은 존재하지 않는다.
    // (예전엔 receptionId를 encounterId 자리에 그대로 넣는 버그가 있었음 — 지금은 그냥 비워둔다)
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
    // chiefComplaint/status/createdAt/updatedAt은 이름이 같아 자동 매핑된다
    @Mapping(target = "recordId", source = "id")
    @Mapping(target = "encounterId", source = "encounter.id")
    @Mapping(target = "patientId", source = "encounter.patientId") // 실제 테이블에 PATIENT_ID가 없어 encounter 조인으로 채움
    // patientNo/patientName은 타 서비스(PAT) 소유 데이터라 값 복제 금지(docs/conventions.md 5장) — 서비스에서 PAT 조회로 채운다
    @Mapping(target = "patientNo", ignore = true)
    @Mapping(target = "patientName", ignore = true)
    MedicalRecordDto toMedicalRecordDto(MedicalRecord entity);

    List<MedicalRecordDto> toRecordDtoList(List<MedicalRecord> entityList);
}
