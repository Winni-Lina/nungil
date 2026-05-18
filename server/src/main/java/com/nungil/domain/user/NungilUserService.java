package com.nungil.domain.user;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class NungilUserService {

    private final NungilUserMapper nungilUserMapper;

    public NungilUserService(NungilUserMapper nungilUserMapper) {
        this.nungilUserMapper = nungilUserMapper;
    }

    public NungilUserVO createUser(String guardianId) {
        int nextIdx = nungilUserMapper.getNextIdx(guardianId);
        System.out.println("[DB] NUNGIL_USER MAX(idx) 조회 (id=" + guardianId + ") → nextIdx=" + nextIdx);
        NungilUserVO user = new NungilUserVO();
        user.setId(guardianId);
        user.setIdx(nextIdx);
        user.setSpecialNote("");
        user.setWhiteList("");
        nungilUserMapper.insert(user);
        System.out.println("[DB] NUNGIL_USER INSERT (id=" + guardianId + ", idx=" + nextIdx + ")");
        return user;
    }

    public List<NungilUserVO> getUsersByGuardian(String guardianId) {
        List<NungilUserVO> list = nungilUserMapper.findByGuardianId(guardianId);
        System.out.println("[DB] NUNGIL_USER 조회 (id=" + guardianId + ") → " + list.size() + "명");
        return list;
    }

    public NungilUserVO getUser(String guardianId, int idx) {
        NungilUserVO user = nungilUserMapper.findByIdAndIdx(guardianId, idx);
        System.out.println("[DB] NUNGIL_USER 조회 (id=" + guardianId + ", idx=" + idx + ") → "
                + (user != null ? "찾음" : "없음"));
        return user;
    }

    public List<Long> addToWhiteList(String guardianId, int idx, Long taskId) {
        NungilUserVO user = nungilUserMapper.findByIdAndIdx(guardianId, idx);
        List<Long> taskIds = parseWhiteList(user.getWhiteList());

        if (taskIds.contains(taskId)) throw new IllegalArgumentException("ITEM_EXISTS");

        taskIds.add(taskId);
        String newList = joinWhiteList(taskIds);
        nungilUserMapper.updateWhiteList(guardianId, idx, newList);
        System.out.println("[DB] NUNGIL_USER UPDATE white_list (id=" + guardianId + ", idx=" + idx + ") → " + newList);
        return taskIds;
    }

    public List<Long> removeFromWhiteList(String guardianId, int idx, Long taskId) {
        NungilUserVO user = nungilUserMapper.findByIdAndIdx(guardianId, idx);
        List<Long> taskIds = parseWhiteList(user.getWhiteList());
        taskIds.remove(taskId);
        String newList = joinWhiteList(taskIds);
        nungilUserMapper.updateWhiteList(guardianId, idx, newList);
        System.out.println("[DB] NUNGIL_USER UPDATE white_list 삭제 (id=" + guardianId + ", idx=" + idx + ") → " + newList);
        return taskIds;
    }

    public List<Long> getWhiteList(String guardianId, int idx) {
        NungilUserVO user = nungilUserMapper.findByIdAndIdx(guardianId, idx);
        List<Long> list = user != null ? parseWhiteList(user.getWhiteList()) : new ArrayList<>();
        System.out.println("[DB] NUNGIL_USER white_list 조회 (id=" + guardianId + ", idx=" + idx + ") → " + list);
        return list;
    }

    public void updateUserInfo(String guardianId, int idx, String userName, String userPhone) {
        nungilUserMapper.updateUserInfo(guardianId, idx, userName, userPhone);
        System.out.println("[DB] NUNGIL_USER UPDATE user_name=" + userName + ", user_phone=" + userPhone + " (id=" + guardianId + ", idx=" + idx + ")");
    }

    public void updateSpecialNote(String guardianId, int idx, String specialNote) {
        nungilUserMapper.updateSpecialNote(guardianId, idx, specialNote);
        System.out.println("[DB] NUNGIL_USER UPDATE special_note (id=" + guardianId + ", idx=" + idx + ")");
    }

    private List<Long> parseWhiteList(String whiteList) {
        List<Long> result = new ArrayList<>();
        if (whiteList == null || whiteList.trim().isEmpty()) return result;
        for (String s : whiteList.split(",")) {
            try { result.add(Long.parseLong(s.trim())); } catch (Exception ignored) {}
        }
        return result;
    }

    private String joinWhiteList(List<Long> taskIds) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < taskIds.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(taskIds.get(i));
        }
        return sb.toString();
    }
}
