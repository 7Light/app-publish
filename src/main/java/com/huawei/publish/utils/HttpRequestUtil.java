package com.huawei.publish.utils;


import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * http的get请求
 *
 * @author chentao
 * @since 2022/08/22 17:15
 */
public class HttpRequestUtil {
    private static final Logger log = LoggerFactory.getLogger(HttpRequestUtil.class);


    /**
     * 发送HTTP_POST请求
     *
     * @param url 请求地址
     * @param param 参数
     * @return responseContent
     */
    public static String doPost(String url, Map<String, String> param) {
        String responseContent  = null;
        // 创建Http Post请求
        HttpPost httpPost = new HttpPost(url);
        httpPost.setHeader(HTTP.CONTENT_TYPE, "application/json");
        try {
            // 创建参数列表
            if (param != null) {
                List<NameValuePair> paramList = new ArrayList<>();
                for (String key : param.keySet()) {
                    paramList.add(new BasicNameValuePair(key, param.get(key)));
                }
                // 模拟表单
                UrlEncodedFormEntity entity = new UrlEncodedFormEntity(paramList);
                httpPost.setEntity(entity);
            }
            // 执行http请求
            CloseableHttpClient httpClient = HttpClients.createDefault();
            CloseableHttpResponse response = httpClient.execute(httpPost);
            responseContent = EntityUtils.toString(response.getEntity(), "utf-8");
            log.info("调用接口返回responseContent{}", responseContent);
        } catch (IOException e) {
            log.error("post请求提交失败,异常信息:{}", e.getStackTrace());
        } finally {
            httpPost.releaseConnection();
        }
        return responseContent;
    }
}

