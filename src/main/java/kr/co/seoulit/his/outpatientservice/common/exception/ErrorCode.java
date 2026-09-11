package kr.co.seoulit.his.outpatientservice.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    INVALID_INPUT("OPD001", "Invalid input.", HttpStatus.BAD_REQUEST), // 입력값이 올바르지 않습니다.
    UNAUTHORIZED("OPD002", "Login required.", HttpStatus.UNAUTHORIZED), // 로그인이 필요합니다.
    FORBIDDEN("OPD003", "Access denied.", HttpStatus.FORBIDDEN), // 접근 권한이 없습니다.
    NOT_FOUND("OPD004", "The requested information could not be found.", HttpStatus.NOT_FOUND), // 요청한 정보를 찾을 수 없습니다.
    CONFLICT("OPD005", "This request has already been processed.", HttpStatus.CONFLICT), // 이미 처리된 요청입니다.
    EXTERNAL_API_ERROR("OPD006", "Failed to call an external service.", HttpStatus.BAD_GATEWAY), // 외부 서비스 호출에 실패했습니다.
    INTERNAL_ERROR("OPD999", "A system error occurred.", HttpStatus.INTERNAL_SERVER_ERROR); // 시스템 오류가 발생했습니다.

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
