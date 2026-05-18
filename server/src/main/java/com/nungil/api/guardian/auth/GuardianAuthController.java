package com.nungil.api.guardian.auth;

import com.nungil.domain.guardian.GuardianService;
import com.nungil.domain.guardian.GuardianVO;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/guardian/auth")
public class GuardianAuthController {

    private final GuardianService guardianService;

    public GuardianAuthController(GuardianService guardianService) {
        this.guardianService = guardianService;
    }

    /** 회원가입 POST /api/v1/guardian/auth/signup */
    @PostMapping("/signup")
    public Map<String, Object> signup(@RequestBody Map<String, Object> body) {
        System.out.println("[API] POST /api/v1/guardian/auth/signup | id=" + body.get("id"));
        Map<String, Object> response = new HashMap<>();
        try {
            GuardianVO guardian = new GuardianVO();
            guardian.setId((String) body.get("id"));
            guardian.setPw((String) body.get("pw"));
            guardian.setEmail((String) body.get("email"));
            guardian.setPhone((String) body.get("phone"));
            guardian.setName((String) body.get("name"));

            guardianService.join(guardian);

            Map<String, Object> result = new HashMap<>();
            result.put("id", guardian.getId());
            result.put("name", guardian.getName());

            System.out.println("[결과] 회원가입 성공 id=" + guardian.getId());
            response.put("status", "SUCCESS");
            response.put("result", result);
        } catch (IllegalArgumentException e) {
            System.out.println("[결과] 회원가입 실패 - " + e.getMessage());
            response.put("status", "ERROR");
            response.put("errorCode", e.getMessage());
            response.put("message", "이미 사용 중인 아이디입니다");
        } catch (Exception e) {
            System.out.println("[ERROR] " + e.getMessage());
            response.put("status", "ERROR");
            response.put("message", e.getMessage());
        }
        return response;
    }

    /** 로그인 POST /api/v1/guardian/auth/login */
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, Object> body) {
        System.out.println("[API] POST /api/v1/guardian/auth/login | id=" + body.get("id"));
        Map<String, Object> response = new HashMap<>();
        try {
            String id = (String) body.get("id");
            String pw = (String) body.get("pw");

            GuardianVO guardian = guardianService.login(id, pw);

            Map<String, Object> result = new HashMap<>();
            result.put("id", guardian.getId());
            result.put("name", guardian.getName());
            result.put("email", guardian.getEmail());

            System.out.println("[결과] 로그인 성공 id=" + guardian.getId());
            response.put("status", "SUCCESS");
            response.put("result", result);
        } catch (IllegalArgumentException e) {
            System.out.println("[결과] 로그인 실패 - 잘못된 자격증명");
            response.put("status", "ERROR");
            response.put("errorCode", "INVALID_CREDENTIALS");
            response.put("message", "아이디 또는 비밀번호가 올바르지 않습니다");
        } catch (Exception e) {
            System.out.println("[ERROR] " + e.getMessage());
            response.put("status", "ERROR");
            response.put("message", e.getMessage());
        }
        return response;
    }

    /** FCM 토큰 등록 PUT /api/v1/guardian/auth/fcm-token */
    @PutMapping("/fcm-token")
    public Map<String, Object> updateFcmToken(@RequestBody Map<String, Object> body) {
        // 클라이언트가 "guardianId" 또는 "id" 중 하나로 전송할 수 있음
        String guardianId = body.get("guardianId") != null
                ? (String) body.get("guardianId")
                : (String) body.get("id");
        System.out.println("[API] PUT /api/v1/guardian/auth/fcm-token | guardianId=" + guardianId);
        Map<String, Object> response = new HashMap<>();
        try {
            if (guardianId == null || guardianId.isBlank()) {
                response.put("status", "ERROR");
                response.put("errorCode", "MISSING_GUARDIAN_ID");
                response.put("message", "guardianId가 필요합니다");
                return response;
            }
            guardianService.updateFcmToken(
                guardianId,
                (String) body.get("fcmToken")
            );
            System.out.println("[결과] FCM 토큰 등록 완료");
            response.put("status", "SUCCESS");
            response.put("message", "FCM 토큰 등록 완료");
        } catch (Exception e) {
            System.out.println("[ERROR] " + e.getMessage());
            response.put("status", "ERROR");
            response.put("message", e.getMessage());
        }
        return response;
    }
}
