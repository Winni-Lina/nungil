# 눈길(Nungil) 실행 가이드

> APK를 받아 바로 설치하거나, 소스에서 직접 빌드해 실행하는 두 가지 방법을 안내합니다.

---

## 방법 A — APK 설치 (가장 빠름)

데모 서버가 켜져 있는 경우, APK 하나만 설치하면 바로 실행됩니다.

1. [Releases 페이지](https://github.com/Winni-Lina/nungil/releases)에서 `눈길-v1.0.apk` 다운로드
2. 안드로이드 폰 → **설정 > 보안 > 출처를 알 수 없는 앱 허용** 체크
3. 다운로드한 APK 파일 터치 → 설치
4. 앱 실행 → 보호자 회원가입 또는 사용자 QR 스캔으로 시작

> **최소 사양**: Android 8.0 (API 26) 이상 / 인터넷 연결 필요

---

## 방법 B — 소스에서 직접 빌드 및 실행

데모 서버가 없거나 직접 전체 환경을 구축하려는 경우 사용합니다.

### 필요한 것 (사전 준비)

| 항목 | 용도 |
|---|---|
| Android Studio (Hedgehog 이상) | 앱 빌드 및 설치 |
| JDK 17 | 서버 빌드 |
| Eclipse + Tomcat 10.1 | 서버 실행 |
| Oracle Database 19c 이상 | 데이터 저장 |
| Google Cloud 계정 | Gemini API, STT API |
| Firebase 프로젝트 | FCM 푸시 알림 |
| ngrok | 로컬 서버를 외부에서 접근 가능하게 터널링 |

---

### Step 1 — Google Cloud 설정

#### 1-A. Gemini (Vertex AI) 활성화

1. [Google Cloud Console](https://console.cloud.google.com) 접속
2. 새 프로젝트 생성 (예: `nungil-demo`)
3. **API 및 서비스 > 라이브러리** 에서 `Vertex AI API` 검색 후 사용 설정
4. **IAM 및 관리자 > 서비스 계정** 에서 새 서비스 계정 생성
   - 역할: `Vertex AI 사용자`
5. 서비스 계정 → **키 > 키 추가 > JSON** 으로 키 파일 다운로드
6. 다운로드한 파일을 `server/src/main/resources/serviceAccountKey.json` 에 저장

#### 1-B. Google Cloud STT 활성화

1. 같은 프로젝트에서 `Cloud Speech-to-Text API` 사용 설정
2. 위에서 만든 서비스 계정 키 파일을 STT에도 재사용 가능

---

### Step 2 — Firebase 설정

1. [Firebase Console](https://console.firebase.google.com) 에서 새 프로젝트 생성
2. **프로젝트 설정 > 서비스 계정 > Firebase Admin SDK** → JSON 키 다운로드
3. 다운로드한 파일을 `server/src/main/resources/firebase/serviceAccountKey.json` 에 저장
4. **프로젝트 설정 > 일반 > 내 앱** 에서 Android 앱 추가
   - 패키지명: `com.example.myapplication`
5. `google-services.json` 다운로드 → `client/app/google-services.json` 에 저장

---

### Step 3 — Oracle DB 설정

1. Oracle DB 실행 확인 (기본 포트 1521, SID: orcl)
2. `nungil` 유저 생성:
   ```sql
   CREATE USER nungil IDENTIFIED BY your_password;
   GRANT CONNECT, RESOURCE TO nungil;
   ```
3. `server/src/main/resources/` 의 SQL 파일 순서대로 실행:
   ```
   check_data.sql → run_seed.sql
   ```

---

### Step 4 — 서버 설정 및 실행

1. `server/src/main/resources/application.properties` 파일 생성:

```properties
# DB 연결
db.driver=oracle.jdbc.OracleDriver
db.url=jdbc:oracle:thin:@localhost:1521/orcl
db.username=nungil
db.password=YOUR_DB_PASSWORD

# Google 서비스 계정 (Gemini + STT 공용)
google.service-account-key=classpath:serviceAccountKey.json
google.vertex.project-id=YOUR_GCP_PROJECT_ID
google.vertex.location=us-central1

# Firebase (서비스 계정 키 경로)
google.firebase.service-account-key=classpath:firebase/serviceAccountKey.json
```

2. Eclipse에서 `server/` 프로젝트 열기 → Tomcat 서버에 추가 → **Start**
3. 서버 확인:
   ```
   http://localhost:8080/nungil-server/
   ```

---

### Step 5 — ngrok으로 외부 접근 설정

실기기에서 로컬 서버에 연결하려면 ngrok이 필요합니다.

1. [ngrok.com](https://ngrok.com) 가입 후 설치
2. 터미널에서 실행:
   ```
   ngrok http 8080
   ```
3. 출력된 `https://xxxx.ngrok-free.app` 주소 복사
4. `client/app/src/main/java/com/example/myapplication/config/AppConfig.kt` 열기:
   ```kotlin
   const val BASE_URL = "https://xxxx.ngrok-free.app/nungil-server/"
   // ↑ 위 주소를 ngrok 주소로 교체
   ```

---

### Step 6 — 클라이언트 빌드 및 설치

1. Android Studio에서 `client/` 폴더 열기
2. `client/app/google-services.json` 이 있는지 확인 (Step 2에서 저장한 파일)
3. 안드로이드 기기 USB 연결 또는 에뮬레이터 실행
4. Android Studio 상단 **Run ▶** 버튼 클릭
5. 또는 APK 직접 빌드:
   ```
   cd client
   ./gradlew assembleDebug
   ```
   빌드된 APK 위치: `client/app/build/outputs/apk/debug/app-debug.apk`

---

### Step 7 — 앱 첫 실행

1. **보호자** 역할로 회원가입 → 로그인
2. **사용자 추가** → QR 코드 발급
3. 두 번째 기기(또는 같은 기기 재설치)에서 **사용자** 역할 선택 → QR 스캔
4. 보호자 앱에서 일정 추가 → AI 단계 생성 확인
5. 사용자 앱에서 "똘똘아" 웨이크워드 테스트

---

## 문제 해결

| 증상 | 확인 사항 |
|---|---|
| 앱이 서버에 연결 안 됨 | `AppConfig.kt`의 `BASE_URL`이 현재 ngrok 주소인지 확인 |
| AI 단계 생성 실패 | `application.properties`의 `google.vertex.project-id` 확인, Vertex AI API 사용 설정 여부 |
| FCM 알림 안 옴 | `google-services.json`과 Firebase 프로젝트가 일치하는지 확인 |
| 알람이 안 울림 | 앱 설정 화면 → "알람이 안 울리면 눌러요" 버튼 탭 |
| Oracle 연결 실패 | `db.url`, `db.username`, `db.password` 확인 |

---

## 보안 주의사항

아래 파일들은 `.gitignore`에 등록되어 있으며 **절대 커밋하면 안 됩니다**:

- `server/src/main/resources/application.properties`
- `server/src/main/resources/serviceAccountKey.json`
- `server/src/main/resources/firebase/serviceAccountKey.json`
- `client/app/google-services.json`
