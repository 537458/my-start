package com.petshop.controller;

import com.petshop.common.Response;
import com.petshop.exception.BusinessException;
import com.petshop.service.CouponService;
import com.petshop.util.AuthUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 优惠券：券模板、领券、我的券、下单可用券
 */
@RestController
@RequestMapping("/coupons")
public class CouponController {

    private static final Logger log = LoggerFactory.getLogger(CouponController.class);

    @Autowired
    private CouponService couponService;

    /** 可领取的优惠券列表（未登录也能浏览） */
    @GetMapping("/templates")
    public Response<List<Map<String, Object>>> templates(Authentication authentication) {
        return Response.success(couponService.listTemplates(AuthUtils.resolveUserId(authentication)));
    }

    /** 领取优惠券 */
    @PostMapping("/{couponId}/receive")
    public Response<Void> receive(@PathVariable Integer couponId, Authentication authentication) {
        Integer userId = AuthUtils.resolveUserId(authentication);
        if (userId == null) {
            return Response.error(401, "请先登录");
        }
        try {
            couponService.receive(userId, couponId);
            return Response.success("领取成功", null);
        } catch (BusinessException e) {
            return Response.error(400, e.getMessage());
        }
    }

    /** 我的优惠券，status 可选 UNUSED / USED */
    @GetMapping("/my")
    public Response<List<Map<String, Object>>> my(@RequestParam(required = false) String status,
                                                  Authentication authentication) {
        Integer userId = AuthUtils.resolveUserId(authentication);
        if (userId == null) {
            return Response.error(401, "请先登录");
        }
        return Response.success(couponService.listMyCoupons(userId, status));
    }

    /** 当前订单金额下可用的券（按抵扣金额从高到低） */
    @GetMapping("/usable")
    public Response<List<Map<String, Object>>> usable(@RequestParam(required = false) Double amount,
                                                      Authentication authentication) {
        Integer userId = AuthUtils.resolveUserId(authentication);
        if (userId == null) {
            return Response.error(401, "请先登录");
        }
        BigDecimal orderAmount = amount == null ? BigDecimal.ZERO : BigDecimal.valueOf(amount);
        log.info("查询可用优惠券: userId={}, amount={}", userId, orderAmount);
        return Response.success(couponService.listUsable(userId, orderAmount));
    }
}
