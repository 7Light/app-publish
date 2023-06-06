package com.huawei;

import org.jasypt.util.text.BasicTextEncryptor;

public class test {
    public static void main(String[] args) {
        BasicTextEncryptor textEncryptor = new BasicTextEncryptor();
        //加密所需的salt(盐)
        textEncryptor.setPassword("nmZ3Ox5fsEYmNhUQa8ePKg");
        //textEncryptor.setPassword("WKae7m+KX6VDiwpazHXC13xCcwFsZrE52GzFuYWaw9eK8YHNvMdAZfa7v1FcHrqIkPTkQKooO68=");
        //要加密的数据（数据库的用户名或密码）
        //String username = textEncryptor.encrypt("majun.platform.release.verification.service.token");
        String username = textEncryptor.encrypt("uPh95Rmnc9DNbrfzGBHSXFezTroUiJMeUVqmwx1bER80lzP+Vi3xw3gtfeQm6TjoOT692x+0zYoOBXQtOwWQLQ==");
        System.out.println("username:"+username);
    }
}
