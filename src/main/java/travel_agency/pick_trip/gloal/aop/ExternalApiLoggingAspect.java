package travel_agency.pick_trip.gloal.aop;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Aspect
@Component
public class ExternalApiLoggingAspect {

    @Around("execution(* travel_agency.pick_trip.domain.content.adapter.TourApiContentAdapter.*(..))")
    public Object logTourApiCall(ProceedingJoinPoint pjp) throws Throwable {
        String method = pjp.getSignature().getName();
        String traceId = resolveTraceId();
        long start = System.currentTimeMillis();
        try {
            Object result = pjp.proceed();
            log.info("[{}] TourAPI {} completed in {}ms", traceId, method, System.currentTimeMillis() - start);
            return result;
        } catch (Exception e) {
            log.warn("[{}] TourAPI {} failed after {}ms - {}", traceId, method, System.currentTimeMillis() - start, e.getMessage());
            throw e;
        }
    }

    private String resolveTraceId() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            HttpServletRequest request = attrs.getRequest();
            Object traceId = request.getAttribute("traceId");
            return traceId != null ? traceId.toString() : "no-trace";
        } catch (IllegalStateException e) {
            // 요청 컨텍스트 밖에서 호출된 경우 (테스트 등)
            return "no-trace";
        }
    }
}
