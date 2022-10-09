package com.huawei.publish.service;

import com.huawei.publish.model.FileFromRepoModel;
import com.obs.services.ObsClient;
import com.obs.services.ObsConfiguration;
import com.obs.services.exception.ObsException;
import com.obs.services.model.*;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ObsUtil {
    private static Logger log = Logger.getLogger(ObsUtil.class);
    private static final String endPoint = "obs.cn-south-1.myhuaweicloud.com";

    private static final String ak = "G3WBWCPXZGXM2P9EIDKP";

    private static final String sk = "jKBChm1BEiH64oP7WdwPmDHOmcRRPd08rk90rbzC";

    private ObsConfiguration config;

    private static String bucketName = "openlibing-test";


    public ObsUtil() {
        config = new ObsConfiguration();
        config.setSocketTimeout(30000);
        config.setConnectionTimeout(10000);
        config.setEndPoint(endPoint);
    }

    public boolean copyObject(String sourceObjectKey, String destObjectKey) {
        ObsClient obsClient = new ObsClient(ak, sk, config);
        try {
            obsClient.copyObject(bucketName, sourceObjectKey, bucketName, destObjectKey);
            return true;
        } catch (ObsException e) {
            log.error(e.getErrorMessage());
            return false;
        } finally {
            if (obsClient != null) {
                try {
                    obsClient.close();
                } catch (IOException e) {
                    log.error("", e);
                }
            }
        }
    }

    public List<FileFromRepoModel> listObjects(String path) {
        ObsClient obsClient = new ObsClient(ak, sk, config);
        try {
            ListObjectsRequest listObjectsRequest = new ListObjectsRequest(bucketName);
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
            if (obsClient != null) {
                try {
                    obsClient.close();
                } catch (IOException e) {
                    log.error("", e);
                }
            }
        }
    }

    public void downFile(String obsFullPath, String localFullPath) {
        ObsClient obsClient = new ObsClient(ak, sk, config);
        try {
            DownloadFileRequest request = new DownloadFileRequest(bucketName, obsFullPath);
            // Set the local path to which the object is downloaded.
            request.setDownloadFile(localFullPath);
            // Set the maximum number of parts that can be concurrently downloaded.
            request.setTaskNum(5);
            // Set the part size to 1 MB.
            request.setPartSize(1 * 1024 * 1024);
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
            if (obsClient != null) {
                try {
                    obsClient.close();
                } catch (IOException e) {
                    log.error("", e);
                }
            }
        }
    }

    public boolean isExist(String objectKey) {
        ObsClient obsClient = new ObsClient(ak, sk, config);
        try {
            boolean exist = obsClient.doesObjectExist(bucketName, objectKey);
            return exist;
        } catch (ObsException e) {
            log.error(e.getErrorMessage());
            return false;
        } finally {
            if (obsClient != null) {
                try {
                    obsClient.close();
                } catch (IOException e) {
                    log.error("", e);
                }
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

    public static void main(String[] args) {
//        ObsUtil.listObjects("1.7.0/");
        new ObsUtil().downFile("xfb/obsutil", "C:\\obsutil");
    }

}
