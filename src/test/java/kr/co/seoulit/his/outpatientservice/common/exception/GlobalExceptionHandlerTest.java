package kr.co.seoulit.his.outpatientservice.common.exception;

import kr.co.seoulit.his.outpatientservice.common.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void 존재하지_않는_주소는_500이_아니라_404로_응답한다() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleNotFound(new NoResourceFoundException(HttpMethod.GET, "api/outpatient/nothing"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getCode()).isEqualTo("OPD004");
    }

    @Test
    void 필수_요청_파라미터가_없으면_500이_아니라_400으로_응답한다() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleBadRequest(new MissingServletRequestParameterException("patientId", "String"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getCode()).isEqualTo("OPD001");
    }

    @Test
    void 예상하지_못한_예외는_그대로_500으로_응답한다() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleException(new IllegalStateException("boom"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getCode()).isEqualTo("OPD999");
    }
}
