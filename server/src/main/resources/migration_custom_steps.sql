-- 일정별 AI 맞춤 단계 저장 컬럼 추가 (nullable)
-- SCHEDULE 테이블에 custom_steps (JSON 배열 문자열) 컬럼 추가
-- Oracle

ALTER TABLE SCHEDULE ADD (custom_steps CLOB NULL);

-- 롤백:
-- ALTER TABLE SCHEDULE DROP COLUMN custom_steps;
