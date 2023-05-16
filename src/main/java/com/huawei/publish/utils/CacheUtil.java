package com.huawei.publish.utils;

import org.apache.log4j.Logger;

import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 基于ConcurrentHashMap的本地缓存工具类
 * 缓存删除基于timer定时器
 *
 * @author chentao
 */
public class CacheUtil {
    private static final Logger log = Logger.getLogger(CacheUtil.class);
    /**
     * 默认大小
     */
    private static final int DEFAULT_CAPACITY = 1024;

    /**
     *  最大缓存大小
     */
    private static final int MAX_CAPACITY = 10000;

    /**
     * 存储缓存的Map
     */
    private static final ConcurrentHashMap<String, Object> CACHE_DATA;

    private static final ScheduledExecutorService executorService;

    static {
        CACHE_DATA = new ConcurrentHashMap<>(DEFAULT_CAPACITY);
        executorService = new ScheduledThreadPoolExecutor(2);
    }

    /**
     * 私有化构造方法
     */
    private CacheUtil() {

    }

    /**
     * 设置缓存过期时间
     *
     * @param key 缓存key
     * @param timeOut 过期时间
     */
    public static void setCacheExpiration(String key, int timeOut) {
        //定时器 调度任务，用于根据 时间 定时清除 对应key 缓存
        executorService.schedule(new TimerTask() {
            @Override
            public void run() {
                remove(key);
            }
        }, timeOut, TimeUnit.SECONDS);
    }

    /**
     * 增加缓存
     *
     * @param key key
     * @param value value
     */
    public static void put(String key, Object value) {
        if (checkCapacity()) {
            CACHE_DATA.put(key, value);
        }
    }

    /**
     *     判断容量大小
     */
    public static boolean checkCapacity() {
        return CACHE_DATA.size() < MAX_CAPACITY;
    }


    /**
     *     删除缓存
     */
    public static void remove(String key) {
        CACHE_DATA.remove(key);
        log.info("key: "+ key +" Cache has expired !");
    }

    /**
     *     获取缓存
     */
    public static Object get(String key) {
        return CACHE_DATA.get(key);
    }

    /**
     *     是否包含某个缓存
     */
    public static boolean isContain(String key) {
        return CACHE_DATA.contains(key);
    }
}
