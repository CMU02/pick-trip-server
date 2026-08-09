package travel_agency.pick_trip.gloal.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import travel_agency.pick_trip.gloal.error.exception.PickTripException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PickTripException.class)
    public ResponseEntity<ErrorResponse> handlePickTripException(PickTripException e, HttpServletRequest request) {
        ErrorCode code = e.getErrorCode();
        log.warn("[{}] {} - {}", request.getAttribute("traceId"), code.name(), e.getMessage());
        return ResponseEntity.status(code.getStatus())
                .body(ErrorResponse.of(code, (String) request.getAttribute("traceId")));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e, HttpServletRequest request) {
        log.warn("[{}] VALIDATION_FAILED - {}", request.getAttribute("traceId"), e.getMessage());
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.getStatus())
                .body(ErrorResponse.of(ErrorCode.VALIDATION_FAILED, (String) request.getAttribute("traceId")));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException e, HttpServletRequest request) {
        log.warn("[{}] VALIDATION_FAILED - {}", request.getAttribute("traceId"), e.getMessage());
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.getStatus())
                .body(ErrorResponse.of(ErrorCode.VALIDATION_FAILED, (String) request.getAttribute("traceId")));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException e, HttpServletRequest request) {
        log.warn("[{}] VALIDATION_FAILED - {}", request.getAttribute("traceId"), e.getMessage());
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.getStatus())
                .body(ErrorResponse.of(ErrorCode.VALIDATION_FAILED, (String) request.getAttribute("traceId")));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        log.warn("[{}] VALIDATION_FAILED - {}", request.getAttribute("traceId"), e.getMessage());
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.getStatus())
                .body(ErrorResponse.of(ErrorCode.VALIDATION_FAILED, (String) request.getAttribute("traceId")));
    }

    /**
     * 매핑되지 않은 경로 요청. 취약점 스캐너가 {@code /.env}, {@code /.git/HEAD} 같은 경로를
     * 끊임없이 긁기 때문에 500 + 스택트레이스로 처리하면 로그가 폭증한다. 서버 결함이 아니므로
     * 404 로 응답하고 한 줄만 남긴다.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(
            NoResourceFoundException e, HttpServletRequest request) {
        log.debug("[{}] RESOURCE_NOT_FOUND - {}", request.getAttribute("traceId"), e.getResourcePath());
        return ResponseEntity.status(ErrorCode.RESOURCE_NOT_FOUND.getStatus())
                .body(ErrorResponse.of(ErrorCode.RESOURCE_NOT_FOUND, (String) request.getAttribute("traceId")));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e, HttpServletRequest request) {
        log.error("[{}] INTERNAL_SERVER_ERROR - {}", request.getAttribute("traceId"), e.getMessage(), e);
        return ResponseEntity.status(ErrorCode.INTERNAL_SERVER_ERROR.getStatus())
                .body(ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR, (String) request.getAttribute("traceId")));
    }
}
