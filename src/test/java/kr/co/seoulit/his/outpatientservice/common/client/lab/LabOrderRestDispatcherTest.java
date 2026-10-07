package kr.co.seoulit.his.outpatientservice.common.client.lab;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LabOrderRestDispatcherTest {

    @Mock
    private LabClient labClient;

    private LabOrderRestDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new LabOrderRestDispatcher(labClient);
    }

    private LabOrderApiDto.LabOrderCancelRequestDto cancelRequest() {
        return new LabOrderApiDto.LabOrderCancelRequestDto("RX-1", "오처방", "DOC-1",
                List.of(new LabOrderApiDto.LabOrderCancelItemDto("CBC", "일반혈액검사", "LO-1")));
    }

    private LabOrderApiDto.LabOrderCancelResult result(String code) {
        return new LabOrderApiDto.LabOrderCancelResult("RX-1", code, "msg", null, List.of());
    }

    @Test
    void 전체취소_일부취소_전부거절은_LAB이_판정을_마친_것이라_true다() {
        when(labClient.cancelOrder(any()))
                .thenReturn(result("LAB118"), result("LAB119"), result("LAB120"));

        assertThat(dispatcher.cancel(cancelRequest())).isTrue();
        assertThat(dispatcher.cancel(cancelRequest())).isTrue();
        assertThat(dispatcher.cancel(cancelRequest())).isTrue();
    }

    @Test
    void 오더_미접수_LAB117은_false다() {
        when(labClient.cancelOrder(any())).thenReturn(result("LAB117"));

        assertThat(dispatcher.cancel(cancelRequest())).isFalse();
    }

    @Test
    void 알_수_없는_코드나_빈_응답은_false다() {
        when(labClient.cancelOrder(any())).thenReturn(result("LAB999"), (LabOrderApiDto.LabOrderCancelResult) null);

        assertThat(dispatcher.cancel(cancelRequest())).isFalse();
        assertThat(dispatcher.cancel(cancelRequest())).isFalse();
    }

    @Test
    void 통신_오류로_예외가_나도_던지지_않고_false다() {
        when(labClient.cancelOrder(any())).thenThrow(new RuntimeException("lab down"));

        assertThat(dispatcher.cancel(cancelRequest())).isFalse();
    }
}
