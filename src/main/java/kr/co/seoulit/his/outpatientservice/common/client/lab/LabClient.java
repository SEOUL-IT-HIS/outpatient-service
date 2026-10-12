package kr.co.seoulit.his.outpatientservice.common.client.lab;

import java.util.List;

public interface LabClient {
    LabOrderApiDto.LabOrderResult sendOrder(LabOrderApiDto.LabOrderCreateRequestDto request);

    // 검사오더 취소 (POST /api/lab-imaging/lab-orders/cancel). 오더 미접수(404, LAB117)는 예외가 아니라 결과로 돌려준다.
    LabOrderApiDto.LabOrderCancelResult cancelOrder(LabOrderApiDto.LabOrderCancelRequestDto request);

    // 검사 항목 카탈로그 검색 (약품의 searchMedication에 대응)
    List<LabOrderApiDto.LabItem> searchLabItem(String name);
}