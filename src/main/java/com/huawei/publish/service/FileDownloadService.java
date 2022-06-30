package com.huawei.publish.service;

import com.huawei.publish.PublishVerifyController;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * file download component
 */
@Component
public class FileDownloadService {

    private static Logger log = Logger.getLogger(PublishVerifyController.class);

    @Autowired
    VerifyService verifyService;

    public void wgetFile(String url, String dir, String fileName) throws IOException, InterruptedException {
        verifyService.execCmd("wget --no-check-certificate -P " + dir + " " + url + fileName);
    }

    /**
     * @param url      downloadUrl
     * @param dir      save path
     * @param fileName file name
     */
    public void downloadHttpUrl(String url, String dir, String fileName) {
        try {
            log.info("downloadHttpUrl:" + url);
            File dirFile = new File(dir);
            if (!dirFile.exists()) {
                dirFile.mkdirs();
            }
            HttpClient client = new HttpClient();
            GetMethod getMethod = new GetMethod(url);
            client.executeMethod(getMethod);
            InputStream is = getMethod.getResponseBodyAsStream();
            int cache = 10 * 1024;
            FileOutputStream fileOut = new FileOutputStream(dir + "/" + fileName);
            byte[] buffer = new byte[cache];
            int ch = 0;
            while ((ch = is.read(buffer)) != -1) {
                fileOut.write(buffer, 0, ch);
            }
            is.close();
            fileOut.flush();
            fileOut.close();
        } catch (Exception e) {
            log.error(e.getMessage());
        }
    }

    public String getContent(String url) {
        log.info("getContent:" + url);
        try {
            HttpClient client = new HttpClient();
            GetMethod getMethod = new GetMethod(url);
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
