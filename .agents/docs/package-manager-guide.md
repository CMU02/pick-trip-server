# Gradle 사용 가이드

이 프로젝트는 React Native 또는 Expo 프로젝트가 아니라 Spring Boot 서버 프로젝트다. 패키지 매니저는 Bun, npm, yarn이 아니라 Gradle Wrapper를 사용한다.

## 기본 원칙

- 로컬에 설치된 Gradle 대신 프로젝트에 포함된 Gradle Wrapper를 사용한다.
- Windows에서는 `gradlew.bat`을 사용한다.
- macOS 또는 Linux에서는 `./gradlew`를 사용한다.
- 의존성은 `build.gradle`에 추가한다.
- Spring Boot 설정은 `application.yaml`과 프로필별 설정 파일에 둔다.

## 주요 명령어

### Windows PowerShell

```powershell
.\gradlew.bat test
.\gradlew.bat bootRun
.\gradlew.bat build
```

### macOS / Linux

```bash
./gradlew test
./gradlew bootRun
./gradlew build
```

## Docker Compose

프로젝트에는 MySQL 개발 환경을 위한 `compose.yaml`이 있다. Spring Boot Docker Compose 지원을 사용하는 경우 `bootRun` 실행 시 개발 DB를 함께 사용할 수 있다.

DB 계정, 비밀번호, 포트는 개발 환경과 운영 환경을 분리한다. 운영 secret은 Git에 커밋하지 않는다.

## 의존성 추가 기준

- Spring Boot starter를 우선 사용한다.
- 이미 Spring Boot BOM이 관리하는 라이브러리는 명시 버전을 적지 않는다.
- 새 라이브러리는 필요한 이유가 분명할 때만 추가한다.
- 테스트 라이브러리는 `testImplementation`에 추가한다.
- annotation processor는 `annotationProcessor`에 추가한다.

## 외부 API 연동 (OpenFeign)

TourAPI 등 외부 HTTP API는 **Spring Cloud OpenFeign**으로 연동한다.

```groovy
// build.gradle
dependencies {
    implementation 'org.springframework.cloud:spring-cloud-starter-openfeign'
}

// Spring Cloud BOM이 없는 경우 dependencyManagement에 추가
dependencyManagement {
    imports {
        mavenBom "org.springframework.cloud:spring-cloud-dependencies:${springCloudVersion}"
    }
}
```

- 메인 클래스 또는 설정 클래스에 `@EnableFeignClients`를 추가한다.
- Feign 클라이언트 인터페이스는 `global/client` 패키지에 위치시킨다.

### 공공데이터 포털 인증키 주입

공공데이터 포털 API(국문 관광정보 서비스, 관광사진 정보)의 인증키는 모두 동일한 키를 사용한다.

`.env` 파일에 정의한다.

```properties
PUBLIC_DATA_PORTAL_KEY=발급받은_인증키
```

`application.yaml`에서 바인딩한다.

```yaml
public-data-portal:
  key:
    encode: ${PUBLIC_DATA_PORTAL_KEY}
```

`@Value`로 주입한다.

```java
@Value("${public-data-portal.key.encode}")
private String serviceKey;
```

### OpenFeign 인터셉터

인증키는 **RequestInterceptor**를 구현해 모든 요청의 쿼리 파라미터에 공통으로 주입한다.

```java
@Component
public class PublicDataPortalRequestInterceptor implements RequestInterceptor {

    @Value("${public-data-portal.key.encode}")
    private String serviceKey;

    @Override
    public void apply(RequestTemplate template) {
        template.query("serviceKey", serviceKey);
        template.query("MobileOS", "ETC");
        template.query("MobileApp", "PickTrip");
        template.query("_type", "json");
    }
}
```

- `serviceKey`는 쿼리 파라미터로 주입한다. 헤더가 아님에 주의한다.
- `MobileOS`, `MobileApp`, `_type`은 TourAPI 공통 필수 파라미터이므로 인터셉터에서 함께 처리한다.

## 스케줄러

TourAPI 데이터 갱신은 Spring `@Scheduled`로 실행한다.

```groovy
// 별도 의존성 없이 Spring Boot에 포함되어 있다.
// 메인 클래스 또는 설정 클래스에 @EnableScheduling 추가
```

스케줄러 클래스는 `global/scheduler` 패키지에 위치시킨다.

## 검증 기준

문서 또는 코드 변경 후 가능한 경우 다음 명령을 실행한다.

```powershell
.\gradlew.bat test
```

기능 구현 후에는 최소한 관련 테스트와 전체 테스트를 실행한다.
