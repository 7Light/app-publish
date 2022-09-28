package com.huawei.publish.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.huawei.publish.model.PublishPO;
import com.huawei.publish.model.SbomPO;
import com.huawei.publish.utils.HttpRequestUtil;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
@Component
public class SbomService {
    private static Logger log = Logger.getLogger(SbomService.class);
    /**
     * SBOM生成
     *
     * @param publishPO 参数
     * @return Map SBOM生成接口结果
     */
    public Map<String, String> generateOpeneulerSbom(PublishPO publishPO, String artifactPath) {
        SbomPO sbomPO = publishPO.getSbom();
        Map<String, String> generateResultMap = new HashMap<>();
        Map<String, String> paramMap = new HashMap<>();
        paramMap.put("artifactPath", artifactPath);
        String paramJson= JSON.toJSONString(paramMap);
        String responseContent = HttpRequestUtil.doPost(sbomPO.getGenerateSbomUrl(), paramJson);
        JSONObject object = JSONObject.parseObject(responseContent);
        if (object.getObject("success", Boolean.class) == null || !object.getObject("success", Boolean.class)) {
            generateResultMap.put("errorInfo", "SBOM生成: " + object.getObject("errorInfo", String.class));
            generateResultMap.put("result", "fail");
            log.error("SBOM生成: " + object.getObject("errorInfo", String.class));
            return generateResultMap;
        }
        generateResultMap.put("result", "success");
        generateResultMap.put("sbomContent", object.getObject("sbomContent", String.class));
        log.info("sbomContent: " +  object.getObject("sbomContent", String.class).length());
        return generateResultMap;
    }

    /**
     * SBOM发布接口
     *
     * @param publishPO 参数
     * @param sbomContent SBOM文本内容
     * @return Map SBOM发布结果
     */
    public Map<String, String> publishSbomFile(PublishPO publishPO, String sbomContent, String productName) {
        Map<String, String> paramMap = new HashMap<>();
        paramMap.put("productName", productName);
        paramMap.put("sbomContent", sbomContent);
        //定义发送数据
        String paramJson= JSON.toJSONString(paramMap);
        String responseContent = HttpRequestUtil.doPost(publishPO.getSbom().getPublishSbomUrl(), paramJson);
        JSONObject object = JSONObject.parseObject(responseContent);
        Map<String, String> publishResultMap = new HashMap<>();
        if (object.getObject("success", Boolean.class) == null || !object.getObject("success", Boolean.class)) {
            publishResultMap.put("errorInfo", "SBOM发布: " + object.getObject("errorInfo", String.class));
            publishResultMap.put("result", "fail");
            log.error("SBOM发布: " + object.getObject("errorInfo", String.class));
            return publishResultMap;
        }
        publishResultMap.put("taskId", object.getObject("taskId", String.class));
        publishResultMap.put("result", "success");
        log.info("taskId: " +  object.getObject("taskId", String.class));
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
            log.error("SBOM结果: " + object.getObject("errorInfo", String.class));
            return queryResultMap;
        }
        if (!object.getObject("finish", Boolean.class)) {
            queryResultMap.put("errorInfo", "SBOM发布未完成");
            queryResultMap.put("result", "publishing");
            return queryResultMap;
        }
        queryResultMap.put("sbomRef", object.getObject("sbomRef", String.class));
        queryResultMap.put("result", "success");
        log.info("sbomRef: " +  object.getObject("sbomRef", String.class));
        return queryResultMap;
    }
}
