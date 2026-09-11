package kr.co.seoulit.his.outpatientservice.common.client.lab;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * LAB 미기동 로컬용 stub. app.services.lab.stub-enabled=true 일 때만 활성.
 */
@Component
@ConditionalOnProperty(name = "app.services.lab.stub-enabled", havingValue = "true")
public class StubLabClient implements LabClient {

    @Override
    public LabOrderApiDto.LabOrderResult sendOrder(LabOrderApiDto.LabOrderCreateRequestDto request) {
        return new LabOrderApiDto.LabOrderResult("SUCCESS", "STUB OK", "STUB-LAB-ORDER-" + request.prescriptionId());
    }
}