# TraceId 규칙

요청 단위 `traceId`는 **MDC(Mapped Diagnostic Context) 수동 설정** 방식을 사용한다.
`OncePerRequestFilter`를 구현해 요청 진입 시 UUID를 생성하고 MDC와 요청 attribute에 저장한다.

## 구현

```java
public class TraceIdFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_KEY = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = UUID.randomUUID().toString();
        MDC.put(TRACE_ID_KEY, traceId);
        request.setAttribute(TRACE_ID_KEY, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
```

## 규칙

- `traceId`는 에러 응답 본문에 포함하고 로그에도 함께 출력한다.
- 개인정보, 토큰, 외부 API key는 MDC에 저장하지 않는다.
- `TraceIdFilter`는 `global/filter` 패키지에 위치시키고 Spring Security 필터 체인보다 앞에 등록한다.
