package com.huawei.publish;

import com.huawei.publish.enums.AppConst;
import com.huawei.publish.model.FilePO;
import com.huawei.publish.model.PublishPO;
import com.huawei.publish.model.PublishResult;
import com.huawei.publish.service.FileDownloadService;
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

    @Autowired
    private FileDownloadService fileDownloadService;

    private VerifyService verifyService;

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
            List<List<FilePO>> filesList = getFilesList(files, missFiles);
            if (!CollectionUtils.isEmpty(missFiles)) {
                for (FilePO missFile : missFiles) {
                    missFile.setVerifyResult("no source file");
                    missFile.setPublishResult("fail");
                }
            }
            for (List<FilePO> fileList : filesList) {
                // 源文件
                FilePO sourceFile = fileList.get(0);
                String fileTempDirPath = (tempDirPath + "/" + UUID.randomUUID() + "/").replace("//", "/");
                String targetPath = StringUtils.isEmpty(sourceFile.getTargetPath()) ? "" : sourceFile.getTargetPath().trim();
                //判断文件是否存在于发布路径
                boolean exists = true;
                if ("obs".equals(publishObject.getUploadType())) {
                    for (FilePO file : fileList) {
                        exists = exists && !verifyService.execCmdAndContainsMessage("obsutil stat " +
                            publishObject.getObsUrl() + (targetPath + "/" + file.getName())
                            .replace("//", "/"), "Error: Status [404]");
                    }
                }
                if ("skip".equals(publishObject.getConflict()) && exists) {
                    for (FilePO file : fileList) {
                        file.setPublishResult("skip");
                    }
                    continue;
                }
                // 验签
                boolean isVerifySuccess = true;
                String authorization = publishObject.getAuthorization();
                if ("yaml".equals(sourceFile.getVerifyType())) {
                    fileDownloadService.downloadHttpUrl(sourceFile.getUrl(), fileTempDirPath, sourceFile.getName(), authorization);
                } else {
                    isVerifySuccess = verifySignature(fileList, fileTempDirPath, authorization);
                }
                // 发布
                if (isVerifySuccess) {
                    publishFile(fileList, fileTempDirPath, publishObject, result, exists);
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
        result.setFiles(files);
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
     * 封装FilePO, 源文件、对应的sha256、对应的asc存入list集合,对应索引0,1,2
     *
     * @param files     要发布的文件
     * @param missFiles 存储缺失源文件的sha256,asc文件
     * @return List<List < FilePO>>
     */
    private List<List<FilePO>> getFilesList(List<FilePO> files, List<FilePO> missFiles) {
        // 存源文件
        List<FilePO> sourceFiles = new ArrayList<>();
        // 存sha256文件
        List<FilePO> sha256Files = new ArrayList<>();
        // 存asc源文件
        List<FilePO> ascFiles = new ArrayList<>();
        for (FilePO file : files) {
            if (file.getName().endsWith(".sha256")) {
                sha256Files.add(file);
            } else if (file.getName().endsWith(".sha256.asc")) {
                ascFiles.add(file);
            } else {
                sourceFiles.add(file);
            }
        }
        List<List<FilePO>> filesList = new ArrayList<>();
        for (FilePO sourceFile : sourceFiles) {
            List<FilePO> fileList = new ArrayList<>();
            fileList.add(sourceFile);
            if ("yaml".equals(sourceFile.getVerifyType())) {
                filesList.add(fileList);
                continue;
            }
            String sourceName = sourceFile.getName();
            for (FilePO sha256File : sha256Files) {
                if (sha256File.getName().equals(sourceName + ".sha256") &&
                    sourceFile.getParentDir().equals(sha256File.getParentDir())) {
                    fileList.add(sha256File);
                    sha256Files.remove(sha256File);
                    break;
                }
            }
            for (FilePO ascFile : ascFiles) {
                if (ascFile.getName().equals(sourceName + ".sha256.asc") &&
                    sourceFile.getParentDir().equals(ascFile.getParentDir())) {
                    fileList.add(ascFile);
                    ascFiles.remove(ascFile);
                    break;
                }
            }
            filesList.add(fileList);
        }
        missFiles.addAll(sha256Files);
        missFiles.addAll(ascFiles);
        return filesList;
    }

    /**
     * 文件验签
     *
     * @param files         源文件、sha256文件、asc文件
     * @param fileTempDirPath 文件临时下载路径
     * @param authorization   mindspore权限认证
     * @return 是否验签成功
     */
    private boolean verifySignature(List<FilePO> files, String fileTempDirPath,
                                    String authorization) throws IOException, InterruptedException {
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
            fileDownloadService.downloadHttpUrl(file.getUrl(), fileTempDirPath, file.getName(), authorization);
        }
        String verifyMessage = verify(fileTempDirPath, sourceFile, sha256File, ascFile);
        if (StringUtils.isEmpty(verifyMessage)) {
            sourceFile.setVerifyResult("success");
            sha256File.setVerifyResult("success");
            return true;
        }
        if (verifyMessage.contains(AppConst.ASC_TAG)) {
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
     * 判断files是否缺失文件
     *
     * @param files files
     * @return false：缺失  true：不缺失
     */
    private boolean isMissing(List<FilePO> files) {
        if (files.size() < AppConst.INTEGER_THREE) {
            // 待发布文件
            FilePO publishFile = files.get(0);
            String fileName = publishFile.getName();
            //判断源文件对应的.sha256文件、.sha256.asc文件是否存在
            boolean sha256Exist = false;
            boolean ascExist = false;
            for (FilePO file : files) {
                if (file.getName().equals(fileName + ".sha256")) {
                    sha256Exist = true;
                }
                if (file.getName().equals(fileName + ".sha256.asc")) {
                    ascExist = true;
                }
            }
            if (!sha256Exist && !ascExist) {
                publishFile.setVerifyResult("no sha256 and asc signature");
                publishFile.setPublishResult("fail");
                return false;
            } else if (!sha256Exist) {
                publishFile.setVerifyResult("no sha256 signature");
                publishFile.setPublishResult("fail");
                files.get(1).setPublishResult("fail");
                return false;
            } else if (!ascExist) {
                publishFile.setVerifyResult("no asc signature");
                files.get(1).setVerifyResult("no asc signature");
                publishFile.setPublishResult("fail");
                files.get(1).setPublishResult("fail");
                return false;
            }
        }
        return true;
    }

    /**
     * asc文件用于验证sha256文件，sha256文件用于验证源文件
     *
     * @param fileTempDirPath 发布文件临时下载路径
     * @param sourceFile      源文件
     * @param sha256File      sha256文件
     * @param ascFile         asc文件
     * @return String         是否验签成功
     */
    private String verify(String fileTempDirPath, FilePO sourceFile, FilePO sha256File,
                          FilePO ascFile) throws IOException, InterruptedException {
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
     *
     * @param files           发布文件列表
     * @param fileTempDirPath 发布文件临时下载路径
     * @param publish         publish model
     * @param result          发布结果
     * @param exists          路径是否存在
     */
    private void publishFile(List<FilePO> files, String fileTempDirPath,
                             PublishPO publish, PublishResult result, boolean exists) throws IOException, InterruptedException {
        if (AppConst.UPLOAD_TYPE_OBS.equals(publish.getUploadType())) {
            FilePO sourceFile = files.get(0);
            String targetPath = StringUtils.isEmpty(sourceFile.getTargetPath()) ? "" : sourceFile.getTargetPath().trim();
            for (FilePO file : files) {
                if (exists) {
                    file.setPublishResult("cover");
                } else {
                    file.setPublishResult("normal");
                }
                String fileName = file.getName();
                // 病毒扫描
                boolean clamScanResult = verifyService.clamScan(fileTempDirPath + "/" + fileName);
                if (clamScanResult) {
                    file.setScanResult("success");
                } else {
                    file.setPublishResult("fail");
                    result.setResult("fail");
                    file.setScanResult("is infected");
                    return;
                }
                boolean uploadSuccess = verifyService.execCmdAndContainsMessage("obsutil cp " + fileTempDirPath
                    + fileName + " " + publish.getObsUrl() + (targetPath + "/" + file.getName())
                    .replace("//", "/"), "Upload successfully");
                if (!uploadSuccess) {
                    file.setPublishResult("fail");
                    result.setResult("fail");
                }
            }
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
