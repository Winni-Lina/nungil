[눈길_통합SRS_v3_0.xlsx](https://github.com/user-attachments/files/28619915/_.SRS_v3_0.xlsx)
# 눈길 (Nungil)

> 지적장애인과 보호자를 연결하는 AI 기반 일상 지원 앱

지적장애인이 일상 과업을 스스로 수행할 수 있도록 AI 어시스턴트 **똘똘이**가 단계별로 안내하고, 보호자가 원격으로 일정을 관리·모니터링할 수 있는 안드로이드 앱입니다.

---

## 목차

1. [서비스 개요](#서비스-개요)
2. [E2E 시나리오](#e2e-시나리오)
3. [메뉴 구성도](#메뉴-구성도)
4. [유스케이스 다이어그램](#유스케이스-다이어그램)
5. [시스템 구성도](#시스템-구성도)
6. [SRS](#srs)
7. [ERD](#erd)
8. [기술 스택](#기술-스택)
9. [실행 방법](#실행-방법)

---

## 서비스 개요

| 구분 | 내용 |
|------|------|
| 대상 | 지적장애인(사용자) + 보호자 |
| 연동 방식 | QR 코드 1대1 페어링 |
| AI 어시스턴트 | 똘똘이 (Gemini API 기반) |
| 음성 입력 | Google Cloud STT |
| 알림 | Firebase Cloud Messaging (FCM) |

---

## E2E 시나리오

### 시나리오 1 — 서비스 시작 및 연동
![E2E 시나리오 1](docs/images/e2e%201.png)

### 시나리오 2 — 일정 등록 및 수행
![E2E 시나리오 2](docs/images/e2e%202.png)

### 시나리오 3 — 자유 시간 질문
![E2E 시나리오 3](docs/images/e2e%203.png)

### 시나리오 4 — 보호자 알림 및 보고서
![E2E 시나리오 4](docs/images/e2e%204.png)

---

## 메뉴 구성도

![메뉴 구성도](docs/images/메뉴구성도.png)

---

## 유스케이스 다이어그램

![유스케이스](docs/images/유스케이스.png)

---

## 시스템 구성도

![시스템 구성도](docs/images/시스템구성도.png)

---

## SRS

> [눈길_통합SRS_v3_0.xlsx](https://github.com/user-attachments/files/28619923/_.SRS_v3_0.xlsx)


---

## ERD

> <img width="3621" height="2361" alt="눈길_ERD" src="https://github.com/user-attachments/assets/05fc6f13-4981-406d-9259-99e453717fbf" />


---

## 기술 스택

| 영역 | 기술 |
|------|------|
| 클라이언트 | Android (Kotlin), Firebase FCM |
| 서버 | Java, Spring MVC, Apache Tomcat 10.1 |
| 데이터베이스 | Oracle |
| AI | Google Gemini API |
| 음성 인식 | Google Cloud Speech-to-Text |
| 인증 | Firebase Authentication |
| 빌드 | Maven |

---

## 실행 방법

### 서버

1. Oracle DB 실행 후 스키마 적용
2. `server/src/main/resources/application.properties` 생성 (아래 형식 참고)

```properties
google.ai.api-key=YOUR_GEMINI_API_KEY
db.driver=oracle.jdbc.OracleDriver
db.url=jdbc:oracle:thin:@localhost:1521/orcl
db.username=YOUR_DB_USERNAME
db.password=YOUR_DB_PASSWORD
```

3. 환경변수 설정

```
GOOGLE_APPLICATION_CREDENTIALS=C:\path\to\stt-service-account.json
```

4. Eclipse에서 Tomcat 서버 실행

### 클라이언트

1. Android Studio에서 `client/` 프로젝트 열기
2. `google-services.json` 배치 (`client/app/` 하위)
3. 기기 또는 에뮬레이터에서 실행

---

> **주의**: `application.properties`, `google-services.json`, STT 서비스 계정 키는 절대 커밋하지 마세요. `.gitignore`에 등록되어 있습니다.
