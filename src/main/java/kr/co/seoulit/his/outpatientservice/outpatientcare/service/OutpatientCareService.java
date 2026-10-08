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

    // 진료 시작 (대기중 -> 진료중). 이미 진료중이면 그대로 돌려준다
    EncounterDto startConsultation(String encounterId);

    // 진료 시작 취소 (진료중 -> 대기중). 진료기록 저장 없이 진료를 그만둘 때 쓴다
    EncounterDto cancelStartConsultation(String encounterId);

    // 접수 ID로 진료 상태 조회 (접수가 취소 전에 진료 중인지 확인할 때 서버 간 호출)
    EncounterStatusDto getEncounterStatusByReception(String receptionId);

    // 환자의 외래 진료 이력 조회 (접수 초진/재진 판정용). departmentCode, withinDays는 선택(없으면 진료과 무관/기간 제한 없음)
    VisitHistoryDto getVisitHistory(String patientId, String departmentCode, Integer withinDays);
}
