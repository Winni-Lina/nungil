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

    /** 아이디 중복 확인 GET /api/v1/guardian/auth/check-id?id=xxx */
    @GetMapping("/check-id")
    public Map<String, Object> checkId(@RequestParam("id") String id) {
        System.out.println("[API] GET /api/v1/guardian/auth/check-id?id=" + id);
        Map<String, Object> response = new HashMap<>();
        try {
            boolean available = guardianService.isIdAvailable(id);
            response.put("available", available);
            response.put("message", available ? "사용 가능한 아이디입니다" : "이미 사용 중인 아이디입니다");
        } catch (Exception e) {
            response.put("available", false);
            response.put("message", e.getMessage());
        }
        return response;
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
            response.put("message", "EMAIL_EXISTS".equals(e.getMessage())
                    ? "이미 사용 중인 이메일입니다" : "이미 사용 중인 아이디입니다");
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

    /** 비밀번호 찾기 POST /api/v1/guardian/auth/reset-password
     *  Body: { "id": "...", "email": "..." }
     */
    @PostMapping("/reset-password")
    public Map<String, Object> resetPassword(@RequestBody Map<String, Object> body) {
        System.out.println("[API] POST /api/v1/guardian/auth/reset-password | id=" + body.get("id"));
        Map<String, Object> response = new HashMap<>();
        try {
            String id    = (String) body.get("id");
            String email = (String) body.get("email");
            String tempPw = guardianService.resetPassword(id, email);

            Map<String, Object> result = new HashMap<>();
            result.put("tempPassword", tempPw);
            response.put("status", "SUCCESS");
            response.put("result", result);
        } catch (IllegalArgumentException e) {
            response.put("status", "ERROR");
            response.put("errorCode", e.getMessage());
            response.put("message", "USER_NOT_FOUND".equals(e.getMessage())
                    ? "존재하지 않는 아이디입니다." : "이메일이 일치하지 않습니다.");
        } catch (Exception e) {
            response.put("status", "ERROR");
            response.put("message", e.getMessage());
        }
        return response;
    }

    /** 계정 삭제 DELETE /api/v1/guardian/auth/{id}
     *  Body: { "pw": "..." }
     */
    @DeleteMapping("/{id}")
    public Map<String, Object> deleteAccount(@PathVariable("id") String id,
                                              @RequestBody Map<String, Object> body) {
        System.out.println("[API] DELETE /api/v1/guardian/auth/" + id);
        Map<String, Object> response = new HashMap<>();
        try {
            String pw = (String) body.get("pw");
            guardianService.deleteAccount(id, pw);
            response.put("status", "SUCCESS");
            response.put("message", "계정이 삭제됐어요.");
        } catch (IllegalArgumentException e) {
            response.put("status", "ERROR");
            response.put("errorCode", e.getMessage());
            response.put("message", "INVALID_CREDENTIALS".equals(e.getMessage())
                    ? "비밀번호가 올바르지 않습니다." : "존재하지 않는 계정입니다.");
        } catch (Exception e) {
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
