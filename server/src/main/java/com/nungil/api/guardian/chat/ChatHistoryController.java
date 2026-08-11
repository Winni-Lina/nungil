package com.nungil.api.guardian.chat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nungil.domain.chat.ChatHistoryService;
import com.nungil.domain.chat.ChatHistoryVO;

/**
 * 대화 History API.
 *  - POST /api/v1/chat-history       : 대화 1건 저장
 *  - GET  /api/v1/chat-history?...    : 일정 번호별 대화 조회 (created_at 오름차순)
 */
@RestController
@RequestMapping("/api/v1/chat-history")
public class ChatHistoryController {

    private final ChatHistoryService chatHistoryService;

    public ChatHistoryController(ChatHistoryService chatHistoryService) {
        this.chatHistoryService = chatHistoryService;
    }

    /** 저장: body = { guardianId, userIdx, scheduleId, stepIndex, chatMode, sender, message, intent } */
    @PostMapping
    public Map<String, Object> save(@RequestBody Map<String, Object> body) {
        System.out.println("[API] POST /api/v1/chat-history | sender=" + body.get("sender")
                + ", chatMode=" + body.get("chatMode"));
        Map<String, Object> response = new HashMap<>();
        try {
            ChatHistoryVO vo = new ChatHistoryVO();
            vo.setGuardianId(str(body.get("guardianId")));
            vo.setUserIdx(toInteger(body.get("userIdx")));
            vo.setScheduleId(toLong(body.get("scheduleId")));
            vo.setStepIndex(toInteger(body.get("stepIndex")));
            vo.setChatMode(str(body.get("chatMode")));
            vo.setSender(str(body.get("sender")));
            vo.setMessage(str(body.get("message")));
            vo.setIntent(str(body.get("intent")));

            chatHistoryService.save(vo);

            System.out.println("[결과] 대화 저장 완료 messageId=" + vo.getMessageId());
            response.put("status", "SUCCESS");
            if (vo.getMessageId() != null) response.put("messageId", vo.getMessageId());
        } catch (IllegalArgumentException e) {
            // 검증 실패 → 성공으로 반환하지 않고 사유를 알려준다
            System.out.println("[결과] 대화 저장 거부: " + e.getMessage());
            response.put("status", "ERROR");
            response.put("errorCode", "INVALID_REQUEST");
            response.put("message", e.getMessage());
        } catch (Exception e) {
            System.out.println("[ERROR] " + e.getMessage());
            response.put("status", "ERROR");
            response.put("message", e.getMessage());
        }
        return response;
    }

    /** 조회: guardianId, userIdx, chatMode 필수 / scheduleId 선택(없으면 GENERAL로 간주) */
    @GetMapping
    public Map<String, Object> list(@RequestParam("guardianId") String guardianId,
                                    @RequestParam("userIdx") int userIdx,
                                    @RequestParam(value = "scheduleId", required = false) Long scheduleId,
                                    @RequestParam(value = "chatMode", defaultValue = "SCHEDULE") String chatMode) {
        System.out.println("[API] GET /api/v1/chat-history | guardianId=" + guardianId
                + ", scheduleId=" + scheduleId + ", chatMode=" + chatMode);
        Map<String, Object> response = new HashMap<>();
        try {
            List<ChatHistoryVO> rows = chatHistoryService.findMessages(guardianId, userIdx, scheduleId, chatMode);

            List<Map<String, Object>> messages = new ArrayList<>();
            for (ChatHistoryVO r : rows) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("messageId", r.getMessageId());
                m.put("sender", r.getSender());
                m.put("message", r.getMessage());
                m.put("intent", r.getIntent());
                m.put("createdAt", r.getCreatedAt());
                messages.add(m);
            }

            System.out.println("[결과] 대화 " + messages.size() + "건 조회");
            response.put("status", "SUCCESS");
            response.put("messages", messages);
        } catch (Exception e) {
            System.out.println("[ERROR] " + e.getMessage());
            response.put("status", "ERROR");
            response.put("message", e.getMessage());
        }
        return response;
    }

    // ── 파싱 헬퍼 ────────────────────────────────────────────────────────
    private String str(Object o) {
        return o == null ? null : o.toString();
    }

    private Integer toInteger(Object o) {
        if (o == null) return null;
        try { return Integer.valueOf(o.toString().trim()); }
        catch (NumberFormatException e) { return null; }
    }

    private Long toLong(Object o) {
        if (o == null) return null;
        try { return Long.valueOf(o.toString().trim()); }
        catch (NumberFormatException e) { return null; }
    }
}
