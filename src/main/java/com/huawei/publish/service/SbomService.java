package com.huawei.publish.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.huawei.publish.model.PublishPO;
import com.huawei.publish.model.SbomPO;
import com.huawei.publish.utils.HttpRequestUtil;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
@Component
public class SbomService {

    /**
     * SBOM生成
     *
     * @param publishPO 参数
     * @return Map SBOM生成接口结果
     */
    public Map<String, String> generateOpeneulerSbom(PublishPO publishPO) {
        SbomPO sbomPO = publishPO.getSbom();
        Map<String, String> paramMap = new HashMap<>();
        paramMap.put("artifactPath",publishPO.getFiles().get(0).getTargetPath());
        String  paramJson= JSON.toJSONString(paramMap);
        String responseContent = HttpRequestUtil.doPost(sbomPO.getGenerateSbomUrl(), paramJson);
        JSONObject object = JSONObject.parseObject(responseContent);
        Map<String, String> generateResultMap = new HashMap<>();
        if (object.getObject("success", Boolean.class) == null || !object.getObject("success", Boolean.class)) {
            generateResultMap.put("errorInfo", "SBOM生成: " + object.getObject("errorInfo", String.class));
            generateResultMap.put("result", "fail");
            return generateResultMap;
        }
        generateResultMap.put("result", "success");
        generateResultMap.put("sbomContent", object.getObject("sbomContent", String.class));
        return generateResultMap;
    }

    /**
     * SBOM发布接口
     *
     * @param publishPO 参数
     * @param sbomContent SBOM文本内容
     * @return Map SBOM发布结果
     */
    public Map<String, String> publishSbomFile(PublishPO publishPO, String sbomContent) {
        Map<String, String> paramMap = new HashMap<>();
        paramMap.put("productName", publishPO.getFiles().get(0).getTargetPath());
        paramMap.put("sbomContent", sbomContent);
        //定义发送数据
        String paramJson= JSON.toJSONString(paramMap);
        String responseContent = HttpRequestUtil.doPost(publishPO.getSbom().getPublishSbomUrl(), paramJson);
        JSONObject object = JSONObject.parseObject(responseContent);
        Map<String, String> publishResultMap = new HashMap<>();
        if (object.getObject("success", Boolean.class) == null || !object.getObject("success", Boolean.class)) {
            publishResultMap.put("errorInfo", "SBOM发布: " + object.getObject("errorInfo", String.class));
            publishResultMap.put("result", "fail");
            return publishResultMap;
        }
        publishResultMap.put("taskId", object.getObject("taskId", String.class));
        publishResultMap.put("result", "success");
        return publishResultMap;
    }

    /**
     * SBOM发布结果查询
     *
     * @param querySbomPublishResultUrl SBOM发布结果查询地址
     * @return Map 查询SBOM发布结果
     */
    public Map<String, String> querySbomPublishResult(String querySbomPublishResultUrl) {
        String responseContent = HttpRequestUtil.doGet(querySbomPublishResultUrl);
        JSONObject object = JSONObject.parseObject(responseContent);
        Map<String, String> queryResultMap = new HashMap<>();
        if (object.getObject("success", Boolean.class) == null || !object.getObject("success", Boolean.class)) {
            queryResultMap.put("errorInfo", "SBOM结果: " + object.getObject("errorInfo", String.class));
            queryResultMap.put("result", "fail");
            return queryResultMap;
        }
        if (!object.getObject("finish", Boolean.class)) {
            queryResultMap.put("errorInfo", "SBOM发布未完成");
            queryResultMap.put("result", "publishing");
            return queryResultMap;
        }
        queryResultMap.put("sbomRef", object.getObject("sbomRef", String.class));
        queryResultMap.put("result", "success");
        return queryResultMap;
    }
}
