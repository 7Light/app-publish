package com.huawei.publish.model;

import java.util.List;

/**
 * @author chentao
 */
public class VirusScanResultPO {
    /**
     * 扫描id(评审单id)
     */
    private String scanId;

    private String result;

    private List<VirusScanDetail> details;

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public List<VirusScanDetail> getDetails() {
        return details;
    }

    public void setDetails(List<VirusScanDetail> details) {
        this.details = details;
    }

    public String getScanId() {
        return scanId;
    }

    public void setScanId(String scanId) {
        this.scanId = scanId;
    }
}
