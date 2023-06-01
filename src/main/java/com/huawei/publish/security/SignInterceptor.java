package com.huawei.publish.security;

import com.huawei.publish.enums.AppConst;
import com.huawei.publish.utils.SecurityUtil;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author chentao
 */
@Component
public class SignInterceptor implements HandlerInterceptor {
    private static final Logger log = LoggerFactory.getLogger(SignInterceptor.class);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        boolean handleResult = false;
        String accessToken =request.getHeader(AppConst.ACCESS_TOKEN);
        String environmentVariable = System.getenv(AppConst.ACCESS_TOKEN);
        if (StringUtils.isNotBlank(accessToken) && StringUtils.isNotBlank(environmentVariable)) {
           handleResult = SecurityUtil.decrypt(environmentVariable).equals(SecurityUtil.decrypt(accessToken));
        }
        if (!handleResult) {
            log.error("Signature authentication failed");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Signature authentication failed");
        }
        return handleResult;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {

    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {

    }
}
