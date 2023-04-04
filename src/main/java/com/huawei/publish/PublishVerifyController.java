package com.huawei.publish;

import com.alibaba.fastjson.JSONObject;
import com.huawei.publish.model.ArchiveInfoPO;
import com.huawei.publish.model.FilePO;
import com.huawei.publish.model.PublishPO;
import com.huawei.publish.model.PublishResult;
import com.huawei.publish.model.RepoIndex;
import com.huawei.publish.model.SbomPO;
import com.huawei.publish.model.SbomResultPO;
import com.huawei.publish.service.FileDownloadService;
import com.huawei.publish.service.SbomService;
import com.huawei.publish.service.VerifyService;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * main controller
 */
@RequestMapping(path = "/publish")
@RestController
public class PublishVerifyController {
    private static final Logger log = LoggerFactory.getLogger(PublishVerifyController.class);
    private static Map<String, PublishResult> publishResult = new HashMap<>();
    private static Map<String, SbomResultPO> sbomResultMap = new HashMap<>();

    @Autowired
    private FileDownloadService fileDownloadService;
    private VerifyService verifyService;
    @Autowired
    private SbomService sbomService;

    /**
     * heartbeat
     *
     * @return heartbeat test
     */
    @RequestMapping(value = "/heartbeat", method = RequestMethod.GET)
    public Map<String, Object> heartbeat() {
        Map<String, Object> result = new HashMap<>();
        result.put("result", "success");
        return result;
    }

    /**
     * publish
     *
     * @param publishPO publish model
     * @return PublishResult PublishResult
     */
    @RequestMapping(value = "/publish", method = RequestMethod.POST)
    public PublishResult publish(@RequestBody PublishPO publishPO) {
        PublishResult result = new PublishResult();
        result.setResult("success");
        String validate = validate(publishPO);
        if (!StringUtils.isEmpty(validate)) {
            result.setResult("fail");
            result.setMessage("Validate failed, " + validate);
            return result;
        }
        verifyService = new VerifyService(publishPO);
        List<FilePO> files = publishPO.getFiles();
        String tempDirPath = getTempDirPath(publishPO.getTempDir());
        try {
            File tempDir = new File(tempDirPath);
            if (!tempDir.exists()) {
                verifyService.execCmd("mkdir -p " + tempDirPath);
            }
            for (FilePO file : files) {
                String fileName = file.getName();
                String fileExistsFlag = verifyService.execCmd("ssh -i /var/log/ssh_key/private.key -o StrictHostKeyChecking=no root@"
                    + publishPO.getRemoteRepoIp() + " \"[ -f " + file.getTargetPath() + "/" + fileName + " ]  &&  echo exists || echo does not exist\"");
                if ("skip".equals(publishPO.getConflict()) && "exists".equals(fileExistsFlag)) {
                    file.setPublishResult("skip");
                    continue;
                }
                fileDownloadService.downloadHttpUrl(file.getUrl(), tempDirPath, fileName);
                String verifyMessage = verify(tempDirPath, file, fileName);
                if (!StringUtils.isEmpty(verifyMessage)) {
                    file.setVerifyResult(verifyMessage);
                    result.setResult("fail");
                    continue;
                } else {
                    file.setVerifyResult("success");
                }
                File targetPathDir = new File(file.getTargetPath());
                if (!targetPathDir.exists()) {
                    targetPathDir.mkdirs();
                }
                if (!StringUtils.isEmpty(tempDirPath) && !tempDirPath.endsWith("/")) {
                    tempDirPath = tempDirPath + "/";
                }
                String folderExistsFlag = verifyService.execCmd("ssh -i /var/log/ssh_key/private.key -o StrictHostKeyChecking=no root@"
                    + publishPO.getRemoteRepoIp() + " \"[ -d " + file.getTargetPath() + " ]  &&  echo exists || echo does not exist\"");
                if (!"exists".equals(folderExistsFlag)) {
                    verifyService.execCmd("ssh -i /var/log/ssh_key/private.key -o StrictHostKeyChecking=no root@"
                        + publishPO.getRemoteRepoIp() + " \"mkdir -p " + file.getTargetPath() + "\"");
                }
                String outPut = verifyService.execCmd("scp -i /var/log/ssh_key/private.key -o StrictHostKeyChecking=no " + tempDirPath
                    + fileName + " root@" + publishPO.getRemoteRepoIp() + ":" + file.getTargetPath() + "/" + fileName);
                if (!StringUtils.isEmpty(outPut)) {
                    file.setPublishResult("failed");
                    result.setResult("fail");
                    continue;
                }
                if ("exists".equals(fileExistsFlag)) {
                    file.setPublishResult("cover");
                } else {
                    file.setPublishResult("normal");
                }
            }
            verifyService.execCmd("rm -rf " + tempDirPath);
            if (!CollectionUtils.isEmpty(publishPO.getRepoIndexList())) {
                for (RepoIndex repoIndex : publishPO.getRepoIndexList()) {
                    if (repoIndex != null) {
                        if ("createrepo".equals(repoIndex.getIndexType())) {
                            verifyService.execCmd("ssh -i /var/log/ssh_key/private.key -o StrictHostKeyChecking=no root@"
                                + publishPO.getRemoteRepoIp() + " \"createrepo -d " + repoIndex.getRepoPath() + "\"");
                        }
                    }
                }
            }
        } catch (IOException | InterruptedException e) {
            result.setResult("fail");
            result.setMessage("publish failed, " + e.getMessage());
            return result;
        }
        result.setFiles(files);
        return result;
    }

    @RequestMapping(value = "/publishAsync", method = RequestMethod.POST)
    public String publishAsync(@RequestBody PublishPO publishPO) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                PublishResult publish = publish(publishPO);
                publishResult.put(publishPO.getPublishId(), publish);
            }
        }).start();
        return "Start publish task success.";
    }

    @RequestMapping(value = "/getPublishResult", method = RequestMethod.GET)
    public PublishResult getPublishResult(@RequestParam(value = "publishId", required = true) String publishId) {
        return publishResult.get(publishId);
    }

    @RequestMapping(value = "/querySbomPublishResult", method = RequestMethod.POST)
    public SbomResultPO querySbomPublishResult(@RequestBody SbomPO sbomPO) {
        String publishId = sbomPO.getPublishId();
        SbomResultPO sbomResult = sbomResultMap.get(publishId);
        if (sbomResult != null) {
            if (sbomResult.getTaskId() == null) {
                if ("publishing".equals(sbomResult.getResult()) && !StringUtils.isEmpty(sbomResult.getMessage())) {
                    sbomResult.setMessage("");
                    // 请求失败，再次发起
                    PublishResult publishResult = JSONObject.parseObject(sbomPO.getPublishResultDetail(), PublishResult.class);
                    sbomResultAsync(sbomPO, publishResult.getFiles());
                }
                return sbomResult;
            }
            Map<String, String> taskIdMap = sbomResult.getTaskId();
            Map<String, String> sbomRefMap = new HashMap<>();
            for (String key : taskIdMap.keySet()) {
                String taskId = taskIdMap.get(key);
                Map<String, String> queryResult = sbomService.querySbomPublishResult(
                    sbomPO.getQuerySbomPublishResultUrl() + "/" + taskId);
                sbomResult.setResult(queryResult.get("result"));
                if (!"success".equals(queryResult.get("result"))) {
                    sbomResult.setMessage(queryResult.get("errorInfo"));
                    sbomResultMap.put(sbomPO.getPublishId(), sbomResult);
                    return sbomResult;
                }
                sbomResult.setMessage("");
                sbomRefMap.put(key, queryResult.get("sbomRef"));
            }
            for (FilePO file : sbomResult.getFiles()) {
                if (file.getTargetPath().contains("/source/")) {
                    continue;
                }
                file.setSbomRef(sbomRefMap.get(file.getTargetPath()));
            }
            sbomResultMap.put(publishId, sbomResult);
        } else {
            sbomResult = new SbomResultPO();
            sbomResult.setResult("publishing");
            sbomResultMap.put(publishId, sbomResult);
            PublishResult publishResult = JSONObject.parseObject(sbomPO.getPublishResultDetail(), PublishResult.class);
            sbomResultAsync(sbomPO, publishResult.getFiles());
        }
        return sbomResultMap.get(publishId);
    }

    public void sbomResultAsync(SbomPO sbomPO, List<FilePO> files) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Set<String> targetPaths = getArtifactPaths(files);
                Map<String, String> taskId = new HashMap<>();
                SbomResultPO sbomResult = new SbomResultPO();
                sbomResult.setResult("success");
                sbomResult.setFiles(files);
                sbomResult.setSbomPO(sbomPO);
                for (String targetPath : targetPaths) {
                    String artifactPath = targetPath;
                    if (artifactPath.contains("/Packages")) {
                        artifactPath = artifactPath.substring(0, artifactPath.indexOf("/Packages"))
                            .replaceAll("//", "/");
                    }
                    Map<String, String> generateResult = sbomService.generateOpeneulerSbom(sbomPO, artifactPath);
                    if (!"success".equals(generateResult.get("result"))) {
                        sbomResult.setResult(generateResult.get("result"));
                        sbomResult.setMessage(generateResult.get("errorInfo"));
                        sbomResultMap.put(sbomPO.getPublishId(), sbomResult);
                        return;
                    }
                    String productName = artifactPath;
                    if (productName.contains("/update/")) {
                        // 更新文件夹
                        productName = productName.substring(productName.indexOf("/repo/openeuler") + 15);
                    } else if (productName.endsWith(".iso")) {
                        // 镜像文件   注：目前不涉及
                        productName = productName.substring(productName.lastIndexOf("/") + 1);
                    }
                    Map<String, String> publishResult = sbomService.publishSbomFile(sbomPO,
                        generateResult.get("sbomContent"), productName);
                    if (!"success".equals(publishResult.get("result"))) {
                        if (publishResult.get("errorInfo").contains("has sbom import job in running")) {
                            // 发布任务正在进行中
                            return;
                        }
                        sbomResult.setResult(publishResult.get("result"));
                        sbomResult.setMessage(publishResult.get("errorInfo"));
                        sbomResultMap.put(sbomPO.getPublishId(), sbomResult);
                        return;
                    }
                    taskId.put(targetPath, publishResult.get("taskId"));
                }
                sbomResult.setTaskId(taskId);
                sbomResultMap.put(sbomPO.getPublishId(), sbomResult);
            }
        }).start();
    }

    /**
     * 发布公告和评审详情归档
     *
     * @param archiveInfo 归档信息
     * @return String
     */
    @RequestMapping(value = "/archiveBulletinAndReview", method = RequestMethod.POST)
    public String archiveBulletinAndReview(@RequestBody ArchiveInfoPO archiveInfo) {
        // 写出发布公告
        String remoteRepoIp = archiveInfo.getRemoteRepoIp();
        String bulletinName = archiveInfo.getVersionNum() + "_publishBulletin.html";
        boolean bulletinResult = archiveFile(remoteRepoIp, archiveInfo.getBulletin(), bulletinName, archiveInfo.getBulletinArchivePath());
        // 写出发布详情
        String reviewName = archiveInfo.getVersionNum() + "_reviewDetails.md";
        boolean reviewResult = archiveFile(remoteRepoIp, archiveInfo.getReviewDetail(), reviewName, archiveInfo.getReviewArchivePath());
        if (bulletinResult && reviewResult) {
            return "success";
        }
        return "fail";
    }

    private boolean archiveFile(String remoteRepoIp, byte[] bytes, String fileName, String archivePath) {
        FileOutputStream fos = null;
        try {
            verifyService = new VerifyService();
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss", Locale.ROOT);
            String tempDirPath = "/var/log/app-publish/file/temp/" + dateFormat.format(new Date()) + "/";
            File tempDir = new File(tempDirPath);
            if (!tempDir.exists()) {
                verifyService.execCmd("mkdir -p " + tempDirPath);
            }
            File bulletinFile = new File(tempDirPath + fileName);
            fos = new FileOutputStream(bulletinFile);
            fos.write(bytes);
            String folderExistsFlag = verifyService.execCmd("ssh -i /var/log/ssh_key/private.key -o StrictHostKeyChecking=no root@"
                + remoteRepoIp + " \"[ -d " + archivePath + " ]  &&  echo exists || echo does not exist\"");
            if (!"exists".equals(folderExistsFlag)) {
                verifyService.execCmd("ssh -i /var/log/ssh_key/private.key -o StrictHostKeyChecking=no root@"
                    + remoteRepoIp + " \"mkdir -p " + archivePath + "\"");
            }
            String bulletinOutPut = verifyService.execCmd("scp -i /var/log/ssh_key/private.key -o StrictHostKeyChecking=no " +
                tempDirPath + fileName + " root@" + remoteRepoIp + ":" + archivePath + fileName);
            if (!StringUtils.isEmpty(bulletinOutPut)) {
                log.error(bulletinOutPut);
                return false;
            }
            verifyService.execCmd("rm -rf " + tempDirPath);
            return true;
        } catch (IOException | InterruptedException e) {
            log.error(e.getMessage());
            return false;
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    log.error(e.getMessage());
                }
            }
        }
    }

    private Set<String> getArtifactPaths(List<FilePO> files) {
        Set<String> set = new HashSet<>();
        for (FilePO file : files) {
            // source目录下的制品不生产sbom
            if (file.getTargetPath().contains("/source/")) {
                continue;
            }
            set.add(file.getTargetPath());
        }
        return set;
    }

    private String verify(String tempDirPath, FilePO file, String fileName) throws IOException, InterruptedException {
        if (!StringUtils.isEmpty(file.getSha256())) {
            if (!verifyService.checksum256Verify(tempDirPath + fileName, file.getSha256())) {
                return fileName + " checksum check failed.";
            }
        }
        if ("asc".equals(file.getVerifyType())) {
            if (!verifyService.fileVerify(tempDirPath + fileName)) {
                return fileName + " digests signatures not OK.";
            }
        }
        if ("rpm".equals(file.getVerifyType())) {
            if (!verifyService.rpmVerify(tempDirPath + fileName)) {
                return fileName + " digests signatures not OK.";
            }
        }
        return "";
    }

    private String validate(PublishPO publishPO) {
        if (StringUtils.isEmpty(publishPO.getGpgKeyUrl())) {
            return "key url cannot be blank.";
        }

        if (CollectionUtils.isEmpty(publishPO.getFiles())) {
            return "files cannot be empty.";
        }

        for (FilePO file : publishPO.getFiles()) {
            if (StringUtils.isEmpty(file.getTargetPath())) {
                return "file target path can not be empty.";
            }
            File targetFile = new File(file.getTargetPath() + "/" + file.getName());
            if ("error".equals(publishPO.getConflict()) && targetFile.exists()) {
                return file.getName() + " already published.";
            }
        }
        return "";
    }

    private String getTempDirPath(String tempDir) {
        if (tempDir.startsWith("/")) {
            return "/var/log" + tempDir;
        } else {
            return "/var/log/" + tempDir;
        }
    }
}
