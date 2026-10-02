package kr.co.seoulit.his.outpatientservice.common.client.lab;

import java.util.List;

public interface LabClient {
    LabOrderApiDto.LabOrderResult sendOrder(LabOrderApiDto.LabOrderCreateRequestDto request);

    // 검사 항목 카탈로그 검색 (약품의 searchMedication에 대응)
    List<LabOrderApiDto.LabItem> searchLabItem(String name);
}