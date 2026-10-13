package com.petshop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.petshop.entity.Coupon;
import com.petshop.entity.UserCoupon;
import com.petshop.exception.BusinessException;
import com.petshop.mapper.CouponMapper;
import com.petshop.mapper.UserCouponMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 优惠券领取、查询与抵扣计算。
 * 抵扣金额一律由服务端按券规则重算，不信任客户端传入的金额。
 */
@Service
public class CouponService {

    private static final Logger log = LoggerFactory.getLogger(CouponService.class);

    @Autowired
    private CouponMapper couponMapper;

    @Autowired
    private UserCouponMapper userCouponMapper;

    /** 可领取的券模板（含用户是否已领取） */
    public List<Map<String, Object>> listTemplates(Integer userId) {
        LambdaQueryWrapper<Coupon> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Coupon::getStatus, 1);
        wrapper.orderByAsc(Coupon::getMinAmount);
        List<Coupon> coupons = couponMapper.selectList(wrapper);

        Map<Integer, UserCoupon> owned = new HashMap<>();
        if (userId != null) {
            for (UserCoupon uc : userCouponMapper.selectList(
                    new LambdaQueryWrapper<UserCoupon>().eq(UserCoupon::getUserId, userId))) {
                owned.put(uc.getCouponId(), uc);
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Coupon coupon : coupons) {
            result.add(toCouponView(coupon, owned.get(coupon.getId())));
        }
        return result;
    }

    /** 用户已领取的券，status 为空时返回全部 */
    public List<Map<String, Object>> listMyCoupons(Integer userId, String status) {
        LambdaQueryWrapper<UserCoupon> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserCoupon::getUserId, userId);
        if (status != null && !status.isEmpty()) {
            wrapper.eq(UserCoupon::getStatus, status);
        }
        wrapper.orderByDesc(UserCoupon::getReceivedAt);
        List<UserCoupon> mine = userCouponMapper.selectList(wrapper);

        List<Map<String, Object>> result = new ArrayList<>();
        for (UserCoupon uc : mine) {
            Coupon coupon = couponMapper.selectById(uc.getCouponId());
            if (coupon == null) {
                continue;
            }
            result.add(toCouponView(coupon, uc));
        }
        return result;
    }

    /**
     * 领取优惠券。
     * 「查重 → 加计数 → 插入记录」三步必须在同一个事务里，且先插入后扣库存：
     * 并发重复领取时由 uk_user_coupon 唯一索引拦住后到的那一次，
     * 扣库存失败抛异常会把已插入的记录一起回滚，不会出现「券没领到但库存少了」。
     */
    @Transactional(rollbackFor = Exception.class)
    public void receive(Integer userId, Integer couponId) {
        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null || coupon.getStatus() == null || coupon.getStatus() != 1) {
            throw new BusinessException("优惠券不存在或已下架");
        }
        if (isExpired(coupon)) {
            throw new BusinessException("优惠券已过期");
        }
        // 先做一次轻量检查，给用户友好提示；真正的并发互斥靠唯一索引
        Long exists = userCouponMapper.selectCount(new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getCouponId, couponId));
        if (exists != null && exists > 0) {
            throw new BusinessException("您已领取过该优惠券");
        }

        UserCoupon userCoupon = new UserCoupon();
        userCoupon.setUserId(userId);
        userCoupon.setCouponId(couponId);
        userCoupon.setStatus(UserCoupon.STATUS_UNUSED);
        userCoupon.setReceivedAt(LocalDateTime.now());
        try {
            userCouponMapper.insert(userCoupon);
        } catch (DuplicateKeyException e) {
            // 并发场景下后到的请求会撞唯一索引
            throw new BusinessException("您已领取过该优惠券");
        }

        if (couponMapper.increaseReceived(couponId) == 0) {
            // 抛异常回滚前面插入的领取记录，库存不会被无故占用
            throw new BusinessException("优惠券已被领完");
        }
        log.info("领券成功: userId={}, couponId={}", userId, couponId);
    }

    /**
     * 计算某张券对给定金额的抵扣额，不可用时抛业务异常
     */
    public BigDecimal computeDiscount(Integer userCouponId, Integer userId, BigDecimal amount) {
        if (userCouponId == null) {
            return BigDecimal.ZERO;
        }
        UserCoupon userCoupon = userCouponMapper.selectById(userCouponId);
        if (userCoupon == null || !userCoupon.getUserId().equals(userId)) {
            throw new BusinessException("优惠券不存在");
        }
        if (!UserCoupon.STATUS_UNUSED.equals(userCoupon.getStatus())) {
            throw new BusinessException("该优惠券已使用或已失效");
        }
        Coupon coupon = couponMapper.selectById(userCoupon.getCouponId());
        if (coupon == null) {
            throw new BusinessException("优惠券不存在");
        }
        if (isExpired(coupon)) {
            throw new BusinessException("优惠券已过期");
        }
        if (coupon.getMinAmount() != null && amount.compareTo(coupon.getMinAmount()) < 0) {
            throw new BusinessException("订单金额未达到使用门槛");
        }
        return discountOf(coupon, amount);
    }

    /** 核销：并发下只有一张券能被占用成功 */
    public void markUsed(Integer userCouponId, Integer userId, Integer orderId) {
        if (userCouponId == null) {
            return;
        }
        if (userCouponMapper.markUsed(userCouponId, userId, orderId) == 0) {
            throw new BusinessException("优惠券核销失败，请重新选择");
        }
    }

    /** 订单取消时把券退还给用户 */
    public void releaseByOrderId(Integer orderId) {
        int released = userCouponMapper.releaseByOrderId(orderId);
        if (released > 0) {
            log.info("订单取消，优惠券已退回: orderId={}", orderId);
        }
    }

    /**
     * 日终任务：把已过 end_time 但仍为 UNUSED 的券批量置为 EXPIRED。
     * 每天 03:10 执行，避免过期券长期停留在“未使用”状态。
     */
    @Scheduled(cron = "0 10 3 * * ?")
    public void expireOverdueCoupons() {
        try {
            int expired = userCouponMapper.expireOverdue();
            if (expired > 0) {
                log.info("日终优惠券过期处理完成，置为 EXPIRED 的券: {} 张", expired);
            } else {
                log.debug("日终优惠券过期处理：无待过期券");
            }
        } catch (Exception e) {
            // 定时任务不能抛异常，否则后续调度可能被中断
            log.error("日终优惠券过期处理失败", e);
        }
    }

    private BigDecimal discountOf(Coupon coupon, BigDecimal amount) {
        BigDecimal discount = BigDecimal.ZERO;
        if (Coupon.TYPE_DISCOUNT.equals(coupon.getType())) {
            BigDecimal rate = coupon.getDiscount() == null ? BigDecimal.ONE : coupon.getDiscount();
            discount = amount.multiply(BigDecimal.ONE.subtract(rate));
        } else {
            discount = coupon.getAmount() == null ? BigDecimal.ZERO : coupon.getAmount();
        }
        discount = discount.setScale(2, RoundingMode.HALF_UP);
        if (discount.compareTo(amount) > 0) {
            return amount;
        }
        return discount;
    }

    private boolean isExpired(Coupon coupon) {
        LocalDateTime now = LocalDateTime.now();
        if (coupon.getStartTime() != null && now.isBefore(coupon.getStartTime())) {
            return true;
        }
        return coupon.getEndTime() != null && now.isAfter(coupon.getEndTime());
    }

    /** 券的快照视图，前端只用这一个结构展示 */
    private Map<String, Object> toCouponView(Coupon coupon, UserCoupon userCoupon) {
        Map<String, Object> view = new java.util.LinkedHashMap<>();
        view.put("id", coupon.getId());
        view.put("name", coupon.getName());
        view.put("type", coupon.getType());
        view.put("amount", coupon.getAmount());
        view.put("discount", coupon.getDiscount());
        view.put("minAmount", coupon.getMinAmount());
        view.put("description", buildDescription(coupon));
        view.put("endTime", coupon.getEndTime() != null ? coupon.getEndTime().toString() : "");
        view.put("status", userCoupon != null ? userCoupon.getStatus() : "AVAILABLE");
        view.put("userCouponId", userCoupon != null ? userCoupon.getId() : null);
        view.put("received", userCoupon != null);
        return view;
    }

    private String buildDescription(Coupon coupon) {
        if (Coupon.TYPE_DISCOUNT.equals(coupon.getType())) {
            BigDecimal rate = coupon.getDiscount() == null ? BigDecimal.ONE : coupon.getDiscount();
            String zhe = rate.multiply(BigDecimal.TEN).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
            return "满" + plain(coupon.getMinAmount()) + "元可用 · 全场" + zhe + "折";
        }
        return "满" + plain(coupon.getMinAmount()) + "元减" + plain(coupon.getAmount()) + "元";
    }

    private String plain(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        return value.stripTrailingZeros().toPlainString();
    }

    /** 供下单页筛选：当前金额下可用的券 */
    public List<Map<String, Object>> listUsable(Integer userId, BigDecimal amount) {
        if (amount == null) {
            amount = BigDecimal.ZERO;
        }
        LambdaQueryWrapper<UserCoupon> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserCoupon::getUserId, userId);
        wrapper.eq(UserCoupon::getStatus, UserCoupon.STATUS_UNUSED);
        List<UserCoupon> mine = userCouponMapper.selectList(wrapper);

        List<Map<String, Object>> result = new ArrayList<>();
        for (UserCoupon uc : mine) {
            Coupon coupon = couponMapper.selectById(uc.getCouponId());
            if (coupon == null || isExpired(coupon)) {
                continue;
            }
            BigDecimal min = coupon.getMinAmount() == null ? BigDecimal.ZERO : coupon.getMinAmount();
            if (amount.compareTo(min) < 0) {
                continue;
            }
            Map<String, Object> view = toCouponView(coupon, uc);
            view.put("discountAmount", discountOf(coupon, amount));
            result.add(view);
        }
        result.sort((a, b) -> ((BigDecimal) b.get("discountAmount")).compareTo((BigDecimal) a.get("discountAmount")));
        return result.isEmpty() ? Collections.emptyList() : result;
    }
}
