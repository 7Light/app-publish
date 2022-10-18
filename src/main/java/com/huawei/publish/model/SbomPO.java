package com.huawei.publish.model;

public class SbomPO {
    private String generateSbomUrl;
    private String publishSbomUrl;
    private String querySbomPublishResultUrl;
    private String publishId;
    private String publishResultDetail;

    public String getPublishResultDetail() {
        return publishResultDetail;
    }

    public void setPublishResultDetail(String publishResultDetail) {
        this.publishResultDetail = publishResultDetail;
    }

    public String getPublishId() {
        return publishId;
    }

    public void setPublishId(String publishId) {
        this.publishId = publishId;
    }

    public String getQuerySbomPublishResultUrl() {
        return querySbomPublishResultUrl;
    }

    public void setQuerySbomPublishResultUrl(String querySbomPublishResultUrl) {
        this.querySbomPublishResultUrl = querySbomPublishResultUrl;
    }

    public String getGenerateSbomUrl() {
        return generateSbomUrl;
    }

    public void setGenerateSbomUrl(String generateSbomUrl) {
        this.generateSbomUrl = generateSbomUrl;
    }

    public String getPublishSbomUrl() {
        return publishSbomUrl;
    }

    public void setPublishSbomUrl(String publishSbomUrl) {
        this.publishSbomUrl = publishSbomUrl;
    }
}
