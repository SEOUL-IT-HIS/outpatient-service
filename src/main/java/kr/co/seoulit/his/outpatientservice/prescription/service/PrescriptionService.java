package kr.co.seoulit.his.outpatientservice.prescription.service;

import kr.co.seoulit.his.outpatientservice.common.client.pharmacy.PharmacyApiDto;
import kr.co.seoulit.his.outpatientservice.prescription.dto.PrescriptionCreateDto;
import kr.co.seoulit.his.outpatientservice.prescription.dto.PrescriptionDto;
import kr.co.seoulit.his.outpatientservice.prescription.dto.PrescriptionItemDto;

import java.util.List;

public interface PrescriptionService {
        // 처방 목록 조회
        List<PrescriptionDto> getPrescriptions(String keyword);

        // 처방 상세 조회
        PrescriptionDto getPrescription(String prescriptionId);

        // 처방 등록
        PrescriptionDto createPrescription(String encounterId, PrescriptionCreateDto request);

        // 처방 중 검사(Lab) 항목만 모아서 검사실로 한 번에 전송하고 전송상태 갱신
        List<PrescriptionItemDto> dispatchLabOrders(String prescriptionId);

        // 검사서비스가 Kafka로 보낸 검사오더 결과(수락/거절)를 PENDING 상태인 항목에 반영
        void applyLabOrderResult(String prescriptionId, String status, String labOrderId, String rejectReason);

        // 약제서비스로 전송
        void dispatchPharmacyOrders(String prescriptionId);

        // 약품 검색 (약제서비스 검색 조회)
        List<PharmacyApiDto.Medication> searchMedication(String name);
}
