package com.huawei.publish.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.huawei.publish.model.ArchiveInfoPO;
import com.huawei.publish.utils.HttpRequestUtil;
import com.huawei.publish.utils.SecurityUtil;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * @author chentao
 */
@Component
public class GiteeUploaderService {
    private static final String API_BASE_URL = "https://gitee.com/api/v5";

    /**
     * Gitee API 上传文件到指定仓库
     *
     * @param archiveInfo 归档信息
     * @return boolean
     */
    public boolean uploadFile2Gitee(ArchiveInfoPO archiveInfo, String filePath, byte[] fileContent) {
        String owner = archiveInfo.getOwner();
        String repoName = archiveInfo.getRepoName();
        String accessToken = archiveInfo.getGiteeUploadAccessToken();
        if (!StringUtils.isEmpty(accessToken)) {
            accessToken = SecurityUtil.decrypt(accessToken);
        }
        String branch = archiveInfo.getBranch();
        String getUrl = API_BASE_URL + "/repos/" + owner + "/" + repoName + "/contents/" + filePath +
            "?access_token=" + accessToken + "&ref=" + branch;
        String getResponseContent = HttpRequestUtil.doGet(getUrl);
        String fileContentBase64 = Base64.encodeBase64String(fileContent);
        String url = API_BASE_URL + "/repos/" + owner + "/" + repoName + "/contents/" + filePath;
        Map<Object, Object> paramJson = new HashMap<>();
        paramJson.put("access_token", accessToken);
        paramJson.put("content", fileContentBase64);
        paramJson.put("path", filePath);
        paramJson.put("branch", branch);
        String uploadResponseContent;
        // 判断文件是否已经存在，存在则更新
        if(StringUtils.isEmpty(getResponseContent) || getResponseContent.contains("[]")) {
            paramJson.put("message", "Add file" + filePath);
            uploadResponseContent = HttpRequestUtil.doPost(url, JSON.toJSONString(paramJson));
        } else {
            JSONObject object = JSONObject.parseObject(getResponseContent);
            // 要更新的文件的原始 SHA 值。
            paramJson.put("sha", object.getObject("sha", String.class));
            paramJson.put("message", "Update file" + filePath);
            uploadResponseContent = HttpRequestUtil.doPut(url, JSON.toJSONString(paramJson));
        }
        return uploadResponseContent.contains("content") && uploadResponseContent.contains("commit");
    }
}