package com.huawei.publish.service;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * file download component
 * @author chentao
 */
@Component
public class FileDownloadService {

    private static final Logger log = Logger.getLogger(FileDownloadService.class);
    /**
     * @param url      downloadUrl
     * @param dir      save path
     * @param fileName file name
     */
    public void downloadHttpUrl(String url, String dir, String fileName, String authorization) {
        try {
            log.info("downloadHttpUrl:" + url);
            File dirFile = new File(dir);
            if (!dirFile.exists()) {
                dirFile.mkdirs();
            }
            HttpClient client = new HttpClient();
            GetMethod getMethod = new GetMethod(url);
            if(!StringUtils.isEmpty(authorization)){
                getMethod.setRequestHeader("Authorization", authorization);
            }
            client.executeMethod(getMethod);
            InputStream is = getMethod.getResponseBodyAsStream();
            Files.copy(is, Paths.get(dir + "/" + fileName), StandardCopyOption.REPLACE_EXISTING);
            is.close();
        } catch (Exception e) {
            log.error(e.getMessage());
        }
    }

    public String getContent(String url, String authorization) {
        log.info("getContent:" + url);
        try {
            HttpClient client = new HttpClient();
            GetMethod getMethod = new GetMethod(url);
            if(!StringUtils.isEmpty(authorization)){
                getMethod.setRequestHeader("Authorization", authorization);
            }
            client.executeMethod(getMethod);
            String body = getMethod.getResponseBodyAsString();
            if (getMethod.getStatusCode() == 200) {
                return body;
            }
            log.info("getContent:" + body);
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        return "";
    }
}
