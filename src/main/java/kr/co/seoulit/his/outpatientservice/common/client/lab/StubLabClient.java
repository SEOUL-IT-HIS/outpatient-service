package kr.co.seoulit.his.outpatientservice.common.client.lab;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

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

    @Override
    public List<LabOrderApiDto.LabItem> searchLabItem(String name) {
        LabOrderApiDto.LabItem sample = new LabOrderApiDto.LabItem(
                "LAB001",
                "CBC(일반혈액검사)",
                "혈액",
                "12"
        );
        return List.of(sample);
    }
}