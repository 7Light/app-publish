package com.huawei.publish;

import com.huawei.publish.model.FilePO;
import com.huawei.publish.model.PublishPO;
import com.huawei.publish.model.PublishResult;
import com.huawei.publish.model.RepoIndex;
import com.huawei.publish.service.FileDownloadService;
import com.huawei.publish.service.VerifyService;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * main controller
 */
@RequestMapping(path = "/publish")
@RestController
public class PublishVerifyController {
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
                    verifyService.execCmd("mkdir " + tempDirPath);
                }
            }
            boolean deleteTemp = true;
            for (FilePO file : files) {
                boolean exists = false;
                if ("obs".equals(publishPO.getUploadType())) {
                    //TODO
                    exists = !verifyService.execCmdAndContainsMessage("obsutil ls " +
                            publishPO.getObsUrl() + (file.getTargetPath() + "/" + file.getName())
                            .replace("//", "/"), "is: 0B");
                } else {
                    File targetFile = new File(file.getTargetPath() + "/" + file.getName());
                    exists = targetFile.exists();
                }
                if ("skip".equals(publishPO.getConflict()) && exists) {
                    file.setPublishResult("skip");
                    continue;
                }
                String fileName = file.getName();
                if (!StringUtils.isEmpty(file.getUrl()) && file.getUrl().startsWith("http")) {
                    fileDownloadService.downloadHttpUrl(file.getUrl(), tempDirPath, fileName);
                    if (!fileName.endsWith(".sha256") && !fileName.endsWith(".asc")) {
                        fileDownloadService.downloadHttpUrl(file.getUrl() + ".sha256", tempDirPath,
                                fileName + ".sha256");
                    }
                } else {
                    deleteTemp = false;
                    tempDirPath = file.getUrl();
                }
                String verifyMessage = verify(tempDirPath, file, fileName);
                if (!StringUtils.isEmpty(verifyMessage)) {
                    file.setVerifyResult(verifyMessage);
                    if (!"no signatures".equals(verifyMessage)) {
                        continue;
                    }
                } else {
                    file.setVerifyResult("success");
                }
                boolean uploadSuccess = true;
                if ("obs".equals(publishPO.getUploadType())) {
                    uploadSuccess = verifyService.execCmdAndContainsMessage("obsutil cp " + tempDirPath
                            + fileName + " " + publishPO.getObsUrl() + (file.getTargetPath() + "/" + file.getName())
                            .replace("//", "/"), "Upload successfully");
                } else {
                    File targetPathDir = new File(file.getTargetPath());
                    if (!targetPathDir.exists()) {
                        targetPathDir.mkdirs();
                    }
                    verifyService.execCmd("mv " + tempDirPath + "/" + fileName + " " + file.getTargetPath() + "/" + fileName);
                }
                if (uploadSuccess) {
                    if (exists) {
                        file.setPublishResult("cover");
                    } else {
                        file.setPublishResult("normal");
                    }
                } else {
                    file.setPublishResult("fail");
                }
            }
            if (deleteTemp) {
                verifyService.execCmd("rm -rf " + tempDirPath);
            }
            if (!CollectionUtils.isEmpty(publishPO.getRepoIndexList())) {
                for (RepoIndex repoIndex : publishPO.getRepoIndexList()) {
                    if (repoIndex != null) {
                        if ("createrepo".equals(repoIndex.getIndexType())) {
                            verifyService.execCmd("createrepo -d " + repoIndex.getRepoPath());
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
        result.setResult("success");
        return result;
    }

    private String verify(String tempDirPath, FilePO file, String fileName) throws IOException, InterruptedException {
        if (fileName.endsWith(".sha256")) {
            return "";
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
        String sha256 = verifyService.execCmd("cat " + tempDirPath + fileName + ".sha256");
        if (!sha256.contains("No such file or directory")) {
            file.setSha256(sha256);
        }
        if (!StringUtils.isEmpty(file.getSha256())) {
            if (!verifyService.checksum256Verify(tempDirPath + fileName, file.getSha256())) {
                return fileName + " checksum check failed.";
            } else {
                return "";
            }
        }
        return "no signatures";
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
}
