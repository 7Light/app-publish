package com.huawei.publish.model;

public class SbomResultPO {
    private String message;

    private String result;

    private String taskId;

    private String sbomRef;

    public String getSbomRef() {
        return sbomRef;
    }

    public void setSbomRef(String sbomRef) {
        this.sbomRef = sbomRef;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }
}
