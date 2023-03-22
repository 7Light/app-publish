package com.huawei.publish.utils;

import org.jasypt.util.text.BasicTextEncryptor;

public class test {
    public static void main(String[] args) {
        BasicTextEncryptor textEncryptor = new BasicTextEncryptor();
        //加密所需的salt(盐)
        textEncryptor.setPassword("nmZ3Ox5fsEYmNhUQa8ePKg");
        //要加密的数据（数据库的用户名或密码）
        String username = textEncryptor.encrypt("platform.release.verification.service.token");
        String password = textEncryptor.encrypt("root123");
        System.out.println("username:"+username);
        System.out.println("password:"+password);
    }
}
