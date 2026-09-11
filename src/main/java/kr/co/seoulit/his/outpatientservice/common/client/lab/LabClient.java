package kr.co.seoulit.his.outpatientservice.common.client.lab;

public interface LabClient {
    LabOrderApiDto.LabOrderResult sendOrder(LabOrderApiDto.LabOrderCreateRequestDto request);
}