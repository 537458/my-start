package com.petshop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.petshop.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<User> {
    User findByUsername(String username);
    User findByEmail(String email);

    /**
     * 按用户名模糊匹配账号 ID，供社区按作者搜索动态使用。
     * 限制条数，避免关键词过宽时把整张用户表塞进 IN 条件。
     */
    @Select("SELECT id FROM users WHERE username LIKE CONCAT('%', #{keyword}, '%') LIMIT 50")
    List<Integer> searchIdsByUsername(@Param("keyword") String keyword);

    /**
     * 清空头像。updateById 会跳过 null 字段，因此清空必须显式写 SQL。
     */
    @Update("UPDATE users SET avatar_data = NULL, avatar_type = NULL, updated_at = NOW() WHERE id = #{userId}")
    int clearAvatar(@Param("userId") Integer userId);
}