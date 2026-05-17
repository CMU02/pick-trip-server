# Content Filter & Detail 확장 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** MVP 2번(콘텐츠 검색·필터)과 3번(콘텐츠 상세 조회) 조건을 완전히 충족하도록 동행 조건·실내외 필터와 체류 시간·데이터 출처 필드를 추가한다.

**Architecture:** TourAPI 목록 API는 contentTypeId 단일 파라미터만 지원하므로, 새 필터(companion, indoorOnly)를 ContentTypeCategory 유틸을 통해 contentTypeId로 변환한다. 상세 응답 신규 필드(stayDuration, reservationRequired, dataSource)는 contentTypeId 기반 하드코딩 또는 상수로 채운다.

**Tech Stack:** Java 21, Spring Boot 4.x, Spring MVC, OpenFeign, JUnit 5, Mockito, AssertJ

---

## 파일 구조

| 동작 | 파일 경로 | 역할 |
|---|---|---|
| 생성 | `src/main/java/travel_agency/pick_trip/domain/content/dto/request/CompanionType.java` | 동행 조건 enum |
| 생성 | `src/main/java/travel_agency/pick_trip/domain/content/adapter/ContentTypeCategory.java` | contentTypeId 변환 + 체류시간 매핑 유틸 |
| 수정 | `src/main/java/travel_agency/pick_trip/domain/content/dto/request/ContentListRequest.java` | companion, indoorOnly 파라미터 추가 |
| 수정 | `src/main/java/travel_agency/pick_trip/domain/content/controller/ContentController.java` | 새 @RequestParam 추가 |
| 수정 | `src/main/java/travel_agency/pick_trip/domain/content/adapter/TourApiContentAdapter.java` | effectiveContentTypeId 변환 로직 추가 |
| 수정 | `src/main/java/travel_agency/pick_trip/domain/content/dto/response/ContentDetailResponse.java` | stayDuration, reservationRequired, dataSource 추가 |
| 수정 | `src/main/java/travel_agency/pick_trip/domain/content/adapter/TourApiContentMapper.java` | 신규 필드 매핑 추가 |
| 수정 | `src/test/java/.../content/adapter/TourApiContentAdapterTest.java` | 필터 변환 테스트 추가 + 생성자 수정 |
| 수정 | `src/test/java/.../content/adapter/TourApiContentMapperTest.java` | 신규 필드 검증 + 생성자 수정 |
| 수정 | `src/test/java/.../content/controller/ContentControllerTest.java` | 생성자 수정 |
| 수정 | `.agents/docs/mvp-scope.md` | 2번·3번 체크박스 체크 |

---

## Task 1: CompanionType enum 생성

**Files:**
- Create: `src/main/java/travel_agency/pick_trip/domain/content/dto/request/CompanionType.java`

- [ ] **Step 1: CompanionType enum 생성**

```java
package travel_agency.pick_trip.domain.content.dto.request;

public enum CompanionType {
    SOLO, COUPLE, FAMILY, FRIENDS
}
```

- [ ] **Step 2: 빌드 확인**

```powershell
.\gradlew.bat compileJava -q
```

Expected: BUILD SUCCESSFUL

---

## Task 2: ContentTypeCategory 유틸 생성 (TDD)

**Files:**
- Create: `src/main/java/travel_agency/pick_trip/domain/content/adapter/ContentTypeCategory.java`
- Test: `src/test/java/travel_agency/pick_trip/domain/content/adapter/ContentTypeCategoryTest.java`

- [ ] **Step 1: 실패 테스트 작성**

```java
package travel_agency.pick_trip.domain.content.adapter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import travel_agency.pick_trip.domain.content.dto.request.CompanionType;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ContentTypeCategory")
class ContentTypeCategoryTest {

    @Nested
    @DisplayName("resolveContentTypeId")
    class ResolveContentTypeId {

        @Test
        @DisplayName("explicit contentTypeId가 있으면 그대로 반환한다")
        void explicitContentTypeId_returnsAsIs() {
            String result = ContentTypeCategory.resolveContentTypeId("28", null, null);
            assertThat(result).isEqualTo("28");
        }

        @Test
        @DisplayName("indoorOnly=true이고 contentTypeId 없으면 문화시설(14) 반환")
        void indoorOnly_true_returnsCulture() {
            String result = ContentTypeCategory.resolveContentTypeId(null, true, null);
            assertThat(result).isEqualTo("14");
        }

        @Test
        @DisplayName("indoorOnly=false이고 contentTypeId 없으면 관광지(12) 반환")
        void indoorOnly_false_returnsTourism() {
            String result = ContentTypeCategory.resolveContentTypeId(null, false, null);
            assertThat(result).isEqualTo("12");
        }

        @Test
        @DisplayName("companion=FAMILY이고 contentTypeId 없으면 문화시설(14) 반환")
        void companion_family_returnsCulture() {
            String result = ContentTypeCategory.resolveContentTypeId(null, null, CompanionType.FAMILY);
            assertThat(result).isEqualTo("14");
        }

        @Test
        @DisplayName("companion=COUPLE이고 contentTypeId 없으면 null 반환")
        void companion_couple_returnsNull() {
            String result = ContentTypeCategory.resolveContentTypeId(null, null, CompanionType.COUPLE);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("indoorOnly와 companion 둘 다 있으면 indoorOnly 우선 적용")
        void indoorOnly_takesOverCompanion() {
            String result = ContentTypeCategory.resolveContentTypeId(null, false, CompanionType.FAMILY);
            assertThat(result).isEqualTo("12");
        }

        @Test
        @DisplayName("explicit contentTypeId가 있으면 indoorOnly와 companion 무시")
        void explicit_ignoresFilters() {
            String result = ContentTypeCategory.resolveContentTypeId("39", true, CompanionType.FAMILY);
            assertThat(result).isEqualTo("39");
        }
    }

    @Nested
    @DisplayName("stayDurationFor")
    class StayDurationFor {

        @Test
        @DisplayName("관광지(12)는 약 2시간 반환")
        void tourism_returns2Hours() {
            assertThat(ContentTypeCategory.stayDurationFor(12)).isEqualTo("약 2시간");
        }

        @Test
        @DisplayName("문화시설(14)은 약 1~2시간 반환")
        void culture_returns1To2Hours() {
            assertThat(ContentTypeCategory.stayDurationFor(14)).isEqualTo("약 1~2시간");
        }

        @Test
        @DisplayName("레포츠(28)은 약 2~3시간 반환")
        void leisure_returns2To3Hours() {
            assertThat(ContentTypeCategory.stayDurationFor(28)).isEqualTo("약 2~3시간");
        }

        @Test
        @DisplayName("음식점(39)은 약 1시간 반환")
        void restaurant_returns1Hour() {
            assertThat(ContentTypeCategory.stayDurationFor(39)).isEqualTo("약 1시간");
        }

        @Test
        @DisplayName("알 수 없는 contentTypeId는 null 반환")
        void unknown_returnsNull() {
            assertThat(ContentTypeCategory.stayDurationFor(99)).isNull();
        }
    }
}
```

- [ ] **Step 2: 테스트 실행 - 실패 확인**

```powershell
.\gradlew.bat test --tests "travel_agency.pick_trip.domain.content.adapter.ContentTypeCategoryTest" -q
```

Expected: FAIL (class not found)

- [ ] **Step 3: ContentTypeCategory 구현**

```java
package travel_agency.pick_trip.domain.content.adapter;

import travel_agency.pick_trip.domain.content.dto.request.CompanionType;

public enum ContentTypeCategory {
    TOURISM("12", "약 2시간"),
    CULTURE("14", "약 1~2시간"),
    EVENT("15", "약 2~3시간"),
    LEISURE("28", "약 2~3시간"),
    SHOPPING("38", "약 1시간"),
    RESTAURANT("39", "약 1시간");

    private final String contentTypeId;
    private final String stayDuration;

    ContentTypeCategory(String contentTypeId, String stayDuration) {
        this.contentTypeId = contentTypeId;
        this.stayDuration = stayDuration;
    }

    public static String resolveContentTypeId(String explicit, Boolean indoorOnly, CompanionType companion) {
        if (explicit != null) return explicit;
        if (indoorOnly != null) {
            return indoorOnly ? CULTURE.contentTypeId : TOURISM.contentTypeId;
        }
        if (companion == CompanionType.FAMILY) {
            return CULTURE.contentTypeId;
        }
        return null;
    }

    public static String stayDurationFor(int contentTypeId) {
        String id = String.valueOf(contentTypeId);
        for (ContentTypeCategory c : values()) {
            if (c.contentTypeId.equals(id)) {
                return c.stayDuration;
            }
        }
        return null;
    }
}
```

- [ ] **Step 4: 테스트 실행 - 통과 확인**

```powershell
.\gradlew.bat test --tests "travel_agency.pick_trip.domain.content.adapter.ContentTypeCategoryTest" -q
```

Expected: BUILD SUCCESSFUL, 8 tests passed

- [ ] **Step 5: 커밋**

```powershell
git add src/main/java/travel_agency/pick_trip/domain/content/dto/request/CompanionType.java
git add src/main/java/travel_agency/pick_trip/domain/content/adapter/ContentTypeCategory.java
git add src/test/java/travel_agency/pick_trip/domain/content/adapter/ContentTypeCategoryTest.java
git commit -m "feat(content): CompanionType enum 및 ContentTypeCategory 유틸 추가"
```

---

## Task 3: ContentListRequest에 companion, indoorOnly 파라미터 추가

**Files:**
- Modify: `src/main/java/travel_agency/pick_trip/domain/content/dto/request/ContentListRequest.java`

- [ ] **Step 1: ContentListRequest 수정**

```java
package travel_agency.pick_trip.domain.content.dto.request;

public record ContentListRequest(
        String region,
        String contentTypeId,
        String keyword,
        CompanionType companion,
        Boolean indoorOnly,
        int page,
        int size
) {
    public ContentListRequest {
        if (page < 0) page = 0;
        if (size <= 0 || size > 40) size = 20;
    }
}
```

- [ ] **Step 2: 빌드 확인 (컴파일 에러 확인)**

```powershell
.\gradlew.bat compileJava -q
```

Expected: FAIL — ContentListRequest 생성자 호출부(Controller, AdapterTest, ControllerTest)에서 컴파일 에러 발생. 다음 Task들에서 순차적으로 수정한다.

---

## Task 4: ContentController에 새 @RequestParam 추가

**Files:**
- Modify: `src/main/java/travel_agency/pick_trip/domain/content/controller/ContentController.java`
- Modify: `src/test/java/travel_agency/pick_trip/domain/content/controller/ContentControllerTest.java`

- [ ] **Step 1: ContentController 수정**

```java
package travel_agency.pick_trip.domain.content.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import travel_agency.pick_trip.domain.content.dto.request.CompanionType;
import travel_agency.pick_trip.domain.content.dto.request.ContentListRequest;
import travel_agency.pick_trip.domain.content.dto.response.ContentDetailResponse;
import travel_agency.pick_trip.domain.content.dto.response.ContentListResponse;
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
            @RequestParam(required = false) CompanionType companion,
            @RequestParam(required = false) Boolean indoorOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        ContentListRequest request = new ContentListRequest(region, contentTypeId, keyword, companion, indoorOnly, page, size);
        return ResponseEntity.ok(contentService.getContents(request));
    }

    @GetMapping("/{contentId}")
    public ResponseEntity<ContentDetailResponse> getContentDetail(@PathVariable String contentId) {
        return ResponseEntity.ok(contentService.getContentDetail(contentId));
    }
}
```

- [ ] **Step 2: ContentControllerTest 생성자 수정**

`ContentControllerTest.java`에서 `getContents` 호출부를 수정한다 (새 파라미터 null로 전달).

```java
// 기존
ResponseEntity<ContentListResponse> result = contentController.getContents(
        "HADONG", null, null, 0, 20
);

// 수정 후
ResponseEntity<ContentListResponse> result = contentController.getContents(
        "HADONG", null, null, null, null, 0, 20
);
```

- [ ] **Step 3: ContentControllerTest 실행 - 통과 확인**

```powershell
.\gradlew.bat test --tests "travel_agency.pick_trip.domain.content.controller.ContentControllerTest" -q
```

Expected: BUILD SUCCESSFUL

---

## Task 5: TourApiContentAdapter에 effectiveContentTypeId 변환 로직 추가

**Files:**
- Modify: `src/main/java/travel_agency/pick_trip/domain/content/adapter/TourApiContentAdapter.java`
- Modify: `src/test/java/travel_agency/pick_trip/domain/content/adapter/TourApiContentAdapterTest.java`

- [ ] **Step 1: 실패 테스트 작성 (TourApiContentAdapterTest에 추가)**

기존 테스트 파일에서 `ContentListRequest` 생성자를 수정하고, 새 테스트 케이스를 추가한다.

```java
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
import travel_agency.pick_trip.domain.content.client.dto.TourApiListResponse;
import travel_agency.pick_trip.domain.content.dto.request.CompanionType;
import travel_agency.pick_trip.domain.content.dto.request.ContentListRequest;
import travel_agency.pick_trip.domain.content.dto.response.ContentListResponse;
import travel_agency.pick_trip.domain.region.Region;
import travel_agency.pick_trip.gloal.error.ErrorCode;
import travel_agency.pick_trip.gloal.error.exception.ContentException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
            ContentListRequest request = new ContentListRequest("HADONG", null, null, null, null, 0, 20);
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
            ContentListRequest request = new ContentListRequest("HADONG", null, "쌍계사", null, null, 0, 20);
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
    @DisplayName("fetchList - 필터 변환")
    class FetchListWithFilter {

        @Test
        @DisplayName("indoorOnly=true이면 contentTypeId=14로 areaBasedList2를 호출한다")
        void indoorOnly_true_callsWithCultureContentTypeId() {
            // given
            ContentListRequest request = new ContentListRequest("HADONG", null, null, null, true, 0, 20);
            Region region = Region.HADONG;
            TourApiListResponse rawResponse = emptyListResponse();
            ContentListResponse expected = new ContentListResponse(0, 0, 20, List.of());

            given(tourApiClient.getAreaBasedList(
                    eq(region.getAreaCode()),
                    eq(region.getSigunguCode()),
                    eq("14"),
                    eq(1),
                    eq(20)
            )).willReturn(rawResponse);
            given(mapper.toListResponse(rawResponse, 0, 20)).willReturn(expected);

            // when
            ContentListResponse result = adapter.fetchList(request, region);

            // then
            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("indoorOnly=false이면 contentTypeId=12로 areaBasedList2를 호출한다")
        void indoorOnly_false_callsWithTourismContentTypeId() {
            // given
            ContentListRequest request = new ContentListRequest("HADONG", null, null, null, false, 0, 20);
            Region region = Region.HADONG;
            TourApiListResponse rawResponse = emptyListResponse();
            ContentListResponse expected = new ContentListResponse(0, 0, 20, List.of());

            given(tourApiClient.getAreaBasedList(
                    eq(region.getAreaCode()),
                    eq(region.getSigunguCode()),
                    eq("12"),
                    eq(1),
                    eq(20)
            )).willReturn(rawResponse);
            given(mapper.toListResponse(rawResponse, 0, 20)).willReturn(expected);

            // when
            ContentListResponse result = adapter.fetchList(request, region);

            // then
            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("companion=FAMILY이면 contentTypeId=14로 areaBasedList2를 호출한다")
        void companionFamily_callsWithCultureContentTypeId() {
            // given
            ContentListRequest request = new ContentListRequest("HADONG", null, null, CompanionType.FAMILY, null, 0, 20);
            Region region = Region.HADONG;
            TourApiListResponse rawResponse = emptyListResponse();
            ContentListResponse expected = new ContentListResponse(0, 0, 20, List.of());

            given(tourApiClient.getAreaBasedList(
                    eq(region.getAreaCode()),
                    eq(region.getSigunguCode()),
                    eq("14"),
                    eq(1),
                    eq(20)
            )).willReturn(rawResponse);
            given(mapper.toListResponse(rawResponse, 0, 20)).willReturn(expected);

            // when
            ContentListResponse result = adapter.fetchList(request, region);

            // then
            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("explicit contentTypeId가 있으면 indoorOnly와 companion을 무시한다")
        void explicitContentTypeId_ignoresFilters() {
            // given
            ContentListRequest request = new ContentListRequest("HADONG", "28", null, CompanionType.FAMILY, true, 0, 20);
            Region region = Region.HADONG;
            TourApiListResponse rawResponse = emptyListResponse();
            ContentListResponse expected = new ContentListResponse(0, 0, 20, List.of());

            given(tourApiClient.getAreaBasedList(
                    eq(region.getAreaCode()),
                    eq(region.getSigunguCode()),
                    eq("28"),
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
            given(tourApiClient.getDetailCommon(any()))
                    .willThrow(FeignException.class);

            // when & then
            assertThatThrownBy(() -> adapter.fetchDetail("2741429"))
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

- [ ] **Step 2: 테스트 실행 - 실패 확인**

```powershell
.\gradlew.bat test --tests "travel_agency.pick_trip.domain.content.adapter.TourApiContentAdapterTest" -q
```

Expected: FAIL (ContentListRequest 생성자 불일치 또는 필터 변환 로직 없음)

- [ ] **Step 3: TourApiContentAdapter 수정**

```java
package travel_agency.pick_trip.domain.content.adapter;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import travel_agency.pick_trip.domain.content.client.TourApiClient;
import travel_agency.pick_trip.domain.content.client.dto.TourApiDetailCommonResponse;
import travel_agency.pick_trip.domain.content.client.dto.TourApiDetailImageResponse;
import travel_agency.pick_trip.domain.content.client.dto.TourApiDetailIntroResponse;
import travel_agency.pick_trip.domain.content.client.dto.TourApiListResponse;
import travel_agency.pick_trip.domain.content.dto.request.ContentListRequest;
import travel_agency.pick_trip.domain.content.dto.response.ContentDetailResponse;
import travel_agency.pick_trip.domain.content.dto.response.ContentListResponse;
import travel_agency.pick_trip.domain.region.Region;
import travel_agency.pick_trip.gloal.error.ErrorCode;
import travel_agency.pick_trip.gloal.error.exception.ContentException;

@Component
@RequiredArgsConstructor
public class TourApiContentAdapter {

    private final TourApiClient tourApiClient;
    private final TourApiContentMapper mapper;

    public ContentListResponse fetchList(ContentListRequest request, Region region) {
        int pageNo = request.page() + 1;
        String effectiveContentTypeId = ContentTypeCategory.resolveContentTypeId(
                request.contentTypeId(), request.indoorOnly(), request.companion()
        );
        try {
            TourApiListResponse raw;
            if (request.keyword() != null && !request.keyword().isBlank()) {
                raw = tourApiClient.searchByKeyword(
                        request.keyword(),
                        region.getAreaCode(),
                        region.getSigunguCode(),
                        effectiveContentTypeId,
                        pageNo,
                        request.size()
                );
            } else {
                raw = tourApiClient.getAreaBasedList(
                        region.getAreaCode(),
                        region.getSigunguCode(),
                        effectiveContentTypeId,
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
            TourApiDetailCommonResponse common = tourApiClient.getDetailCommon(contentId);

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
            TourApiDetailImageResponse image = tourApiClient.getDetailImage(contentId);

            return mapper.toDetailResponse(common, intro, image);
        } catch (ContentException e) {
            throw e;
        } catch (FeignException e) {
            throw new ContentException(ErrorCode.CONTENT_PROVIDER_FAILED);
        }
    }
}
```

- [ ] **Step 4: 테스트 실행 - 통과 확인**

```powershell
.\gradlew.bat test --tests "travel_agency.pick_trip.domain.content.adapter.TourApiContentAdapterTest" -q
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 커밋**

```powershell
git add src/main/java/travel_agency/pick_trip/domain/content/dto/request/ContentListRequest.java
git add src/main/java/travel_agency/pick_trip/domain/content/controller/ContentController.java
git add src/main/java/travel_agency/pick_trip/domain/content/adapter/TourApiContentAdapter.java
git add src/test/java/travel_agency/pick_trip/domain/content/adapter/TourApiContentAdapterTest.java
git add src/test/java/travel_agency/pick_trip/domain/content/controller/ContentControllerTest.java
git commit -m "feat(content): 동행 조건·실내외 필터 파라미터 추가 및 contentTypeId 변환 로직 구현"
```

---

## Task 6: ContentDetailResponse에 stayDuration, reservationRequired, dataSource 추가

**Files:**
- Modify: `src/main/java/travel_agency/pick_trip/domain/content/dto/response/ContentDetailResponse.java`
- Modify: `src/main/java/travel_agency/pick_trip/domain/content/adapter/TourApiContentMapper.java`
- Modify: `src/test/java/travel_agency/pick_trip/domain/content/adapter/TourApiContentMapperTest.java`
- Modify: `src/test/java/travel_agency/pick_trip/domain/content/controller/ContentControllerTest.java`

- [ ] **Step 1: ContentDetailResponse 수정**

```java
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
        String stayDuration,
        Boolean reservationRequired,
        String dataSource,
        List<ImageItem> images
) {
    public record ImageItem(String imageUrl, String title) {}
}
```

- [ ] **Step 2: TourApiContentMapper 수정 — toDetailResponse 메서드**

`TourApiContentMapper.java`의 `toDetailResponse` 메서드를 아래와 같이 수정한다:

```java
public ContentDetailResponse toDetailResponse(
        TourApiDetailCommonResponse common,
        TourApiDetailIntroResponse intro,
        TourApiDetailImageResponse image
) {
    TourApiDetailCommonResponse.Item commonItem = extractFirst(common);
    TourApiDetailIntroResponse.Item introItem = extractFirst(intro);
    List<ContentDetailResponse.ImageItem> images = extractImages(image);
    int contentTypeId = parseIntOrZero(commonItem.contenttypeid());

    return new ContentDetailResponse(
            commonItem.contentid(),
            commonItem.title(),
            contentTypeId,
            buildAddress(commonItem.addr1(), commonItem.addr2()),
            commonItem.tel(),
            commonItem.homepage(),
            parseDouble(commonItem.mapy()),
            parseDouble(commonItem.mapx()),
            commonItem.overview(),
            introItem != null ? introItem.usetime() : null,
            introItem != null ? introItem.restdate() : null,
            introItem != null ? introItem.parking() : null,
            introItem != null ? introItem.usefee() : null,
            introItem != null ? introItem.chkbabycarriage() : null,
            introItem != null ? introItem.chkpet() : null,
            ContentTypeCategory.stayDurationFor(contentTypeId),
            null,
            "TourAPI",
            images
    );
}
```

- [ ] **Step 3: TourApiContentMapperTest 수정**

기존 `mergesThreeResponses` 테스트의 검증부에 새 필드 확인을 추가하고, ContentDetailResponse 생성자 호출부를 수정한다.

```java
// then 블록에 아래 검증 추가
assertThat(result.stayDuration()).isEqualTo("약 2시간"); // contentTypeId=12 (관광지)
assertThat(result.reservationRequired()).isNull();
assertThat(result.dataSource()).isEqualTo("TourAPI");
```

- [ ] **Step 4: ContentControllerTest의 ContentDetailResponse 생성자 수정**

`ContentControllerTest.java`에서 `ContentDetailResponse` 생성자 호출 시 새 필드를 추가한다:

```java
// 기존 (images 직전에 새 필드 3개 삽입)
ContentDetailResponse expected = new ContentDetailResponse(
        "2741429", "쌍계사", 12, "경상남도 하동군", "055-883-1901", "http://ssanggyesa.net",
        35.27, 127.58, "한국의 4대 총림", "03:00~18:00", "연중무휴",
        "가능", "성인 3,000원", "불가", "불가",
        "약 2시간", null, "TourAPI",    // 신규 필드
        List.of()
);
```

- [ ] **Step 5: 테스트 전체 실행 - 통과 확인**

```powershell
.\gradlew.bat test --tests "travel_agency.pick_trip.domain.content.*" -q
```

Expected: BUILD SUCCESSFUL, 전체 content 도메인 테스트 통과

- [ ] **Step 6: 커밋**

```powershell
git add src/main/java/travel_agency/pick_trip/domain/content/dto/response/ContentDetailResponse.java
git add src/main/java/travel_agency/pick_trip/domain/content/adapter/TourApiContentMapper.java
git add src/test/java/travel_agency/pick_trip/domain/content/adapter/TourApiContentMapperTest.java
git add src/test/java/travel_agency/pick_trip/domain/content/controller/ContentControllerTest.java
git commit -m "feat(content): 상세 조회 응답에 체류 시간·예약 여부·데이터 출처 필드 추가"
```

---

## Task 7: mvp-scope.md 체크박스 업데이트

**Files:**
- Modify: `.agents/docs/mvp-scope.md`

- [ ] **Step 1: 전체 테스트 통과 확인**

```powershell
.\gradlew.bat test -q
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 2: mvp-scope.md 체크박스 업데이트**

`.agents/docs/mvp-scope.md`에서 아래 3개 항목을 `[ ]` → `[x]`로 변경한다:

```markdown
- [x] 하동, 영주, 예천 지역 콘텐츠 조회
- [x] 콘텐츠 검색과 필터
- [x] 콘텐츠 상세 조회
```

- [ ] **Step 3: 커밋**

```powershell
git add .agents/docs/mvp-scope.md
git commit -m "docs: MVP 콘텐츠 조회·필터·상세 조건 달성 체크"
```
