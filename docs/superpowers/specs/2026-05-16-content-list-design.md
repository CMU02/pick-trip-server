# 콘텐츠 조회 API 설계 스펙

**날짜:** 2026-05-16  
**브랜치:** feat/content-list  
**범위:** 하동/영주/예천 지역 콘텐츠 목록, 검색/필터, 상세 조회

---

## 1. 배경 및 목적

MVP에서 사용자가 여행지(하동, 영주, 예천)의 콘텐츠를 탐색할 수 있어야 한다.  
인증 없이 접근 가능하며, 비로그인 사용자도 콘텐츠를 검색하고 상세 정보를 볼 수 있다.

---

## 2. 아키텍처 결정

### 데이터 소스
- **TourAPI 직접 호출** (영구 아키텍처로 확정)
- 별도 DB 저장 없이 TourAPI 응답을 정규화하여 반환
- 향후 응답속도 개선은 Redis 캐싱으로 진행 예정

### 레이어 구조 (어댑터 패턴)
```
ContentController
  └── ContentService          (비즈니스 로직, 검색/목록 분기)
        └── TourApiContentAdapter  (TourAPI 파라미터 변환 + 응답 정규화)
              └── TourApiClient   (OpenFeign, TourAPI 호출)
```

### 지역 코드 관리
DB 테이블 대신 Enum으로 관리한다. TourAPI 코드 매핑 포함.

```java
public enum Region {
    HADONG("하동", "38", "730"),   // 경남 하동군
    YEONGJU("영주", "35", "220"),  // 경북 영주시
    YECHEON("예천", "35", "260");  // 경북 예천군
}
```
> areaCode/sigunguCode는 TourAPI /areaCode2 확인 후 정확값으로 교체

---

## 3. API 스펙

### 3.1 목록 조회

```
GET /api/v1/contents
인증 필요: X (비로그인 허용)
```

**Query Parameters:**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|:---:|-------|-----|
| `region` | `HADONG\|YEONGJU\|YECHEON` | ✓ | - | 지역 |
| `contentTypeId` | `int` | - | 전체 | 12(관광지), 14(문화시설), 15(축제), 28(레포츠), 38(쇼핑), 39(음식점) |
| `keyword` | `String` | - | - | 검색어 |
| `page` | `int` | - | 0 | 페이지 번호 |
| `size` | `int` | - | 20 | 페이지 크기 (최대 40) |

**분기 로직:**
- `keyword`가 있으면 → TourAPI `/searchKeyword2` 호출
- `keyword`가 없으면 → TourAPI `/areaBasedList2` 호출

**Response Body:**
```json
{
  "totalCount": 150,
  "page": 0,
  "size": 20,
  "items": [
    {
      "contentId": "2741429",
      "title": "쌍계사",
      "contentTypeId": 12,
      "address": "경상남도 하동군 화개면 쌍계사길 59",
      "firstImage": "https://tong.visitkorea.or.kr/...",
      "latitude": 35.27,
      "longitude": 127.58
    }
  ]
}
```

---

### 3.2 상세 조회

```
GET /api/v1/contents/{contentId}
인증 필요: X (비로그인 허용)
```

TourAPI 3개 병렬 호출 후 병합:
- `/detailCommon2` → 기본 정보 (주소, 좌표, 개요, 홈페이지)
- `/detailIntro2` → 운영 정보 (이용시간, 휴무, 주차, 요금, 유모차, 반려동물)
- `/detailImage2` → 이미지 목록

**Response Body:**
```json
{
  "contentId": "2741429",
  "title": "쌍계사",
  "contentTypeId": 12,
  "address": "경상남도 하동군 화개면 쌍계사길 59",
  "tel": "055-883-1901",
  "homepage": "http://www.ssanggyesa.net",
  "latitude": 35.27,
  "longitude": 127.58,
  "summary": "한국의 4대 총림 중 하나인 사찰...",
  "useTime": "03:00~18:00",
  "restDate": "연중무휴",
  "parking": "가능",
  "useFee": "성인 3,000원 / 청소년 1,500원",
  "chkBabyCarriage": "불가",
  "chkPet": "불가",
  "images": [
    {
      "imageUrl": "https://tong.visitkorea.or.kr/...",
      "title": "쌍계사 대웅전"
    }
  ]
}
```

---

## 4. 패키지 구조

```
domain/content/
├── controller/
│   └── ContentController.java
├── service/
│   └── ContentService.java
├── adapter/
│   ├── TourApiContentAdapter.java      ← TourAPI 호출 오케스트레이션
│   └── TourApiContentMapper.java       ← TourAPI raw DTO → 내부 DTO 변환
├── dto/
│   ├── request/
│   │   └── ContentListRequest.java
│   └── response/
│       ├── ContentListResponse.java
│       ├── ContentSummaryResponse.java
│       └── ContentDetailResponse.java
├── client/
│   ├── TourApiClient.java              ← OpenFeign 인터페이스
│   └── dto/
│       ├── TourApiItem.java            ← 목록 아이템 공통 DTO
│       ├── TourApiListResponse.java    ← /areaBasedList2, /searchKeyword2 응답
│       ├── TourApiDetailCommonResponse.java
│       ├── TourApiDetailIntroResponse.java
│       └── TourApiDetailImageResponse.java
└── exception/
    └── ContentException.java

domain/region/ (또는 global/constant/)
└── Region.java  ← Enum (areaCode, sigunguCode 포함)
```

---

## 5. TourAPI 연동 설정

- **Base URL:** `https://apis.data.go.kr/B551011/KorService1`
- **인증키:** `${PUBLIC_DATA_PORTAL_KEY}` (application-dev.yaml에 이미 정의됨)
- **공통 파라미터:** `_type=json`, `MobileOS=ETC`, `MobileApp=PickTrip`
- **OpenFeign 구성:** `FeignConfig`에 TourAPI Base URL 등록

---

## 6. 에러 처리

| 상황 | ErrorCode | HTTP |
|-----|-----------|:----:|
| TourAPI에서 콘텐츠 없음 | `CONTENT_NOT_FOUND` | 404 |
| 지원하지 않는 region 값 | `CONTENT_INVALID_REGION` | 400 |
| TourAPI 호출 실패 (timeout/5xx) | `CONTENT_PROVIDER_FAILED` | 502 |
| 요청 파라미터 검증 실패 | `VALIDATION_FAILED` | 400 |

- `FeignException` → `ContentException(CONTENT_PROVIDER_FAILED)` 변환은 `ContentService` 또는 Adapter에서 처리
- `@NotNull`, `@Pattern` 검증 실패는 기존 `GlobalExceptionHandler`가 처리

---

## 7. MVP 제외 항목

MVP에서 필터로 **제외**하는 필드 (자체 검수 데이터 없음):
- `indoorOutdoor` (실내/실외)
- `walkingLevel` (걷기 부담)
- `familyTags` (가족 태그)
- `reservationRequired` (예약 필요 여부)

---

## 8. 검증 방법

1. `GET /api/v1/contents?region=HADONG` → 200, 목록 반환 확인
2. `GET /api/v1/contents?region=HADONG&keyword=쌍계사` → 200, 검색 결과 확인
3. `GET /api/v1/contents?region=HADONG&contentTypeId=12` → 200, 관광지만 필터
4. `GET /api/v1/contents?region=INVALID` → 400, `CONTENT_INVALID_REGION`
5. `GET /api/v1/contents/{validId}` → 200, 상세 정보 확인 (이미지 포함)
6. `GET /api/v1/contents/99999999` → 404, `CONTENT_NOT_FOUND`
7. TourAPI Mock 실패 시 → 502, `CONTENT_PROVIDER_FAILED`
