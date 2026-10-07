package kr.co.seoulit.his.outpatientservice.common.client.pharmacy;

import java.util.List;

public interface PharmacyClient {
    List<PharmacyApiDto.Medication> searchMedication(String name);

    // 약품 목록 (이름 필터 선택, 이름순, 페이지 단위 — 약제는 한 번에 최대 100건)
    PharmacyApiDto.MedicationPage listMedications(String name, int page, int size);
}
