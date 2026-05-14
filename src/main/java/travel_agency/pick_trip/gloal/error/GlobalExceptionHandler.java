package travel_agency.pick_trip.gloal.error;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e, HttpServletRequest request) {
        log.error("[{}] INTERNAL_SERVER_ERROR - {}", request.getAttribute("traceId"), e.getMessage(), e);
        return ResponseEntity.status(ErrorCode.INTERNAL_SERVER_ERROR.getStatus())
                .body(ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR, (String) request.getAttribute("traceId")));
    }
}
