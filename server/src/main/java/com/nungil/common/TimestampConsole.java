package com.nungil.common;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.io.PrintStream;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * System.out.println 전체에 타임스탬프 자동 주입.
 * 기존 컨트롤러/서비스 코드 수정 없이 모든 로그에 시간이 찍힘.
 */
@Component
public class TimestampConsole implements InitializingBean {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    @Override
    public void afterPropertiesSet() {
        PrintStream original = System.out;
        PrintStream timestamped = new PrintStream(original, true) {
            @Override
            public void println(String x) {
                if (x != null && !x.isEmpty()) {
                    super.println("[" + LocalTime.now().format(FMT) + "] " + x);
                } else {
                    super.println(x);
                }
            }

            @Override
            public void println(Object x) {
                println(String.valueOf(x));
            }
        };
        System.setOut(timestamped);
        System.out.println("===== 눈길 서버 로그 시작 =====");
    }
}
