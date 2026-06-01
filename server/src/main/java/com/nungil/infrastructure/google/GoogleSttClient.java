package com.nungil.infrastructure.google;

import javax.annotation.PreDestroy;
import java.io.InputStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.speech.v1.RecognitionConfig;
import com.google.cloud.speech.v1.RecognizeResponse;
import com.google.cloud.speech.v1.SpeechClient;
import com.google.cloud.speech.v1.SpeechSettings;
import com.google.protobuf.ByteString;

@Component
public class GoogleSttClient {

    @Value("${google.service-account-key}")
    private Resource serviceAccountKey;

    private SpeechClient speechClient;

    private void initIfNeeded() throws Exception {
        if (speechClient != null) return;

        System.out.println("[STT] Google Cloud Speech 초기화 중...");

        try (InputStream is = serviceAccountKey.getInputStream()) {
            GoogleCredentials credentials = GoogleCredentials.fromStream(is)
                .createScoped("https://www.googleapis.com/auth/cloud-platform");

            SpeechSettings settings = SpeechSettings.newBuilder()
                    .setCredentialsProvider(com.google.api.gax.core.FixedCredentialsProvider.create(credentials))
                    .build();

            this.speechClient = SpeechClient.create(settings);
            System.out.println("[STT] 초기화 완료!");
        }
    }

    /** 음성 파일을 텍스트로 변환 */
    public String transcribe(MultipartFile audioFile) {
        try {
            initIfNeeded();

            byte[] audioBytes = audioFile.getBytes();
            ByteString audioData = ByteString.copyFrom(audioBytes);

            com.google.cloud.speech.v1.RecognitionAudio audio =
                com.google.cloud.speech.v1.RecognitionAudio.newBuilder()
                    .setContent(audioData)
                    .build();

            RecognitionConfig config = RecognitionConfig.newBuilder()
                    .setEncoding(RecognitionConfig.AudioEncoding.MP3)
                    .setSampleRateHertz(44100)
                    .setLanguageCode("ko-KR")
                    .setEnableAutomaticPunctuation(true)
                    .setModel("latest_short")
                    .build();

            System.out.println("[STT] 음성 인식 시작... (파일 크기: " + audioBytes.length + " bytes)");
            RecognizeResponse response = speechClient.recognize(config, audio);

            if (response.getResultsCount() == 0) return "";

            return response.getResults(0).getAlternatives(0).getTranscript();

        } catch (Exception e) {
            System.err.println("[STT] 오류 발생: " + e.getMessage());
            return "";
        }
    }

    @PreDestroy
    public void shutdown() {
        if (speechClient != null) {
            speechClient.close();
            System.out.println("[STT] 클라이언트 리소스 정리 완료");
        }
    }
}
