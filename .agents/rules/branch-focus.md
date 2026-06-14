# Branch Focus Rule

현재 브랜치가 특정 기능 또는 수정 범위를 나타낼 때, 해당 브랜치의 목적을 벗어나는 작업은 진행하지 않는다.

## 규칙

- 현재 브랜치명의 `type`과 연관된 이슈 번호로 작업 범위를 파악한다.
  - 브랜치는 `<type>/<issue_number>` 형식이므로, 범위는 해당 이슈(`#<issue_number>`)의 내용을 기준으로 한다.
  - 예: `feat/42` → 이슈 #42에 정의된 기능 범위
  - 예: `fix/57` → 이슈 #57에 정의된 버그 수정 범위
- 사용자가 현재 브랜치 범위를 벗어나는 기능 개발을 요청하면:
  1. 요청이 현재 브랜치 범위를 벗어남을 명확히 알린다.
  2. 작업을 진행하지 않는다.
  3. 새 브랜치를 생성해 작업할 것을 안내한다.

## 예외

- 현재 브랜치가 `main`이면 이 규칙을 적용하지 않는다.
- 문서 정리, 오타 수정, 테스트 보강처럼 현재 변경의 안정성을 높이는 작업은 허용할 수 있다.
- 긴급 보안 수정은 별도 `hotfix/` 브랜치를 만든 뒤 진행한다.

## 권장 브랜치명

브랜치는 `<type>/<issue_number>` 형식으로 만든다. 자세한 규칙은 `git-convention.md`의 Branch Naming Convention을 따른다.

- 기능 추가: `feat/<issue_number>` (예: `feat/42`)
- 버그 수정: `fix/<issue_number>` (예: `fix/57`)
- 리팩터링: `refactor/<issue_number>` (예: `refactor/13`)
- 문서 변경: `docs/<issue_number>` (예: `docs/9`)
- 환경 작업: `chore/<issue_number>` (예: `chore/24`)
