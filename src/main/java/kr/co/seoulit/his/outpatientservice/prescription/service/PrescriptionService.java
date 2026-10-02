package kr.co.seoulit.his.outpatientservice.prescription.service;

import kr.co.seoulit.his.outpatientservice.common.client.lab.LabOrderApiDto;
import kr.co.seoulit.his.outpatientservice.common.client.lab.LabResultEventDto;
import kr.co.seoulit.his.outpatientservice.common.client.pharmacy.PharmacyApiDto;
import kr.co.seoulit.his.outpatientservice.prescription.dto.PrescriptionCreateDto;
import kr.co.seoulit.his.outpatientservice.prescription.dto.PrescriptionDto;
import kr.co.seoulit.his.outpatientservice.prescription.dto.PrescriptionItemDto;

import java.util.List;

public interface PrescriptionService {
        // 처방 목록 조회 (receptionId 지정 시 해당 접수건의 처방만 — 응급이 orderId 선택/취소 대상 선택에 사용)
        List<PrescriptionDto> getPrescriptions(String keyword, String receptionId);

        // 처방 상세 조회
        PrescriptionDto getPrescription(String prescriptionId);

        // 처방 등록
        PrescriptionDto createPrescription(String encounterId, PrescriptionCreateDto request);

        // 입원(admission) 처방 등록 — 병동 서비스가 서버 간 호출로 등록
        PrescriptionDto createPrescriptionForAdmission(String admissionId, PrescriptionCreateDto request);

        // 응급(emergency) 처방 등록 — 응급 서비스가 서버 간 호출로 등록
        PrescriptionDto createPrescriptionForEmergency(String receptionId, PrescriptionCreateDto request);

        // 구두처방 확정 — verbalYn=Y인 처방에 확정일시/확정자를 기록
        PrescriptionDto confirmVerbalOrder(String prescriptionId, String confirmedBy);

        // 처방 중 검사(Lab) 항목만 모아서 검사실로 한 번에 전송하고 전송상태 갱신
        List<PrescriptionItemDto> dispatchLabOrders(String prescriptionId);

        // 검사서비스가 Kafka로 보낸 검사오더 결과(수락/거절)를 PENDING 상태인 항목에 반영
        void applyLabOrderResult(String prescriptionId, String status, String labOrderId, String rejectReason);

        // 약제서비스로 전송
        void dispatchPharmacyOrders(String prescriptionId);

        // 약품 검색 (약제서비스 검색 조회)
        List<PharmacyApiDto.Medication> searchMedication(String name);

        // 검사 항목 검색 (검사서비스 카탈로그 조회)
        List<LabOrderApiDto.LabItem> searchLabItem(String name);

        //처방 비활성화
        void deactivatePrescription(String prescriptionId, String cancelReason, String userId);

        //검사결과조회
        void applyLabResult(String eventId, LabResultEventDto.ResultData data, LabResultEventDto.ResultItem item);
}
