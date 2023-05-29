package com.huawei.publish.model;

import java.util.Arrays;

/**
 * 归档信息-->app-publish
 *
 * @author jrsgxtc
 * @since 2023/3/21
 */
public class ArchiveInfoPO {
    /**
     * 版本号
     */
    private String versionNum;

    /**
     * 发布公告信息
     */
    private byte[] bulletin;

    /**
     * 发布评审信息
     */
    private byte[] reviewDetail;

    /**
     * 归档目标ip
     */
    private String remoteRepoIp;

    /**
     * 发布公告归档地址
     */
    private String bulletinArchivePath;

    /**
     * 评审详情归档地址
     */
    private String reviewArchivePath;

    /**
     * 唯一标识，reviewId
     */
    private String archiveId;

    /**
     * 存储库的所有者或组织
     */
    private String owner;

    /**
     * 存储库的名称
     */
    private String repoName;

    /**
     * 文件在存储库中的路径
     */
    private String filePath;

    /**
     * 分支名称，用于指定要将文件添加到哪个分支中。
     */
    private String branch;

    /**
     *  Gitee 令牌，用于进行身份验证
     */
    private String giteeUploadAccessToken ;

    public String getRepoName() {
        return repoName;
    }

    public void setRepoName(String repoName) {
        this.repoName = repoName;
    }

    public String getVersionNum() {
        return versionNum;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public String getGiteeUploadAccessToken() {
        return giteeUploadAccessToken;
    }

    public void setGiteeUploadAccessToken(String giteeUploadAccessToken) {
        this.giteeUploadAccessToken = giteeUploadAccessToken;
    }

    public void setVersionNum(String versionNum) {
        this.versionNum = versionNum;
    }

    public byte[] getBulletin() {
        return bulletin;
    }

    public void setBulletin(byte[] bulletin) {
        this.bulletin = bulletin;
    }

    public byte[] getReviewDetail() {
        return reviewDetail;
    }

    public void setReviewDetail(byte[] reviewDetail) {
        this.reviewDetail = reviewDetail;
    }

    public String getRemoteRepoIp() {
        return remoteRepoIp;
    }

    public void setRemoteRepoIp(String remoteRepoIp) {
        this.remoteRepoIp = remoteRepoIp;
    }

    public String getArchiveId() {
        return archiveId;
    }

    public void setArchiveId(String archiveId) {
        this.archiveId = archiveId;
    }

    public String getBulletinArchivePath() {
        return bulletinArchivePath;
    }

    public void setBulletinArchivePath(String bulletinArchivePath) {
        this.bulletinArchivePath = bulletinArchivePath;
    }

    public String getReviewArchivePath() {
        return reviewArchivePath;
    }

    public void setReviewArchivePath(String reviewArchivePath) {
        this.reviewArchivePath = reviewArchivePath;
    }

    @Override
    public String toString() {
        return "ArchiveInfoPO{" +
            "versionNum='" + versionNum + '\'' +
            ", bulletin=" + Arrays.toString(bulletin) +
            ", reviewDetail=" + Arrays.toString(reviewDetail) +
            ", remoteRepoIp='" + remoteRepoIp + '\'' +
            ", bulletinArchivePath='" + bulletinArchivePath + '\'' +
            ", reviewArchivePath='" + reviewArchivePath + '\'' +
            ", archiveId='" + archiveId + '\'' +
            ", owner='" + owner + '\'' +
            ", repoName='" + repoName + '\'' +
            ", filePath='" + filePath + '\'' +
            ", branch='" + branch + '\'' +
            ", giteeUploadAccessToken='" + giteeUploadAccessToken + '\'' +
            '}';
    }
}
