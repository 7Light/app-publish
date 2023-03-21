package com.huawei.publish.config;

import com.huawei.publish.security.SignInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;

/**
 * @author chentao
 */
@Configuration
@Component
public class InterceptorConfig extends WebMvcConfigurationSupport {
    @Autowired
    private SignInterceptor signInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(signInterceptor).addPathPatterns("/publish/*").excludePathPatterns("/publish/heartbeat");
        super.addInterceptors(registry);
    }
}
