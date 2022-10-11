package com.huawei.publish;

import com.huawei.publish.model.FileFromRepoModel;
import com.huawei.publish.model.FilePO;
import com.huawei.publish.model.PublishPO;
import com.huawei.publish.model.PublishResult;
import com.huawei.publish.service.ObsUtil;
import com.huawei.publish.service.VerifyService;
import org.apache.log4j.Logger;
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
                boolean deleteTemp = false;
                String fileTempDirPath = tempDirPath + "/" + UUID.randomUUID() + "/";
                String targetPath = StringUtils.isEmpty(file.getTargetPath()) ? "" : file.getTargetPath().trim();
                String fileName = file.getName();
                if ("obs".equals(publishPO.getUploadType())) {
                    exists = obsUtil.isExist(targetPath + fileName);
                }
                if ("skip".equals(publishPO.getConflict()) && exists) {
                    file.setPublishResult("skip");
                    continue;
                }
                //验签
                String verifyMessage = "";
                if (!fileName.endsWith(".sha256") && !"latest/".equals(file.getParentDir()) && !file.getParentDir().contains(
                        "binarylibs_update/") && !file.getParentDir().contains("binarylibs/") && !"git_num.txt".equals(fileName)) {
                    File fileTempDir = new File(fileTempDirPath);
                    fileTempDir.mkdir();
                    deleteTemp = true;
                    obsUtil.downFile(file.getParentDir() + fileName, fileTempDirPath + fileName);
                    if (fileName.endsWith(".tar.bz2")) {
                        obsUtil.downFile(file.getParentDir() + fileName.replace(".tar.bz2", ".sha256"),
                                fileTempDirPath + fileName.replace(".tar.bz2", ".sha256"));
                    } else {
                        obsUtil.downFile(file.getParentDir() + fileName + ".sha256", fileTempDirPath + fileName + ".sha256");
                    }
                    verifyMessage = verify(fileTempDirPath, file, fileName);
                }
                if (!StringUtils.isEmpty(verifyMessage)) {
                    file.setVerifyResult(verifyMessage);
                    file.setPublishResult("fail");
                    continue;
                } else {
                    file.setVerifyResult("success");
                }
                //发布源文件
                boolean uploadSuccess = true;
                if ("obs".equals(publishPO.getUploadType())) {
                    //发布源文件
                    if (!fileName.endsWith(".sha256")) {
                        uploadSuccess = obsUtil.copyObject(file.getParentDir() + fileName, targetPath + fileName);
                    } else { //根据源文件是否已发布，判断是否发布源文件对应的sha256文件
                        boolean sha256Publish = obsUtil.isExist(targetPath + fileName.replace(".sha256", ""))
                                || obsUtil.isExist(targetPath + fileName.replace(".sha256", ".tar.bz2"));
                        if (sha256Publish) {
                            uploadSuccess = obsUtil.copyObject(file.getParentDir() + fileName, targetPath + fileName);
                        } else {
                            uploadSuccess = false;
                        }
                    }
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
                //删除下载的临时文件
                if (deleteTemp) {
                    if (fileName.endsWith(".tar.bz2")) {
                        verifyService.execCmd("rm -rf " + fileTempDirPath + fileName);
                        verifyService.execCmd("rm -rf " + fileTempDirPath + fileName.replace(".tar.bz2", ".sha256"));
                    } else {
                        verifyService.execCmd("rm -rf " + fileTempDirPath + fileName);
                        verifyService.execCmd("rm -rf " + fileTempDirPath + fileName + ".sha256");
                    }
                }
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
     * 提供需要发布的一层文件列表,由FileFromRepoUtil类中的getFiles（）方法请求调用
     *
     * @param path
     * @return
     */
    @RequestMapping(value = "/getPublishList", method = RequestMethod.GET)
    public List<FileFromRepoModel> getPublishList(@RequestParam(value = "path") String path) {
        obsUtil = new ObsUtil();
        ArrayList<FileFromRepoModel> result = new ArrayList<>();
        List<FileFromRepoModel> files = obsUtil.listObjects(path);
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
     * 提供需要发布的所有文件列表,由FileFromRepoUtil类中的getAllFiles（）方法请求调用
     *
     * @param path
     * @return
     */
    @RequestMapping(value = "/getAllPublishList", method = RequestMethod.GET)
    public List<FileFromRepoModel> getAllPublishList(@RequestParam(value = "path") String path) {
        obsUtil = new ObsUtil();
        ArrayList<FileFromRepoModel> result = new ArrayList<>();
        List<FileFromRepoModel> files = obsUtil.listObjects(path);
        for (FileFromRepoModel file : files) {
            if (!file.isDir()) {
                result.add(file);
            }
        }
        return result;
    }

    private String verify(String tempDirPath, FilePO file, String fileName) throws IOException, InterruptedException {
        if (StringUtils.isEmpty(file.getSha256())) {
            String sha256;
            if (fileName.endsWith(".tar.bz2")) {
                sha256 = verifyService.execCmd("cat " + tempDirPath + fileName.replace(".tar.bz2", ".sha256"));
            } else {
                sha256 = verifyService.execCmd("cat " + tempDirPath + fileName + ".sha256");
            }
            if (!sha256.contains("No such file or directory")) {
                file.setSha256(sha256);
            }
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
