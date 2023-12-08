package com.huawei.publish.service;

import com.huawei.publish.enums.AppConst;
import com.huawei.publish.model.FilePO;
import com.huawei.publish.model.VirusScanDetail;
import com.huawei.publish.model.VirusScanPO;
import com.huawei.publish.model.VirusScanResultPO;
import com.huawei.publish.utils.FileDownloadUtil;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * @author chentao
 */
@Component
public class VirusScanService {
    private static final Logger log = LoggerFactory.getLogger(VerifyService.class);

    public VirusScanResultPO virusScanning(VirusScanPO virusScan) {
        List<FilePO> files = virusScan.getFiles();
        String tempDirPath = FileDownloadUtil.getTempDirPath(virusScan.getTempDir());
        try {
            File tempDir = new File(tempDirPath);
            if (!tempDir.exists()) {
                execCmd("mkdir -p " + tempDirPath);
            }
            VirusScanResultPO virusScanResult = new VirusScanResultPO();
            virusScanResult.setResult(AppConst.SUCCESS_TAG);
            List<VirusScanDetail> details = new ArrayList<>();
            VirusScanDetail detail;
            for (FilePO file : files) {
                detail = new VirusScanDetail();
                String fileName = file.getName();
                String fileExistsFlag = execCmd("ssh -i /var/log/ssh_key/private.key -o StrictHostKeyChecking=no root@"
                    + virusScan.getRemoteRepoIp() + " \"[ -f " + file.getTargetPath() + "/" + fileName + " ]  &&  echo exists || echo does not exist\"");
                if ("skip".equals(virusScan.getConflict()) && "exists".equals(fileExistsFlag)) {
                    // 发布件已存在跳过病毒扫描
                    detail.setVirusScanResult("skip");
                    continue;
                }
                log.info("URL:"+file.getUrl()+"tempDirPath"+tempDirPath+"fileName"+fileName);
                String downloadResult = FileDownloadUtil.downloadHttpUrl(file.getUrl(), tempDirPath, fileName);
                if ("fail".equals(downloadResult)){
                    detail.setVirusScanResult(AppConst.FAIL_TAG);
                    virusScanResult.setResult(AppConst.FAIL_TAG);
                    execCmd("rm -rf " + tempDirPath);
                    continue;
                }
                File targetPathDir = new File(file.getTargetPath());
                if (!targetPathDir.exists()) {
                    targetPathDir.mkdirs();
                }
                if (!StringUtils.isEmpty(tempDirPath) && !tempDirPath.endsWith(AppConst.SLASH)) {
                    tempDirPath = tempDirPath + AppConst.SLASH;
                }
                // clamAv扫描病毒
                String ret = clamScan(tempDirPath + fileName);
                detail.setVirusScanResult(AppConst.SUCCESS_TAG);
                if (!(ret.contains(": OK") && !ret.contains("FOUND"))) {
                    detail.setVirusScanResult(AppConst.FAIL_TAG);
                    virusScanResult.setResult(AppConst.FAIL_TAG);
                }
                detail.setFileName(fileName);
                detail.setReviewId(virusScan.getScanId());
                detail.setDetails(ret);
                details.add(detail);
                execCmd("rm -rf " + tempDirPath);
            }
            virusScanResult.setDetails(details);
            return  virusScanResult;
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
    /**
     * clamAv 扫描病毒
     *
     * @param filePath 扫描路径
     */
    public String clamScan(String filePath) {
        try {
            return execCmd("clamscan " + filePath);
        } catch (Exception e) {
            log.error("clamScan verify error,file:" + filePath + " error:" + e.getMessage());
            return " error:" + e.getMessage();
        }
    }

    /**
     * @param cmd cmd
     * @return output
     * @throws IOException
     * @throws InterruptedException
     */
    public String execCmd(String cmd) throws IOException, InterruptedException {
        log.info("cmd:" + cmd);
        Process exec = Runtime.getRuntime().exec(new String[] {"/bin/sh","-c",cmd});
        exec.waitFor();
        String output = getExecOutput(exec);
        log.info("output:" + output);
        return output;
    }

    private String getExecOutput(Process exec) {
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(exec.getInputStream()));
        BufferedReader bufferedReader2 = new BufferedReader(new InputStreamReader(exec.getErrorStream()));
        StringBuilder sb = new StringBuilder();
        try {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                sb.append(line);
            }
            while ((line = bufferedReader2.readLine()) != null) {
                sb.append(line);
            }
        } catch (IOException e) {
            log.error(e.getMessage());
        }
        return sb + "";
    }
}
