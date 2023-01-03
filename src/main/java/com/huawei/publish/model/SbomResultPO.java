package com.huawei.publish.model;

import java.util.List;
import java.util.Map;

/**
 * SbomResultPO
 *
 * @author jrsgxtc
 * @since 2022/12/5
 */
public class SbomResultPO {
    private String message;

    private String result;

    private Map<String, String> taskId;

    private List<FilePO> files;

    private SbomPO sbomPO;

    private boolean isPublishSuccess;

    public boolean isPublishSuccess() {
        return isPublishSuccess;
    }

    public void setPublishSuccess(boolean publishSuccess) {
        isPublishSuccess = publishSuccess;
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

    public Map<String, String> getTaskId() {
        return taskId;
    }

    public void setTaskId(Map<String, String> taskId) {
        this.taskId = taskId;
    }

    public List<FilePO> getFiles() {
        return files;
    }

    public void setFiles(List<FilePO> files) {
        this.files = files;
    }

    public SbomPO getSbomPO() {
        return sbomPO;
    }

    public void setSbomPO(SbomPO sbomPO) {
        this.sbomPO = sbomPO;
    }
}
