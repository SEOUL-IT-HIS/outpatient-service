package kr.co.seoulit.his.outpatientservice.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    INVALID_INPUT("OPD001", "입력값이 올바르지 않습니다.", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED("OPD002", "로그인이 필요합니다.", HttpStatus.UNAUTHORIZED),
    FORBIDDEN("OPD003", "접근 권한이 없습니다.", HttpStatus.FORBIDDEN),
    NOT_FOUND("OPD004", "요청한 정보를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    CONFLICT("OPD005", "이미 처리된 요청입니다.", HttpStatus.CONFLICT),
    EXTERNAL_API_ERROR("OPD006", "외부 서비스 호출에 실패했습니다.", HttpStatus.BAD_GATEWAY),
    INTERNAL_ERROR("OPD999", "시스템 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
