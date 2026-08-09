# 찜하기(Favorite) 백엔드 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 프론트엔드가 제안한 찜하기 백엔드 계약(`GET/POST/DELETE /api/v1/favorites`, `contentId: string`)을 실제로 구현한다.

**Architecture:** `basket` 도메인과 병렬로 `domain/favorite` 패키지를 신규 생성한다. Basket과 달리 여행 조건 같은 집계 상태가 없으므로, 사용자+콘텐츠 조합의 flat row(`Favorite`) 하나로 표현한다. 찜할 때 카드 렌더링에 필요한 표시용 필드(title/address/firstImage/category/summary/indoor/region)를 스냅샷으로 저장해 목록 조회 시 TourAPI 재호출이 없도록 한다.

**Tech Stack:** Java 21, Spring Boot MVC, Spring Data JPA, Lombok, JUnit 5 + Mockito + AssertJ.

## Global Constraints

- `jakarta.*`를 사용한다 (`javax.*` 금지).
- Entity에 `@Setter`, `@Data` 금지. `@NoArgsConstructor(access = AccessLevel.PROTECTED)` + `@Builder` 사용.
- Controller는 Entity를 직접 반환하지 않는다. DTO(`record`)로만 응답한다.
- Controller에 `@Transactional` 금지. 조회는 `@Transactional(readOnly = true)`, 변경은 `@Transactional`.
- 새 도메인 예외는 `PickTripException`을 상속하고, `ErrorCode` enum에 `FAVORITE_` 접두사로 추가한다.
- 모든 테스트는 Given/When/Then 형식, `@DisplayName`은 한국어, AssertJ 사용, Mockito는 `@Mock`+`@InjectMocks`.
- `contentId`는 TourAPI 자연키(`String`, 서버 검증 없이 저장) — `BasketItem.contentId`와 동일 정책.
- 커밋 메시지 끝에 `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>`를 추가한다.
- 이미 `feat/favorite-backend` 브랜치에 있으므로 각 태스크는 이 브랜치에 커밋한다 (별도 브랜치 생성 불필요).

---

### Task 1: ErrorCode / FavoriteException 추가

**Files:**
- Modify: `src/main/java/travel_agency/pick_trip/gloal/error/ErrorCode.java`
- Create: `src/main/java/travel_agency/pick_trip/gloal/error/exception/FavoriteException.java`

**Interfaces:**
- Produces: `ErrorCode.FAVORITE_NOT_FOUND`, `ErrorCode.FAVORITE_DUPLICATE`, `FavoriteException(ErrorCode)` — Task 4(Service)가 이 두 상수와 예외 클래스를 그대로 사용한다.

- [ ] **Step 1: ErrorCode에 Favorite 항목 추가**

`ErrorCode.java`의 `// Basket` 블록과 `// Share` 블록 사이에 다음을 삽입한다 (기존 `BASKET_ITEM_DUPLICATE(...)` 줄 바로 다음):

```java
    // Favorite
    FAVORITE_NOT_FOUND(HttpStatus.NOT_FOUND, "찜한 콘텐츠를 찾을 수 없습니다."),
    FAVORITE_DUPLICATE(HttpStatus.CONFLICT, "이미 찜한 콘텐츠입니다."),
```

- [ ] **Step 2: FavoriteException 클래스 작성**

`src/main/java/travel_agency/pick_trip/gloal/error/exception/FavoriteException.java`:

```java
package travel_agency.pick_trip.gloal.error.exception;

import travel_agency.pick_trip.gloal.error.ErrorCode;

public class FavoriteException extends PickTripException {
    public FavoriteException(ErrorCode errorCode) {
        super(errorCode);
    }
}
```

- [ ] **Step 3: 컴파일 확인**

Run: `.\gradlew.bat compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/travel_agency/pick_trip/gloal/error/ErrorCode.java src/main/java/travel_agency/pick_trip/gloal/error/exception/FavoriteException.java
git commit -m "feat(favorite): 에러 코드 및 예외 클래스 추가

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 2: Favorite 엔티티 / 리포지토리 / DTO 생성

**Files:**
- Create: `src/main/java/travel_agency/pick_trip/domain/favorite/entity/Favorite.java`
- Create: `src/main/java/travel_agency/pick_trip/domain/favorite/repository/FavoriteRepository.java`
- Create: `src/main/java/travel_agency/pick_trip/domain/favorite/dto/request/AddFavoriteRequest.java`
- Create: `src/main/java/travel_agency/pick_trip/domain/favorite/dto/response/FavoriteResponse.java`
- Create: `src/main/java/travel_agency/pick_trip/domain/favorite/dto/response/FavoritesResponse.java`

**Interfaces:**
- Consumes: `travel_agency.pick_trip.domain.content.entity.ContentCategory` (기존), `travel_agency.pick_trip.domain.region.Region` (기존).
- Produces:
  - `Favorite` 엔티티 — 필드: `id(UUID)`, `userId(UUID)`, `contentId(String)`, `title(String)`, `address(String)`, `firstImage(String, nullable)`, `category(ContentCategory, nullable)`, `summary(String, nullable)`, `indoor(Boolean, nullable)`, `region(Region)`, `createdAt(LocalDateTime)`. `Favorite.builder()`로 생성.
  - `FavoriteRepository`: `findAllByUserIdOrderByCreatedAtDesc(UUID)`, `findByUserIdAndContentId(UUID, String)`, `existsByUserIdAndContentId(UUID, String)`.
  - `AddFavoriteRequest(String contentId, String title, String address, String firstImage, ContentCategory category, String summary, Boolean indoor, Region region)`.
  - `FavoriteResponse(UUID id, String contentId, String title, String address, String firstImage, ContentCategory category, String summary, Boolean indoor, Region region, LocalDateTime createdAt)` + `static FavoriteResponse from(Favorite)`.
  - `FavoritesResponse(List<FavoriteResponse> items)`.
- 이 산출물은 Task 4(Service)와 Task 5(Controller)가 그대로 사용한다.

- [ ] **Step 1: Favorite 엔티티 작성**

`src/main/java/travel_agency/pick_trip/domain/favorite/entity/Favorite.java`:

```java
package travel_agency.pick_trip.domain.favorite.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import travel_agency.pick_trip.domain.content.entity.ContentCategory;
import travel_agency.pick_trip.domain.region.Region;

/**
 * 사용자가 찜한 콘텐츠.
 * content는 DB에 영속화하지 않으므로 TourAPI contentId(문자열)를 그대로 저장하고,
 * 찜 목록 조회 시 TourAPI 재조회 없이 표시할 수 있도록 표시용 스냅샷을 함께 보관한다
 * (basket_items와 동일 정책).
 */
@Getter
@Entity
@Table(
        name = "favorites",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_favorites_user_content",
                        columnNames = {"user_id", "content_id"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Favorite {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "content_id", nullable = false, length = 50)
    private String contentId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 500)
    private String address;

    @Column(name = "first_image", length = 500)
    private String firstImage;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ContentCategory category;

    @Column(columnDefinition = "TEXT")
    private String summary;

    private Boolean indoor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Region region;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private Favorite(
            UUID userId,
            String contentId,
            String title,
            String address,
            String firstImage,
            ContentCategory category,
            String summary,
            Boolean indoor,
            Region region
    ) {
        this.userId = userId;
        this.contentId = contentId;
        this.title = title;
        this.address = address;
        this.firstImage = firstImage;
        this.category = category;
        this.summary = summary;
        this.indoor = indoor;
        this.region = region;
    }
}
```

- [ ] **Step 2: FavoriteRepository 작성**

`src/main/java/travel_agency/pick_trip/domain/favorite/repository/FavoriteRepository.java`:

```java
package travel_agency.pick_trip.domain.favorite.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import travel_agency.pick_trip.domain.favorite.entity.Favorite;

@Repository
public interface FavoriteRepository extends JpaRepository<Favorite, UUID> {

    List<Favorite> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<Favorite> findByUserIdAndContentId(UUID userId, String contentId);

    boolean existsByUserIdAndContentId(UUID userId, String contentId);
}
```

- [ ] **Step 3: AddFavoriteRequest 작성**

`src/main/java/travel_agency/pick_trip/domain/favorite/dto/request/AddFavoriteRequest.java`:

```java
package travel_agency.pick_trip.domain.favorite.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import travel_agency.pick_trip.domain.content.entity.ContentCategory;
import travel_agency.pick_trip.domain.region.Region;

/**
 * 콘텐츠를 찜하는 요청.
 * contentId는 TourAPI ID이며 서버에서 별도 검증 없이 저장한다.
 * title/address 외 나머지는 목록 화면에서 확보한 표시용 스냅샷(선택값)이다.
 */
public record AddFavoriteRequest(
        @NotBlank String contentId,
        @NotBlank String title,
        @NotBlank String address,
        String firstImage,
        ContentCategory category,
        String summary,
        Boolean indoor,
        @NotNull Region region
) {
}
```

- [ ] **Step 4: FavoriteResponse / FavoritesResponse 작성**

`src/main/java/travel_agency/pick_trip/domain/favorite/dto/response/FavoriteResponse.java`:

```java
package travel_agency.pick_trip.domain.favorite.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;
import travel_agency.pick_trip.domain.content.entity.ContentCategory;
import travel_agency.pick_trip.domain.favorite.entity.Favorite;
import travel_agency.pick_trip.domain.region.Region;

/**
 * 찜 항목 응답. 필드명은 /api/v1/contents 응답 규약(contentId/title/address/firstImage/...)을 따라
 * 프론트가 기존 매퍼를 재사용할 수 있게 한다.
 */
public record FavoriteResponse(
        UUID id,
        String contentId,
        String title,
        String address,
        String firstImage,
        ContentCategory category,
        String summary,
        Boolean indoor,
        Region region,
        LocalDateTime createdAt
) {

    public static FavoriteResponse from(Favorite favorite) {
        return new FavoriteResponse(
                favorite.getId(),
                favorite.getContentId(),
                favorite.getTitle(),
                favorite.getAddress(),
                favorite.getFirstImage(),
                favorite.getCategory(),
                favorite.getSummary(),
                favorite.getIndoor(),
                favorite.getRegion(),
                favorite.getCreatedAt()
        );
    }
}
```

`src/main/java/travel_agency/pick_trip/domain/favorite/dto/response/FavoritesResponse.java`:

```java
package travel_agency.pick_trip.domain.favorite.dto.response;

import java.util.List;

public record FavoritesResponse(List<FavoriteResponse> items) {
}
```

- [ ] **Step 5: 컴파일 확인**

Run: `.\gradlew.bat compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/travel_agency/pick_trip/domain/favorite
git commit -m "feat(favorite): Favorite 엔티티/리포지토리/DTO 추가

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 3: SecurityConfig에 `/api/v1/favorites/**` 인증 규칙 추가

찜하기는 로그인 사용자만 접근해야 한다. `SecurityConfig`의 매칭 목록에 추가하지 않으면 마지막 `.anyRequest().permitAll()`에 걸려 **비로그인 접근이 허용되는 보안 결함**이 생기므로 반드시 필요하다.

**Files:**
- Modify: `src/main/java/travel_agency/pick_trip/gloal/security/SecurityConfig.java:72`

**Interfaces:**
- Consumes: 없음 (URL 패턴 매칭만 추가).
- Produces: `/api/v1/favorites/**` 경로가 `authenticated()`로 보호됨 — Task 5의 컨트롤러 테스트가 실제 인증 필터를 타지 않고 컨트롤러를 직접 호출하는 방식(basket과 동일)이라 이 설정 자체를 검증하는 자동화 테스트는 없다. 수동으로 diff를 확인한다.

- [ ] **Step 1: 인증 규칙 라인 추가**

`SecurityConfig.java`의 다음 부분:

```java
                        // Basket - 여행 바구니는 로그인 필요
                        .requestMatchers("/api/v1/baskets/**").authenticated()
```

바로 다음 줄에 추가:

```java
                        // Favorite - 찜하기는 로그인 필요
                        .requestMatchers("/api/v1/favorites/**").authenticated()
```

- [ ] **Step 2: 컴파일 확인**

Run: `.\gradlew.bat compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 추가된 줄 확인**

Run: `git diff src/main/java/travel_agency/pick_trip/gloal/security/SecurityConfig.java`
Expected: `/api/v1/favorites/**` 를 `authenticated()`로 매칭하는 라인 하나만 추가됨.

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/travel_agency/pick_trip/gloal/security/SecurityConfig.java
git commit -m "feat(favorite): 찜하기 API 인증 필요 규칙 추가

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 4: FavoriteService 구현 (TDD)

**Files:**
- Create: `src/test/java/travel_agency/pick_trip/domain/favorite/service/FavoriteServiceTest.java`
- Create: `src/main/java/travel_agency/pick_trip/domain/favorite/service/FavoriteService.java`

**Interfaces:**
- Consumes: Task 1의 `ErrorCode.FAVORITE_NOT_FOUND`/`FAVORITE_DUPLICATE`/`FavoriteException`, Task 2의 `Favorite`/`FavoriteRepository`/`AddFavoriteRequest`/`FavoriteResponse`/`FavoritesResponse`.
- Produces: `FavoriteService`
  - `FavoritesResponse getFavorites(UUID userId)`
  - `FavoriteResponse addFavorite(UUID userId, AddFavoriteRequest request)`
  - `void removeFavorite(UUID userId, String contentId)`
  — Task 5(Controller)가 이 세 메서드 시그니처를 그대로 사용한다.

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/java/travel_agency/pick_trip/domain/favorite/service/FavoriteServiceTest.java`:

```java
package travel_agency.pick_trip.domain.favorite.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import travel_agency.pick_trip.domain.content.entity.ContentCategory;
import travel_agency.pick_trip.domain.favorite.dto.request.AddFavoriteRequest;
import travel_agency.pick_trip.domain.favorite.dto.response.FavoriteResponse;
import travel_agency.pick_trip.domain.favorite.dto.response.FavoritesResponse;
import travel_agency.pick_trip.domain.favorite.entity.Favorite;
import travel_agency.pick_trip.domain.favorite.repository.FavoriteRepository;
import travel_agency.pick_trip.domain.region.Region;
import travel_agency.pick_trip.gloal.error.ErrorCode;
import travel_agency.pick_trip.gloal.error.exception.PickTripException;

@ExtendWith(MockitoExtension.class)
@DisplayName("FavoriteService")
class FavoriteServiceTest {

    @Mock private FavoriteRepository favoriteRepository;
    @InjectMocks private FavoriteService favoriteService;

    private static final UUID USER_ID = UUID.randomUUID();

    private Favorite newFavorite(String contentId) {
        return Favorite.builder()
                .userId(USER_ID)
                .contentId(contentId)
                .title("쌍계사")
                .address("경남 하동군")
                .firstImage("https://img/1.jpg")
                .category(ContentCategory.ATTRACTION)
                .summary("천년 고찰")
                .indoor(false)
                .region(Region.HADONG)
                .build();
    }

    @Nested
    @DisplayName("getFavorites")
    class GetFavorites {

        @Test
        @DisplayName("찜한 콘텐츠가 있으면 최신순으로 정렬된 목록을 반환한다")
        void hasFavorites_returnsResponse() {
            // given
            given(favoriteRepository.findAllByUserIdOrderByCreatedAtDesc(USER_ID))
                    .willReturn(List.of(newFavorite("126508")));

            // when
            FavoritesResponse response = favoriteService.getFavorites(USER_ID);

            // then
            assertThat(response.items()).hasSize(1);
            assertThat(response.items().get(0).contentId()).isEqualTo("126508");
        }

        @Test
        @DisplayName("찜한 콘텐츠가 없으면 빈 목록을 반환한다")
        void noFavorites_returnsEmptyResponse() {
            // given
            given(favoriteRepository.findAllByUserIdOrderByCreatedAtDesc(USER_ID))
                    .willReturn(List.of());

            // when
            FavoritesResponse response = favoriteService.getFavorites(USER_ID);

            // then
            assertThat(response.items()).isEmpty();
        }
    }

    @Nested
    @DisplayName("addFavorite")
    class AddFavorite {

        private final AddFavoriteRequest request = new AddFavoriteRequest(
                "126508", "쌍계사", "경남 하동군", "https://img/1.jpg",
                ContentCategory.ATTRACTION, "천년 고찰", false, Region.HADONG
        );

        @Test
        @DisplayName("중복이 아니면 찜을 추가한다")
        void noDuplicate_addsFavorite() {
            // given
            given(favoriteRepository.existsByUserIdAndContentId(USER_ID, "126508")).willReturn(false);
            given(favoriteRepository.save(any(Favorite.class))).willAnswer(invocation -> invocation.getArgument(0));

            // when
            FavoriteResponse response = favoriteService.addFavorite(USER_ID, request);

            // then
            assertThat(response.contentId()).isEqualTo("126508");
            assertThat(response.title()).isEqualTo("쌍계사");
            assertThat(response.region()).isEqualTo(Region.HADONG);
        }

        @Test
        @DisplayName("이미 찜한 콘텐츠를 추가하면 FAVORITE_DUPLICATE 예외를 던진다")
        void duplicate_throwsException() {
            // given
            given(favoriteRepository.existsByUserIdAndContentId(USER_ID, "126508")).willReturn(true);

            // when
            ThrowableAssert.ThrowingCallable action = () -> favoriteService.addFavorite(USER_ID, request);

            // then
            assertThatThrownBy(action)
                    .isInstanceOf(PickTripException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.FAVORITE_DUPLICATE);
            verify(favoriteRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("removeFavorite")
    class RemoveFavorite {

        @Test
        @DisplayName("찜한 콘텐츠가 있으면 삭제한다")
        void favoriteExists_removesFavorite() {
            // given
            Favorite favorite = newFavorite("126508");
            given(favoriteRepository.findByUserIdAndContentId(USER_ID, "126508"))
                    .willReturn(Optional.of(favorite));

            // when
            favoriteService.removeFavorite(USER_ID, "126508");

            // then
            verify(favoriteRepository).delete(favorite);
        }

        @Test
        @DisplayName("찜한 콘텐츠가 없으면 FAVORITE_NOT_FOUND 예외를 던진다")
        void favoriteMissing_throwsException() {
            // given
            given(favoriteRepository.findByUserIdAndContentId(USER_ID, "126508"))
                    .willReturn(Optional.empty());

            // when
            ThrowableAssert.ThrowingCallable action = () -> favoriteService.removeFavorite(USER_ID, "126508");

            // then
            assertThatThrownBy(action)
                    .isInstanceOf(PickTripException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.FAVORITE_NOT_FOUND);
        }
    }
}
```

- [ ] **Step 2: 테스트 실행 → 컴파일 실패로 실패하는지 확인**

Run: `.\gradlew.bat test --tests "travel_agency.pick_trip.domain.favorite.service.FavoriteServiceTest"`
Expected: FAIL — `FavoriteService` 클래스가 없어 컴파일 에러.

- [ ] **Step 3: FavoriteService 최소 구현 작성**

`src/main/java/travel_agency/pick_trip/domain/favorite/service/FavoriteService.java`:

```java
package travel_agency.pick_trip.domain.favorite.service;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import travel_agency.pick_trip.domain.favorite.dto.request.AddFavoriteRequest;
import travel_agency.pick_trip.domain.favorite.dto.response.FavoriteResponse;
import travel_agency.pick_trip.domain.favorite.dto.response.FavoritesResponse;
import travel_agency.pick_trip.domain.favorite.entity.Favorite;
import travel_agency.pick_trip.domain.favorite.repository.FavoriteRepository;
import travel_agency.pick_trip.gloal.error.ErrorCode;
import travel_agency.pick_trip.gloal.error.exception.FavoriteException;

/**
 * 찜하기 유스케이스.
 */
@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;

    @Transactional(readOnly = true)
    public FavoritesResponse getFavorites(UUID userId) {
        List<FavoriteResponse> items = favoriteRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(FavoriteResponse::from)
                .toList();
        return new FavoritesResponse(items);
    }

    @Transactional
    public FavoriteResponse addFavorite(UUID userId, AddFavoriteRequest request) {
        if (favoriteRepository.existsByUserIdAndContentId(userId, request.contentId())) {
            throw new FavoriteException(ErrorCode.FAVORITE_DUPLICATE);
        }
        Favorite favorite = Favorite.builder()
                .userId(userId)
                .contentId(request.contentId())
                .title(request.title())
                .address(request.address())
                .firstImage(request.firstImage())
                .category(request.category())
                .summary(request.summary())
                .indoor(request.indoor())
                .region(request.region())
                .build();
        return FavoriteResponse.from(favoriteRepository.save(favorite));
    }

    @Transactional
    public void removeFavorite(UUID userId, String contentId) {
        Favorite favorite = favoriteRepository.findByUserIdAndContentId(userId, contentId)
                .orElseThrow(() -> new FavoriteException(ErrorCode.FAVORITE_NOT_FOUND));
        favoriteRepository.delete(favorite);
    }
}
```

- [ ] **Step 4: 테스트 실행 → 통과 확인**

Run: `.\gradlew.bat test --tests "travel_agency.pick_trip.domain.favorite.service.FavoriteServiceTest"`
Expected: BUILD SUCCESSFUL, 6개 테스트 모두 PASS.

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/travel_agency/pick_trip/domain/favorite/service src/test/java/travel_agency/pick_trip/domain/favorite/service
git commit -m "feat(favorite): FavoriteService 구현

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 5: FavoriteController 구현 (TDD)

**Files:**
- Create: `src/test/java/travel_agency/pick_trip/domain/favorite/controller/FavoriteControllerTest.java`
- Create: `src/main/java/travel_agency/pick_trip/domain/favorite/controller/FavoriteController.java`

**Interfaces:**
- Consumes: Task 4의 `FavoriteService.getFavorites/addFavorite/removeFavorite`, Task 2의 DTO, `travel_agency.pick_trip.gloal.jwt.JwtUserPrincipal`(기존, `getUid(): UUID`).
- Produces: `FavoriteController` — `GET /api/v1/favorites`, `POST /api/v1/favorites`, `DELETE /api/v1/favorites/{contentId}`. 이후 태스크 없음(최종 산출물).

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/java/travel_agency/pick_trip/domain/favorite/controller/FavoriteControllerTest.java`:

```java
package travel_agency.pick_trip.domain.favorite.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.jsonwebtoken.Claims;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import travel_agency.pick_trip.domain.content.entity.ContentCategory;
import travel_agency.pick_trip.domain.favorite.dto.request.AddFavoriteRequest;
import travel_agency.pick_trip.domain.favorite.dto.response.FavoriteResponse;
import travel_agency.pick_trip.domain.favorite.dto.response.FavoritesResponse;
import travel_agency.pick_trip.domain.favorite.service.FavoriteService;
import travel_agency.pick_trip.domain.region.Region;
import travel_agency.pick_trip.gloal.jwt.JwtUserPrincipal;

@ExtendWith(MockitoExtension.class)
@DisplayName("FavoriteController")
class FavoriteControllerTest {

    @Mock private FavoriteService favoriteService;
    @InjectMocks private FavoriteController favoriteController;

    private static final UUID USER_UID = UUID.randomUUID();

    // standaloneSetup에서 @AuthenticationPrincipal 주입이 불안정하므로 컨트롤러를 직접 호출한다
    private JwtUserPrincipal principal() {
        Claims claims = mock(Claims.class);
        given(claims.getSubject()).willReturn(USER_UID.toString());
        given(claims.get("role", String.class)).willReturn("USER");
        return JwtUserPrincipal.from(claims);
    }

    private FavoriteResponse favoriteResponse(String contentId) {
        return new FavoriteResponse(
                UUID.randomUUID(), contentId, "쌍계사", "경남 하동군", "https://img/1.jpg",
                ContentCategory.ATTRACTION, "천년 고찰", false, Region.HADONG, LocalDateTime.now()
        );
    }

    @Nested
    @DisplayName("GET /api/v1/favorites")
    class GetFavorites {

        @Test
        @DisplayName("인증된 사용자가 요청하면 200과 찜 목록을 반환한다")
        void authenticated_returns200WithFavorites() {
            // given
            FavoritesResponse expected = new FavoritesResponse(List.of(favoriteResponse("126508")));
            given(favoriteService.getFavorites(USER_UID)).willReturn(expected);

            // when
            ResponseEntity<FavoritesResponse> result = favoriteController.getFavorites(principal());

            // then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(result.getBody()).isNotNull();
            assertThat(result.getBody().items()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("POST /api/v1/favorites")
    class AddFavorite {

        @Test
        @DisplayName("콘텐츠를 찜하면 201과 추가된 항목을 반환한다")
        void addFavorite_returns201() {
            // given
            AddFavoriteRequest request = new AddFavoriteRequest(
                    "126508", "쌍계사", "경남 하동군", "https://img/1.jpg",
                    ContentCategory.ATTRACTION, "천년 고찰", false, Region.HADONG
            );
            given(favoriteService.addFavorite(USER_UID, request)).willReturn(favoriteResponse("126508"));

            // when
            ResponseEntity<FavoriteResponse> result = favoriteController.addFavorite(principal(), request);

            // then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(result.getBody()).isNotNull();
            assertThat(result.getBody().contentId()).isEqualTo("126508");
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/favorites/{contentId}")
    class RemoveFavorite {

        @Test
        @DisplayName("찜을 해제하면 204를 반환하고 서비스에 위임한다")
        void removeFavorite_returns204() {
            // when
            ResponseEntity<Void> result = favoriteController.removeFavorite(principal(), "126508");

            // then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            verify(favoriteService).removeFavorite(USER_UID, "126508");
        }
    }
}
```

- [ ] **Step 2: 테스트 실행 → 컴파일 실패로 실패하는지 확인**

Run: `.\gradlew.bat test --tests "travel_agency.pick_trip.domain.favorite.controller.FavoriteControllerTest"`
Expected: FAIL — `FavoriteController` 클래스가 없어 컴파일 에러.

- [ ] **Step 3: FavoriteController 작성**

`src/main/java/travel_agency/pick_trip/domain/favorite/controller/FavoriteController.java`:

```java
package travel_agency.pick_trip.domain.favorite.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import travel_agency.pick_trip.domain.favorite.dto.request.AddFavoriteRequest;
import travel_agency.pick_trip.domain.favorite.dto.response.FavoriteResponse;
import travel_agency.pick_trip.domain.favorite.dto.response.FavoritesResponse;
import travel_agency.pick_trip.domain.favorite.service.FavoriteService;
import travel_agency.pick_trip.gloal.jwt.JwtUserPrincipal;

@RestController
@RequestMapping("/api/v1/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @GetMapping
    public ResponseEntity<FavoritesResponse> getFavorites(
            @AuthenticationPrincipal JwtUserPrincipal principal
    ) {
        return ResponseEntity.ok(favoriteService.getFavorites(principal.getUid()));
    }

    @PostMapping
    public ResponseEntity<FavoriteResponse> addFavorite(
            @AuthenticationPrincipal JwtUserPrincipal principal,
            @Valid @RequestBody AddFavoriteRequest request
    ) {
        FavoriteResponse response = favoriteService.addFavorite(principal.getUid(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{contentId}")
    public ResponseEntity<Void> removeFavorite(
            @AuthenticationPrincipal JwtUserPrincipal principal,
            @PathVariable String contentId
    ) {
        favoriteService.removeFavorite(principal.getUid(), contentId);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 4: 테스트 실행 → 통과 확인**

Run: `.\gradlew.bat test --tests "travel_agency.pick_trip.domain.favorite.controller.FavoriteControllerTest"`
Expected: BUILD SUCCESSFUL, 3개 테스트 모두 PASS.

- [ ] **Step 5: 전체 테스트 스위트 실행 (회귀 확인)**

Run: `.\gradlew.bat test`
Expected: BUILD SUCCESSFUL — 기존 basket/content/itinerary 등 다른 도메인 테스트도 모두 PASS.

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/travel_agency/pick_trip/domain/favorite/controller src/test/java/travel_agency/pick_trip/domain/favorite/controller
git commit -m "feat(favorite): FavoriteController 구현

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## 완료 후 확인 사항 (참고용, 별도 태스크 아님)

- 새 엔드포인트: `GET/POST /api/v1/favorites`, `DELETE /api/v1/favorites/{contentId}`.
- `ddl-auto`가 `validate`/`none`이므로 `favorites` 테이블은 별도 마이그레이션(Flyway/수동 DDL)이 필요할 수 있다 — 이 저장소에 마이그레이션 도구 설정이 있는지 확인 후, 있다면 해당 방식으로 테이블 생성 스크립트를 추가한다. 없다면 로컬 개발 DB에서 Hibernate가 아닌 수동 DDL로 테이블을 만들어야 한다 (운영 `create`/`create-drop` 금지 규칙 때문).
- 이 플랜은 백엔드 API만 다룬다. 프론트엔드(`pick-trip-client`)의 `favoriteStore.ts`를 실제 API 호출로 바꾸는 작업은 별도 프론트엔드 작업이다.
