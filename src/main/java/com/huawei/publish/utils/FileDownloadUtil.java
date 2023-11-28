package com.huawei.publish.utils;

import com.huawei.publish.enums.AppConst;
import com.huawei.publish.service.VerifyService;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * @author chentao
 */
public class FileDownloadUtil {
    private static final Logger log = LoggerFactory.getLogger(FileDownloadUtil.class);
    /**
     * @param url      downloadUrl
     * @param dir      save path
     * @param fileName file name
     */
    public static String downloadHttpUrl(String url, String dir, String fileName) {
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
            if (!dir.endsWith("/")) {
                dir = dir + "/";
            }
            FileOutputStream fileOut = new FileOutputStream(dir + fileName);
            byte[] buffer = new byte[cache];
            int ch = 0;
            while ((ch = is.read(buffer)) != -1) {
                fileOut.write(buffer, 0, ch);
            }
            is.close();
            fileOut.flush();
            fileOut.close();
        } catch (Exception e) {
            e.printStackTrace();
            log.error("download error: " + e.getMessage());
            return "fail";
        }
        return "success";
    }

    public static String getTempDirPath(String tempDir) {
        if (tempDir.startsWith(AppConst.SLASH)) {
            return "/var/log" + tempDir;
        } else {
            return "/var/log/" + tempDir;
        }
    }
}
