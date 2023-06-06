package com.huawei.publish.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.huawei.publish.model.SbomPO;
import com.huawei.publish.utils.HttpRequestUtil;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;


import java.util.HashMap;
import java.util.Map;

/**
 * SbomService
 *
 * @author jrsgxtc
 * @since 2022/12/6
 */
@Component
public class SbomService {
    private static final Logger log = LoggerFactory.getLogger(SbomService.class);

    /**
     * SBOM发布接口
     *
     * @param sbomObject      参数
     * @param sbomContent SBOM文本内容
     * @return Map SBOM发布结果
     */
    public Map<String, String> publishSbomFile(SbomPO sbomObject, String sbomContent, String productName) {
        Map<String, String> paramMap = new HashMap<>();
        paramMap.put("productName", productName);
        paramMap.put("sbomContent", sbomContent);
        paramMap.put("sbomContentType", "sbom_tracer_data");
        //定义发送数据
        String paramJson = JSON.toJSONString(paramMap);
        String responseContent = HttpRequestUtil.doPost(sbomObject.getPublishSbomUrl(), paramJson);
        Map<String, String> publishResultMap = new HashMap<>();
        publishResultMap.put("result", "publishing");
        if (StringUtils.isEmpty(responseContent)) {
            // 请求异常返回空值，检查服务是否通
            publishResultMap.put("errorInfo", "发布请求异常：" + sbomObject.getPublishSbomUrl());
            log.error("SBOM发布: " + publishResultMap.get("errorInfo"));
            return publishResultMap;
        }
        JSONObject object = JSONObject.parseObject(responseContent);
        if (object.getObject("success", Boolean.class) == null) {
            publishResultMap.put("errorInfo", "发布请求异常：" + sbomObject.getPublishSbomUrl());
            log.error("SBOM发布: " + publishResultMap.get("errorInfo"));
            return publishResultMap;
        }
        if (!object.getObject("success", Boolean.class)) {
            publishResultMap.put("errorInfo", "SBOM发布: " + object.getObject("errorInfo", String.class));
            log.error("SBOM发布: errorInfo = " + object.getObject("errorInfo", String.class) + "; url = "
                + sbomObject.getPublishSbomUrl() + "; sbomContent = "
                + sbomContent.length() + "; productName = " + productName);
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
            queryResultMap.put("errorInfo", "查询请求异常：" + querySbomPublishResultUrl);
            log.error("SBOM发布结果查询: " + queryResultMap.get("errorInfo"));
            return queryResultMap;
        }
        JSONObject object = JSONObject.parseObject(responseContent);
        if (object.getObject("success", Boolean.class) == null) {
            queryResultMap.put("errorInfo", "查询请求异常：" + querySbomPublishResultUrl);
            log.error("SBOM发布结果查询: " + queryResultMap.get("errorInfo"));
            return queryResultMap;
        }
        if (!object.getObject("success", Boolean.class)) {
            queryResultMap.put("errorInfo", "SBOM结果查询: " + object.getObject("errorInfo", String.class));
            log.error("SBOM发布结果查询: " + object.getObject("errorInfo", String.class));
            return queryResultMap;
        }
        if (!object.getObject("finish", Boolean.class)) {
            // SBOM发布进行中
            queryResultMap.put("errorInfo", "SBOM发布进行中！");
            log.info("SBOM发布进行中！");
            return queryResultMap;
        }
        queryResultMap.put("sbomRef", object.getObject("sbomRef", String.class));
        queryResultMap.put("result", "success");
        return queryResultMap;
    }
}
