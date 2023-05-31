package com.huawei.publish;

import com.alibaba.fastjson.JSONObject;
import com.huawei.publish.enums.AppConst;
import com.huawei.publish.model.FileFromRepoModel;
import com.huawei.publish.model.FilePO;
import com.huawei.publish.model.PublishPO;
import com.huawei.publish.model.PublishResult;
import com.huawei.publish.model.SbomPO;
import com.huawei.publish.model.SbomResultPO;
import com.huawei.publish.service.ObsUtil;
import com.huawei.publish.service.SbomService;
import com.huawei.publish.service.VerifyService;
import com.huawei.publish.utils.CacheUtil;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * main controller
 * @author chentao
 */
@RequestMapping(path = "/publish")
@RestController
public class PublishVerifyController {
    private static final Logger log = Logger.getLogger(PublishVerifyController.class);

    private VerifyService verifyService;

    private static ObsUtil obsUtil = new ObsUtil();

    @Autowired
    private SbomService sbomService;

    @Autowired
    @Qualifier("publishTaskExecutor")
    private ThreadPoolTaskExecutor publishTaskExecutor;
    /**
     * heartbeat
     *
     * @return heartbeat test
     */
    @RequestMapping(value = "/heartbeat", method = RequestMethod.GET)
    public Map<String, Object> heartbeat() {
        Map<String, Object> result = new HashMap<>(1);
        result.put("result", "success");
        log.info("heartbeat");
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
        List<FilePO> originalFiles = publishObject.getFiles();
        String tempDirPath = publishObject.getTempDir();
        try {
            if (!StringUtils.isEmpty(tempDirPath)) {
                File tempDir = new File(tempDirPath);
                if (!tempDir.exists()) {
                    verifyService.execCmd("mkdir -p " + tempDirPath);
                }
            }
            // 存储缺失源文件的sha256,asc文件
            List<FilePO> missFiles = new ArrayList<>();
            List<List<FilePO>> filesList = getFileList(originalFiles, missFiles);
            if (!CollectionUtils.isEmpty(missFiles)) {
                for (FilePO missFile : missFiles) {
                    missFile.setVerifyResult("no source file");
                    missFile.setPublishResult("fail");
                }
            }
            for (List<FilePO> files : filesList) {
                // 源文件
                FilePO sourceFile = files.get(0);
                String fileTempDirPath = tempDirPath + "/" + UUID.randomUUID() + "/";
                String targetPath = StringUtils.isEmpty(sourceFile.getTargetPath()) ? "" : sourceFile.getTargetPath().trim();
                // 判断文件是否存在于发布路径
                boolean exists = true;
                if ("obs".equals(publishObject.getUploadType())) {
                    for (FilePO file : files) {
                        exists = exists && obsUtil.isExist(targetPath + file.getName());
                    }
                }
                if ("skip".equals(publishObject.getConflict()) && exists) {
                    for (FilePO file : files) {
                        file.setPublishResult("skip");
                    }
                    continue;
                }
                // 验签
                boolean isSuccess = true;
                if (!"latest/".equals(sourceFile.getParentDir()) && !sourceFile.getParentDir().contains("binarylibs_update/")
                    && !sourceFile.getParentDir().contains("binarylibs/") && !"git_num.txt".equals(sourceFile.getName())
                    && !sourceFile.getParentDir().contains("latest/docs/")) {
                    isSuccess = verifySignature(files, fileTempDirPath);
                }
                // 发布
                if (isSuccess) {
                    if ("obs".equals(publishObject.getUploadType())) {
                        for (FilePO file : files) {
                            // clamAv扫描病毒
                            boolean clamScanResult =
                                verifyService.clamScan(fileTempDirPath + file.getName());
                            if (clamScanResult) {
                                publishFile(file, targetPath, exists, result);
                                file.setScanResult("success");
                            } else {
                                file.setScanResult("is infected");
                                file.setPublishResult("fail");
                                result.setResult("fail");
                            }
                        }
                    }
                } else {
                    result.setResult("fail");
                }
                verifyService.execCmd("rm -rf " + fileTempDirPath);
            }
            verifyService.execCmd("rm -rf " + tempDirPath);
        } catch (IOException | InterruptedException e) {
            result.setResult("fail");
            result.setMessage("publish failed, " + e.getMessage());
            return result;
        }
        result.setFiles(originalFiles);
        return result;
    }

    @RequestMapping(value = "/publishAsync", method = RequestMethod.POST)
    public String publishAsync(@RequestBody PublishPO publishObject) {
        publishTaskExecutor.execute(() -> {
            PublishResult publishResult = publish(publishObject);
            CacheUtil.put(publishObject.getPublishId(), publishResult);
        });
        return "Start publish task success.";
    }

    @RequestMapping(value = "/getPublishResult", method = RequestMethod.GET)
    public PublishResult getPublishResult(@RequestParam(value = "publishId") String publishId) {
        if (Objects.isNull(CacheUtil.get(publishId))) {
            return new PublishResult();
        }
        PublishResult result = (PublishResult) CacheUtil.get(publishId);
        if (!StringUtils.isEmpty(result.getResult())) {
            // 发布成功后缓存5分钟过期
            CacheUtil.setCacheExpiration(publishId, 60*5);
        }
        return result;
    }

    /**
     * 查询sbom发布结果
     *
     * @param sbomObject sbomObject
     * @return SbomResultPO
     */
    @RequestMapping(value = "/querySbomPublishResult", method = RequestMethod.POST)
    public SbomResultPO querySbomPublishResult(@RequestBody SbomPO sbomObject) {
        SbomResultPO sbomResult = new SbomResultPO();
        String message = validateSbom(sbomObject);
        if (!StringUtils.isEmpty(message)) {
            sbomResult.setMessage(message);
            sbomResult.setResult("failed");
            return sbomResult;
        }
        String sbomPublishId = AppConst.SBOM_PUBLISH_ID_PREFIX + sbomObject.getPublishId();
        String sbomTaskId = AppConst.SBOM_TASK_ID_PREFIX + sbomObject.getPublishId();
        if (CacheUtil.isContain(sbomPublishId)) {
            // 有sbom发布结果
            sbomResult = (SbomResultPO) CacheUtil.get(sbomPublishId);
            if (Objects.isNull(CacheUtil.get(sbomTaskId))) {
                return sbomResult;
            }
            @SuppressWarnings("unchecked")
            Map<String, String> taskId = (Map<String, String>) CacheUtil.get(sbomTaskId);
            sbomResult.setTaskId(taskId);
            String result = sbomResult.getResult();
            if (AppConst.PUBLISHING_TAG.equals(result)) {
                return sbomResult;
            }
            // sbom发布成功的包查询sbom归档链接
            String resultUrl = sbomObject.getQuerySbomPublishResultUrl();
            boolean flag = true;
            for (FilePO file : sbomResult.getFiles()) {
                if (!"publish success".equals(file.getSbomResult())) {
                    continue;
                }
                String key = file.getParentDir() + file.getName();
                if (!taskId.containsKey(key)) {
                    file.setSbomResult("publish fail");
                    sbomResult.setPublishSuccess(false);
                    continue;
                }
                String url = resultUrl.endsWith("/") ? resultUrl + taskId.get(key) : resultUrl + "/" + taskId.get(key);
                Map<String, String> querySbomMap = sbomService.querySbomPublishResult(url);
                if (!"success".equals(querySbomMap.get("result"))) {
                    flag = false;
                    file.setSbomRef(querySbomMap.get("errorInfo"));
                    continue;
                }
                file.setSbomRef(querySbomMap.get("sbomRef"));
                file.setSbomResult("success");
            }
            if (flag && sbomResult.isPublishSuccess()) {
                sbomResult.setResult("success");
            } else {
                sbomResult.setResult("partial success");
            }
            CacheUtil.put(sbomPublishId, sbomResult);
            // sbom发布失败的包再次尝试发布
            if (CollectionUtils.isEmpty(taskId) || !sbomResult.isPublishSuccess()) {
                PublishResult publishResult = JSONObject.parseObject(sbomObject.getPublishResultDetail(), PublishResult.class);
                sbomResultAsync(sbomObject, publishResult.getFiles());
            }
        } else {
            // 无sbom发布结果
            sbomResult = new SbomResultPO();
            sbomResult.setResult("publishing");
            // 初始化taskId
            Map<String, String> taskId = new HashMap<>();
            CacheUtil.put(sbomTaskId, taskId);
            sbomResult.setTaskId(taskId);
            CacheUtil.put(sbomPublishId, sbomResult);
            // 异步发布
            PublishResult publishResult = JSONObject.parseObject(sbomObject.getPublishResultDetail(), PublishResult.class);
            sbomResultAsync(sbomObject, publishResult.getFiles());
        }
        SbomResultPO result = (SbomResultPO) CacheUtil.get(sbomPublishId);
        if (AppConst.SUCCESS_TAG.equals(result.getResult())) {
            // sbom发布成功后缓存5分钟过期
            CacheUtil.setCacheExpiration(sbomPublishId, 60*5);
            // taskId缓存过期
            CacheUtil.setCacheExpiration(sbomTaskId, 60*5);
        }
        return result;
    }

    private String validateSbom(SbomPO sbomObject) {
        if (StringUtils.isEmpty(sbomObject.getPublishSbomUrl())) {
            return "publishSbomUrl connot be blank";
        }
        if (StringUtils.isEmpty(sbomObject.getQuerySbomPublishResultUrl())) {
            return "querySbomPublishResultUrl connot be blank";
        }
        if (StringUtils.isEmpty(sbomObject.getPublishId())) {
            return "publishId connot be blank";
        }
        if (StringUtils.isEmpty(sbomObject.getPublishResultDetail())) {
            return "publishResultDetail connot be blank";
        }
        return "";
    }

    /**
     * 发布sbom
     *
     * @param sbomObject sbomObject
     * @param files  发布的files
     */
    public void sbomResultAsync(SbomPO sbomObject, List<FilePO> files) {
        publishTaskExecutor.execute(() -> {
                SbomResultPO sbomResult = new SbomResultPO();
                @SuppressWarnings("unchecked")
                Map<String, String> taskId =
                    (Map<String, String>) CacheUtil.get(AppConst.SBOM_TASK_ID_PREFIX + sbomObject.getPublishId());
                boolean flag = false;
                for (FilePO file : files) {
                    // 不做sbom发布的文件
                    if (file.getName().endsWith(".sha256") || file.getName().endsWith(".asc")
                        || file.getParentDir().contains("latest/docs/") || "git_num.txt".equals(file.getName())
                        || "fail".equals(file.getPublishResult())) {
                        continue;
                    }
                    // sbom发布成功的文件
                    if ("publish success".equals(file.getSbomResult()) || "success".equals(file.getSbomResult())) {
                        continue;
                    }
                    // sbom待发布的文件
                    String parentDir = file.getParentDir();
                    String fileName = file.getName();
                    String sbomParentDir = parentDir.replace("latest/", "latest/sbom_tracer/");
                    String sbomFileName = fileName + "_tracer_result.tar.gz";
                    boolean exist = obsUtil.isExist(sbomParentDir + sbomFileName);
                    if (!exist) {
                        file.setSbomResult(fileName + "不需要做sbom发布");
                        log.info(fileName + "不需要做sbom发布");
                        continue;
                    }
                    String sbomContent = obsUtil.getSbomContent(sbomParentDir + sbomFileName);
                    String productName = parentDir.substring(parentDir.substring(0, parentDir.length() - 1).lastIndexOf("/") + 1) + fileName;
                    Map<String, String> publishSbomMap = sbomService.publishSbomFile(sbomObject, sbomContent, productName);
                    if (!"success".equals(publishSbomMap.get("result"))) {
                        if (publishSbomMap.get("errorInfo").contains("has sbom import job in running")) {
                            // 发布任务正在进行中
                            log.info("sbom import job in running！");
                            continue;
                        }
                        flag = true;
                        file.setSbomResult("publish fail");
                        file.setSbomRef(publishSbomMap.get("errorInfo"));
                        continue;
                    }
                    file.setSbomResult("publish success");
                    taskId.put(parentDir + fileName, publishSbomMap.get("taskId"));
                }
                sbomResult.setPublishSuccess(!flag);
                sbomResult.setFiles(files);
                sbomResult.setSbomPO(sbomObject);
            CacheUtil.put(AppConst.SBOM_PUBLISH_ID_PREFIX + sbomObject.getPublishId(), sbomResult);
            });
    }

    /**
     * 提供需要发布的一层文件列表,由majun FileFromRepoUtil类中的getFiles（）方法请求调用
     *
     * @param path 路径前缀
     * @return 一层文件列表
     */
    @RequestMapping(value = "/getPublishList", method = RequestMethod.GET)
    public List<FileFromRepoModel> getPublishList(@RequestParam(value = "path") String path) {
        ArrayList<FileFromRepoModel> result = new ArrayList<>();
        List<FileFromRepoModel> files = obsUtil.listObjects(path);
        if (CollectionUtils.isEmpty(files)) {
            return Collections.emptyList();
        }
        if ("latest".equals(path)) {
            for (FileFromRepoModel file : files) {
                //获取latest文件
                if (file.getParentDir() == null && "latest".equals(file.getName())) {
                    result.add(file);
                }
            }
        } else {
            for (FileFromRepoModel file : files) {
                //获取内层文件
                if (path.equals(file.getParentDir())) {
                    result.add(file);
                }
            }
        }
        return result;
    }

    /**
     * 提供需要发布的所有文件列表,由majun FileFromRepoUtil类中的getAllFiles（）方法请求调用
     *
     * @param path 路径前缀
     * @return 所有文件列表
     */
    @RequestMapping(value = "/getAllPublishList", method = RequestMethod.GET)
    public List<FileFromRepoModel> getAllPublishList(@RequestParam(value = "path") String path) {
        ArrayList<FileFromRepoModel> result = new ArrayList<>();
        List<FileFromRepoModel> files = obsUtil.listObjects(path);
        if (CollectionUtils.isEmpty(files)) {
            return Collections.emptyList();
        }
        for (FileFromRepoModel file : files) {
            // 排除目录，排除sbom_tracer下所有文件
            if (!file.isDir() && !file.getParentDir().contains("sbom_tracer")) {
                result.add(file);
            }
        }
        return result;
    }

    /**
     * 封装FilePO, 源文件、对应的sha256、对应的asc存入list集合,对应索引0,1,2
     *
     * @param originalFiles     要发布的文件(原文件)
     * @param missFiles 存储缺失源文件的sha256和asc文件
     * @return List<List < FilePO>>
     */
    private List<List<FilePO>> getFileList(List<FilePO> originalFiles, List<FilePO> missFiles) {
        // 存源文件
        List<FilePO> sourceFiles = new ArrayList<>();
        // 存sha256文件
        List<FilePO> sha256Files = new ArrayList<>();
        // 存asc源文件
        List<FilePO> ascFiles = new ArrayList<>();
        for (FilePO file : originalFiles) {
            if (file.getName().endsWith(".sha256")) {
                sha256Files.add(file);
            } else if (file.getName().endsWith(".sha256.asc")) {
                ascFiles.add(file);
            } else {
                sourceFiles.add(file);
            }
        }
        List<List<FilePO>> list = new ArrayList<>();
        for (FilePO file : sourceFiles) {
            List<FilePO> files = new ArrayList<>();
            files.add(file);
            if ("latest/".equals(file.getParentDir()) || file.getParentDir().contains("binarylibs_update/")
                || file.getParentDir().contains("binarylibs/") || "git_num.txt".equals(file.getName())
                || file.getParentDir().contains("latest/docs/")) {
                list.add(files);
                continue;
            }
            String sourceName = file.getName();
            if (sourceName.endsWith(".tar.bz2")) {
                for (FilePO sha256File : sha256Files) {
                    if (sha256File.getName().equals(sourceName.replace(".tar.bz2", ".sha256")) &&
                        file.getParentDir().equals(sha256File.getParentDir())) {
                        files.add(sha256File);
                        sha256Files.remove(sha256File);
                        break;
                    }
                }
                for (FilePO ascFile : ascFiles) {
                    if (ascFile.getName().equals(sourceName.replace(".tar.bz2", ".sha256.asc")) &&
                        file.getParentDir().equals(ascFile.getParentDir())) {
                        files.add(ascFile);
                        ascFiles.remove(ascFile);
                        break;
                    }
                }
            } else {
                for (FilePO sha256File : sha256Files) {
                    if (sha256File.getName().equals(sourceName + ".sha256") &&
                        file.getParentDir().equals(sha256File.getParentDir())) {
                        files.add(sha256File);
                        sha256Files.remove(sha256File);
                        break;
                    }
                }
                for (FilePO ascFile : ascFiles) {
                    if (ascFile.getName().equals(sourceName + ".sha256.asc") &&
                        file.getParentDir().equals(ascFile.getParentDir())) {
                        files.add(ascFile);
                        ascFiles.remove(ascFile);
                        break;
                    }
                }
            }
            list.add(files);
        }
        missFiles.addAll(sha256Files);
        missFiles.addAll(ascFiles);
        return list;
    }

    /**
     * 文件验签
     *
     * @param files         源文件、sha256文件、asc文件
     * @param fileTempDirPath 文件临时下载路径
     * @return 是否验签成功
     * @throws IOException  异常
     * @throws InterruptedException  异常
     */
    private boolean verifySignature(List<FilePO> files, String fileTempDirPath) throws IOException, InterruptedException {
        // 判断files是否为白名单文件
        if (isWhiteListFile(files)) {
            return true;
        }
        if (!isMissing(files)) {
            return false;
        }
        // 源文件
        FilePO sourceFile = files.get(0);
        // sha256文件
        FilePO sha256File = files.get(1);
        // asc文件
        FilePO ascFile = files.get(2);
        File fileTempDir = new File(fileTempDirPath);
        fileTempDir.mkdir();
        for (FilePO file : files) {
            obsUtil.downFile(file.getParentDir() + file.getName(), fileTempDirPath + file.getName());
        }
        String verifyMessage = verify(fileTempDirPath, sourceFile, sha256File, ascFile);
        if (StringUtils.isEmpty(verifyMessage)) {
            sourceFile.setVerifyResult("success");
            sha256File.setVerifyResult("success");
            return true;
        }
        if (verifyMessage.contains("asc")) {
            sha256File.setVerifyResult(verifyMessage);
            sourceFile.setVerifyResult(verifyMessage);
        }
        if (verifyMessage.contains("checksum")) {
            sha256File.setVerifyResult("success");
            sourceFile.setVerifyResult(verifyMessage);
        }
        sourceFile.setPublishResult("fail");
        sha256File.setPublishResult("fail");
        ascFile.setPublishResult("fail");
        return false;
    }

    /**
     * 判断文件是否为白名单文件，白名单文件不需要验签
     *
     * @param files 待检验文件
     * @return boolean
     */
    private boolean isWhiteListFile(List<FilePO> files) {
        if (files.size() < 3) {
            FilePO sourceFile = files.get(0);
            String fileName = sourceFile.getName();
            return fileName.endsWith(".log");
        }
        return false;
    }

    /**
     * 判断files是否缺失文件
     *
     * @param files files
     * @return false：缺失  true：不缺失
     */
    private boolean isMissing(List<FilePO> files) {
        if (files.size() < 3) {
            FilePO sourceFile = files.get(0);
            String fileName = sourceFile.getName();
            //判断源文件对应的.sha256文件、.sha256.asc文件是否存在
            boolean sha256Exist = false;
            boolean ascExist = false;
            if (fileName.endsWith(".tar.bz2")) {
                for (FilePO file : files) {
                    if (file.getName().equals(fileName.replace(".tar.bz2", ".sha256"))) {
                        sha256Exist = true;
                    }
                    if (file.getName().equals(fileName.replace(".tar.bz2", ".sha256.asc"))) {
                        ascExist = true;
                    }
                }
            } else {
                for (FilePO file : files) {
                    if (file.getName().equals(fileName + ".sha256")) {
                        sha256Exist = true;
                    }
                    if (file.getName().equals(fileName + ".sha256.asc")) {
                        ascExist = true;
                    }
                }
            }
            if (!sha256Exist && !ascExist) {
                sourceFile.setVerifyResult("no sha256 and asc signature");
                sourceFile.setPublishResult("fail");
                return false;
            } else if (!sha256Exist) {
                sourceFile.setVerifyResult("no sha256 signature");
                sourceFile.setPublishResult("fail");
                files.get(1).setPublishResult("fail");
                return false;
            } else if (!ascExist) {
                sourceFile.setVerifyResult("no asc signature");
                files.get(1).setVerifyResult("no asc signature");
                sourceFile.setPublishResult("fail");
                files.get(1).setPublishResult("fail");
                return false;
            }
        }
        return true;
    }

    private String verify(String fileTempDirPath, FilePO sourceFile, FilePO sha256File, FilePO ascFile) throws IOException, InterruptedException {
        if (!verifyService.fileVerify(fileTempDirPath + ascFile.getName())) {
            return sha256File.getName() + " asc signatures not OK.";
        }
        if (StringUtils.isEmpty(sourceFile.getSha256())) {
            String sha256 = verifyService.execCmd("cat " + fileTempDirPath + sha256File.getName());
            sourceFile.setSha256(sha256);
        }
        String fileName = sourceFile.getName();
        if (!verifyService.checksum256Verify(fileTempDirPath + fileName, sourceFile.getSha256())) {
            return fileName + " checksum check failed.";
        }
        return "";
    }

    /**
     * 文件发布
     *  @param file       发布的文件
     * @param targetPath 发布的目标路径
     * @param exists     发布目标路径中是否存在该文件
     * @param result
     */
    private void publishFile(FilePO file, String targetPath, boolean exists, PublishResult result) {
        String fileName = file.getName();
        boolean uploadSuccess = obsUtil.copyObject(file.getParentDir() + fileName, targetPath + fileName);
        if (uploadSuccess) {
            if (exists) {
                file.setPublishResult("cover");
            } else {
                file.setPublishResult("normal");
            }
        } else {
            file.setPublishResult("fail");
            result.setResult("fail");
        }
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
            File targetFile = new File(file.getTargetPath().trim() + "/" + file.getName());
            if ("error".equals(publishObject.getConflict()) && targetFile.exists()) {
                return file.getName() + " already published.";
            }
        }
        return "";
    }
}
