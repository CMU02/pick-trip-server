현재 브랜치의 변경사항을 분석해 GitHub PR을 생성한다.

## 실행 순서

1. 현재 브랜치명과 main 브랜치 간의 커밋 목록을 확인한다.
   - `git log main...HEAD --oneline`
2. 변경된 파일 목록을 확인한다.
   - `git diff main...HEAD --name-only`
3. 아래 형식으로 PR 제목과 본문을 작성한다.

## PR 형식

**제목:** `[타입] 변경 내용 요약` (50자 이내)
- 타입: feat / fix / refactor / docs / chore

**본문:**
```
## 변경 내용
- 변경사항을 bullet point로 요약

## 관련 도메인
- 영향받는 도메인 목록 (예: content, basket)

## 체크리스트
- [ ] 테스트 통과 확인
- [ ] 컨벤션 위반 없음
- [ ] .env 및 secret 파일 미포함
```

4. PR 내용을 출력하고 사용자 최종 확인을 받는다.
5. 확인 후 `gh pr create` 로 PR을 생성한다.
   - base 브랜치: `main`
