package kr.co.seoulit.his.outpatientservice.outpatientcare.service;

import kr.co.seoulit.his.outpatientservice.common.client.reception.ReceptionEventDto;
import kr.co.seoulit.his.outpatientservice.outpatientcare.dto.*;

import java.util.List;

public interface OutpatientCareService {

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

    // 접수(RCP) 이벤트 수신 시 Encounter 생성/갱신
    void registerEncounter(ReceptionEventDto.ReceptionData data);

    // 당일 외래 환자 목록 조회
    List<EncounterDto> getTodayEncounters();

    // 환자의 외래 진료 이력 조회 (접수 초진/재진 판정용). departmentCode, withinDays는 선택(없으면 진료과 무관/기간 제한 없음)
    VisitHistoryDto getVisitHistory(String patientId, String departmentCode, Integer withinDays);
}
