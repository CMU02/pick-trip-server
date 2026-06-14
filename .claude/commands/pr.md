현재 브랜치의 변경사항을 분석해 GitHub PR을 생성한다.

## 실행 순서

1. 현재 브랜치명을 확인한다.
   - `git rev-parse --abbrev-ref HEAD`
   - 브랜치는 `<type>/<issue_number>` 형식이므로, 브랜치명 끝의 숫자를 연관 이슈 번호로 사용한다.
2. 현재 브랜치명과 main 브랜치 간의 커밋 목록을 확인한다.
   - `git log main...HEAD --oneline`
3. 변경된 파일 목록을 확인한다.
   - `git diff main...HEAD --name-only`
4. `.github/pull_request_template.md` 를 읽어 그 형식에 맞춰 PR 제목과 본문을 작성한다.

## PR 형식

**제목:** `[타입] 변경 내용 요약` (50자 이내)
- 타입: feat / fix / refactor / docs / chore

**본문:** `.github/pull_request_template.md` 의 구조를 그대로 따른다.
```
## 작업 내용

- 변경사항을 bullet point로 요약

## 관련 이슈

- Closes #<issue_number>

## 테스트 플랜

- 테스트 및 검증 방법
```

- `관련 이슈`의 번호는 1단계에서 파악한 브랜치명의 이슈 번호를 사용한다. 번호를 확정할 수 없으면 사용자에게 확인한다.
- 템플릿이 변경되었을 수 있으므로 반드시 파일을 읽어 최신 구조를 반영한다.

5. PR 내용을 출력하고 사용자 최종 확인을 받는다.
6. 확인 후 `gh pr create` 로 PR을 생성한다.
   - base 브랜치: `main`
