INSERT INTO GUARDIAN (id, pw, email, phone, name)
VALUES ('demo', '1234', 'demo@nungil.com', '010-1234-5678', '김보호');
COMMIT;
SELECT id, name FROM GUARDIAN;
EXIT;
