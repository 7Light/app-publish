package com.huawei.publish.model;

import java.util.List;

/**
 * publish main model
 */
public class PublishPO {
    private String gpgKeyUrl;
    private String keyFileName;
    private String rpmKey;
    private String fileKey;
    private String tempDir;
    private String conflict = "skip";//normal/skip/overwrite/error
    private String uploadType;//obs/others
    private String obsUrl;
    private String authorization;

    private String publishId;

    public String getPublishId() {
        return publishId;
    }

    public void setPublishId(String publishId) {
        this.publishId = publishId;
    }

    List<FilePO> files;
    private List<RepoIndex> repoIndexList;

    public String getGpgKeyUrl() {
        return gpgKeyUrl;
    }

    public void setGpgKeyUrl(String gpgKeyUrl) {
        this.gpgKeyUrl = gpgKeyUrl;
    }

    public String getKeyFileName() {
        return keyFileName;
    }

    public void setKeyFileName(String keyFileName) {
        this.keyFileName = keyFileName;
    }

    public String getRpmKey() {
        return rpmKey;
    }

    public void setRpmKey(String rpmKey) {
        this.rpmKey = rpmKey;
    }

    public String getFileKey() {
        return fileKey;
    }

    public void setFileKey(String fileKey) {
        this.fileKey = fileKey;
    }

    public String getTempDir() {
        return tempDir;
    }

    public void setTempDir(String tempDir) {
        this.tempDir = tempDir;
    }

    public List<FilePO> getFiles() {
        return files;
    }

    public void setFiles(List<FilePO> files) {
        this.files = files;
    }

    public List<RepoIndex> getRepoIndexList() {
        return repoIndexList;
    }

    public void setRepoIndexList(List<RepoIndex> repoIndexList) {
        this.repoIndexList = repoIndexList;
    }

    public String getConflict() {
        return conflict;
    }

    public void setConflict(String conflict) {
        this.conflict = conflict;
    }

    public String getUploadType() {
        return uploadType;
    }

    public void setUploadType(String uploadType) {
        this.uploadType = uploadType;
    }

    public String getObsUrl() {
        return obsUrl;
    }

    public void setObsUrl(String obsUrl) {
        this.obsUrl = obsUrl;
    }

    public String getAuthorization() {
        return authorization;
    }

    public void setAuthorization(String authorization) {
        this.authorization = authorization;
    }
}
