package com.huawei.publish.model;

import java.util.List;

/**
 * @author chentao
 */
public class VirusScanPO {
    /**
     * 扫描id(评审单id)
     */
    private String scanId;

    /**
     * 远程地址
     */
    private String remoteRepoIp;

    /**
     * 临时文件路径
     */
    private String tempDir;

    /**
     * 发布仓已存在发布件处理方式 skip-跳过扫描
     */
    private String conflict;
    /**
     * 扫描发布件
     */
    private  List<FilePO> files;

    public String getScanId() {
        return scanId;
    }

    public void setScanId(String scanId) {
        this.scanId = scanId;
    }

    public String getTempDir() {
        return tempDir;
    }

    public void setTempDir(String tempDir) {
        this.tempDir = tempDir;
    }

    public String getConflict() {
        return conflict;
    }

    public void setConflict(String conflict) {
        this.conflict = conflict;
    }

    public List<FilePO> getFiles() {
        return files;
    }

    public void setFiles(List<FilePO> files) {
        this.files = files;
    }

    public String getRemoteRepoIp() {
        return remoteRepoIp;
    }

    public void setRemoteRepoIp(String remoteRepoIp) {
        this.remoteRepoIp = remoteRepoIp;
    }
}
