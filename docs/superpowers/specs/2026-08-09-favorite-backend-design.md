# 찜하기(Favorite) 백엔드 설계

- 작성일: 2026-08-09
- 배경: 프론트엔드(`pick-trip-client`)가 찜하기 기능을 localStorage로 먼저 구현하며, 향후 백엔드 연동을 위한
  제안 계약(`src/stores/favoriteStore.ts` 주석)을 남겼다. 이 문서는 그 제안을 검증하고 실제 백엔드
  구현 스펙으로 확정한다.

## 프론트엔드 제안 검증 결과

- 제안: `POST/GET/DELETE /api/v1/favorites`, 생성 요청 필드 `{ contentId: string }`
  (basket의 `AddBasketItemRequest.contentId`와 동일 네이밍).
- 검증: `contentId`는 백엔드에서 이미 `String` 타입(TourAPI 자연키)으로 통용된다
  (`BasketItem.contentId`, `TravelContent.sourceContentId`). 타입/네이밍 모두 기존 컨벤션과 일치하므로
  프론트 제안은 그대로 채택한다.
- 추가로 확인된 제약: `ContentService`는 콘텐츠 상세/목록을 매번 TourAPI 라이브 호출로 만든다
  (`TravelContent` DB 테이블은 동기화용 보조 캐시일 뿐 서빙 경로에서 쓰이지 않음). 찜 목록 화면
  (`FavoritesClient.tsx`)은 카드 렌더링에 `Content` 타입 전체 필드(`name, region, category, imageUrl,
  address, summary, indoor`)가 필요하므로, basket과 동일하게 **찜할 때 표시용 스냅샷을 저장**하는 방식을
  채택한다 (목록 조회마다 TourAPI를 재호출하지 않기 위함).

## 아키텍처

`basket` 도메인과 병렬로 `domain/favorite` 패키지를 신규 생성한다. Basket은 "사용자당 1개 집계 엔티티(Basket)
+ 하위 items(BasketItem)" 구조이지만, 찜은 여행 조건 같은 집계 상태가 없으므로 **`Favorite` 테이블 하나
(사용자+콘텐츠 flat row)**로 충분하다.

```text
domain/favorite
├── controller/FavoriteController.java
├── dto/request/AddFavoriteRequest.java
├── dto/response/FavoriteResponse.java
├── dto/response/FavoritesResponse.java
├── entity/Favorite.java
├── repository/FavoriteRepository.java
└── service/FavoriteService.java
```

## Entity: `Favorite`

| 필드 | 타입 | 비고 |
|---|---|---|
| `id` | `UUID` (PK, `GenerationType.UUID`) | `userId`가 UUID이므로 basket/BasketItem과 동일하게 UUID 채택 |
| `userId` | `UUID` | |
| `contentId` | `String(50)` | TourAPI 자연키, 서버에서 별도 검증 없이 저장 (basket과 동일 정책) |
| `title` | `String` | 찜한 시점 스냅샷 |
| `firstImage` | `String` (nullable) | |
| `address` | `String` | |
| `category` | `ContentCategory` (nullable, `@Enumerated(STRING)`) | `content` 도메인 enum 재사용 |
| `summary` | `String` (`TEXT`, nullable) | |
| `indoor` | `Boolean` (nullable) | |
| `region` | `Region` (`@Enumerated(STRING)`, not null) | `region` 도메인 enum 재사용 |
| `createdAt` | `LocalDateTime` (`@CreationTimestamp`) | |

- Unique constraint: `uk_favorites_user_content` on `(user_id, content_id)` — 중복 찜 방지
  (`basket_items`의 `uk_basket_items_basket_content`와 동일 패턴).
- `@NoArgsConstructor(access = PROTECTED)`, `@Getter`, `@Builder`(private 생성자) — Entity 규칙 준수.
- Setter 없음. 찜은 수정 개념이 없어(추가/삭제만 존재) 상태 변경 메서드도 불필요.

## API

### `GET /api/v1/favorites`

- 인증 필요 (`@AuthenticationPrincipal JwtUserPrincipal`).
- 응답: `FavoritesResponse { items: FavoriteResponse[] }` — `createdAt` 최신순(desc) 정렬.
- 찜이 하나도 없으면 `items: []`.

### `POST /api/v1/favorites` (201 Created)

- 요청 바디: `AddFavoriteRequest`

  ```java
  public record AddFavoriteRequest(
          @NotBlank String contentId,
          @NotBlank String title,
          @NotBlank String address,
          String firstImage,
          ContentCategory category,
          String summary,
          Boolean indoor,
          @NotNull Region region
  ) {}
  ```

- 이미 찜한 콘텐츠(`userId` + `contentId` 중복)면 `FAVORITE_DUPLICATE` (409) 예외.
- 응답: `FavoriteResponse` (생성된 항목 1건).

### `DELETE /api/v1/favorites/{contentId}` (204 No Content)

- `contentId`로 식별 (UUID PK가 아님) — 프론트 스토어의 `remove(contentId)` 시그니처와 그대로 매칭되고,
  찜 여부는 사용자+콘텐츠 조합으로 유일하므로 대표성 문제가 없다.
- 대상이 없으면 `FAVORITE_NOT_FOUND` (404) 예외.

### 응답 DTO 필드 네이밍

`FavoriteResponse`는 프론트가 이미 `/api/v1/contents` 응답에서 사용 중인 필드명
(`contentId, title, address, firstImage, category, summary, indoor, region`)을 그대로 따른다
(`pick-trip-client/src/services/contentService.ts`의 `RawContentItem`/`toContent()` 참고). 이렇게 하면
프론트가 찜 API 연동 시 기존 매퍼를 거의 그대로 재사용할 수 있다.

```java
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
) {}

public record FavoritesResponse(List<FavoriteResponse> items) {}
```

## 에러 처리

`.agents/skills/picktrip-error-handling/SKILL.md` 패턴을 따른다.

- `FavoriteException extends PickTripException`
- `ErrorCode.FAVORITE_DUPLICATE` — `HttpStatus.CONFLICT`, "이미 찜한 콘텐츠입니다."
- `ErrorCode.FAVORITE_NOT_FOUND` — `HttpStatus.NOT_FOUND`, "찜한 콘텐츠를 찾을 수 없습니다."

## 인증/인가

Basket과 동일하게 로그인 사용자만 접근 가능(`AGENTS.md`의 "일정 저장·공유는 인증 사용자에게만" 원칙과 동일선상).
비로그인 사용자의 찜하기는 프론트가 이미 라우트 가드로 처리 중(`FavoritesClient.tsx`의 `unauthenticated` 리다이렉트).

## 테스트 계획

- `FavoriteServiceTest` — Mockito(`@Mock`/`@InjectMocks`), Given/When/Then, AssertJ. basket의
  `BasketServiceTest`와 동일한 형식.
  - 정상 추가
  - 중복 추가 시 `FAVORITE_DUPLICATE`
  - 목록 조회(빈 목록 포함)
  - 정상 삭제
  - 존재하지 않는 항목 삭제 시 `FAVORITE_NOT_FOUND`

## 범위 밖

- 프론트엔드 연동 코드(실제 fetch 호출, `favoriteStore.ts` 변경)는 이 작업 범위에 포함하지 않는다.
  백엔드 API만 구현한다.
- 콘텐츠 원본이 삭제/변경되었을 때 기존 찜 스냅샷을 갱신하는 배치는 basket과 마찬가지로 범위 밖이다.
