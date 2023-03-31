package com.huawei.publish.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 发布线程池配置类
 *
 * @author chentao
 * @since: 2022-04-22 15:54
 */
@Configuration
@EnableAsync
public class ThreadPoolConfig {
    private static final int CORE_POOL_SIZE = 10;

    private static final int MAX_POOL_SIZE = 20;

    /**
     * 允许线程空闲时间（单位：默认为秒）
     */
    private static final int KEEP_ALIVE_TIME = 30 * 60;

    private static final int QUEUE_CAPACITY = 100;

    /**
     * 线程名前缀
     */
    private static final String THREAD_NAME_PREFIX = "publish-task-thread";

    /**
     * 漏洞线程池
     *
     * @return ThreadPoolTaskExecutor 线程池对象
     */
    @Bean("publishTaskExecutor")
    public ThreadPoolTaskExecutor publishTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(MAX_POOL_SIZE);
        executor.setKeepAliveSeconds(KEEP_ALIVE_TIME);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setThreadNamePrefix(THREAD_NAME_PREFIX);
        // 线程池对拒绝任务的处理策略
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}