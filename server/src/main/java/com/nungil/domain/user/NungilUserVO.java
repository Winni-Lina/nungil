package com.nungil.domain.user;

public class NungilUserVO {

    private String id;       // 보호자 id (FK)
    private int idx;         // 보호자당 순번
    private String specialNote;
    private String whiteList; // 콤마 구분 task_id 목록 (예: "1,2,3")
    private String userName;
    private String userPhone;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public int getIdx() { return idx; }
    public void setIdx(int idx) { this.idx = idx; }

    public String getSpecialNote() { return specialNote; }
    public void setSpecialNote(String specialNote) { this.specialNote = specialNote; }

    public String getWhiteList() { return whiteList; }
    public void setWhiteList(String whiteList) { this.whiteList = whiteList; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getUserPhone() { return userPhone; }
    public void setUserPhone(String userPhone) { this.userPhone = userPhone; }
}
