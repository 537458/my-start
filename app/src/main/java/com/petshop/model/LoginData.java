package com.petshop.model;

/**
 * 登录成功返回的数据结构 {token, user}
 */
public class LoginData {
    private String token;
    private User user;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
}
