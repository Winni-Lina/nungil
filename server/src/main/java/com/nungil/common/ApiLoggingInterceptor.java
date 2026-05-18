package com.nungil.common;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class ApiLoggingInterceptor implements HandlerInterceptor {

    private static final String ATTR_START = "reqStart";

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
        req.setAttribute(ATTR_START, System.currentTimeMillis());
        String ip = req.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) ip = req.getRemoteAddr();
        System.out.println("──────────────────────────────────────────");
        System.out.println("▶ " + req.getMethod() + " " + req.getRequestURI()
                + (req.getQueryString() != null ? "?" + req.getQueryString() : "")
                + " [IP: " + ip + "]");
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse res,
                                 Object handler, Exception ex) {
        Long start = (Long) req.getAttribute(ATTR_START);
        long ms = start != null ? System.currentTimeMillis() - start : -1;
        System.out.println("◀ " + req.getMethod() + " " + req.getRequestURI()
                + " → HTTP " + res.getStatus() + " (" + ms + "ms)");
        System.out.println("──────────────────────────────────────────");
    }
}
