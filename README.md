# PickTrip Server

## 프로젝트 소개

PickTrip 서버는 경상도 소도시(하동, 영주, 예천) 여행 일정 생성 서비스의 백엔드를 담당한다.

사용자가 선택한 지역 콘텐츠와 여행 조건을 AI에 전달해 현실적인 일정을 생성하고, 이를 저장·공유할 수 있는 API를 제공한다.
비로그인 사용자도 콘텐츠를 탐색할 수 있으며, 일정 저장·공유는 소셜 로그인(Kakao, Google) 기반 인증 사용자에게만 제공한다.

## 프로젝트 배경

국내 여행자는 유명 관광지에 집중되는 경향이 있다. 하동, 영주, 예천처럼 고유한 음식·자연·문화·축제 자원을 가진 지역은 정보가 흩어져 있어 매력을 한눈에 파악하기 어렵다.

기존 지도 서비스나 블로그는 개별 장소 정보를 제공하지만, 사용자 선택을 실제 일정으로 연결하는 데 한계가 있다.
이 서버는 지역 콘텐츠 탐색과 AI 일정 생성을 하나의 흐름으로 연결해 이 문제를 해결한다.

## 기술 스택

| 분류       | 기술                          |
|----------|-------------------------------|
| 언어       | Java 21                       |
| 프레임워크  | Spring Boot 4.0.6             |
| 웹        | Spring MVC                    |
| 영속성     | Spring Data JPA / Hibernate   |
| DB       | MySQL                         |
| 빌드      | Gradle Wrapper                |
| 코드 생성  | Lombok                        |
| 테스트     | JUnit 5 / Spring Boot Test    |
| 개발 DB 환경 | Docker Compose (`compose.yaml`) |

## Quick Start

### 로컬 실행 (Windows)

```powershell
# 개발 DB 포함 실행 (Docker Compose 자동 연동)
.\gradlew.bat bootRun

# 테스트 실행
.\gradlew.bat test

# 빌드
.\gradlew.bat build
```

### 로컬 실행 (macOS / Linux)

```bash
./gradlew bootRun
./gradlew test
./gradlew build
```

### 설정

- 로컬 개발: `application.yaml` + `application-dev.yaml`
- 운영: `application-prod.yaml` (secret 값은 환경 변수 또는 secret manager에서 주입)
- `.env` 파일로 로컬 secret을 주입할 수 있다 (`spring.config.import` 설정 참고)

## 패키지 구조

```text
src/main/java/travel_agency/pick_trip
├── PickTripApplication.java
├── global
│   ├── config
│   ├── error
│   ├── security
│   └── util
└── domain
    ├── auth
    ├── user
    ├── region
    ├── content
    ├── basket
    ├── itinerary
    └── share
```

## 핵심 도메인 개요

| 도메인        | 책임                                        |
|-------------|---------------------------------------------|
| `auth`      | Kakao / Google OAuth, 토큰 발급·검증          |
| `user`      | 내부 사용자 계정 관리                          |
| `region`    | 지역 코드 및 메타데이터 (HADONG, YEONGJU, YECHEON) |
| `content`   | 지역 콘텐츠 저장·검색·필터                      |
| `basket`    | 여행 바구니 및 우선순위 관리                     |
| `itinerary` | AI 일정 생성 연동, 저장, 수정                   |
| `share`     | 공유 토큰 생성 및 공개 일정 조회                  |

## 에러 응답 규약

모든 API 예외는 아래 형태로 응답한다.

```json
{
  "code": "DOMAIN_ERROR_TYPE",
  "message": "사용자에게 표시할 한국어 메시지",
  "traceId": "request-trace-id"
}
```

에러 코드 목록과 HTTP 상태 매핑은 `.agents/docs/error-handling-flow.md`를 참고한다.

## 프로젝트 문서

| 제목                  | 경로                                                          |
|-----------------------|---------------------------------------------------------------|
| 핵심 기능              | `.agents/docs/key-features.md`                                |
| 주요 사용 흐름          | `.agents/docs/key-usage-flow.md`                              |
| MVP 범위              | `.agents/docs/mvp-scope.md`                                   |
| API 엔드포인트 초안     | `.agents/docs/api-endpoints.md`                               |
| 도메인 모델             | `.agents/docs/domain-model.md`                                |
| AI 일정 생성 연동       | `.agents/docs/ai-integration.md`                              |
| TourAPI 수집·동기화    | `.agents/docs/tour-api-sync.md`                               |
| 지역별 콘텐츠 방향       | `.agents/docs/content-direction-by-region.md`                 |
| 예외 처리 흐름          | `.agents/docs/error-handling-flow.md`                         |
| 정보 보안 보완 방안      | `.agents/docs/measures-to-enhance-information-security.md`    |
| Gradle 사용 가이드     | `.agents/docs/package-manager-guide.md`                       |

## 컨벤션

| 제목        | 경로                                |
|------------|-------------------------------------|
| 코드 규칙   | `.agents/rules/code-convention.md`  |
| 테스트 규칙  | `.agents/rules/test-convention.md`  |
| TraceId 규칙 | `.agents/rules/trace-id.md`        |
| 깃 규칙     | `.agents/rules/git-convention.md`   |
| 브랜치 포커스 | `.agents/rules/branch-focus.md`   |

## 기여 방법

기여 방법은 [CONTRIBUTING.md](CONTRIBUTING.md)를 참고한다.
