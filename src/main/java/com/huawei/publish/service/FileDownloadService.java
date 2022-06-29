package com.huawei.publish.service;

import com.huawei.publish.PublishVerifyController;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * file download component
 */
@Component
public class FileDownloadService {

    private static Logger log = Logger.getLogger(PublishVerifyController.class);

    /**
     * @param url      downloadUrl
     * @param dir      save path
     * @param fileName file name
     */
    public void downloadHttpUrl(String url, String dir, String fileName) {
        try {
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
        try {
            HttpClient client = new HttpClient();
            GetMethod getMethod = new GetMethod(url);
            client.executeMethod(getMethod);
            return getMethod.getResponseBodyAsString();
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        return "";
    }
}
