package com.huawei.publish.utils;

import org.jasypt.util.text.BasicTextEncryptor;

/**
 * 加解密
 *
 * @author chentao
 * @since: 2023/3/17 14:08
 */
public class SecurityUtil {
    /**
     * 解密
     *
     * @param data 待解密数据
     * @return 解密后的数据
     */
    public static String decrypt(String data){
        BasicTextEncryptor basicTextEncryptor = new BasicTextEncryptor();
        basicTextEncryptor.setPassword(System.getenv("jasypt.encryptor.password"));
        return basicTextEncryptor.decrypt(data);
    }

    /**
     * 加密
     *
     * @param data 待加密数据
     * @return 加密后的数据
     */
    public static String encrypt(String data){
        BasicTextEncryptor basicTextEncryptor=new BasicTextEncryptor();
        basicTextEncryptor.setPassword(System.getenv("jasypt.encryptor.password"));
        return basicTextEncryptor.encrypt(data);
    }
}
