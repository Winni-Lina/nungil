-- ============================================================
-- 눈길 발표 시연용 Seed Data
-- ============================================================

-- TASK 8개
INSERT INTO TASK (name, process) VALUES ('손 씻기',
'["화장실로 가요","수도꼭지를 돌려서 물을 켜요","손에 물을 묻혀요","비누를 한 번 짜요","비누 거품을 만들어요","손가락 사이도 닦아요","물로 깨끗하게 헹궈요","수건으로 닦아요"]');

INSERT INTO TASK (name, process) VALUES ('양치질',
'["칫솔을 잡아요","치약을 콩알만큼 짜요","칫솔을 입에 넣어요","윗니를 닦아요","아랫니를 닦아요","어금니를 닦아요","혀를 살짝 닦아요","물을 머금어요","물을 뱉어요","칫솔을 헹궈요"]');

INSERT INTO TASK (name, process) VALUES ('빨래하기',
'["빨래를 모아요","세탁실로 가요","세탁기 문을 열어요","옷을 넣어요","세제 통을 열어요","세제를 한 컵 부어요","세제 통을 닫아요","세탁기 문을 닫아요","전원 버튼을 눌러요","표준 코스를 눌러요","시작 버튼을 눌러요","끝나면 옷을 꺼내요"]');

INSERT INTO TASK (name, process) VALUES ('설거지',
'["그릇을 모아요","싱크대로 가요","남은 음식을 버려요","물을 켜요","그릇을 물에 적셔요","수세미에 세제를 묻혀요","그릇을 닦아요","물로 헹궈요","건조대에 놓아요","물을 잠가요"]');

INSERT INTO TASK (name, process) VALUES ('컵라면 끓이기',
'["주전자에 물을 부어요","가스레인지를 켜요","물이 끓을 때까지 기다려요","컵라면 뚜껑을 절반만 열어요","스프를 넣어요","뜨거운 물을 부어요","뚜껑을 닫고 3분 기다려요","잘 저어서 먹어요"]');

INSERT INTO TASK (name, process) VALUES ('청소기 돌리기',
'["청소기를 꺼내요","전원 코드를 꽂아요","전원을 켜요","거실을 청소해요","방을 청소해요","전원을 꺼요","청소기를 정리해요"]');

INSERT INTO TASK (name, process) VALUES ('옷 입기',
'["속옷을 입어요","양말을 신어요","바지를 입어요","윗옷을 입어요","거울을 보고 확인해요","단추를 잠가요"]');

INSERT INTO TASK (name, process) VALUES ('신발 신기',
'["현관으로 가요","신발을 꺼내요","오른발을 신어요","왼발을 신어요","매직테이프를 잘 붙여요"]');

-- 보호자 계정
INSERT INTO GUARDIAN (id, pw, email, phone, name)
VALUES ('demo', '1234', 'demo@nungil.com', '010-1234-5678', '김보호');

-- 사용자 (장애인)
INSERT INTO NUNGIL_USER (id, idx, special_note, white_list, user_name, user_phone, fcm_token)
VALUES ('demo', 1, '천천히 설명해주면 잘 따라해요. 칭찬을 좋아해요.', NULL, '이눈길', '010-2222-3333', NULL);

-- 일정 3건 (서브쿼리로 task_id 참조)
-- 1) 오늘 14:00 손 씻기 - custom_steps 채워진 일정
INSERT INTO SCHEDULE (task_id, id, idx, status, scheduled_at, location, special_note, custom_steps)
SELECT t.task_id, 'demo', 1, 'pending',
  TRUNC(SYSDATE) + 14/24, '집 화장실', '혼자서 처음 해봐요. 천천히 알려주세요.',
  '["화장실로 천천히 가요","수도꼭지를 오른쪽으로 돌려요","두 손에 물을 묻혀요","비누를 한 번만 눌러서 짜요","두 손을 비벼서 거품을 만들어요","손가락 사이도 잊지 말고 닦아요","물로 깨끗하게 헹궈요","수건으로 부드럽게 닦아요. 잘했어요!"]'
FROM TASK t WHERE t.name = '손 씻기';

-- 2) 오늘 15:00 양치질 - custom_steps NULL (앱에서 AI 생성 시연용)
INSERT INTO SCHEDULE (task_id, id, idx, status, scheduled_at, location, special_note, custom_steps)
SELECT t.task_id, 'demo', 1, 'pending',
  TRUNC(SYSDATE) + 15/24, '집 화장실', '양치 마무리까지 꼼꼼하게.', NULL
FROM TASK t WHERE t.name = '양치질';

-- 3) 오늘 16:00 빨래하기 - custom_steps 채워진 일정
INSERT INTO SCHEDULE (task_id, id, idx, status, scheduled_at, location, special_note, custom_steps)
SELECT t.task_id, 'demo', 1, 'pending',
  TRUNC(SYSDATE) + 16/24, '집 세탁실', '세제 양 조심.',
  '["빨래 바구니를 들고 세탁실로 가요","세탁기 문을 두 손으로 열어요","옷을 한 벌씩 넣어요","세제통 뚜껑을 열어요","세제를 컵 절반만 부어요","세제통 뚜껑을 닫아요","세탁기 문을 꼭 닫아요","전원 버튼을 눌러요","표준 코스를 한 번 눌러요","시작 버튼을 눌러요. 잘했어요!"]'
FROM TASK t WHERE t.name = '빨래하기';

COMMIT;

-- 결과 확인
SELECT 'TASK: ' || COUNT(*) AS result FROM TASK
UNION ALL SELECT 'GUARDIAN: ' || COUNT(*) FROM GUARDIAN
UNION ALL SELECT 'NUNGIL_USER: ' || COUNT(*) FROM NUNGIL_USER
UNION ALL SELECT 'SCHEDULE: ' || COUNT(*) FROM SCHEDULE;

EXIT;
