package com.huawei.publish.model;

/**
 * @author chentao
 */
public class VirusScanDetail {
    /**
     * 扫描id(评审单id)
     */
    private String reviewId;
    /**
     * 文件名
     */
    private String fileName;


    /**
     * 扫描结果
     */
    private String virusScanResult;

    /**
     * 结果明细
     */
    private String details;


    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getVirusScanResult() {
        return virusScanResult;
    }

    public void setVirusScanResult(String virusScanResult) {
        this.virusScanResult = virusScanResult;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public String getReviewId() {
        return reviewId;
    }

    public void setReviewId(String reviewId) {
        this.reviewId = reviewId;
    }
}
