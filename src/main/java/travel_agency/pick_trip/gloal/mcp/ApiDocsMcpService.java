package travel_agency.pick_trip.gloal.mcp;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import travel_agency.pick_trip.gloal.error.ErrorCode;

/**
 * 프론트 팀원의 AI 클라이언트에 PickTrip REST API 문서를 제공하는 read-only MCP 도구 모음.
 *
 * <p>스펙은 Spring MVC 핵심 빈 {@link RequestMappingHandlerMapping}을 introspection 하고
 * 핸들러 시그니처를 리플렉션해 구성한다(springdoc 내부 API 비의존 → Boot 4에서 안정적).
 * 조회 전용이며 서버 엔드포인트를 호출하거나 상태를 변경하지 않는다.
 */
@Service
public class ApiDocsMcpService {

    private final RequestMappingHandlerMapping handlerMapping;
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    public ApiDocsMcpService(
            @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping) {
        this.handlerMapping = handlerMapping;
    }

    // --- MCP 도구 ---

    @Tool(description = "PickTrip 백엔드의 모든 REST 엔드포인트 목록(HTTP 메서드, 경로, 핸들러)을 반환한다.")
    public List<EndpointInfo> listEndpoints() {
        return allDetails().stream()
                .map(d -> new EndpointInfo(d.method(), d.path(), d.handler()))
                .sorted(Comparator.comparing(EndpointInfo::path).thenComparing(EndpointInfo::method))
                .toList();
    }

    @Tool(description = "특정 엔드포인트의 상세(파라미터, 요청/응답 타입과 필드)를 반환한다. 경로는 listEndpoints 의 path 를 그대로 전달한다.")
    public List<EndpointDetail> getEndpoint(
            @ToolParam(description = "HTTP 메서드 (예: GET, POST)") String httpMethod,
            @ToolParam(description = "엔드포인트 경로 (예: /api/v1/contents)") String path) {
        String m = httpMethod == null ? "" : httpMethod.trim().toUpperCase(Locale.ROOT);
        String p = path == null ? "" : path.trim();
        return allDetails().stream()
                .filter(d -> d.path().equals(p) && (m.isEmpty() || d.method().equals(m)))
                .toList();
    }

    @Tool(description = "키워드로 엔드포인트를 검색한다(경로 또는 핸들러명 부분 일치, 대소문자 무시).")
    public List<EndpointInfo> searchEndpoints(@ToolParam(description = "검색 키워드") String keyword) {
        String k = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        return listEndpoints().stream()
                .filter(e -> e.path().toLowerCase(Locale.ROOT).contains(k)
                        || e.handler().toLowerCase(Locale.ROOT).contains(k))
                .toList();
    }

    @Tool(description = "모든 API 예외에 공통으로 적용되는 에러 응답 계약(code/message/traceId)과 전체 에러 코드 목록을 반환한다.")
    public ErrorContract getErrorContract() {
        List<ErrorCodeInfo> codes = new ArrayList<>();
        for (ErrorCode code : ErrorCode.values()) {
            codes.add(new ErrorCodeInfo(
                    code.name(),
                    code.getStatus().value() + " " + code.getStatus().name(),
                    code.getMessage()));
        }
        String format = "{ \"code\": \"DOMAIN_ERROR_TYPE\", \"message\": \"사용자에게 표시할 한국어 메시지\", \"traceId\": \"요청 추적 ID\" }";
        return new ErrorContract(format, codes);
    }

    // --- introspection ---

    private List<EndpointDetail> allDetails() {
        List<EndpointDetail> out = new ArrayList<>();
        Map<RequestMappingInfo, HandlerMethod> handlers = handlerMapping.getHandlerMethods();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlers.entrySet()) {
            RequestMappingInfo info = entry.getKey();
            HandlerMethod handler = entry.getValue();
            Set<String> patterns = patternsOf(info);
            List<String> methods = httpMethodsOf(info);
            for (String pattern : patterns) {
                for (String method : methods) {
                    out.add(buildDetail(method, pattern, handler));
                }
            }
        }
        return out;
    }

    private Set<String> patternsOf(RequestMappingInfo info) {
        if (info.getPathPatternsCondition() != null) {
            return info.getPathPatternsCondition().getPatternValues();
        }
        if (info.getPatternsCondition() != null) {
            return info.getPatternsCondition().getPatterns();
        }
        return Set.of();
    }

    private List<String> httpMethodsOf(RequestMappingInfo info) {
        List<String> methods = info.getMethodsCondition().getMethods().stream()
                .map(Enum::name)
                .toList();
        return methods.isEmpty() ? List.of("ANY") : methods;
    }

    private EndpointDetail buildDetail(String method, String path, HandlerMethod handler) {
        List<ParamInfo> params = new ArrayList<>();
        List<FieldInfo> requestBody = new ArrayList<>();
        for (MethodParameter parameter : handler.getMethodParameters()) {
            parameter.initParameterNameDiscovery(parameterNameDiscoverer);
            String in = paramIn(parameter);
            Class<?> type = parameter.getParameterType();
            String name = parameter.getParameterName();
            params.add(new ParamInfo(name != null ? name : "arg" + parameter.getParameterIndex(),
                    type.getSimpleName(), in));
            if ("BODY".equals(in)) {
                requestBody.addAll(fieldsOf(type));
            }
        }
        Class<?> responseType = responseTypeOf(handler);
        return new EndpointDetail(
                method,
                path,
                handler.getBeanType().getSimpleName() + "#" + handler.getMethod().getName(),
                params,
                requestBody,
                responseType != null ? responseType.getSimpleName() : "void",
                fieldsOf(responseType));
    }

    private String paramIn(MethodParameter parameter) {
        if (parameter.hasParameterAnnotation(PathVariable.class)) {
            return "PATH";
        }
        if (parameter.hasParameterAnnotation(RequestParam.class)
                || parameter.hasParameterAnnotation(ModelAttribute.class)) {
            return "QUERY";
        }
        if (parameter.hasParameterAnnotation(RequestBody.class)) {
            return "BODY";
        }
        return "OTHER";
    }

    /** ResponseEntity&lt;T&gt; / Optional&lt;T&gt; 는 한 겹 벗겨 내부 타입을 반환한다. */
    private Class<?> responseTypeOf(HandlerMethod handler) {
        MethodParameter returnType = handler.getReturnType();
        Class<?> raw = returnType.getParameterType();
        if (ResponseEntity.class.isAssignableFrom(raw) || Optional.class.isAssignableFrom(raw)) {
            Type generic = returnType.getGenericParameterType();
            if (generic instanceof ParameterizedType pt && pt.getActualTypeArguments().length > 0) {
                Type arg = pt.getActualTypeArguments()[0];
                if (arg instanceof Class<?> c) {
                    return c;
                }
                if (arg instanceof ParameterizedType inner && inner.getRawType() instanceof Class<?> c) {
                    return c;
                }
            }
        }
        return raw;
    }

    /** record 면 컴포넌트(필드)를 한 단계 펼친다. record 가 아니면 빈 목록. */
    private List<FieldInfo> fieldsOf(Class<?> type) {
        if (type == null || !type.isRecord()) {
            return List.of();
        }
        List<FieldInfo> fields = new ArrayList<>();
        for (RecordComponent rc : type.getRecordComponents()) {
            fields.add(new FieldInfo(rc.getName(), rc.getType().getSimpleName()));
        }
        return fields;
    }

    // --- 도구 출력 모델 ---

    public record EndpointInfo(String method, String path, String handler) {}

    public record ParamInfo(String name, String type, String in) {}

    public record FieldInfo(String name, String type) {}

    public record EndpointDetail(
            String method,
            String path,
            String handler,
            List<ParamInfo> parameters,
            List<FieldInfo> requestBody,
            String responseType,
            List<FieldInfo> responseFields) {}

    public record ErrorCodeInfo(String code, String status, String message) {}

    public record ErrorContract(String format, List<ErrorCodeInfo> codes) {}
}
