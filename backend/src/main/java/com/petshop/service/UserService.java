package com.petshop.service;

import com.petshop.common.Response;
import com.petshop.entity.User;

import java.util.List;

public interface UserService {
    User registerUser(User user);
    User login(String username, String password);
    Response<Void> register(User user);
    Response<String> loginByPhone(String phone);
    User findById(Integer id);
    User update(User user);
    void delete(Integer id);
    List<User> findAll();
    boolean updatePassword(Integer userId, String oldPassword, String newPassword);
    Response<Void> updatePassword(Integer userId, String newPassword);
    User getUserByPhone(String phone);

    /** 统计同一手机号下的账号数，重号时禁止走短信找回 */
    long countUsersByPhone(String phone);

    /**
     * 判断某个唯一字段的新值是否已被别的账号占用。
     * 适用于修改资料时提前给出友好提示，而不是让数据库唯一索引报 500。
     *
     * @param column    只支持 username / phone / email
     * @param excludeId 本次更新的目标用户，自身不算冲突
     */
    boolean isTakenByOther(String column, String value, Integer excludeId);
    /** 清除头像，恢复系统默认头像 */
    boolean resetAvatar(Integer userId);
}