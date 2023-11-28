package com.huawei.publish;

import com.alibaba.fastjson.JSONObject;
import com.huawei.publish.enums.AppConst;
import com.huawei.publish.model.ArchiveInfoPO;
import com.huawei.publish.model.FilePO;
import com.huawei.publish.model.PublishPO;
import com.huawei.publish.model.PublishResult;
import com.huawei.publish.model.RepoIndex;
import com.huawei.publish.model.SbomPO;
import com.huawei.publish.model.SbomResultPO;
import com.huawei.publish.model.VirusScanPO;
import com.huawei.publish.model.VirusScanResultPO;
import com.huawei.publish.service.FileDownloadService;
import com.huawei.publish.service.GiteeUploaderService;
import com.huawei.publish.service.SbomService;
import com.huawei.publish.service.VerifyService;
import com.huawei.publish.service.VirusScanService;
import com.huawei.publish.utils.CacheUtil;
import com.huawei.publish.utils.FileDownloadUtil;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * main controller
 * @author chentao
 */
@RequestMapping(path = "/publish")
@RestController
public class PublishVerifyController {
    private static final Logger log = LoggerFactory.getLogger(PublishVerifyController.class);

    @Autowired
    private FileDownloadService fileDownloadService;

    private VerifyService verifyService;

    @Autowired
    private VirusScanService virusScanService;

    @Autowired
    private SbomService sbomService;

    @Autowired
    @Qualifier("publishTaskExecutor")
    private ThreadPoolTaskExecutor publishTaskExecutor;

    @Autowired
    private GiteeUploaderService giteeUploaderService;

    /**
     * heartbeat
     *
     * @return heartbeat test
     */
    @RequestMapping(value = "/heartbeat", method = RequestMethod.GET)
    public Map<String, Object> heartbeat() {
        Map<String, Object> result = new HashMap<>(2);
        result.put("result", "success");
        return result;
    }

    /**
     * publish
     *
     * @param publishObject publish model
     * @return PublishResult PublishResult
     */
    @RequestMapping(value = "/publish", method = RequestMethod.POST)
    public PublishResult publish(@RequestBody PublishPO publishObject) {
        PublishResult result = new PublishResult();
        result.setResult("success");
        String validate = validate(publishObject);
        if (!StringUtils.isEmpty(validate)) {
            result.setResult("fail");
            result.setMessage("Validate failed, " + validate);
            return result;
        }
        verifyService = new VerifyService(publishObject);
        List<FilePO> files = publishObject.getFiles();
        String tempDirPath = FileDownloadUtil.getTempDirPath(publishObject.getTempDir());
        try {
            File tempDir = new File(tempDirPath);
            if (!tempDir.exists()) {
                verifyService.execCmd("mkdir -p " + tempDirPath);
            }
            for (FilePO file : files) {
                String fileName = file.getName();
                String fileExistsFlag = verifyService.execCmd("ssh -i /var/log/ssh_key/private.key -o StrictHostKeyChecking=no root@"
                    + publishObject.getRemoteRepoIp() + " \"[ -f " + file.getTargetPath() + "/" + fileName + " ]  &&  echo exists || echo does not exist\"");
                if ("skip".equals(publishObject.getConflict()) && "exists".equals(fileExistsFlag)) {
                    file.setPublishResult("skip");
                    continue;
                }
                if (StringUtils.isNotBlank(fileExistsFlag) && fileExistsFlag.contains("Connection timed out")){
                    file.setPublishResult("failed");
                    result.setResult("fail");
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
                if (!StringUtils.isEmpty(tempDirPath) && !tempDirPath.endsWith(AppConst.SLASH)) {
                    tempDirPath = tempDirPath + AppConst.SLASH;
                }
                String folderExistsFlag = verifyService.execCmd("ssh -i /var/log/ssh_key/private.key -o StrictHostKeyChecking=no root@"
                    + publishObject.getRemoteRepoIp() + " \"[ -d " + file.getTargetPath() + " ]  &&  echo exists || echo does not exist\"");
                if (!AppConst.EXISTS.equals(folderExistsFlag)) {
                    verifyService.execCmd("ssh -i /var/log/ssh_key/private.key -o StrictHostKeyChecking=no root@"
                        + publishObject.getRemoteRepoIp() + " \"mkdir -p " + file.getTargetPath() + "\"");
                }
                String outPut = verifyService.execCmd("scp -i /var/log/ssh_key/private.key -o StrictHostKeyChecking=no " + tempDirPath
                        + fileName + " root@" + publishObject.getRemoteRepoIp() + ":" + file.getTargetPath() + AppConst.SLASH + fileName);
                if (!StringUtils.isEmpty(outPut)) {
                    file.setPublishResult("failed");
                    result.setResult("fail");
                    continue;
                }
                if (AppConst.EXISTS.equals(fileExistsFlag)) {
                    file.setPublishResult("cover");
                } else {
                    file.setPublishResult("normal");
                }
            }
            verifyService.execCmd("rm -rf " + tempDirPath);
            if (!CollectionUtils.isEmpty(publishObject.getRepoIndexList())) {
                for (RepoIndex repoIndex : publishObject.getRepoIndexList()) {
                    if (repoIndex != null) {
                        if ("createrepo".equals(repoIndex.getIndexType())) {
                            verifyService.execCmd("ssh -i /var/log/ssh_key/private.key -o StrictHostKeyChecking=no root@"
                                + publishObject.getRemoteRepoIp() + " \"createrepo -d " + repoIndex.getRepoPath() + "\"");
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
    public String publishAsync(@RequestBody PublishPO publishObject) {
        publishTaskExecutor.execute(() -> {
            PublishResult publish = publish(publishObject);
            CacheUtil.put(publishObject.getPublishId(), publish);
        });
        return "Start publish task success.";
    }

    @RequestMapping(value = "/getPublishResult", method = RequestMethod.GET)
    public PublishResult getPublishResult(@RequestParam(value = "publishId", required = true) String publishId) {
        if (Objects.isNull(CacheUtil.get(publishId))) {
            return new PublishResult();
        }
        PublishResult result = (PublishResult) CacheUtil.get(publishId);
        if (!StringUtils.isEmpty(result.getResult()) && !AppConst.PUBLISHING.equals(result.getResult())) {
            // 发布成功后缓存5分钟过期
            CacheUtil.setCacheExpiration(publishId, 60*5);
        }
        return result;
    }

    /**
     * VirusScan
     *
     * @param virusScan publish model
     * @return PublishResult PublishResult
     */
    @RequestMapping(value = "/executeVirusScanning", method = RequestMethod.POST)
    public String executeVirusScanning(@RequestBody VirusScanPO virusScan) {
        publishTaskExecutor.execute(() -> {
            VirusScanResultPO virusScanResult = virusScanService.virusScanning(virusScan);
            CacheUtil.put(AppConst.VIRUS_SCAN_ID_PREFIX + virusScan.getScanId(), virusScanResult);
        });
        VirusScanResultPO virusScanResult = new VirusScanResultPO();
        virusScanResult.setResult(AppConst.SCANNING);
        CacheUtil.put(AppConst.VIRUS_SCAN_ID_PREFIX + virusScan.getScanId(), virusScanResult);
        return "Start virus scan task success.";
    }

    /**
     * queryVirusScanResult
     *
     * @param scanId publish model
     * @return PublishResult PublishResult
     */
    @RequestMapping(value = "/queryVirusScanResult", method = RequestMethod.POST)
    public VirusScanResultPO queryVirusScanResult(@RequestParam(value = "scanId", required = true) String scanId) {
        if (Objects.isNull(CacheUtil.get(AppConst.VIRUS_SCAN_ID_PREFIX + scanId))) {
            return new VirusScanResultPO();
        }
        VirusScanResultPO result = (VirusScanResultPO) CacheUtil.get(AppConst.VIRUS_SCAN_ID_PREFIX + scanId);
        if (!StringUtils.isEmpty(result.getResult()) && !AppConst.SCANNING.equals(result.getResult())) {
            // 发布成功后缓存5分钟过期
            CacheUtil.setCacheExpiration(scanId, 60*5);
        }
        return result;
    }
        /**
     * 查询sbom发布结果
     * @param sbomObject
     * @return
     */
    @RequestMapping(value = "/querySbomPublishResult", method = RequestMethod.POST)
    public SbomResultPO querySbomPublishResult(@RequestBody SbomPO sbomObject) {
        String sbomPublishId = AppConst.SBOM_PUBLISH_ID_PREFIX + sbomObject.getPublishId();
        SbomResultPO sbomResult = (SbomResultPO) CacheUtil.get(sbomPublishId);
        if (sbomResult != null) {
            if (sbomResult.getTaskId() == null) {
                if (AppConst.PUBLISHING.equals(sbomResult.getResult())
                    && !StringUtils.isEmpty(sbomResult.getMessage())) {
                    sbomResult.setMessage("");
                    // 请求失败，再次发起
                    PublishResult publishResult =
                        JSONObject.parseObject(sbomObject.getPublishResultDetail(), PublishResult.class);
                    sbomResultAsync(sbomObject, publishResult.getFiles());
                }
                return sbomResult;
            }
            Map<String, String> taskIdMap = sbomResult.getTaskId();
            Map<String, String> sbomRefMap = new HashMap<>(16);
            for (String key : taskIdMap.keySet()) {
                String taskId = taskIdMap.get(key);
                Map<String, String> queryResult = sbomService.querySbomPublishResult(
                    sbomObject.getQuerySbomPublishResultUrl() + AppConst.SLASH + taskId);
                sbomResult.setResult(queryResult.get("result"));
                if (!"success".equals(queryResult.get("result"))) {
                    sbomResult.setMessage(queryResult.get("errorInfo"));
                    CacheUtil.put(sbomObject.getPublishId(), sbomResult);
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
            CacheUtil.put(sbomPublishId, sbomResult);
        } else {
            sbomResult = new SbomResultPO();
            sbomResult.setResult(AppConst.PUBLISHING);
            CacheUtil.put(sbomPublishId, sbomResult);
            PublishResult publishResult = JSONObject.parseObject(sbomObject.getPublishResultDetail(), PublishResult.class);
            sbomResultAsync(sbomObject, publishResult.getFiles());
        }
        SbomResultPO result = (SbomResultPO) CacheUtil.get(sbomPublishId);
        if (AppConst.SUCCESS_TAG.equals(result.getResult())) {
            // sbom发布成功后缓存5分钟过期
            CacheUtil.setCacheExpiration(sbomPublishId, 60*5);
        }
        return result;
    }

    public void sbomResultAsync(SbomPO sbomObject, List<FilePO> files) {
        publishTaskExecutor.execute(() -> {
            Set<String> targetPaths = getArtifactPaths(files);
            Map<String, String> taskId = new HashMap<>(16);
            SbomResultPO sbomResult = new SbomResultPO();
            sbomResult.setResult("success");
            sbomResult.setFiles(files);
            sbomResult.setSbomPO(sbomObject);
            String sbomPublishId = AppConst.SBOM_PUBLISH_ID_PREFIX + sbomObject.getPublishId();
            for (String targetPath : targetPaths) {
                String artifactPath = targetPath;
                if (artifactPath.contains("/Packages")) {
                    artifactPath = artifactPath.substring(0, artifactPath.indexOf("/Packages"))
                        .replaceAll("//", "/");
                }
                // 第一步SBOM生成，获取sbomContent
                Map<String, String> generateResult = sbomService.generateOpeneulerSbom(sbomObject, artifactPath);
                if (!"success".equals(generateResult.get("result"))) {
                    sbomResult.setResult(generateResult.get("result"));
                    sbomResult.setMessage(generateResult.get("errorInfo"));
                    CacheUtil.put(sbomPublishId, sbomResult);
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
                // 第二步SBOM生成，返回taskId
                Map<String, String> publishResult = sbomService.publishSbomFile(sbomObject,
                    generateResult.get("sbomContent"), productName);
                if (!"success".equals(publishResult.get("result"))) {
                    if (publishResult.get("errorInfo").contains("has sbom import job in running")) {
                        // 发布任务正在进行中
                        return;
                    }
                    sbomResult.setResult(publishResult.get("result"));
                    sbomResult.setMessage(publishResult.get("errorInfo"));
                    CacheUtil.put(sbomPublishId, sbomResult);
                    return;
                }
                taskId.put(targetPath, publishResult.get("taskId"));
            }
            sbomResult.setTaskId(taskId);
            CacheUtil.put(sbomPublishId, sbomResult);
        });
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
        String bulletinName = archiveInfo.getBulletinArchivePath() + archiveInfo.getVersionNum() + "_publishBulletin.html";
        boolean bulletinResult = giteeUploaderService.uploadFile2Gitee(archiveInfo, bulletinName, archiveInfo.getBulletin());
        // 写出发布详情
        String reviewName = archiveInfo.getReviewArchivePath() + archiveInfo.getVersionNum() + "_reviewDetails.md";
        boolean reviewResult = giteeUploaderService.uploadFile2Gitee(archiveInfo, reviewName, archiveInfo.getReviewDetail());
        if (bulletinResult && reviewResult) {
            return "success";
        }
        return "failed";
    }

    private Set<String> getArtifactPaths(List<FilePO> files) {
        Set<String> set = new HashSet<>();
        for (FilePO file : files) {
            // source目录下的制品不生产sbom
            if (file.getTargetPath().contains("/source/")) {
                continue;
            }
            // 已发布过的包
            if ("skip".equals(file.getPublishResult())) {
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
        if (AppConst.VERIFY_TYPE_ASC.equals(file.getVerifyType())) {
            if (!verifyService.fileVerify(tempDirPath + fileName)) {
                return fileName + " digests signatures not OK.";
            }
        }
        if (AppConst.VERIFY_TYPE_RPM.equals(file.getVerifyType())) {
            if (!verifyService.rpmVerify(tempDirPath + fileName)) {
                return fileName + " digests signatures not OK.";
            }
        }
        return "";
    }

    private String validate(PublishPO publishObject) {
        if (StringUtils.isEmpty(publishObject.getGpgKeyUrl())) {
            return "key url cannot be blank.";
        }

        if (CollectionUtils.isEmpty(publishObject.getFiles())) {
            return "files cannot be empty.";
        }

        for (FilePO file : publishObject.getFiles()) {
            if (StringUtils.isEmpty(file.getTargetPath())) {
                return "file target path can not be empty.";
            }
            File targetFile = new File(file.getTargetPath() + AppConst.SLASH + file.getName());
            if ("error".equals(publishObject.getConflict()) && targetFile.exists()) {
                return file.getName() + " already published.";
            }
        }
        return "";
    }


}
