테스트를 실행하고 실패한 경우 원인을 분석한다.

## 실행 순서

1. `.\gradlew.bat test` (Windows) 또는 `./gradlew test` (macOS/Linux) 로 전체 테스트를 실행한다.
2. 실패한 테스트가 있으면 아래 순서로 분석한다.

## 실패 분석 순서

1. 실패한 테스트 클래스와 메서드명을 나열한다.
2. 각 실패에 대해:
   - 에러 메시지와 스택 트레이스를 확인한다.
   - 실패 원인을 한 줄로 요약한다. (예: "NPE — contentRepository mock 누락")
   - 수정 방향을 제시한다.
3. 수정 후 실패한 테스트만 재실행해 통과 여부를 확인한다.

## 테스트 규칙 준수 확인

- Given / When / Then 구조로 작성되었는가
- `@DisplayName` 에 한국어 의도가 명시되어 있는가
- 외부 API(OAuth, TourAPI, AI)는 Mockito로 대역 처리되었는가
