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

    public String getVersionNum() {
        return versionNum;
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
            '}';
    }
}
