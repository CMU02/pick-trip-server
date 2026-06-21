package travel_agency.pick_trip.gloal.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.lenient;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPatternParser;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApiDocsMcpService")
class ApiDocsMcpServiceTest {

    @Mock private RequestMappingHandlerMapping handlerMapping;

    private ApiDocsMcpService service;

    // --- 테스트 픽스처 ---

    record SampleRequest(String title, int count) {}

    record SampleResponse(String id, String name) {}

    static class SampleController {
        public ResponseEntity<SampleResponse> getOne(@PathVariable String id,
                                                     @RequestParam(required = false) String q) {
            return null;
        }

        public ResponseEntity<Void> create(@RequestBody SampleRequest body) {
            return null;
        }
    }

    private RequestMappingInfo info(String path, RequestMethod method) {
        RequestMappingInfo.BuilderConfiguration config = new RequestMappingInfo.BuilderConfiguration();
        config.setPatternParser(new PathPatternParser());
        return RequestMappingInfo.paths(path).methods(method).options(config).build();
    }

    @BeforeEach
    void setUp() throws NoSuchMethodException {
        SampleController controller = new SampleController();
        HandlerMethod getOne = new HandlerMethod(controller,
                SampleController.class.getMethod("getOne", String.class, String.class));
        HandlerMethod create = new HandlerMethod(controller,
                SampleController.class.getMethod("create", SampleRequest.class));

        lenient().when(handlerMapping.getHandlerMethods()).thenReturn(Map.of(
                info("/api/v1/samples/{id}", RequestMethod.GET), getOne,
                info("/api/v1/samples", RequestMethod.POST), create));

        service = new ApiDocsMcpService(handlerMapping);
    }

    @Test
    @DisplayName("listEndpoints 는 모든 엔드포인트를 method·path·handler 로 반환한다")
    void listEndpoints() {
        assertThat(service.listEndpoints())
                .extracting(ApiDocsMcpService.EndpointInfo::method,
                        ApiDocsMcpService.EndpointInfo::path,
                        ApiDocsMcpService.EndpointInfo::handler)
                .containsExactlyInAnyOrder(
                        tuple("GET", "/api/v1/samples/{id}", "SampleController#getOne"),
                        tuple("POST", "/api/v1/samples", "SampleController#create"));
    }

    @Test
    @DisplayName("getEndpoint 는 파라미터 위치와 응답 필드를 펼쳐 반환한다")
    void getEndpoint_GET() {
        var details = service.getEndpoint("get", "/api/v1/samples/{id}");

        assertThat(details).hasSize(1);
        ApiDocsMcpService.EndpointDetail detail = details.get(0);
        assertThat(detail.parameters())
                .extracting(ApiDocsMcpService.ParamInfo::name, ApiDocsMcpService.ParamInfo::in)
                .containsExactlyInAnyOrder(tuple("id", "PATH"), tuple("q", "QUERY"));
        assertThat(detail.responseType()).isEqualTo("SampleResponse");
        assertThat(detail.responseFields())
                .extracting(ApiDocsMcpService.FieldInfo::name)
                .containsExactlyInAnyOrder("id", "name");
    }

    @Test
    @DisplayName("getEndpoint 는 @RequestBody record 의 필드를 requestBody 로 펼친다")
    void getEndpoint_POST_body() {
        var details = service.getEndpoint("POST", "/api/v1/samples");

        assertThat(details).hasSize(1);
        ApiDocsMcpService.EndpointDetail detail = details.get(0);
        assertThat(detail.parameters())
                .extracting(ApiDocsMcpService.ParamInfo::in).containsExactly("BODY");
        assertThat(detail.requestBody())
                .extracting(ApiDocsMcpService.FieldInfo::name, ApiDocsMcpService.FieldInfo::type)
                .containsExactlyInAnyOrder(tuple("title", "String"), tuple("count", "int"));
        assertThat(detail.responseType()).isEqualTo("Void");
    }

    @Test
    @DisplayName("searchEndpoints 는 키워드로 엔드포인트를 검색한다")
    void searchEndpoints() {
        assertThat(service.searchEndpoints("samples")).hasSize(2);
        assertThat(service.searchEndpoints("getone")).hasSize(1);
        assertThat(service.searchEndpoints("없는키워드")).isEmpty();
    }

    @Test
    @DisplayName("getErrorContract 는 공통 형식과 에러 코드 목록을 반환한다")
    void getErrorContract() {
        ApiDocsMcpService.ErrorContract contract = service.getErrorContract();

        assertThat(contract.format()).contains("code", "message", "traceId");
        assertThat(contract.codes())
                .extracting(ApiDocsMcpService.ErrorCodeInfo::code)
                .contains("CONTENT_NOT_FOUND", "ITINERARY_NOT_FOUND");
    }
}
