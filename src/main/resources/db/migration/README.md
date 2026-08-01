# Flyway 마이그레이션

스키마 변경은 전부 이 디렉터리의 SQL 파일로만 한다. `ddl-auto` 는 `validate` 이므로
엔티티에 컬럼을 추가하고 마이그레이션을 빼먹으면 부팅이 실패한다.

## 파일 이름 규칙

```
V{버전}__{설명}.sql
```

- 버전은 1 부터 증가시킨다. 되돌리거나 재사용하지 않는다.
- 설명은 snake_case 로 적는다. 언더스코어 두 개(`__`)로 구분한다.
- 예: `V1__add_itinerary_memo.sql`

## 예시

```sql
-- V1__add_itinerary_memo.sql
ALTER TABLE itinerary ADD COLUMN memo VARCHAR(500) NULL;
```

## 주의

- 이미 적용된 파일은 절대 수정하지 않는다. 체크섬이 어긋나면 부팅이 실패한다. 수정이
  필요하면 새 버전 파일을 추가한다.
- 운영 DB 는 `baseline-on-migrate: true` 로 기존 스키마를 버전 0 기준선으로 잡는다.
  따라서 첫 마이그레이션은 `V1` 부터 시작한다.
- 신규 환경을 처음부터 구축하려면 기준선 스키마가 필요하다. 그때
  `mysqldump --no-data` 로 현재 운영 스키마를 떠서 `V1__baseline.sql` 로 넣고,
  기존 마이그레이션 번호를 밀어준다.
