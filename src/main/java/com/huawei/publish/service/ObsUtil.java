package com.huawei.publish.service;

import com.huawei.publish.model.FileFromRepoModel;
import com.obs.services.ObsClient;
import com.obs.services.ObsConfiguration;
import com.obs.services.exception.ObsException;
import com.obs.services.model.DownloadFileRequest;
import com.obs.services.model.ListObjectsRequest;
import com.obs.services.model.MonitorableProgressListener;
import com.obs.services.model.ObjectListing;
import com.obs.services.model.ObjectMetadata;
import com.obs.services.model.ObsObject;
import com.obs.services.model.ProgressStatus;
import org.apache.log4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;

/**
 * @author xiongfengbo
 */
public class ObsUtil {
    private static final Logger log = Logger.getLogger(ObsUtil.class);
    private static final String END_POINT = System.getenv("endPoint");
    private static final String AK = System.getenv("ak");
    private static final String SK = System.getenv("sk");
    private ObsConfiguration config;
    private static final String BUCKET_NAME = System.getenv("bucketName");

    public ObsUtil() {
        config = new ObsConfiguration();
        config.setSocketTimeout(30000);
        config.setConnectionTimeout(10000);
        config.setEndPoint(END_POINT);
    }

    public boolean copyObject(String sourceObjectKey, String destObjectKey) {
        ObsClient obsClient = new ObsClient(AK, SK, config);
        try {
            obsClient.copyObject(BUCKET_NAME, sourceObjectKey, BUCKET_NAME, destObjectKey);
            return true;
        } catch (ObsException e) {
            log.error(e.getErrorMessage());
            return false;
        } finally {
            try {
                obsClient.close();
            } catch (IOException e) {
                log.error("", e);
            }
        }
    }

    public List<FileFromRepoModel> listObjects(String path) {
        ObsClient obsClient = new ObsClient(AK, SK, config);
        try {
            ListObjectsRequest listObjectsRequest = new ListObjectsRequest(BUCKET_NAME);
            listObjectsRequest.setPrefix(path);
            ObjectListing objectListing = obsClient.listObjects(listObjectsRequest);
            List<FileFromRepoModel> result = new ArrayList<>();
            for (ObsObject object : objectListing.getObjects()) {
                FileFromRepoModel file = new FileFromRepoModel();
                String objectKey = object.getObjectKey();
                ObjectMetadata metadata = object.getMetadata();
                String pathStr = "/";
                boolean isDir = objectKey.endsWith(pathStr);
                if (isDir) {
                    objectKey = objectKey.substring(0, objectKey.length() - 1);
                    file.setSize("-");
                } else {
                    file.setSize(toFormatSize(metadata.getContentLength()));
                }
                if (objectKey.contains(pathStr)) {
                    file.setName(objectKey.substring(objectKey.lastIndexOf(pathStr) + 1));
                    file.setParentDir(objectKey.substring(0, objectKey.lastIndexOf(pathStr) + 1));
                } else {
                    file.setName(objectKey);
                }
                file.setDir(isDir);
                result.add(file);
            }
            return result;
        } catch (ObsException e) {
            log.error(e.getErrorMessage());
            return null;
        } finally {
            try {
                obsClient.close();
            } catch (IOException e) {
                log.error("", e);
            }
        }
    }

    public void downFile(String obsFullPath, String localFullPath) {
        ObsClient obsClient = new ObsClient(AK, SK, config);
        try {
            DownloadFileRequest request = new DownloadFileRequest(BUCKET_NAME, obsFullPath);
            // Set the local path to which the object is downloaded.
            request.setDownloadFile(localFullPath);
            // Set the maximum number of parts that can be concurrently downloaded.
            request.setTaskNum(5);
            // Set the part size to 1 MB.
            request.setPartSize(1024 * 1024);
            // Enable resumable upload.
            request.setEnableCheckpoint(true);
            // Trigger the listener callback every 100 KB.
            request.setProgressInterval(100 * 1024L);

            MonitorableProgressListener progressListener = new MonitorableProgressListener() {
                @Override
                public void progressChanged(ProgressStatus status) {
                    log.info(new Date() + "  TransferPercentage:" + status.getTransferPercentage());
                }
            };
            // Set a data transmission listener that can monitor the running status of subprocesses.
            request.setProgressListener(progressListener);

            // Start a thread to download an object.
            ObsDownloadManager downloadManager = new ObsDownloadManager(obsClient, request, progressListener);
            downloadManager.download();
            downloadManager.waitingFinish();
        } catch (ObsException e) {
            log.info("Response Code: " + e.getResponseCode());
            log.info("Error Message: " + e.getErrorMessage());
            log.info("Error Code:       " + e.getErrorCode());
            log.info("Request ID:      " + e.getErrorRequestId());
            log.info("Host ID:           " + e.getErrorHostId());
        } catch (InterruptedException e) {
            log.error(e.getMessage());
        } finally {
            try {
                obsClient.close();
            } catch (IOException e) {
                log.error("", e);
            }
        }
    }

    public boolean isExist(String objectName) {
        ObsClient obsClient = new ObsClient(AK, SK, config);
        try {
            return obsClient.doesObjectExist(BUCKET_NAME, objectName);
        } catch (ObsException e) {
            log.error(e.getErrorMessage());
            return false;
        } finally {
            try {
                obsClient.close();
            } catch (IOException e) {
                log.error("", e);
            }
        }
    }

    private String toFormatSize(Long length) {
        BigDecimal dividend = new BigDecimal(length);
        String result = "";
        if (length == 0) {
            return "0B";
        }
        if (length < 1024) {
            result = length + "B";
        } else if (length < 1048576) {
            result = dividend.divide(new BigDecimal(1024), 2, RoundingMode.HALF_UP) + "KB";
        } else if (length < 1073741824) {
            result = dividend.divide(new BigDecimal(1048576), 2, RoundingMode.HALF_UP) + "MB";
        } else {
            result = dividend.divide(new BigDecimal(1073741824), 2, RoundingMode.HALF_UP) + "GB";
        }
        return result;
    }

    /**
     * 将obs上的监控数据文件base64 encode为string
     *
     * @param objectName 文件路径
     * @return String
     */
    public String getSbomContent(String objectName) {
        ObsClient obsClient = new ObsClient(AK, SK, config);
        InputStream is = null;
        ByteArrayOutputStream bos = null;
        byte[] bytes = null;
        try {
            ObsObject obsObject = obsClient.getObject(BUCKET_NAME, objectName);
            is = obsObject.getObjectContent();
            bos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len;
            while ((len = is.read(buffer)) != -1) {
                bos.write(buffer, 0, len);
            }
            bytes = bos.toByteArray();
        } catch (IOException | ObsException e) {
            log.error(e.getMessage());
        } finally {
            if (bos != null) {
                try {
                    bos.close();
                } catch (IOException e) {
                    log.error(e.getMessage());
                }
            }
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    log.error(e.getMessage());
                }
            }
            try {
                obsClient.close();
            } catch (IOException e) {
                log.error("", e);
            }
        }
        return new String(Base64.getEncoder().encode(bytes));
    }
}
