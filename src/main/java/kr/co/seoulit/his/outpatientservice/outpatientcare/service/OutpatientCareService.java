package kr.co.seoulit.his.outpatientservice.outpatientcare.service;

import kr.co.seoulit.his.outpatientservice.outpatientcare.dto.*;

import java.util.List;

public interface OutpatientCareService {

    //당일 외래 진료/환자 목록 조회
    List<EncounterDto> getEncounters(EncounterSearchDto request);

    //RCP 대기 환자를 담당의/진료과에 배정해서 OPD 진료(Encounter)로 등록
    EncounterDto createEncounter(EncounterCreateDto request);

    //진료 ID(encounterId) 기준 진료 기록 목록 조회
    List<MedicalRecordDto> getRecords(String keyword);

    //진료기록 ID(recordId) 기준 단건 상세 조회
    MedicalRecordDto getRecord(String recordId);

    //진료기록 등록
    MedicalRecordDto createRecord(MedicalRecordCreateDto request);

    //진료기록 수정
    MedicalRecordDto updateRecord(String recordId, MedicalRecordCreateDto request);

    //진료기록 비활성화
    void deactivateRecord(String recordId, String userId);
}