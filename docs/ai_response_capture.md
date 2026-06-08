# AI 응답 사전 캡처 가이드

발표 직전 (T-1시간 권장) 실행해서 결과를 캡처. 슬라이드 8번(테스트 결과)과 6번(AI 비교) 보조 자료로 사용.

## 사전 준비
- `application.properties` 의 `google.ai.api-key` 가 살아있는지 확인
- 인터넷 연결 확인
- `seed_data.sql` 적용된 상태(데모 일정·계정 존재)
- 서버를 별도 터미널에서 실행: `cd "C:\Users\User\Desktop\Nungil Main\server" && mvn spring-boot:run`

---

## 1. 단위 테스트 결과 캡처 (라이브 제외)
```bat
cd /d "C:\Users\User\Desktop\Nungil Main\server"
mvn -q test -Dtest=!GeminiLiveIntegrationTest > test_result.txt 2>&1
type test_result.txt | findstr /R "Tests run BUILD"
```
- 캡처할 줄: `Tests run: 22, Failures: 0, Errors: 0, Skipped: 0`
- 슬라이드 8에 콘솔 스크린샷으로 첨부

## 2. 라이브 AI 테스트 (실호출)
```bat
cd /d "C:\Users\User\Desktop\Nungil Main\server"
set RUN_LIVE_AI=true
mvn test -Dtest=GeminiLiveIntegrationTest -DfailIfNoTests=false > live_ai.txt 2>&1
notepad live_ai.txt
```
- 출력에서 다음 3블럭을 찾아 캡처:
  - `===== [LIVE 1: chat] =====` → 채팅 응답 JSON
  - `===== [LIVE 2: step generation] =====` → 단계 리스트
  - `===== [LIVE 3: summarize] =====` → 요약 문장
- 슬라이드 6 (AI 비교) 보조 자료로 사용

## 3. curl 직접 호출 — 단계 생성
```bat
curl -X POST http://localhost:8080/api/v1/guardian/schedules/generate-steps ^
  -H "Content-Type: application/json" ^
  -d "{\"taskId\":1,\"location\":\"화장실\",\"scheduleNote\":\"혼자서 처음 해요\",\"guardianId\":\"demo\",\"idx\":1}" ^
  -o steps_response.json
type steps_response.json
```
- 정상이면 `{ "steps": ["...", "..."] }` 형태
- 슬라이드 6/7 시연 비상시 백업 화면으로 사용

## 4. curl 직접 호출 — 요약
```bat
curl -X POST http://localhost:8080/api/v1/question/summarize ^
  -H "Content-Type: application/json" ^
  -d "{\"text\":\"오늘 14시 손씻기 8단계 모두 완료, 비누 단계에서 한 번 멈춤\"}" ^
  -o summary_response.json
type summary_response.json
```

## 5. 캡처 정리
- 위 4건의 결과를 `docs/images/` 아래에 `live_chat.png`, `live_steps.png`, `live_summary.png`, `unit_tests_pass.png` 로 저장
- 슬라이드 8에 `unit_tests_pass.png`, 슬라이드 6에 `live_steps.png` 삽입

## 시간 부족 시 우선순위
1. 단위 테스트 통과 캡처 (1번) — 가장 빠르고 안전
2. 단계 생성 라이브 캡처 (2번 LIVE 2) — 핵심 기능
3. 나머지는 영상(backup_recording_guide.md)으로 대체 가능
