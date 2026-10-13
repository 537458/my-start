package com.petshop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.petshop.entity.Notification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {

    /** 消息条数超过上限的用户，清理任务据此按用户裁剪 */
    @Select("SELECT user_id FROM notifications GROUP BY user_id HAVING COUNT(*) > #{limit}")
    List<Integer> selectUserIdsOverLimit(@Param("limit") int limit);

    /** 某用户保留前 offset 条之后的消息 id，只取一批，避免一次拉全表 */
    @Select("SELECT id FROM notifications WHERE user_id = #{userId} ORDER BY id DESC LIMIT #{offset}, #{count}")
    List<Integer> selectIdsBeyond(@Param("userId") Integer userId,
                                  @Param("offset") int offset,
                                  @Param("count") int count);
}
