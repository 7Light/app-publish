package com.huawei.publish.model;

public class VirusScanDetail {
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
}
