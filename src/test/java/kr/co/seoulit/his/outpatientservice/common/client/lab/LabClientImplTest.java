package kr.co.seoulit.his.outpatientservice.common.client.lab;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.seoulit.his.outpatientservice.common.exception.BusinessException;
import kr.co.seoulit.his.outpatientservice.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class LabClientImplTest {

    private static final String CANCEL_URL = "http://lab/api/lab-imaging/lab-orders/cancel";

    private MockRestServiceServer server;
    private LabClientImpl client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://lab");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new LabClientImpl(builder.build(), new ObjectMapper());
    }

    private LabOrderApiDto.LabOrderCancelRequestDto cancelRequest() {
        return new LabOrderApiDto.LabOrderCancelRequestDto("RX-1", "오처방", "DOC-1",
                List.of(new LabOrderApiDto.LabOrderCancelItemDto("CBC", "일반혈액검사", "LO-1")));
    }

    @Test
    void 취소_요청_본문은_LAB_계약의_필드명으로_전송한다() {
        server.expect(requestTo(CANCEL_URL))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(jsonPath("$.prescriptionId").value("RX-1"))
                .andExpect(jsonPath("$.cancelReason").value("오처방"))
                .andExpect(jsonPath("$.cancelledBy").value("DOC-1"))
                .andExpect(jsonPath("$.cancelledItems[0].itemCode").value("CBC"))
                .andExpect(jsonPath("$.cancelledItems[0].labOrderId").value("LO-1"))
                .andRespond(withSuccess("{\"prescriptionId\":\"RX-1\",\"code\":\"LAB118\"}", MediaType.APPLICATION_JSON));

        client.cancelOrder(cancelRequest());

        server.verify();
    }

    @Test
    void 전체_취소_응답을_결과로_변환한다() {
        server.expect(requestTo(CANCEL_URL)).andRespond(withSuccess("""
                {"prescriptionId":"RX-1","code":"LAB118","message":"요청한 검사오더가 모두 취소되었습니다.",
                 "outcome":"CANCELLED",
                 "items":[{"itemCode":"CBC","result":"CANCELLED","message":"취소되었습니다."}]}
                """, MediaType.APPLICATION_JSON));

        LabOrderApiDto.LabOrderCancelResult result = client.cancelOrder(cancelRequest());

        assertThat(result.code()).isEqualTo("LAB118");
        assertThat(result.outcome()).isEqualTo("CANCELLED");
        assertThat(result.items()).extracting(LabOrderApiDto.CancelItemResult::result).containsExactly("CANCELLED");
    }

    @Test
    void 일부취소_응답은_항목별_거절_사유를_담는다() {
        server.expect(requestTo(CANCEL_URL)).andRespond(withSuccess("""
                {"prescriptionId":"RX-1","code":"LAB119","outcome":"PARTIAL",
                 "items":[{"itemCode":"CBC","result":"CANCELLED"},
                          {"itemCode":"CRP","result":"REFUSED_PROG","message":"검체가 이미 등록되었습니다."}]}
                """, MediaType.APPLICATION_JSON));

        LabOrderApiDto.LabOrderCancelResult result = client.cancelOrder(cancelRequest());

        assertThat(result.code()).isEqualTo("LAB119");
        assertThat(result.items()).extracting(LabOrderApiDto.CancelItemResult::result)
                .containsExactly("CANCELLED", "REFUSED_PROG");
    }

    @Test
    void 오더_미접수_404는_예외가_아니라_LAB117_결과로_돌려준다() {
        server.expect(requestTo(CANCEL_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"code\":\"LAB117\",\"message\":\"해당 처방의 오더가 아직 접수되지 않았습니다.\"}"));

        LabOrderApiDto.LabOrderCancelResult result = client.cancelOrder(cancelRequest());

        assertThat(result.code()).isEqualTo("LAB117");
    }

    @Test
    void 본문_없는_404나_서버오류는_EXTERNAL_API_ERROR로_변환한다() {
        server.expect(requestTo(CANCEL_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.cancelOrder(cancelRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.EXTERNAL_API_ERROR);

        server.reset();
        server.expect(requestTo(CANCEL_URL)).andRespond(withServerError());

        assertThatThrownBy(() -> client.cancelOrder(cancelRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.EXTERNAL_API_ERROR);
    }
}
