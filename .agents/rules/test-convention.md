  # 테스트 규칙

이 문서는 PickTrip Spring Boot 서버의 테스트 작성 기준을 정의한다.

## 테스트 어노테이션 기준

- Spring Bean(Controller, Service, Repository 등)이 필요한 테스트는 `@SpringBootTest`를 사용한다.
- 순수 함수 또는 외부 의존성이 없는 로직은 어노테이션 없이 테스트를 작성한다.

## 테스트 프레임워크

- **Mockito**: 외부 의존성(OAuth, TourAPI, AI 제공자 등) 대역(mock) 처리에 사용한다.
- **AssertJ** (`org.assertj.core.api.Assertions`): 검증에 사용한다. JUnit 기본 `assert` 대신 AssertJ를 우선한다.
- **JUnit 5**: 테스트 구조와 생명주기 관리에 사용한다.

## 테스트 형식

모든 테스트는 **Given / When / Then** 형식으로 작성한다.

```java
@Test
@DisplayName("콘텐츠 ID가 없으면 예외를 던진다.")
void throwExceptionWhenContentIdNotFound() {
    // given
    Long invalidId = -1L;
    given(contentRepository.findById(invalidId)).willReturn(Optional.empty());

    // when
    ThrowableAssert.ThrowingCallable action = () -> contentService.findById(invalidId);

    // then
    assertThatThrownBy(action)
            .isInstanceOf(PickTripException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CONTENT_NOT_FOUND);
}
```

- 테스트 메서드명은 영문 `camelCase`로 작성한다.
- `@DisplayName`에 한국어로 테스트 의도를 작성한다.
- 외부 OAuth, TourAPI, AI 제공자는 실제 호출하지 않고 Mockito 대역을 사용한다.
- 버그 수정에는 재발 방지 테스트를 함께 추가한다.
