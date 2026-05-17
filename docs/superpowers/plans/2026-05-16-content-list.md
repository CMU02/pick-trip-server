# 콘텐츠 조회 API 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** TourAPI 직접 호출 방식으로 하동/영주/예천 지역 콘텐츠 목록 조회, 검색/필터, 상세 조회 API를 구현한다.

**Architecture:** ContentController → ContentService → TourApiContentAdapter → TourApiClient (OpenFeign) → TourAPI. DB 저장 없이 TourAPI 응답을 정규화하여 반환하며, 어댑터 레이어가 TourAPI 파라미터 변환 및 응답 정규화를 담당한다.

**Tech Stack:** Spring Boot 4.0.6, OpenFeign (spring-cloud-starter-openfeign), JUnit 5 / Mockito, Lombok, Jackson

---

## 파일 구조 요약

```
신규 생성:
domain/content/
├── controller/ContentController.java
├── service/ContentService.java
├── adapter/TourApiContentAdapter.java
├── adapter/TourApiContentMapper.java
├── dto/request/ContentListRequest.java
├── dto/response/ContentListResponse.java
├── dto/response/ContentSummaryResponse.java
├── dto/response/ContentDetailResponse.java
└── client/
    ├── TourApiClient.java
    ├── TourApiRequestInterceptor.java
    └── dto/
        ├── TourApiListResponse.java
        ├── TourApiDetailCommonResponse.java
        ├── TourApiDetailIntroResponse.java
        └── TourApiDetailImageResponse.java

domain/region/Region.java  (Enum)

gloal/error/exception/ContentException.java
gloal/config/TourApiProperties.java

수정:
src/main/resources/application-dev.yaml  (tour-api 설정 추가)
src/main/java/.../PickTripApplication.java  (@EnableFeignClients 확인)

테스트:
domain/content/adapter/TourApiContentMapperTest.java
domain/content/adapter/TourApiContentAdapterTest.java
domain/content/service/ContentServiceTest.java
domain/content/controller/ContentControllerTest.java
```

---

## Task 1: Region Enum + ContentException

**Files:**
- Create: `src/main/java/travel_agency/pick_trip/domain/region/Region.java`
- Create: `src/main/java/travel_agency/pick_trip/gloal/error/exception/ContentException.java`

> Region의 areaCode/sigunguCode는 TourAPI 실제 응답으로 검증 후 사용할 것.
> 검증 URL: `https://apis.data.go.kr/B551011/KorService1/areaCode2?serviceKey=<key>&_type=json&numOfRows=100&pageNo=1&areaCode=38`

- [ ] **Step 1: Region Enum 작성**

```java
// src/main/java/travel_agency/pick_trip/domain/region/Region.java
package travel_agency.pick_trip.domain.region;

import travel_agency.pick_trip.gloal.error.ErrorCode;
import travel_agency.pick_trip.gloal.error.exception.ContentException;

import java.util.Arrays;

public enum Region {
    HADONG("하동", "38", "730"),    // 경남 하동군
    YEONGJU("영주", "35", "220"),   // 경북 영주시
    YECHEON("예천", "35", "260");   // 경북 예천군

    private final String name;
    private final String areaCode;
    private final String sigunguCode;

    Region(String name, String areaCode, String sigunguCode) {
        this.name = name;
        this.areaCode = areaCode;
        this.sigunguCode = sigunguCode;
    }

    public String getName() { return name; }
    public String getAreaCode() { return areaCode; }
    public String getSigunguCode() { return sigunguCode; }

    public static Region fromCode(String code) {
        return Arrays.stream(values())
                .filter(r -> r.name().equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new ContentException(ErrorCode.CONTENT_INVALID_REGION));
    }
}
```

- [ ] **Step 2: ContentException 작성**

```java
// src/main/java/travel_agency/pick_trip/gloal/error/exception/ContentException.java
package travel_agency.pick_trip.gloal.error.exception;

import travel_agency.pick_trip.gloal.error.ErrorCode;

public class ContentException extends PickTripException {
    public ContentException(ErrorCode errorCode) {
        super(errorCode);
    }
}
```

- [ ] **Step 3: 빌드 확인**

```powershell
.\gradlew.bat compileJava
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/travel_agency/pick_trip/domain/region/Region.java
git add src/main/java/travel_agency/pick_trip/gloal/error/exception/ContentException.java
git commit -m "feat(content): Region Enum 및 ContentException 정의"
```

---

## Task 2: TourAPI 설정 (Properties + RequestInterceptor)

**Files:**
- Create: `src/main/java/travel_agency/pick_trip/gloal/config/TourApiProperties.java`
- Create: `src/main/java/travel_agency/pick_trip/domain/content/client/TourApiRequestInterceptor.java`
- Modify: `src/main/resources/application-dev.yaml`
- Verify: `src/main/java/travel_agency/pick_trip/PickTripApplication.java` (`@EnableFeignClients` 확인)

- [ ] **Step 1: application-dev.yaml에 TourAPI 설정 추가**

기존 파일에 아래 내용을 추가한다:

```yaml
tour-api:
  base-url: https://apis.data.go.kr/B551011/KorService1
  service-key: ${PUBLIC_DATA_PORTAL_KEY}
```

- [ ] **Step 2: TourApiProperties 작성**

```java
// src/main/java/travel_agency/pick_trip/gloal/config/TourApiProperties.java
package travel_agency.pick_trip.gloal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tour-api")
public record TourApiProperties(String baseUrl, String serviceKey) {}
```

- [ ] **Step 3: PickTripApplication에 @EnableFeignClients 및 @ConfigurationPropertiesScan 확인**

`PickTripApplication.java`를 열어 `@EnableFeignClients`와 `@ConfigurationPropertiesScan`이 있는지 확인한다.
없으면 추가한다:

```java
@SpringBootApplication
@EnableFeignClients
@ConfigurationPropertiesScan
public class PickTripApplication { ... }
```

- [ ] **Step 4: TourApiRequestInterceptor 작성 (공통 파라미터 자동 주입)**

```java
// src/main/java/travel_agency/pick_trip/domain/content/client/TourApiRequestInterceptor.java
package travel_agency.pick_trip.domain.content.client;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import travel_agency.pick_trip.gloal.config.TourApiProperties;

@Component
@RequiredArgsConstructor
public class TourApiRequestInterceptor implements RequestInterceptor {

    private final TourApiProperties tourApiProperties;

    @Override
    public void apply(RequestTemplate template) {
        template.query("serviceKey", tourApiProperties.serviceKey());
        template.query("_type", "json");
        template.query("MobileOS", "ETC");
        template.query("MobileApp", "PickTrip");
    }
}
```

- [ ] **Step 5: 빌드 확인**

```powershell
.\gradlew.bat compileJava
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 커밋**

```bash
git add src/main/resources/application-dev.yaml
git add src/main/java/travel_agency/pick_trip/gloal/config/TourApiProperties.java
git add src/main/java/travel_agency/pick_trip/domain/content/client/TourApiRequestInterceptor.java
git add src/main/java/travel_agency/pick_trip/PickTripApplication.java  # 변경된 경우만
git commit -m "feat(content): TourAPI 설정 및 공통 파라미터 인터셉터 추가"
```

---

## Task 3: TourAPI 응답 DTO 정의

**Files:**
- Create: `src/main/java/travel_agency/pick_trip/domain/content/client/dto/TourApiListResponse.java`
- Create: `src/main/java/travel_agency/pick_trip/domain/content/client/dto/TourApiDetailCommonResponse.java`
- Create: `src/main/java/travel_agency/pick_trip/domain/content/client/dto/TourApiDetailIntroResponse.java`
- Create: `src/main/java/travel_agency/pick_trip/domain/content/client/dto/TourApiDetailImageResponse.java`

> TourAPI는 결과가 1건일 때 `items.item`이 배열이 아닌 단일 객체로 응답한다.
> `DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY`로 처리한다.

- [ ] **Step 1: TourApiListResponse 작성 (목록 조회 공통 응답 DTO)**

```java
// src/main/java/travel_agency/pick_trip/domain/content/client/dto/TourApiListResponse.java
package travel_agency.pick_trip.domain.content.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TourApiListResponse(Response response) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(Body body) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(Items items, int numOfRows, int pageNo, int totalCount) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Items(List<Item> item) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            String contentid,
            String contenttypeid,
            String title,
            String addr1,
            String addr2,
            String mapx,
            String mapy,
            String firstimage,
            String firstimage2
    ) {}
}
```

- [ ] **Step 2: TourApiDetailCommonResponse 작성**

```java
// src/main/java/travel_agency/pick_trip/domain/content/client/dto/TourApiDetailCommonResponse.java
package travel_agency.pick_trip.domain.content.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TourApiDetailCommonResponse(Response response) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(Body body) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(Items items) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Items(List<Item> item) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            String contentid,
            String contenttypeid,
            String title,
            String addr1,
            String addr2,
            String tel,
            String homepage,
            String mapx,
            String mapy,
            String firstimage,
            String overview
    ) {}
}
```

- [ ] **Step 3: TourApiDetailIntroResponse 작성**

```java
// src/main/java/travel_agency/pick_trip/domain/content/client/dto/TourApiDetailIntroResponse.java
package travel_agency.pick_trip.domain.content.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TourApiDetailIntroResponse(Response response) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(Body body) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(Items items) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Items(List<Item> item) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            String contentid,
            String contenttypeid,
            String usetime,
            String restdate,
            String parking,
            String usefee,
            String chkbabycarriage,
            String chkpet
    ) {}
}
```

- [ ] **Step 4: TourApiDetailImageResponse 작성**

```java
// src/main/java/travel_agency/pick_trip/domain/content/client/dto/TourApiDetailImageResponse.java
package travel_agency.pick_trip.domain.content.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TourApiDetailImageResponse(Response response) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(Body body) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(Items items) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Items(List<Item> item) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            String contentid,
            String originimgurl,
            String imgname
    ) {}
}
```

- [ ] **Step 5: 빌드 확인**

```powershell
.\gradlew.bat compileJava
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/travel_agency/pick_trip/domain/content/client/dto/
git commit -m "feat(content): TourAPI 응답 DTO 정의"
```

---

## Task 4: TourApiClient OpenFeign 인터페이스

**Files:**
- Create: `src/main/java/travel_agency/pick_trip/domain/content/client/TourApiClient.java`

- [ ] **Step 1: TourApiClient 작성**

```java
// src/main/java/travel_agency/pick_trip/domain/content/client/TourApiClient.java
package travel_agency.pick_trip.domain.content.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import travel_agency.pick_trip.domain.content.client.dto.*;
import travel_agency.pick_trip.gloal.config.TourApiProperties;

@FeignClient(
        name = "tour-api",
        url = "${tour-api.base-url}"
)
public interface TourApiClient {

    // 지역 기반 목록 조회 (키워드 없을 때)
    @GetMapping("/areaBasedList2")
    TourApiListResponse getAreaBasedList(
            @RequestParam String areaCode,
            @RequestParam String sigunguCode,
            @RequestParam(required = false) String contentTypeId,
            @RequestParam int pageNo,
            @RequestParam int numOfRows
    );

    // 키워드 검색
    @GetMapping("/searchKeyword2")
    TourApiListResponse searchByKeyword(
            @RequestParam String keyword,
            @RequestParam String areaCode,
            @RequestParam String sigunguCode,
            @RequestParam(required = false) String contentTypeId,
            @RequestParam int pageNo,
            @RequestParam int numOfRows
    );

    // 상세 공통 정보
    @GetMapping("/detailCommon2")
    TourApiDetailCommonResponse getDetailCommon(
            @RequestParam String contentId,
            @RequestParam String defaultYN,
            @RequestParam String overviewYN
    );

    // 상세 소개 정보 (운영시간, 휴무, 주차 등)
    @GetMapping("/detailIntro2")
    TourApiDetailIntroResponse getDetailIntro(
            @RequestParam String contentId,
            @RequestParam String contentTypeId
    );

    // 상세 이미지
    @GetMapping("/detailImage2")
    TourApiDetailImageResponse getDetailImage(
            @RequestParam String contentId,
            @RequestParam String imageYN,
            @RequestParam String subImageYN
    );
}
```

- [ ] **Step 2: Feign Jackson 설정 추가 (ACCEPT_SINGLE_VALUE_AS_ARRAY)**

`TourApiRequestInterceptor`가 있는 같은 패키지에 Feign config 클래스를 만든다:

```java
// src/main/java/travel_agency/pick_trip/domain/content/client/TourApiFeignConfig.java
package travel_agency.pick_trip.domain.content.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.codec.Decoder;
import feign.jackson.JacksonDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TourApiFeignConfig {

    @Bean
    public Decoder tourApiDecoder() {
        ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return new JacksonDecoder(mapper);
    }
}
```

그리고 TourApiClient의 `@FeignClient`에 configuration을 등록한다:

```java
@FeignClient(
        name = "tour-api",
        url = "${tour-api.base-url}",
        configuration = TourApiFeignConfig.class
)
public interface TourApiClient { ... }
```

- [ ] **Step 3: 빌드 확인**

```powershell
.\gradlew.bat compileJava
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/travel_agency/pick_trip/domain/content/client/TourApiClient.java
git add src/main/java/travel_agency/pick_trip/domain/content/client/TourApiFeignConfig.java
git commit -m "feat(content): TourApiClient OpenFeign 인터페이스 및 Feign 설정 추가"
```

---

## Task 5: 내부 응답 DTO + TourApiContentMapper

**Files:**
- Create: `src/main/java/travel_agency/pick_trip/domain/content/dto/response/ContentSummaryResponse.java`
- Create: `src/main/java/travel_agency/pick_trip/domain/content/dto/response/ContentListResponse.java`
- Create: `src/main/java/travel_agency/pick_trip/domain/content/dto/response/ContentDetailResponse.java`
- Create: `src/main/java/travel_agency/pick_trip/domain/content/dto/request/ContentListRequest.java`
- Create: `src/main/java/travel_agency/pick_trip/domain/content/adapter/TourApiContentMapper.java`
- Test: `src/test/java/travel_agency/pick_trip/domain/content/adapter/TourApiContentMapperTest.java`

- [ ] **Step 1: ContentSummaryResponse 작성**

```java
// src/main/java/travel_agency/pick_trip/domain/content/dto/response/ContentSummaryResponse.java
package travel_agency.pick_trip.domain.content.dto.response;

import travel_agency.pick_trip.domain.content.client.dto.TourApiListResponse;

public record ContentSummaryResponse(
        String contentId,
        String title,
        int contentTypeId,
        String address,
        String firstImage,
        double latitude,
        double longitude
) {
    public static ContentSummaryResponse from(TourApiListResponse.Item item) {
        double lat = parseDouble(item.mapy());
        double lon = parseDouble(item.mapx());
        return new ContentSummaryResponse(
                item.contentid(),
                item.title(),
                parseIntOrZero(item.contenttypeid()),
                buildAddress(item.addr1(), item.addr2()),
                item.firstimage(),
                lat,
                lon
        );
    }

    private static double parseDouble(String value) {
        if (value == null || value.isBlank()) return 0.0;
        try { return Double.parseDouble(value); } catch (NumberFormatException e) { return 0.0; }
    }

    private static int parseIntOrZero(String value) {
        if (value == null || value.isBlank()) return 0;
        try { return Integer.parseInt(value); } catch (NumberFormatException e) { return 0; }
    }

    private static String buildAddress(String addr1, String addr2) {
        if (addr2 == null || addr2.isBlank()) return addr1;
        return addr1 + " " + addr2;
    }
}
```

- [ ] **Step 2: ContentListResponse 작성**

```java
// src/main/java/travel_agency/pick_trip/domain/content/dto/response/ContentListResponse.java
package travel_agency.pick_trip.domain.content.dto.response;

import java.util.List;

public record ContentListResponse(
        int totalCount,
        int page,
        int size,
        List<ContentSummaryResponse> items
) {}
```

- [ ] **Step 3: ContentDetailResponse 작성**

```java
// src/main/java/travel_agency/pick_trip/domain/content/dto/response/ContentDetailResponse.java
package travel_agency.pick_trip.domain.content.dto.response;

import java.util.List;

public record ContentDetailResponse(
        String contentId,
        String title,
        int contentTypeId,
        String address,
        String tel,
        String homepage,
        double latitude,
        double longitude,
        String summary,
        String useTime,
        String restDate,
        String parking,
        String useFee,
        String chkBabyCarriage,
        String chkPet,
        List<ImageItem> images
) {
    public record ImageItem(String imageUrl, String title) {}
}
```

- [ ] **Step 4: ContentListRequest 작성**

```java
// src/main/java/travel_agency/pick_trip/domain/content/dto/request/ContentListRequest.java
package travel_agency.pick_trip.domain.content.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ContentListRequest(
        @NotBlank String region,
        String contentTypeId,
        String keyword,
        @Min(0) int page,
        @Min(1) @Max(40) int size
) {
    public ContentListRequest {
        if (page < 0) page = 0;
        if (size <= 0 || size > 40) size = 20;
    }
}
```

- [ ] **Step 5: TourApiContentMapper 작성**

```java
// src/main/java/travel_agency/pick_trip/domain/content/adapter/TourApiContentMapper.java
package travel_agency.pick_trip.domain.content.adapter;

import org.springframework.stereotype.Component;
import travel_agency.pick_trip.domain.content.client.dto.*;
import travel_agency.pick_trip.domain.content.dto.response.*;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Component
public class TourApiContentMapper {

    public ContentListResponse toListResponse(TourApiListResponse raw, int page, int size) {
        List<ContentSummaryResponse> items = Optional.ofNullable(raw.response())
                .map(TourApiListResponse.Response::body)
                .map(TourApiListResponse.Body::items)
                .map(TourApiListResponse.Items::item)
                .orElse(Collections.emptyList())
                .stream()
                .map(ContentSummaryResponse::from)
                .toList();

        int totalCount = Optional.ofNullable(raw.response())
                .map(TourApiListResponse.Response::body)
                .map(TourApiListResponse.Body::totalCount)
                .orElse(0);

        return new ContentListResponse(totalCount, page, size, items);
    }

    public ContentDetailResponse toDetailResponse(
            TourApiDetailCommonResponse common,
            TourApiDetailIntroResponse intro,
            TourApiDetailImageResponse image
    ) {
        TourApiDetailCommonResponse.Item commonItem = extractFirst(common);
        TourApiDetailIntroResponse.Item introItem = extractFirst(intro);
        List<ContentDetailResponse.ImageItem> images = extractImages(image);

        double lat = parseDouble(commonItem.mapy());
        double lon = parseDouble(commonItem.mapx());

        return new ContentDetailResponse(
                commonItem.contentid(),
                commonItem.title(),
                parseIntOrZero(commonItem.contenttypeid()),
                buildAddress(commonItem.addr1(), commonItem.addr2()),
                commonItem.tel(),
                commonItem.homepage(),
                lat,
                lon,
                commonItem.overview(),
                introItem != null ? introItem.usetime() : null,
                introItem != null ? introItem.restdate() : null,
                introItem != null ? introItem.parking() : null,
                introItem != null ? introItem.usefee() : null,
                introItem != null ? introItem.chkbabycarriage() : null,
                introItem != null ? introItem.chkpet() : null,
                images
        );
    }

    private TourApiDetailCommonResponse.Item extractFirst(TourApiDetailCommonResponse response) {
        return Optional.ofNullable(response.response())
                .map(TourApiDetailCommonResponse.Response::body)
                .map(TourApiDetailCommonResponse.Body::items)
                .map(TourApiDetailCommonResponse.Items::item)
                .filter(list -> !list.isEmpty())
                .map(list -> list.get(0))
                .orElse(null);
    }

    private TourApiDetailIntroResponse.Item extractFirst(TourApiDetailIntroResponse response) {
        return Optional.ofNullable(response.response())
                .map(TourApiDetailIntroResponse.Response::body)
                .map(TourApiDetailIntroResponse.Body::items)
                .map(TourApiDetailIntroResponse.Items::item)
                .filter(list -> !list.isEmpty())
                .map(list -> list.get(0))
                .orElse(null);
    }

    private List<ContentDetailResponse.ImageItem> extractImages(TourApiDetailImageResponse response) {
        return Optional.ofNullable(response.response())
                .map(TourApiDetailImageResponse.Response::body)
                .map(TourApiDetailImageResponse.Body::items)
                .map(TourApiDetailImageResponse.Items::item)
                .orElse(Collections.emptyList())
                .stream()
                .map(item -> new ContentDetailResponse.ImageItem(item.originimgurl(), item.imgname()))
                .toList();
    }

    private double parseDouble(String value) {
        if (value == null || value.isBlank()) return 0.0;
        try { return Double.parseDouble(value); } catch (NumberFormatException e) { return 0.0; }
    }

    private int parseIntOrZero(String value) {
        if (value == null || value.isBlank()) return 0;
        try { return Integer.parseInt(value); } catch (NumberFormatException e) { return 0; }
    }

    private String buildAddress(String addr1, String addr2) {
        if (addr2 == null || addr2.isBlank()) return addr1;
        return addr1 + " " + addr2;
    }
}
```

- [ ] **Step 6: TourApiContentMapperTest 실패 테스트 작성**

```java
// src/test/java/travel_agency/pick_trip/domain/content/adapter/TourApiContentMapperTest.java
package travel_agency.pick_trip.domain.content.adapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import travel_agency.pick_trip.domain.content.client.dto.*;
import travel_agency.pick_trip.domain.content.dto.response.*;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TourApiContentMapper")
class TourApiContentMapperTest {

    private TourApiContentMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new TourApiContentMapper();
    }

    @Nested
    @DisplayName("toListResponse")
    class ToListResponse {

        @Test
        @DisplayName("정상적인 TourAPI 목록 응답을 ContentListResponse로 변환한다")
        void validResponse_mapsToContentListResponse() {
            // given
            TourApiListResponse.Item item = new TourApiListResponse.Item(
                    "2741429", "12", "쌍계사",
                    "경상남도 하동군 화개면 쌍계사길 59", "",
                    "127.581783", "35.273185",
                    "https://example.com/img.jpg", ""
            );
            TourApiListResponse raw = new TourApiListResponse(
                    new TourApiListResponse.Response(
                            new TourApiListResponse.Body(
                                    new TourApiListResponse.Items(List.of(item)),
                                    20, 1, 150
                            )
                    )
            );

            // when
            ContentListResponse result = mapper.toListResponse(raw, 0, 20);

            // then
            assertThat(result.totalCount()).isEqualTo(150);
            assertThat(result.items()).hasSize(1);
            assertThat(result.items().get(0).contentId()).isEqualTo("2741429");
            assertThat(result.items().get(0).title()).isEqualTo("쌍계사");
            assertThat(result.items().get(0).latitude()).isEqualTo(35.273185);
            assertThat(result.items().get(0).longitude()).isEqualTo(127.581783);
        }

        @Test
        @DisplayName("items.item이 null이면 빈 목록을 반환한다")
        void nullItems_returnsEmptyList() {
            // given
            TourApiListResponse raw = new TourApiListResponse(
                    new TourApiListResponse.Response(
                            new TourApiListResponse.Body(
                                    new TourApiListResponse.Items(null),
                                    20, 1, 0
                            )
                    )
            );

            // when
            ContentListResponse result = mapper.toListResponse(raw, 0, 20);

            // then
            assertThat(result.items()).isEmpty();
            assertThat(result.totalCount()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("toDetailResponse")
    class ToDetailResponse {

        @Test
        @DisplayName("세 API 응답을 병합해 ContentDetailResponse를 반환한다")
        void mergesThreeResponses() {
            // given
            TourApiDetailCommonResponse common = new TourApiDetailCommonResponse(
                    new TourApiDetailCommonResponse.Response(
                            new TourApiDetailCommonResponse.Body(
                                    new TourApiDetailCommonResponse.Items(List.of(
                                            new TourApiDetailCommonResponse.Item(
                                                    "2741429", "12", "쌍계사",
                                                    "경상남도 하동군 화개면", "",
                                                    "055-883-1901", "http://ssanggyesa.net",
                                                    "127.58", "35.27",
                                                    "https://img.jpg", "한국의 4대 총림"
                                            )
                                    ))
                            )
                    )
            );
            TourApiDetailIntroResponse intro = new TourApiDetailIntroResponse(
                    new TourApiDetailIntroResponse.Response(
                            new TourApiDetailIntroResponse.Body(
                                    new TourApiDetailIntroResponse.Items(List.of(
                                            new TourApiDetailIntroResponse.Item(
                                                    "2741429", "12",
                                                    "03:00~18:00", "연중무휴",
                                                    "가능", "성인 3,000원",
                                                    "불가", "불가"
                                            )
                                    ))
                            )
                    )
            );
            TourApiDetailImageResponse image = new TourApiDetailImageResponse(
                    new TourApiDetailImageResponse.Response(
                            new TourApiDetailImageResponse.Body(
                                    new TourApiDetailImageResponse.Items(List.of(
                                            new TourApiDetailImageResponse.Item(
                                                    "2741429", "https://img1.jpg", "대웅전"
                                            )
                                    ))
                            )
                    )
            );

            // when
            ContentDetailResponse result = mapper.toDetailResponse(common, intro, image);

            // then
            assertThat(result.contentId()).isEqualTo("2741429");
            assertThat(result.summary()).isEqualTo("한국의 4대 총림");
            assertThat(result.useTime()).isEqualTo("03:00~18:00");
            assertThat(result.parking()).isEqualTo("가능");
            assertThat(result.images()).hasSize(1);
            assertThat(result.images().get(0).imageUrl()).isEqualTo("https://img1.jpg");
        }
    }
}
```

- [ ] **Step 7: 테스트 실패 확인**

```powershell
.\gradlew.bat test --tests "*TourApiContentMapperTest" --info
```
Expected: 컴파일 성공 후 테스트 실행 (아직 구현 안 됐다면 실패)

- [ ] **Step 8: 테스트 통과 확인**

```powershell
.\gradlew.bat test --tests "*TourApiContentMapperTest"
```
Expected: BUILD SUCCESSFUL, 3 tests passed

- [ ] **Step 9: 커밋**

```bash
git add src/main/java/travel_agency/pick_trip/domain/content/
git add src/test/java/travel_agency/pick_trip/domain/content/adapter/TourApiContentMapperTest.java
git commit -m "feat(content): 내부 응답 DTO 및 TourApiContentMapper 구현"
```

---

## Task 6: TourApiContentAdapter

**Files:**
- Create: `src/main/java/travel_agency/pick_trip/domain/content/adapter/TourApiContentAdapter.java`
- Test: `src/test/java/travel_agency/pick_trip/domain/content/adapter/TourApiContentAdapterTest.java`

- [ ] **Step 1: 실패 테스트 작성**

```java
// src/test/java/travel_agency/pick_trip/domain/content/adapter/TourApiContentAdapterTest.java
package travel_agency.pick_trip.domain.content.adapter;

import feign.FeignException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import travel_agency.pick_trip.domain.content.client.TourApiClient;
import travel_agency.pick_trip.domain.content.client.dto.*;
import travel_agency.pick_trip.domain.content.dto.request.ContentListRequest;
import travel_agency.pick_trip.domain.content.dto.response.*;
import travel_agency.pick_trip.domain.region.Region;
import travel_agency.pick_trip.gloal.error.ErrorCode;
import travel_agency.pick_trip.gloal.error.exception.ContentException;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("TourApiContentAdapter")
class TourApiContentAdapterTest {

    @Mock private TourApiClient tourApiClient;
    @Mock private TourApiContentMapper mapper;
    @InjectMocks private TourApiContentAdapter adapter;

    @Nested
    @DisplayName("fetchList - 키워드 없음")
    class FetchListWithoutKeyword {

        @Test
        @DisplayName("keyword가 없으면 areaBasedList2를 호출한다")
        void noKeyword_callsAreaBasedList() {
            // given
            ContentListRequest request = new ContentListRequest("HADONG", null, null, 0, 20);
            Region region = Region.HADONG;
            TourApiListResponse rawResponse = emptyListResponse();
            ContentListResponse expected = new ContentListResponse(0, 0, 20, List.of());

            given(tourApiClient.getAreaBasedList(
                    eq(region.getAreaCode()),
                    eq(region.getSigunguCode()),
                    isNull(),
                    eq(1),
                    eq(20)
            )).willReturn(rawResponse);
            given(mapper.toListResponse(rawResponse, 0, 20)).willReturn(expected);

            // when
            ContentListResponse result = adapter.fetchList(request, region);

            // then
            assertThat(result).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("fetchList - 키워드 있음")
    class FetchListWithKeyword {

        @Test
        @DisplayName("keyword가 있으면 searchKeyword2를 호출한다")
        void withKeyword_callsSearchKeyword() {
            // given
            ContentListRequest request = new ContentListRequest("HADONG", null, "쌍계사", 0, 20);
            Region region = Region.HADONG;
            TourApiListResponse rawResponse = emptyListResponse();
            ContentListResponse expected = new ContentListResponse(1, 0, 20, List.of());

            given(tourApiClient.searchByKeyword(
                    eq("쌍계사"),
                    eq(region.getAreaCode()),
                    eq(region.getSigunguCode()),
                    isNull(),
                    eq(1),
                    eq(20)
            )).willReturn(rawResponse);
            given(mapper.toListResponse(rawResponse, 0, 20)).willReturn(expected);

            // when
            ContentListResponse result = adapter.fetchList(request, region);

            // then
            assertThat(result).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("fetchDetail")
    class FetchDetail {

        @Test
        @DisplayName("TourAPI 호출 실패 시 CONTENT_PROVIDER_FAILED 예외를 던진다")
        void feignException_throwsContentProviderFailed() {
            // given
            given(tourApiClient.getDetailCommon(any(), any(), any()))
                    .willThrow(FeignException.class);

            // when
            ThrowableAssert.ThrowingCallable action = () -> adapter.fetchDetail("2741429");

            // then
            assertThatThrownBy(action)
                    .isInstanceOf(ContentException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CONTENT_PROVIDER_FAILED);
        }
    }

    private TourApiListResponse emptyListResponse() {
        return new TourApiListResponse(
                new TourApiListResponse.Response(
                        new TourApiListResponse.Body(
                                new TourApiListResponse.Items(List.of()),
                                20, 1, 0
                        )
                )
        );
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

```powershell
.\gradlew.bat test --tests "*TourApiContentAdapterTest"
```
Expected: 컴파일 실패 또는 `TourApiContentAdapter` 클래스 없음 오류

- [ ] **Step 3: TourApiContentAdapter 구현**

```java
// src/main/java/travel_agency/pick_trip/domain/content/adapter/TourApiContentAdapter.java
package travel_agency.pick_trip.domain.content.adapter;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import travel_agency.pick_trip.domain.content.client.TourApiClient;
import travel_agency.pick_trip.domain.content.client.dto.*;
import travel_agency.pick_trip.domain.content.dto.request.ContentListRequest;
import travel_agency.pick_trip.domain.content.dto.response.*;
import travel_agency.pick_trip.domain.region.Region;
import travel_agency.pick_trip.gloal.error.ErrorCode;
import travel_agency.pick_trip.gloal.error.exception.ContentException;

@Component
@RequiredArgsConstructor
public class TourApiContentAdapter {

    private final TourApiClient tourApiClient;
    private final TourApiContentMapper mapper;

    public ContentListResponse fetchList(ContentListRequest request, Region region) {
        int pageNo = request.page() + 1; // TourAPI는 1-indexed
        try {
            TourApiListResponse raw;
            if (request.keyword() != null && !request.keyword().isBlank()) {
                raw = tourApiClient.searchByKeyword(
                        request.keyword(),
                        region.getAreaCode(),
                        region.getSigunguCode(),
                        request.contentTypeId(),
                        pageNo,
                        request.size()
                );
            } else {
                raw = tourApiClient.getAreaBasedList(
                        region.getAreaCode(),
                        region.getSigunguCode(),
                        request.contentTypeId(),
                        pageNo,
                        request.size()
                );
            }
            return mapper.toListResponse(raw, request.page(), request.size());
        } catch (FeignException e) {
            throw new ContentException(ErrorCode.CONTENT_PROVIDER_FAILED);
        }
    }

    public ContentDetailResponse fetchDetail(String contentId) {
        try {
            TourApiDetailCommonResponse common = tourApiClient.getDetailCommon(contentId, "Y", "Y");

            // 콘텐츠가 없으면 CONTENT_NOT_FOUND
            boolean isEmpty = common.response() == null
                    || common.response().body() == null
                    || common.response().body().items() == null
                    || common.response().body().items().item() == null
                    || common.response().body().items().item().isEmpty();
            if (isEmpty) {
                throw new ContentException(ErrorCode.CONTENT_NOT_FOUND);
            }

            String contentTypeId = common.response().body().items().item().get(0).contenttypeid();
            TourApiDetailIntroResponse intro = tourApiClient.getDetailIntro(contentId, contentTypeId);
            TourApiDetailImageResponse image = tourApiClient.getDetailImage(contentId, "Y", "Y");

            return mapper.toDetailResponse(common, intro, image);
        } catch (ContentException e) {
            throw e;
        } catch (FeignException e) {
            throw new ContentException(ErrorCode.CONTENT_PROVIDER_FAILED);
        }
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

```powershell
.\gradlew.bat test --tests "*TourApiContentAdapterTest"
```
Expected: BUILD SUCCESSFUL, 3 tests passed

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/travel_agency/pick_trip/domain/content/adapter/TourApiContentAdapter.java
git add src/test/java/travel_agency/pick_trip/domain/content/adapter/TourApiContentAdapterTest.java
git commit -m "feat(content): TourApiContentAdapter 구현"
```

---

## Task 7: ContentService

**Files:**
- Create: `src/main/java/travel_agency/pick_trip/domain/content/service/ContentService.java`
- Test: `src/test/java/travel_agency/pick_trip/domain/content/service/ContentServiceTest.java`

- [ ] **Step 1: 실패 테스트 작성**

```java
// src/test/java/travel_agency/pick_trip/domain/content/service/ContentServiceTest.java
package travel_agency.pick_trip.domain.content.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import travel_agency.pick_trip.domain.content.adapter.TourApiContentAdapter;
import travel_agency.pick_trip.domain.content.dto.request.ContentListRequest;
import travel_agency.pick_trip.domain.content.dto.response.*;
import travel_agency.pick_trip.domain.region.Region;
import travel_agency.pick_trip.gloal.error.ErrorCode;
import travel_agency.pick_trip.gloal.error.exception.ContentException;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContentService")
class ContentServiceTest {

    @Mock private TourApiContentAdapter adapter;
    @InjectMocks private ContentService contentService;

    @Nested
    @DisplayName("getContents")
    class GetContents {

        @Test
        @DisplayName("유효한 region으로 요청하면 ContentListResponse를 반환한다")
        void validRegion_returnsContentListResponse() {
            // given
            ContentListRequest request = new ContentListRequest("HADONG", null, null, 0, 20);
            ContentListResponse expected = new ContentListResponse(1, 0, 20, List.of(
                    new ContentSummaryResponse("123", "쌍계사", 12, "경상남도 하동군", "https://img.jpg", 35.27, 127.58)
            ));
            given(adapter.fetchList(request, Region.HADONG)).willReturn(expected);

            // when
            ContentListResponse result = contentService.getContents(request);

            // then
            assertThat(result.totalCount()).isEqualTo(1);
            assertThat(result.items().get(0).title()).isEqualTo("쌍계사");
        }

        @Test
        @DisplayName("지원하지 않는 region이면 CONTENT_INVALID_REGION 예외를 던진다")
        void invalidRegion_throwsContentInvalidRegion() {
            // given
            ContentListRequest request = new ContentListRequest("INVALID", null, null, 0, 20);

            // when
            ThrowableAssert.ThrowingCallable action = () -> contentService.getContents(request);

            // then
            assertThatThrownBy(action)
                    .isInstanceOf(ContentException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CONTENT_INVALID_REGION);
        }
    }

    @Nested
    @DisplayName("getContentDetail")
    class GetContentDetail {

        @Test
        @DisplayName("유효한 contentId로 상세 조회 시 ContentDetailResponse를 반환한다")
        void validContentId_returnsContentDetailResponse() {
            // given
            ContentDetailResponse expected = new ContentDetailResponse(
                    "2741429", "쌍계사", 12, "경상남도 하동군", "055-883-1901", "http://ssanggyesa.net",
                    35.27, 127.58, "한국의 4대 총림", "03:00~18:00", "연중무휴",
                    "가능", "성인 3,000원", "불가", "불가", List.of()
            );
            given(adapter.fetchDetail("2741429")).willReturn(expected);

            // when
            ContentDetailResponse result = contentService.getContentDetail("2741429");

            // then
            assertThat(result.contentId()).isEqualTo("2741429");
            assertThat(result.title()).isEqualTo("쌍계사");
        }
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

```powershell
.\gradlew.bat test --tests "*ContentServiceTest"
```
Expected: 컴파일 오류 또는 클래스 없음

- [ ] **Step 3: ContentService 구현**

```java
// src/main/java/travel_agency/pick_trip/domain/content/service/ContentService.java
package travel_agency.pick_trip.domain.content.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import travel_agency.pick_trip.domain.content.adapter.TourApiContentAdapter;
import travel_agency.pick_trip.domain.content.dto.request.ContentListRequest;
import travel_agency.pick_trip.domain.content.dto.response.*;
import travel_agency.pick_trip.domain.region.Region;

@Service
@RequiredArgsConstructor
public class ContentService {

    private final TourApiContentAdapter adapter;

    public ContentListResponse getContents(ContentListRequest request) {
        Region region = Region.fromCode(request.region()); // 잘못된 region이면 CONTENT_INVALID_REGION 예외
        return adapter.fetchList(request, region);
    }

    public ContentDetailResponse getContentDetail(String contentId) {
        return adapter.fetchDetail(contentId);
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

```powershell
.\gradlew.bat test --tests "*ContentServiceTest"
```
Expected: BUILD SUCCESSFUL, 3 tests passed

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/travel_agency/pick_trip/domain/content/service/ContentService.java
git add src/test/java/travel_agency/pick_trip/domain/content/service/ContentServiceTest.java
git commit -m "feat(content): ContentService 구현"
```

---

## Task 8: ContentController

**Files:**
- Create: `src/main/java/travel_agency/pick_trip/domain/content/controller/ContentController.java`
- Test: `src/test/java/travel_agency/pick_trip/domain/content/controller/ContentControllerTest.java`

- [ ] **Step 1: 실패 테스트 작성**

```java
// src/test/java/travel_agency/pick_trip/domain/content/controller/ContentControllerTest.java
package travel_agency.pick_trip.domain.content.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import travel_agency.pick_trip.domain.content.dto.request.ContentListRequest;
import travel_agency.pick_trip.domain.content.dto.response.*;
import travel_agency.pick_trip.domain.content.service.ContentService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContentController")
class ContentControllerTest {

    @Mock private ContentService contentService;
    @InjectMocks private ContentController contentController;

    @Nested
    @DisplayName("GET /api/v1/contents")
    class GetContents {

        @Test
        @DisplayName("정상 요청이면 200과 ContentListResponse를 반환한다")
        void validRequest_returns200WithList() {
            // given
            ContentListResponse expected = new ContentListResponse(1, 0, 20, List.of(
                    new ContentSummaryResponse("123", "쌍계사", 12, "경상남도 하동군", "https://img.jpg", 35.27, 127.58)
            ));
            given(contentService.getContents(any(ContentListRequest.class))).willReturn(expected);

            // when
            ResponseEntity<ContentListResponse> result = contentController.getContents(
                    "HADONG", null, null, 0, 20
            );

            // then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(result.getBody()).isNotNull();
            assertThat(result.getBody().items()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/contents/{contentId}")
    class GetContentDetail {

        @Test
        @DisplayName("정상 요청이면 200과 ContentDetailResponse를 반환한다")
        void validContentId_returns200WithDetail() {
            // given
            ContentDetailResponse expected = new ContentDetailResponse(
                    "2741429", "쌍계사", 12, "경상남도 하동군", "055-883-1901", "http://ssanggyesa.net",
                    35.27, 127.58, "한국의 4대 총림", "03:00~18:00", "연중무휴",
                    "가능", "성인 3,000원", "불가", "불가", List.of()
            );
            given(contentService.getContentDetail("2741429")).willReturn(expected);

            // when
            ResponseEntity<ContentDetailResponse> result = contentController.getContentDetail("2741429");

            // then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(result.getBody()).isNotNull();
            assertThat(result.getBody().contentId()).isEqualTo("2741429");
        }
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

```powershell
.\gradlew.bat test --tests "*ContentControllerTest"
```
Expected: 컴파일 오류 또는 클래스 없음

- [ ] **Step 3: ContentController 구현**

```java
// src/main/java/travel_agency/pick_trip/domain/content/controller/ContentController.java
package travel_agency.pick_trip.domain.content.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import travel_agency.pick_trip.domain.content.dto.request.ContentListRequest;
import travel_agency.pick_trip.domain.content.dto.response.*;
import travel_agency.pick_trip.domain.content.service.ContentService;

@RestController
@RequestMapping("/api/v1/contents")
@RequiredArgsConstructor
public class ContentController {

    private final ContentService contentService;

    @GetMapping
    public ResponseEntity<ContentListResponse> getContents(
            @RequestParam String region,
            @RequestParam(required = false) String contentTypeId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        ContentListRequest request = new ContentListRequest(region, contentTypeId, keyword, page, size);
        return ResponseEntity.ok(contentService.getContents(request));
    }

    @GetMapping("/{contentId}")
    public ResponseEntity<ContentDetailResponse> getContentDetail(@PathVariable String contentId) {
        return ResponseEntity.ok(contentService.getContentDetail(contentId));
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

```powershell
.\gradlew.bat test --tests "*ContentControllerTest"
```
Expected: BUILD SUCCESSFUL, 2 tests passed

- [ ] **Step 5: 전체 테스트 실행**

```powershell
.\gradlew.bat test
```
Expected: BUILD SUCCESSFUL, 모든 기존 테스트 포함 통과

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/travel_agency/pick_trip/domain/content/controller/ContentController.java
git add src/test/java/travel_agency/pick_trip/domain/content/controller/ContentControllerTest.java
git commit -m "feat(content): ContentController 구현 및 전체 테스트 통과"
```

---

## Task 9: E2E 검증 (실제 TourAPI 연결)

> 로컬에서 `.env` 파일에 `PUBLIC_DATA_PORTAL_KEY`가 설정되어 있어야 한다.

- [ ] **Step 1: 애플리케이션 기동**

```powershell
.\gradlew.bat bootRun
```
Expected: Tomcat started on port 8080

- [ ] **Step 2: 콘텐츠 목록 조회 확인**

```bash
curl "http://localhost:8080/api/v1/contents?region=HADONG&page=0&size=5"
```
Expected: HTTP 200, `items` 배열에 하동 콘텐츠 반환

- [ ] **Step 3: 키워드 검색 확인**

```bash
curl "http://localhost:8080/api/v1/contents?region=HADONG&keyword=쌍계사"
```
Expected: HTTP 200, 쌍계사 관련 콘텐츠 반환

- [ ] **Step 4: 카테고리 필터 확인**

```bash
curl "http://localhost:8080/api/v1/contents?region=YEONGJU&contentTypeId=39"
```
Expected: HTTP 200, 영주 음식점 목록 반환

- [ ] **Step 5: 상세 조회 확인 (Step 2 응답의 contentId 사용)**

```bash
curl "http://localhost:8080/api/v1/contents/{contentId}"
```
Expected: HTTP 200, `useTime`, `parking`, `images` 포함 상세 응답

- [ ] **Step 6: 잘못된 region 에러 확인**

```bash
curl "http://localhost:8080/api/v1/contents?region=INVALID"
```
Expected: HTTP 400, `{"code":"CONTENT_INVALID_REGION","message":"지원하지 않는 지역입니다.","traceId":"..."}`

- [ ] **Step 7: Region Enum areaCode/sigunguCode 검증**

실제 응답 데이터의 지역이 하동/영주/예천과 맞는지 확인한다.
맞지 않으면 `Region.java`의 코드를 수정하고 재테스트한다.

- [ ] **Step 8: 최종 커밋**

```bash
git commit -m "chore(content): E2E 검증 완료" --allow-empty
```

---

## 검증 체크리스트 (스펙 기반)

- [ ] `GET /api/v1/contents?region=HADONG` → 200, 하동 콘텐츠 목록
- [ ] `GET /api/v1/contents?region=HADONG&keyword=쌍계사` → 200, 검색 결과
- [ ] `GET /api/v1/contents?region=HADONG&contentTypeId=12` → 200, 관광지만
- [ ] `GET /api/v1/contents?region=INVALID` → 400, CONTENT_INVALID_REGION
- [ ] `GET /api/v1/contents/{validId}` → 200, 이미지 포함 상세 정보
- [ ] `GET /api/v1/contents/99999999` → 404, CONTENT_NOT_FOUND
- [ ] TourAPI Mock 실패 → 502, CONTENT_PROVIDER_FAILED (단위 테스트로 확인)
