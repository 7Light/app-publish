package com.huawei.publish;

import com.huawei.publish.model.FilePO;
import com.huawei.publish.model.PublishPO;
import com.huawei.publish.model.PublishResult;
import com.huawei.publish.service.FileDownloadService;
import com.huawei.publish.service.VerifyService;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
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
import java.util.UUID;

/**
 * main controller
 */
@RequestMapping(path = "/publish")
@RestController
public class PublishVerifyController {
    private static Map<String, PublishResult> publishResult = new HashMap<>();
    private static Logger log = Logger.getLogger(PublishVerifyController.class);
    @Autowired
    private FileDownloadService fileDownloadService;
    private VerifyService verifyService;

    /**
     * heartbeat
     *
     * @return heartbeat test
     */
    @RequestMapping(value = "/heartbeat", method = RequestMethod.GET)
    public Map<String, Object> heartbeat() {
        Map<String, Object> result = new HashMap<>();
        result.put("result", "success");
        log.info("heartbeat");
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
        String validate = validate(publishPO);
        if (!StringUtils.isEmpty(validate)) {
            result.setResult("fail");
            result.setMessage("Validate failed, " + validate);
            return result;
        }
        verifyService = new VerifyService(publishPO);
        List<FilePO> files = publishPO.getFiles();
        String tempDirPath = publishPO.getTempDir();
        try {
            if (!StringUtils.isEmpty(tempDirPath)) {
                File tempDir = new File(tempDirPath);
                if (!tempDir.exists()) {
                    verifyService.execCmd("mkdir -p " + tempDirPath);
                }
            }
            List<FilePO> missFiles = new ArrayList<>();// 存储缺失源文件的sha256,asc文件
            List<List<FilePO>> filePOList = getFilePOList(files, missFiles);
            if (!CollectionUtils.isEmpty(missFiles)) {
                for (FilePO missFile : missFiles) {
                    missFile.setVerifyResult("no source file");
                    missFile.setPublishResult("fail");
                }
            }
            for (List<FilePO> filePOS : filePOList) {
                FilePO sourceFile = filePOS.get(0);// 源文件
                String fileTempDirPath = (tempDirPath + "/" + UUID.randomUUID() + "/").replace("//", "/");
                String targetPath = StringUtils.isEmpty(sourceFile.getTargetPath()) ? "" : sourceFile.getTargetPath().trim();
                //判断文件是否存在于发布路径
                boolean exists = true;
                if ("obs".equals(publishPO.getUploadType())) {
                    for (FilePO filePO : filePOS) {
                        exists = exists && !verifyService.execCmdAndContainsMessage("obsutil stat " +
                            publishPO.getObsUrl() + (targetPath + "/" + filePO.getName())
                            .replace("//", "/"), "Error: Status [404]");
                    }
                }
                if ("skip".equals(publishPO.getConflict()) && exists) {
                    for (FilePO file : filePOS) {
                        file.setPublishResult("skip");
                    }
                    continue;
                }
                // 验签
                boolean isVerifySuccess = true;
                if (!"yaml".equals(sourceFile.getVerifyType())) {
                    String authorization = publishPO.getAuthorization();
                    isVerifySuccess = verifySignature(filePOS, fileTempDirPath, authorization);
                }
                // 发布
                if (isVerifySuccess) {
                    publishFile(filePOS, targetPath, fileTempDirPath, exists, publishPO);
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
        result.setResult("success");
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

    /**
     * 封装FilePO, 源文件、对应的sha256、对应的asc存入list集合,对应索引0,1,2
     *
     * @param files     要发布的文件
     * @param missFiles 存储缺失源文件的sha256,asc文件
     * @return List<List < FilePO>>
     */
    private List<List<FilePO>> getFilePOList(List<FilePO> files, List<FilePO> missFiles) {
        List<FilePO> sourceFiles = new ArrayList<>();//存源文件
        List<FilePO> sha256Files = new ArrayList<>();//存sha256文件
        List<FilePO> ascFiles = new ArrayList<>();//存asc源文件
        for (FilePO file : files) {
            if (file.getName().endsWith(".sha256")) {
                sha256Files.add(file);
            } else if (file.getName().endsWith(".sha256.asc")) {
                ascFiles.add(file);
            } else {
                sourceFiles.add(file);
            }
        }
        List<List<FilePO>> list = new ArrayList<>();
        for (FilePO filePO : sourceFiles) {
            List<FilePO> filePOS = new ArrayList<>();
            filePOS.add(filePO);
            if ("yaml".equals(filePO.getVerifyType())) {
                list.add(filePOS);
                continue;
            }
            String sourceName = filePO.getName();
            for (FilePO sha256File : sha256Files) {
                if (sha256File.getName().equals(sourceName + ".sha256") &&
                    filePO.getParentDir().equals(sha256File.getParentDir())) {
                    filePOS.add(sha256File);
                    sha256Files.remove(sha256File);
                    break;
                }
            }
            for (FilePO ascFile : ascFiles) {
                if (ascFile.getName().equals(sourceName + ".sha256.asc") &&
                    filePO.getParentDir().equals(ascFile.getParentDir())) {
                    filePOS.add(ascFile);
                    ascFiles.remove(ascFile);
                    break;
                }
            }
            list.add(filePOS);
        }
        missFiles.addAll(sha256Files);
        missFiles.addAll(ascFiles);
        return list;
    }

    /**
     * 文件验签
     *
     * @param filePOS         源文件、sha256文件、asc文件
     * @param fileTempDirPath 文件临时下载路径
     * @param authorization   mindspore权限认证
     * @return 是否验签成功
     * @throws IOException
     * @throws InterruptedException
     */
    private boolean verifySignature(List<FilePO> filePOS, String fileTempDirPath,
                                    String authorization) throws IOException, InterruptedException {
        if (!isMissing(filePOS)) {
            return false;
        }
        FilePO sourceFile = filePOS.get(0);// 源文件
        FilePO sha256File = filePOS.get(1);// sha256文件
        FilePO ascFile = filePOS.get(2);// asc文件
        File fileTempDir = new File(fileTempDirPath);
        fileTempDir.mkdir();
        for (FilePO filePO : filePOS) {
            fileDownloadService.downloadHttpUrl(filePO.getUrl(), fileTempDirPath, filePO.getName(), authorization);
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
     * 判断filePOS是否缺失文件
     *
     * @param filePOS filePOS
     * @return false：缺失  true：不缺失
     */
    private boolean isMissing(List<FilePO> filePOS) {
        if (filePOS.size() < 3) {
            FilePO file = filePOS.get(0);
            String fileName = file.getName();
            //判断源文件对应的.sha256文件、.sha256.asc文件是否存在
            boolean sha256Exist = false;
            boolean ascExist = false;
            for (FilePO filePO : filePOS) {
                if (filePO.getName().equals(fileName + ".sha256")) {
                    sha256Exist = true;
                }
                if (filePO.getName().equals(fileName + ".sha256.asc")) {
                    ascExist = true;
                }
            }
            if (!sha256Exist && !ascExist) {
                file.setVerifyResult("no sha256 and asc signature");
                file.setPublishResult("fail");
                return false;
            } else if (!sha256Exist) {
                file.setVerifyResult("no sha256 signature");
                file.setPublishResult("fail");
                filePOS.get(1).setPublishResult("fail");
                return false;
            } else if (!ascExist) {
                file.setVerifyResult("no asc signature");
                filePOS.get(1).setVerifyResult("no asc signature");
                file.setPublishResult("fail");
                filePOS.get(1).setPublishResult("fail");
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
     * @return 是否验签成功
     * @throws IOException
     * @throws InterruptedException
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
     * @param filePOS         发布文件列表
     * @param targetPath      发布目标路径
     * @param fileTempDirPath 发布文件临时下载路径
     * @param exists          发布目标路径是否存在待发布文件
     * @param publishPO       publish model
     * @throws IOException
     * @throws InterruptedException
     */
    private void publishFile(List<FilePO> filePOS, String targetPath, String fileTempDirPath, boolean exists,
                             PublishPO publishPO) throws IOException, InterruptedException {
        if ("obs".equals(publishPO.getUploadType())) {
            for (FilePO filePO : filePOS) {
                String fileName = filePO.getName();
                boolean uploadSuccess = verifyService.execCmdAndContainsMessage("obsutil cp " + fileTempDirPath
                    + fileName + " " + publishPO.getObsUrl() + (targetPath + "/" + filePO.getName())
                    .replace("//", "/"), "Upload successfully");
                if (uploadSuccess) {
                    if (exists) {
                        filePO.setPublishResult("cover");
                    } else {
                        filePO.setPublishResult("normal");
                    }
                } else {
                    filePO.setPublishResult("fail");
                }
            }
        }
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
            File targetFile = new File(file.getTargetPath().trim() + "/" + file.getName());
            if ("error".equals(publishPO.getConflict()) && targetFile.exists()) {
                return file.getName() + " already published.";
            }
        }
        return "";
    }
}
