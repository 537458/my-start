package com.petshop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.petshop.entity.CommunityPost;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CommunityPostMapper extends BaseMapper<CommunityPost> {

    /** 点赞数原子增减；GREATEST 兜底，避免并发下减成负数 */
    @Update("UPDATE community_posts SET like_count = GREATEST(like_count + #{delta}, 0) WHERE id = #{postId}")
    int adjustLikeCount(@Param("postId") Integer postId, @Param("delta") int delta);

    /** 评论数原子增减 */
    @Update("UPDATE community_posts SET comment_count = GREATEST(comment_count + #{delta}, 0) WHERE id = #{postId}")
    int adjustCommentCount(@Param("postId") Integer postId, @Param("delta") int delta);
}
