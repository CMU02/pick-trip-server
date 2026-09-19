-- 이전 스톱 → 이 스톱 구간의 상승고도·오르막 추가 시간을 조회 시에도 내려주기 위해 저장한다.
-- 기존 일정 행을 보존해야 하고 저장 시 선택 입력이므로 nullable 로 추가한다.
ALTER TABLE itinerary_items
    ADD COLUMN elevation_gain_meters double NULL,
    ADD COLUMN incline_penalty_minutes int NULL;
