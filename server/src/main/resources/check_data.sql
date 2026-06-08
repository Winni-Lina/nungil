SELECT COUNT(*) AS task_cnt FROM TASK;
SELECT COUNT(*) AS schedule_cnt FROM SCHEDULE;
SELECT COUNT(*) AS guardian_cnt FROM GUARDIAN;
SELECT COUNT(*) AS user_cnt FROM NUNGIL_USER;
SELECT id, name FROM GUARDIAN;
SELECT id, idx, user_name FROM NUNGIL_USER;
SELECT s.schedule_id, t.name AS task_name, s.status, s.scheduled_at,
       CASE WHEN s.custom_steps IS NOT NULL THEN 'Y' ELSE 'N' END AS has_custom
FROM SCHEDULE s JOIN TASK t ON s.task_id = t.task_id ORDER BY s.scheduled_at;
EXIT;
