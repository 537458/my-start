package com.petshop.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.petshop.entity.Order;
import com.petshop.mapper.OrderMapper;
import com.petshop.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class OrderScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderScheduler.class);

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderService orderService;

    @Scheduled(fixedRate = 10000)
    public void cancelExpiredOrders() {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getStatus, "待付款");
        wrapper.le(Order::getExpiresAt, LocalDateTime.now());
        List<Order> expiredOrders = orderMapper.selectList(wrapper);

        for (Order order : expiredOrders) {
            try {
                // 走订单服务取消，避免绕过状态校验，同时返还下单时占用的库存
                orderService.cancel(order.getId());
                log.info("订单已自动取消并返还库存: {}", order.getOrderNo());
            } catch (Exception e) {
                log.warn("订单自动取消失败: orderId={}, reason={}", order.getId(), e.getMessage());
            }
        }
    }

    /**
     * 自营商品（未关联商家）支付后自动进入配送状态。
     * 关联了商家的订单仍由商家调用 /orders/{id}/ship 发货。
     * 支付后保留 5 分钟的「待发货」窗口，否则订单会被秒级推送成已发货，
     * 用户几乎看不到待发货状态。
     */
    @Scheduled(fixedRate = 30000)
    public void autoShipSelfOperatedOrders() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(5);
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getStatus, "已付款");
        wrapper.isNull(Order::getMerchantId);
        // 正常订单看 updatedAt；历史数据 updatedAt 为空时退回看 createdAt，
        // 否则这些订单会永远停在「已付款」
        wrapper.and(w -> w.isNull(Order::getUpdatedAt).le(Order::getCreatedAt, threshold)
                .or()
                .le(Order::getUpdatedAt, threshold));
        List<Order> orders = orderMapper.selectList(wrapper);

        for (Order order : orders) {
            try {
                orderService.ship(order.getId());
                log.info("自营订单已自动发货: {}", order.getOrderNo());
            } catch (Exception e) {
                log.warn("订单自动发货失败: orderId={}, reason={}", order.getId(), e.getMessage());
            }
        }
    }
}
