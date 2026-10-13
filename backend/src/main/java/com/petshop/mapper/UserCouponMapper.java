package com.petshop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.petshop.entity.UserCoupon;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserCouponMapper extends BaseMapper<UserCoupon> {

    /**
     * 核销优惠券：只有未使用的券才能被占用，避免并发重复抵扣
     */
    @Update("UPDATE user_coupons SET status = 'USED', order_id = #{orderId}, used_at = NOW() "
            + "WHERE id = #{id} AND user_id = #{userId} AND status = 'UNUSED'")
    int markUsed(@Param("id") Integer id, @Param("userId") Integer userId, @Param("orderId") Integer orderId);

    /**
     * 订单取消时退回优惠券
     */
    @Update("UPDATE user_coupons SET status = 'UNUSED', order_id = NULL, used_at = NULL "
            + "WHERE order_id = #{orderId} AND status = 'USED'")
    int releaseByOrderId(@Param("orderId") Integer orderId);

    /**
     * 批量过期：关联券模板判断 end_time，把已过期但仍为 UNUSED 的券置为 EXPIRED
     */
    @Update("UPDATE user_coupons uc JOIN coupons c ON uc.coupon_id = c.id "
            + "SET uc.status = 'EXPIRED' "
            + "WHERE uc.status = 'UNUSED' AND c.end_time IS NOT NULL AND c.end_time < NOW()")
    int expireOverdue();
}
