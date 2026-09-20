-- 바구니 항목별로 사용자가 직접 지정한 희망 체류시간(분)을 저장한다.
-- 기존 행을 보존해야 하고 저장 시 선택 입력이므로 nullable 로 추가한다.
ALTER TABLE basket_items
    ADD COLUMN desired_stay_minutes int NULL;
