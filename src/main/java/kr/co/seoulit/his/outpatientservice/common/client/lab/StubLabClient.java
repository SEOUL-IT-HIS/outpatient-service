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
    public LabOrderApiDto.LabOrderCancelResult cancelOrder(LabOrderApiDto.LabOrderCancelRequestDto request) {
        List<LabOrderApiDto.CancelItemResult> items = request.cancelledItems().stream()
                .map(item -> new LabOrderApiDto.CancelItemResult(item.itemCode(), "CANCELLED", "STUB 취소"))
                .toList();
        return new LabOrderApiDto.LabOrderCancelResult(
                request.prescriptionId(), "LAB118", "STUB OK", "CANCELLED", items);
    }

    @Override
    public List<LabOrderApiDto.LabItem> searchLabItem(String name) {
        LabOrderApiDto.LabItem sample = new LabOrderApiDto.LabItem(
                "LAB001",
                "CBC(일반혈액검사)",
                "GENERAL",
                List.of("혈액")
        );
        return List.of(sample);
    }
}