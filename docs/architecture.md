# 눈길 시스템 아키텍처

발표용 시각 자료. GitHub/타입ora/Mermaid Live Editor 등에서 렌더링.

---

## (A) 시스템 구성도

```mermaid
graph TB
  subgraph 보호자["보호자 앱"]
    G1[일정 등록]
    G2[AI 단계 검토 UI]
    G3[진행 모니터링]
  end
  subgraph 사용자["사용자 앱 (장애인)"]
    U1[음성 대화]
    U2[단계별 안내]
    U3[사진 인식]
  end
  subgraph 서버["서버 (Spring Boot)"]
    S1[Schedule API]
    S2[Analysis Orchestrator]
    S3[Step Generation Service]
    S4[Summarize API]
  end
  subgraph 외부["외부 API"]
    GAI[Google Gemini 2.5 Flash]
    ST[Google STT]
    F[Firebase FCM]
  end
  DB[(Oracle DB)]
  보호자 --> 서버
  사용자 --> 서버
  서버 --> DB
  서버 --> 외부
  서버 -. 푸시 .-> F
  F -. 알림 .-> 사용자
```

---

## (B) AI 구조 변경 Before / After

### ❌ 기존 구조 (문제점)

```mermaid
flowchart LR
  subgraph OLD["기존 구조 (문제)"]
    direction TB
    A[보호자: 일정 등록] --> B[DB 저장]
    B --> C[사용자: 일정 시작]
    C --> D["AI 호출 ①\n단계 목록 생성"]
    D --> E["AI 호출 ②\n현재 단계 안내"]
    E --> F{완료?}
    F -- stepComplete=true --> G[다음 단계]
    G --> E
    F -- 질문 --> E
  end
```

**문제:**
- 실행 때마다 AI가 단계 목록을 새로 만듦 → 매번 다른 단계, 사용자 혼란
- 단계 완료 판단을 AI가 함 → 보호자가 통제 불가
- AI 호출이 2중으로 발생 → 응답 지연, 비용↑

---

### ✅ 현재 구조 (개선)

```mermaid
flowchart TB
  subgraph CREATE["① 등록 시점 (보호자)"]
    direction LR
    G1[일정 정보 입력\n장소·특이사항] --> G2["AI: 맞춤 단계 생성\ngemini-2.5-flash"]
    G2 --> G3[보호자 검토·편집]
    G3 --> G4[DB 저장\ncustom_steps CLOB]
  end

  subgraph RUN["② 실행 시점 (사용자)"]
    direction LR
    U1[알람 → 일정 시작] --> U2[DB에서 단계 로드]
    U2 --> U3["AI: 현재 단계 안내만\n(단계 생성 X)"]
    U3 --> U4{사용자 발화}
    U4 -- "완료 키워드\n(했어·응·끝)" --> U5["클라이언트가\n단계 완료 결정"]
    U5 --> U3
    U4 -- "질문·모르겠어" --> U3
  end

  CREATE --> RUN
```

**개선점:**
- 단계는 **등록 시 1회만** 생성 → 일관성 보장
- 보호자가 직접 검토·수정 가능
- 실행 시 AI는 **안내만** → 빠른 응답
- **단계 완료 판단은 클라이언트** (키워드 감지) → AI 오판 없음

---

## (C) 프롬프트 구조 (System / User 분리)

```mermaid
flowchart LR
  subgraph REQ["서버 → Gemini 요청"]
    direction TB
    SYS["systemInstruction\n━━━━━━━━━━━━━━\n• 똘똘이 역할 정의\n• 4분류 판단 규칙\n• 말투·안전 규칙\n• 필드 의미 정의"]
    USR["userMessage\n━━━━━━━━━━━━━━\n• 사용자 정보·특이사항\n• 이번 일정 특이사항\n• 현재 단계 (N/총)\n• 이전 대화 히스토리\n• 사용자 발화"]
  end

  subgraph RES["Gemini 응답 (JSON 강제)"]
    direction TB
    R1["answer: 안내 문구"]
    R2["stepComplete: boolean"]
    R3["suggestedQuestions: []"]
    R4["photoRequest: boolean"]
  end

  subgraph SCHEMA["responseSchema 강제"]
    SC["OBJECT 4필드 필수\n→ JSON 파싱 오류 0%"]
  end

  REQ --> SCHEMA --> RES
```

### 4분류 판단 로직 (일정 수행 모드)

```mermaid
flowchart TD
  IN[사용자 발화] --> Q1{완료 신호?\n했어·응·끝났어}
  Q1 -- YES --> A1["칭찬 + stepComplete=true\n클라이언트가 다음 단계 이동"]
  Q1 -- NO --> Q2{일정 관련 질문?\n방법·도구·이 일정}
  Q2 -- YES --> A2["짧게 답변 + 단계 유도\nphotoRequest 판단"]
  Q2 -- NO --> Q3{관련 없는 말?\n심심해·엄마 언제와}
  Q3 -- YES --> A3["한 번 받아주고\n부드럽게 단계로 유도"]
  Q3 -- NO --> A4["못하겠다·힘들어\n→ 재촉 없이 응원\n더 쉽게 재안내"]
```

---

## (D) 기존 vs 현재 구조 비교표

| 항목 | 기존 | 현재 |
|---|---|---|
| 단계 생성 시점 | 실행 때마다 AI 생성 | 등록 시 1회 생성 |
| 보호자 검토 | 불가 | ✅ 검토·편집 후 저장 |
| 단계 완료 판단 | AI (stepComplete) | 클라이언트 키워드 감지 |
| AI 역할 | 단계 생성 + 안내 | 안내만 |
| 프롬프트 구조 | 단일 prompt | system / user 분리 |
| 응답 형식 | 텍스트 파싱 | responseSchema JSON 강제 |
| 모델 | gemini-2.5-flash | gemini-2.5-flash |
| 단계 일관성 | 매번 달라짐 | 항상 동일 (DB 저장) |
