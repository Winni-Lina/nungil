<div align="center">

# 👁️ 눈길 (Nungil)

### 지적장애인과 보호자를 잇는 **AI 일상 지원 앱**

AI 어시스턴트 **똘똘이**가 일상 과업을 한 단계씩 음성으로 안내하고,
보호자가 원격에서 일정을 등록·관리·모니터링합니다.

<br/>

![Android](https://img.shields.io/badge/Android-Kotlin-3DDC84?logo=android&logoColor=white)
![Spring](https://img.shields.io/badge/Server-Spring%20MVC-6DB33F?logo=spring&logoColor=white)
![Gemini](https://img.shields.io/badge/AI-Gemini%202.5%20Flash-4285F4?logo=google&logoColor=white)
![Oracle](https://img.shields.io/badge/DB-Oracle-F80000?logo=oracle&logoColor=white)
![FCM](https://img.shields.io/badge/Push-Firebase%20FCM-FFCA28?logo=firebase&logoColor=black)
![STT](https://img.shields.io/badge/Voice-Google%20STT-34A853?logo=google&logoColor=white)
![Tests](https://img.shields.io/badge/Tests-28%20passing-success)

</div>

---

> ### 💡 한 줄 정의
> **"AI에게 모든 걸 맡기지 않는다."**
> 단계 순서는 등록 시 미리 정해 저장하고, 완료 판단은 AI가 아니라 앱이 키워드로 한다.
> AI는 **'안내와 대응'만** 담당한다 — 환각의 영향 범위를 통제한 설계.

---

## 📑 목차

1. [서비스 개요](#-서비스-개요)
2. [핵심 차별점 — AI를 통제하는 설계](#-핵심-차별점--ai를-통제하는-설계)
3. [AI 파이프라인 & 프롬프트 엔지니어링](#-ai-파이프라인--프롬프트-엔지니어링)
4. [선행 연구 근거](#-선행-연구-근거)
5. [시스템 아키텍처](#-시스템-아키텍처)
6. [주요 기능](#-주요-기능)
7. [알람 다층 안전망](#-알람-다층-안전망)
8. [E2E 시나리오](#-e2e-시나리오)
9. [메뉴 · 유스케이스 · 시스템 구성도](#-메뉴--유스케이스--시스템-구성도)
10. [SRS · ERD](#-srs--erd)
11. [기술 스택](#-기술-스택)
12. [테스트](#-테스트)
13. [실행 방법](#-실행-방법)

---

## 🎯 서비스 개요

| 구분 | 내용 |
|------|------|
| 대상 | 지적장애인(사용자) + 보호자 |
| 연동 방식 | QR 코드 1:1 페어링 |
| AI 어시스턴트 | 똘똘이 (Google Gemini 2.5 Flash) |
| 음성 입력 | Google Cloud STT + 온디바이스 웨이크워드(Vosk) |
| 알림 | Firebase Cloud Messaging (FCM) |

**해결하려는 문제 → 눈길의 접근**

| 문제 | 접근 |
|------|------|
| 보호자 없이 일상 과업을 혼자 끝내기 어려움 | AI + TTS 단계별 음성 안내로 **자립 지원** |
| "어떻게 해?" 반복 질문 → 보호자 부담 | AI가 즉시 답변, **보호자 개입 최소화** |
| 완료 판단 · 다음 단계 진행이 어려움 | "했어요" 자연어 키워드 감지로 **자동 진행** |
| 보호자가 실시간 상황을 모름 | FCM으로 미수행 · 반복질문 **즉시 통보** |

---

## ✨ 핵심 차별점 — AI를 통제하는 설계

생성형 AI는 강력하지만 **비결정적**입니다. 지적장애 사용자에게는 "똑똑하지만 가끔 틀리는" 것보다
**"예측 가능하고 일관된"** 것이 더 중요합니다. 그래서 눈길은 AI의 역할을 의도적으로 재배치했습니다.

<div align="center">

![AI 구조 변경 전후](docs/다이어그램/AI구조_변경전후.png)

</div>

| 항목 | 변경 전 | 변경 후 |
|------|---------|---------|
| **단계 출처** | 과업 공통 고정 가이드 (개인화 ✕) | 등록 시 AI가 장소·특이사항 반영해 **맞춤 생성**, DB 저장 |
| **완료 판단** | AI가 판단 (오판 잦음) | **앱이 키워드 직접 감지** (결정적) |
| **프롬프트 구조** | 단일 프롬프트 (규칙이 입력에 묻힘) | **system / user 분리** (규칙 준수·일관성↑) |

> **핵심 메시지** — AI를 *잘하는 일(단계 생성·안내·대응)* 에 집중시키고,
> *결정적이어야 하는 일(흐름 제어·완료 판단)* 에서 분리했습니다.

---

## 🧠 AI 파이프라인 & 프롬프트 엔지니어링

AI(Gemini)는 **세 군데에서만** 호출되며, 흐름 제어는 전부 앱이 가집니다.

| 호출 | 시점 | 역할 |
|------|------|------|
| **단계 생성** | 보호자 일정 등록 시 1회 | 과업+장소+특이사항 → 맞춤 단계 생성 |
| **대화 응답** | 자유 시간 질문 | 질문 답변 + 사진 필요 판단(멀티모달) |
| **일정 안내** | 수행 중 매 발화 | 현재 단계 안내 + 4분류 대응 |

### 도입한 프롬프트 엔지니어링 기법

| 기법 | 효과 |
|------|------|
| `system_instruction` / `user` **역할 분리** | 규칙 준수·일관성·프롬프트 인젝션 저항 |
| `responseSchema` **구조화 출력** | JSON 형식 보장 → 파싱 안정성 |
| `responseMimeType=application/json` | 마크다운 펜스 제거 불필요 |
| `thinkingBudget=0` | 내부 추론 비활성 → 지연·비용 절감 |
| `maxOutputTokens=512` | 짧은 답변 보장 (사용자 특성 고려) |
| **4분류 행동 지침** + `stepComplete` 제거 | AI를 안내에 한정, 완료 판단 차단 |

### 사용자 발화 처리 흐름

앱이 먼저 완료 키워드(정확 일치 5개 + 포함 9개)를 검사하고, 그 외 모든 말은 AI가
**① 완료 신호 · ② 일정 질문 · ③ 관련 없는 말 · ④ 힘들어함** 중 하나로 분류해 대응합니다.

<div align="center">

![AI 4분류 흐름](docs/다이어그램/AI_4분류_흐름.png)

</div>

---

## 📚 선행 연구 근거

눈길의 설계는 임의적 기획이 아니라, 지적장애인 대상 보조기술 연구에서 효과가 입증된
**두 흐름의 결합**입니다.

| 흐름 | 대표 연구 | 입증된 사실 |
|------|-----------|-------------|
| **단방향 단계별 지시** | Mechling et al.(2009), Cannella-Malone et al.(2013), Lancioni et al.(2017), Resta et al.(2021) | 과업 정확도·자발적 시작률 현저히 향상 (예: 0~20% → 93~100%) |
| **양방향 음성 상호작용** | Smith et al.(2023) *semi-RCT, n=44* | 지적장애인이 AI 음성 대화에 적응 가능, 자율성 향상 |

> **눈길의 위치** — 두 요소를 **생성형 AI로 결합한 첫 서비스 모델**.
> 단계별 지시 + 과업 중 질문 응답 + 실시간 AI + 보호자 일정 연동.

---

## 🏗 시스템 아키텍처

하나의 Android APK 안에 보호자 앱과 사용자 앱이 공존하며, 서버는 Spring(WAR) 기반으로
Gemini · STT · FCM · Oracle과 연동합니다.

<div align="center">

![시스템 아키텍처](docs/다이어그램/시스템_아키텍처.png)

</div>

> **식별 방식** — 세션 토큰 없이 `guardianId`(보호자) + `userIdx`(연동 사용자 인덱스, 1:N)로
> 모든 요청을 식별. QR에는 `{userId, userIdx}`가 담겨 1:1 페어링에 사용됩니다.

### 일정 데이터 흐름 (등록 → 동기화 → 수행 → 완료)

<div align="center">

![일정 데이터 흐름](docs/다이어그램/일정_데이터흐름.png)

</div>

---

## 🚀 주요 기능

| 기능 | 설명 |
|------|------|
| 🔐 보호자 회원가입 · 로그인 · 계정 관리 | 아이디 중복확인, 임시 비번 발급 |
| 📋 일정 등록 + AI 단계 자동 생성 | 등록 시 맞춤 단계 생성 → 보호자 검토·편집·확정 |
| 📱 QR 1:1 페어링 | 보호자 발급 → 사용자 스캔 연동 |
| 🗣️ "똘똘아" 웨이크워드 | Vosk 온디바이스, 오프라인 동작 |
| 🎧 단계별 음성 안내 | Google STT + TTS, 한 번에 한 단계 |
| ✅ 완료 키워드 감지 | "했어 / 응 / 됐어" 등 → 자동 다음 단계 |
| 📷 카메라 멀티모달 질문 | 사진 촬영 → Gemini 분석 → 음성 답변 |
| 🔔 FCM 실시간 알림 | 미수행 · 반복질문 발생 시 보호자 통보 |
| 📊 수행 리포트 | 일별 완료율 · 14일 과업 추세 |

---

## 🔔 알람 다층 안전망

지적장애 사용자에게 **알람 누락은 곧 일정 실패**입니다. 그래서 다층으로 방어합니다.

<div align="center">

![알람 안전망](docs/다이어그램/알람_안전망.png)

</div>

- **1차** `AlarmManager.setExactAndAllowWhileIdle` — Doze 모드 관통 정시 발동
- **2차** WorkManager 15분 주기 — 5~60분 지연 구간 "늦은 알림" 백업
- **3차** 재부팅 복구 — 즉시 sync + 네트워크 연결 후 재시도 + 주기 워커 재등록
- **유령 알람 정리** — 삭제된 일정의 알람을 동기화 시 자동 취소

---

## 🎬 E2E 시나리오

| 시나리오 1 — 서비스 시작 및 연동 | 시나리오 2 — 일정 등록 및 수행 |
|:---:|:---:|
| ![E2E 1](docs/images/e2e%201.png) | ![E2E 2](docs/images/e2e%202.png) |
| **시나리오 3 — 자유 시간 질문** | **시나리오 4 — 보호자 알림 및 보고서** |
| ![E2E 3](docs/images/e2e%203.png) | ![E2E 4](docs/images/e2e%204.png) |

---

## 🗂 메뉴 · 유스케이스 · 시스템 구성도

| 메뉴 구성도 | 유스케이스 | 시스템 구성도 |
|:---:|:---:|:---:|
| ![메뉴](docs/images/메뉴구성도.png) | ![유스케이스](docs/images/유스케이스.png) | ![시스템](docs/images/시스템구성도.png) |

---

## 📐 SRS · ERD

- **SRS**: [눈길_통합SRS_v3_0.xlsx](https://github.com/user-attachments/files/28619923/_.SRS_v3_0.xlsx)
- **ERD**:

<div align="center">

![ERD](docs/다이어그램/ERD.png)

</div>

---

## 🛠 기술 스택

| 영역 | 기술 |
|------|------|
| 클라이언트 | Android (Kotlin), OkHttp, Vosk(오프라인 웨이크워드), AlarmManager, WorkManager, Firebase FCM |
| 서버 | Java, Spring MVC, MyBatis, Apache Tomcat 10.1, Maven |
| 데이터베이스 | Oracle |
| AI | Google Gemini 2.5 Flash (REST v1beta) |
| 음성 인식 | Google Cloud Speech-to-Text + Android SpeechRecognizer |
| 인증 · 푸시 | Firebase Authentication / Cloud Messaging |

---

## ✅ 테스트

설계의 타당성은 **선행 연구**로, 구현의 정확성은 **테스트**로 검증합니다. 서버 핵심 로직 대상 **28개 전부 통과**.

| 종류 | 검증 대상 | 개수 |
|------|-----------|------|
| 단위 테스트 (JUnit 5) | 프롬프트 구조, 완료 키워드 판단, 서비스 위임 | 20 |
| E2E 테스트 (MockMvc) | HTTP 요청→응답 8 시나리오 | 8 |
| AI 통합 테스트 | 실제 Gemini 호출 (조건부 실행) | 3 |

> 커버리지: AI 파이프라인 54% · 보호자 도메인 53% (목표 40% 초과)

---

## ▶ 실행 방법

### 서버

1. Oracle DB 실행 후 스키마 적용
2. `server/src/main/resources/application.properties` 생성:

```properties
google.ai.api-key=YOUR_GEMINI_API_KEY
db.driver=oracle.jdbc.OracleDriver
db.url=jdbc:oracle:thin:@localhost:1521/orcl
db.username=YOUR_DB_USERNAME
db.password=YOUR_DB_PASSWORD
```

3. 환경변수 설정:

```
GOOGLE_APPLICATION_CREDENTIALS=C:\path\to\stt-service-account.json
```

4. Eclipse에서 Tomcat 서버 실행

### 클라이언트

1. Android Studio에서 `client/` 프로젝트 열기
2. `google-services.json` 배치 (`client/app/` 하위)
3. 기기 또는 에뮬레이터에서 실행

---

> ⚠️ **보안 주의**: `application.properties`, `google-services.json`, STT 서비스 계정 키는
> **절대 커밋하지 마세요.** `.gitignore`에 등록되어 있습니다.

<div align="center">

**눈길 팀** · 한이음 ICT 멘토링 클럽 · 2026

</div>
