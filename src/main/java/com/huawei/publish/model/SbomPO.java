package com.huawei.publish.model;

public class SbomPO {
    private String generateSbomUrl;
    private String publishSbomUrl;
    private String querySbomPublishResultUrl;

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
