package com.petshop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.petshop.entity.Coupon;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CouponMapper extends BaseMapper<Coupon> {

    /** 领券计数自增，总量为 0 表示不限量 */
    @Update("UPDATE coupons SET received = received + 1 WHERE id = #{id} AND (total = 0 OR received < total)")
    int increaseReceived(@Param("id") Integer id);
}
