package kr.co.seoulit.his.outpatientservice.common.client.pharmacy;

import kr.co.seoulit.his.outpatientservice.common.exception.BusinessException;
import kr.co.seoulit.his.outpatientservice.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PharmacyClientImplTest {

    private MockRestServiceServer server;
    private PharmacyClientImpl client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://phm");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new PharmacyClientImpl(builder.build());
    }

    @Test
    void 약제서비스가_오류를_응답하면_500이_아니라_EXTERNAL_API_ERROR로_변환한다() {
        server.expect(requestTo("http://phm/api/pharmacy/medications?name=tylenol"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.searchMedication("tylenol"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.EXTERNAL_API_ERROR);
    }

    @Test
    void 정상_응답이면_약품_목록을_반환한다() {
        server.expect(requestTo("http://phm/api/pharmacy/medications?name=A"))
                .andRespond(withSuccess("{\"code\":200,\"message\":\"OK\",\"data\":[]}", MediaType.APPLICATION_JSON));

        List<PharmacyApiDto.Medication> result = client.searchMedication("A");

        assertThat(result).isEmpty();
        server.verify();
    }

    @Test
    void 약품_목록은_이름이_없으면_name_없이_page와_size만_보내고_페이지_정보를_반환한다() {
        server.expect(requestTo("http://phm/api/pharmacy/medications/page?page=0&size=100"))
                .andRespond(withSuccess(
                        "{\"code\":200,\"message\":\"OK\",\"data\":{\"content\":[{\"medicationId\":1,\"medicationName\":\"모빅캡슐\","
                                + "\"dosageFormCd\":\"01\",\"ediCode\":\"653500890\"}],"
                                + "\"totalElements\":109,\"totalPages\":2,\"number\":0,\"size\":100,\"first\":true,\"last\":false,"
                                + "\"pageable\":{\"offset\":0},\"sort\":{\"empty\":true}}}",
                        MediaType.APPLICATION_JSON));

        PharmacyApiDto.MedicationPage result = client.listMedications(null, 0, 100);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).ediCode()).isEqualTo("653500890");
        assertThat(result.totalElements()).isEqualTo(109);
        assertThat(result.last()).isFalse(); // 모르는 필드(pageable, sort)가 있어도 역직렬화에 실패하지 않는다
        server.verify();
    }

    @Test
    void 약품_목록은_이름이_있으면_name을_함께_보낸다() {
        server.expect(requestTo("http://phm/api/pharmacy/medications/page?name=mobic&page=1&size=20"))
                .andRespond(withSuccess(
                        "{\"code\":200,\"message\":\"OK\",\"data\":{\"content\":[],\"totalElements\":0,\"totalPages\":0,"
                                + "\"number\":1,\"size\":20,\"first\":false,\"last\":true}}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.listMedications("mobic", 1, 20).content()).isEmpty();
        server.verify();
    }

    @Test
    void 약품_목록_조회가_실패하면_EXTERNAL_API_ERROR로_변환한다() {
        server.expect(requestTo("http://phm/api/pharmacy/medications/page?page=0&size=100"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.listMedications(null, 0, 100))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.EXTERNAL_API_ERROR);
    }

    @Test
    void 약품의_투약형태코드를_그대로_전달한다() {
        server.expect(requestTo("http://phm/api/pharmacy/medications?name=ketorolac"))
                .andRespond(withSuccess(
                        "{\"code\":200,\"message\":\"OK\",\"data\":[{\"medicationId\":1,\"medicationName\":\"케토로락주 30mg\","
                                + "\"dosageFormCd\":\"03\",\"ediCode\":\"ER-KETO-30\"}]}",
                        MediaType.APPLICATION_JSON));

        List<PharmacyApiDto.Medication> result = client.searchMedication("ketorolac");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).dosageFormCd()).isEqualTo("03"); // 프론트가 제형별로 약을 걸러내는 데 사용
        server.verify();
    }
}
