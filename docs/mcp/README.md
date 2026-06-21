# PickTrip API Docs MCP 서버

프론트 팀원의 AI 코딩 도구(Cursor, Claude 등)가 PickTrip 백엔드 REST API 스펙을 직접 읽을 수 있도록, **read-only API 문서 MCP 서버**를 Spring 앱에 내장했습니다. (Spring AI MCP Server, SSE/HTTP)

## 1. 서버 실행

```bash
./gradlew bootRun        # macOS / Linux
.\gradlew.bat bootRun    # Windows
```

- `spring-boot-docker-compose`가 **MySQL을 Docker로 자동 기동**하므로 Docker만 떠 있으면 됩니다.
- 부팅에 필요한 환경변수는 `.env`로 주입합니다(없어도 기본값으로 기동).
- 기본 포트는 `8080`입니다.

## 2. MCP 엔드포인트

- SSE 연결: `http://localhost:8080/sse`
- (메시지 엔드포인트는 `/mcp/` 하위에 자동 노출)

인증 불필요(`SecurityConfig`에서 `/sse`·`/mcp/**` 허용).

## 3. AI 클라이언트에 등록

각자 사용하는 AI 도구의 MCP 설정에 아래 SSE 서버를 추가하세요.

```jsonc
{
  "mcpServers": {
    "picktrip-api-docs": {
      "type": "sse",
      "url": "http://localhost:8080/sse"
    }
  }
}
```

- **Claude Code**: 프로젝트 루트의 `.mcp.json` 또는 `claude mcp add` 명령
- **Cursor**: Settings → MCP → Add Server (SSE, 위 URL)
- 클라이언트별 설정 키 이름이 조금 다를 수 있으나 **전송=SSE, URL=`http://localhost:8080/sse`** 는 동일합니다.

## 4. 제공 도구 (read-only)

| 도구 | 설명 |
|------|------|
| `listEndpoints` | 전체 엔드포인트 목록(HTTP 메서드·경로·핸들러) |
| `getEndpoint(httpMethod, path)` | 특정 엔드포인트 상세(파라미터 위치·요청/응답 필드) |
| `searchEndpoints(keyword)` | 경로/핸들러명 키워드 검색 |
| `getErrorContract` | 공통 에러 계약(`code`/`message`/`traceId`)과 전체 에러 코드 목록 |

> 조회 전용입니다. 서버 엔드포인트를 실제로 호출하거나 데이터를 변경하지 않습니다.

## 5. 사람용 문서(보너스)

같은 스펙을 사람이 브라우저로 볼 수 있게 springdoc도 함께 제공합니다.

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

AI에게 전체 스펙을 통째로 주고 싶을 때는 위 OpenAPI JSON을 컨텍스트로 넘겨도 됩니다.
