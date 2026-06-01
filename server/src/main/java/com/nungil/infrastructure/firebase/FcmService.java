package com.nungil.infrastructure.firebase;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Service
public class FcmService implements InitializingBean {

    @Override
    public void afterPropertiesSet() {
        try {
            GoogleCredentials credentials;
            // 환경변수 FIREBASE_SERVICE_ACCOUNT_JSON 에 JSON 전체 문자열을 넣으면 우선 사용
            String keyJson = System.getenv("FIREBASE_SERVICE_ACCOUNT_JSON");
            if (keyJson != null && !keyJson.isEmpty()) {
                credentials = GoogleCredentials.fromStream(
                    new ByteArrayInputStream(keyJson.getBytes(StandardCharsets.UTF_8))
                );
            } else {
                // fallback: 로컬 개발용 파일 (git에 올리지 말 것)
                InputStream is = getClass().getResourceAsStream("/firebase/serviceAccountKey.json");
                if (is == null) throw new RuntimeException("serviceAccountKey.json 없음 — FIREBASE_SERVICE_ACCOUNT_JSON 환경변수를 설정하세요");
                credentials = GoogleCredentials.fromStream(is);
            }
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .build();
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
            }
            System.out.println("[FCM] Firebase 초기화 완료");
        } catch (Exception e) {
            System.out.println("[FCM] Firebase 초기화 실패: " + e.getMessage());
        }
    }

    /**
     * 사용자 앱 일정 업데이트 알림 (알람 재등록 트리거)
     */
    public void sendScheduleUpdated(String fcmToken) {
        if (fcmToken == null || fcmToken.isBlank()) return;
        send(fcmToken, "SCHEDULE_UPDATED", "", "", "");
    }

    /**
     * NT-003: 일정 지연 알림
     */
    public void sendScheduleOverdue(String fcmToken, String userName, String userPhone, Long scheduleId) {
        send(fcmToken, "SCHEDULE_OVERDUE", userName, userPhone, String.valueOf(scheduleId));
    }

    /**
     * NT-004: 반복 질문 알림
     */
    public void sendRepeatQuestion(String fcmToken, String userName, String userPhone) {
        send(fcmToken, "REPEAT_QUESTION", userName, userPhone, "");
    }

    private void send(String fcmToken, String type, String userName, String userPhone, String scheduleId) {
        try {
            Message message = Message.builder()
                    .putData("type", type)
                    .putData("userName", userName != null ? userName : "사용자")
                    .putData("userPhone", userPhone != null ? userPhone : "")
                    .putData("scheduleId", scheduleId)
                    .setToken(fcmToken)
                    .build();
            String result = FirebaseMessaging.getInstance().send(message);
            System.out.println("[FCM] 발송 완료 type=" + type + " token=***" + fcmToken.substring(Math.max(0, fcmToken.length() - 6)) + " result=" + result);
        } catch (Exception e) {
            System.out.println("[FCM] 발송 실패 type=" + type + " : " + e.getMessage());
        }
    }
}
