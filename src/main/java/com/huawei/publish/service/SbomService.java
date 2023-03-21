package com.huawei.publish.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.huawei.publish.model.SbomPO;
import com.huawei.publish.utils.HttpRequestUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
/**
 * @author chentao
 */
@Component
public class SbomService {
    private static final Logger log = LoggerFactory.getLogger(SbomService.class);

    /**
     * SBOM生成
     *
     * @param sbomPO 参数
     * @return Map SBOM生成接口结果
     */
    public Map<String, String> generateOpeneulerSbom(SbomPO sbomPO, String artifactPath) {
        Map<String, String> generateResultMap = new HashMap<>();
        Map<String, String> paramMap = new HashMap<>();
        paramMap.put("artifactPath", artifactPath);
        String paramJson= JSON.toJSONString(paramMap);
        String responseContent = HttpRequestUtil.doPost(sbomPO.getGenerateSbomUrl(), paramJson);
        generateResultMap.put("result", "publishing");
        if (StringUtils.isEmpty(responseContent)) {
            // 请求异常返回空值，检查服务是否通
            generateResultMap.put("errorInfo", "请求异常：" + sbomPO.getGenerateSbomUrl());
            log.error("SBOM生成: " + generateResultMap.get("errorInfo"));
            return generateResultMap;
        }
        JSONObject object = JSONObject.parseObject(responseContent);
        if (object.getObject("success", Boolean.class) == null ) {
            generateResultMap.put("errorInfo", "请求异常：" + sbomPO.getGenerateSbomUrl());
            log.error("SBOM生成: " + generateResultMap.get("errorInfo"));
            return generateResultMap;
        }
        if (!object.getObject("success", Boolean.class)) {
            generateResultMap.put("errorInfo", "SBOM生成: " + object.getObject("errorInfo", String.class));
            log.error("SBOM生成: errorInfo = " + object.getObject("errorInfo", String.class) + "; artifactPath = "
                + artifactPath +"; url = " + sbomPO.getGenerateSbomUrl());
            return generateResultMap;
        }
        generateResultMap.put("result", "success");
        generateResultMap.put("sbomContent", object.getObject("sbomContent", String.class));
        return generateResultMap;
    }

    /**
     * SBOM发布接口
     *
     * @param sbomPO 参数
     * @param sbomContent SBOM文本内容
     * @return Map SBOM发布结果
     */
    public Map<String, String> publishSbomFile(SbomPO sbomPO, String sbomContent, String productName) {
        Map<String, String> paramMap = new HashMap<>();
        paramMap.put("productName", productName);
        paramMap.put("sbomContent", sbomContent);
        //定义发送数据
        String paramJson= JSON.toJSONString(paramMap);
        String responseContent = HttpRequestUtil.doPost(sbomPO.getPublishSbomUrl(), paramJson);
        Map<String, String> publishResultMap = new HashMap<>();
        publishResultMap.put("result", "publishing");
        if (StringUtils.isEmpty(responseContent)) {
            // 请求异常返回空值，检查服务是否通
            publishResultMap.put("errorInfo", "请求异常：" + sbomPO.getPublishSbomUrl());
            log.error("SBOM发布: " + publishResultMap.get("errorInfo"));
            return publishResultMap;
        }
        JSONObject object = JSONObject.parseObject(responseContent);
        if (object.getObject("success", Boolean.class) == null ) {
            publishResultMap.put("errorInfo", "请求异常：" + sbomPO.getPublishSbomUrl());
            log.error("SBOM发布: " + publishResultMap.get("errorInfo"));
            return publishResultMap;
        }
        if (object.getObject("success", Boolean.class) == null || !object.getObject("success", Boolean.class)) {
            publishResultMap.put("errorInfo", "SBOM发布: " + object.getObject("errorInfo", String.class));
            log.error("SBOM发布: errorInfo = " + object.getObject("errorInfo", String.class) + "; url = "
                + sbomPO.getPublishSbomUrl() + "; sbomContent = " + sbomContent.length() + "; productName = " + productName);
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
        Map<String, String> queryResultMap = new HashMap<>();
        queryResultMap.put("result", "publishing");
        if (StringUtils.isEmpty(responseContent)) {
            // 请求异常返回空值，检查服务是否通
            queryResultMap.put("errorInfo", "请求异常：" + querySbomPublishResultUrl);
            log.error("SBOM发布结果查询: " + queryResultMap.get("errorInfo"));
            return queryResultMap;
        }
        JSONObject object = JSONObject.parseObject(responseContent);
        if (object.getObject("success", Boolean.class) == null ) {
            queryResultMap.put("errorInfo", "请求异常：" + querySbomPublishResultUrl);
            log.error("SBOM发布结果查询: " + queryResultMap.get("errorInfo"));
            return queryResultMap;
        }
        if (!object.getObject("success", Boolean.class)) {
            queryResultMap.put("errorInfo", "SBOM结果查询: " + object.getObject("errorInfo", String.class));
            log.error("SBOM发布结果查询: " + object.getObject("errorInfo", String.class));
            return queryResultMap;
        }
        if (!object.getObject("finish", Boolean.class)) {
            // SBOM发布未完成
            log.info("SBOM发布未完成！");
            return queryResultMap;
        }
        queryResultMap.put("sbomRef", object.getObject("sbomRef", String.class));
        queryResultMap.put("result", "success");
        return queryResultMap;
    }
}
