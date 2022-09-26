package com.huawei.publish;

import com.huawei.publish.model.*;
import com.huawei.publish.service.FileDownloadService;
import com.huawei.publish.service.ObsUtil;
import com.huawei.publish.service.VerifyService;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.util.*;

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
    private ObsUtil obsUtil;

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
        obsUtil = new ObsUtil();
        try {
            if (!StringUtils.isEmpty(tempDirPath)) {
                File tempDir = new File(tempDirPath);
                if (!tempDir.exists()) {
                    verifyService.execCmd("mkdir -p " + tempDirPath);
                }
            }
            for (FilePO file : files) {
                boolean exists = false;
                String targetPath = StringUtils.isEmpty(file.getTargetPath()) ? "" : file.getTargetPath().trim();
                if ("obs".equals(publishPO.getUploadType())) {
                    exists = !verifyService.execCmdAndContainsMessage("obsutil ls " +
                            publishPO.getObsUrl() + (targetPath + "/" + file.getName())
                            .replace("//", "/"), "is: 0B");
                } else {
                    File targetFile = new File(targetPath + file.getName());
                    exists = targetFile.exists();
                }
                if ("skip".equals(publishPO.getConflict()) && exists) {
                    file.setPublishResult("skip");
                    continue;
                }
                String fileName = file.getName();
                String fileTempDirPath = tempDirPath + "/" + UUID.randomUUID() + "/";
                File fileTempDir = new File(fileTempDirPath);
                fileTempDir.mkdir();
                if (!StringUtils.isEmpty(file.getUrl())) {
                    obsUtil.downFile(file.getUrl(), fileTempDirPath);
                    if (fileName.endsWith(".tar.bz2")) {
                        file.setSha256(fileDownloadService.getContent(file.getUrl().replace(".tar.bz2", ".sha256"), null));
                    }else if(!fileName.endsWith(".sha256")) {
                        file.setSha256(fileDownloadService.getContent(file.getUrl() + ".sha256", null));
                    }
                } else {
                    fileTempDirPath = file.getUrl();
                }
                String verifyMessage = verify(fileTempDirPath, file, fileName);
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
                    uploadSuccess = new ObsUtil().copyObject(file.getUrl(), targetPath);
                } else {
                    File targetPathDir = new File(targetPath);
                    if (!targetPathDir.exists()) {
                        targetPathDir.mkdirs();
                    }
                    verifyService.execCmd("mv " + fileTempDirPath + fileName + " " + targetPath + fileName);
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
                verifyService.execCmd("rm -rf " + fileTempDirPath + fileName);
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
     * 提供需要发布的文件列表,由FileFromRepoUtil类中的getFiles（）方法请求调用
     *
     * @param path
     * @return
     */
    @RequestMapping(value = "/getPublishList", method = RequestMethod.GET)
    public List<FileFromRepoModel> getPublishList(@RequestParam(value = "path") String path) {
        obsUtil = new ObsUtil();
        ArrayList<FileFromRepoModel> result = new ArrayList<>();
        if (StringUtils.isEmpty(path)) {
            List<FileFromRepoModel> files = obsUtil.listObjects();
            for (FileFromRepoModel file : files) {
                //获取顶层文件
                if (file.getParentDir() == null) {
                    result.add(file);
                }
            }
        } else {
            List<FileFromRepoModel> files = obsUtil.listObjects(path);
            for (FileFromRepoModel file : files) {
                //获取内层文件
                if (path.equals(file.getParentDir())) {
                    result.add(file);
                }
            }
        }
        return result;
    }

    private String verify(String tempDirPath, FilePO file, String fileName) throws IOException, InterruptedException {
        if (fileName.endsWith(".sha256") || "latest/".equals(file.getParentDir()) || file.getParentDir().contains(
                "binarylibs_update/") || file.getParentDir().contains("binarylibs/") || "git.num.txt".equals(fileName)) {
            return "";
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
