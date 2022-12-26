package com.huawei.publish.utils;

import org.apache.http.HttpEntity;
import org.apache.http.HttpVersion;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

/**
 * HttpRequestUtil
 *
 * @author jrsgxtc
 * @since 2022/12/7
 */
public class HttpRequestUtil {
    private static final Logger log = LoggerFactory.getLogger(HttpRequestUtil.class);
    /**
     * 发送HTTP_POST请求
     *
     * @param url 请求地址
     * @param paramJson 请求参数
     * @return responseContent
     */
    public static String doPost(String url, String paramJson) {
        String responseContent  = null;
        // 创建Http Post请求
        HttpPost httpPost = new HttpPost(url.trim());
        httpPost.setProtocolVersion(HttpVersion.HTTP_1_0);
        httpPost.setHeader(HTTP.CONTENT_TYPE, "application/json");
        try  {
            StringEntity entity = new StringEntity(paramJson,  StandardCharsets.UTF_8);
            entity.setContentEncoding("UTF-8");
            entity.setContentType("application/json");
            httpPost.setEntity(entity);
            // 执行http请求
            CloseableHttpClient httpClient = HttpClients.createDefault();
            CloseableHttpResponse response = httpClient.execute(httpPost);
            responseContent = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("post请求提交失败,异常信息:{}", e.getMessage());
        } finally {
            httpPost.releaseConnection();
        }
        return responseContent;
    }

    /**
     * 发送HTTP_GET请求
     *
     * @param url 请求地址
     * @return responseContent
     */
    public static String doGet (String url) {
        String responseContent  = null;
        //构造httpGet对象
        HttpGet httpGet = new HttpGet(url.trim());
        try{
            CloseableHttpClient closeableHttpClient = HttpClients.createDefault();
            //构造响应对象
            CloseableHttpResponse response = closeableHttpClient.execute(httpGet);
            //获取响应结果
            HttpEntity entity = response.getEntity();
            //对HttpEntity操作的工具类
            responseContent = EntityUtils.toString(entity, StandardCharsets.UTF_8);
            //确保流关闭
            EntityUtils.consume(entity);
        }catch (Exception e){
            log.error("get请求提交失败,异常信息:{}", e.getMessage());
        }finally {
            httpGet.releaseConnection();
        }
        return responseContent;
    }
}
