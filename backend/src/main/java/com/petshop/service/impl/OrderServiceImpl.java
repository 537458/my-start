package com.petshop.service.impl;

import com.petshop.entity.Order;
import com.petshop.entity.OrderItem;
import com.petshop.entity.OrderReview;
import com.petshop.entity.User;
import com.petshop.entity.Product;
import com.petshop.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.petshop.common.OrderNoGenerator;
import com.petshop.mapper.OrderItemMapper;
import com.petshop.mapper.OrderMapper;
import com.petshop.mapper.OrderReviewMapper;
import com.petshop.mapper.ProductMapper;
import com.petshop.mapper.UserCouponMapper;
import com.petshop.service.NotificationService;
import com.petshop.service.OrderService;
import com.petshop.service.UserService;
import com.petshop.service.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class OrderServiceImpl implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    /** 待付款 */
    private static final String STATUS_PENDING_PAYMENT = "待付款";
    /** 已付款（待发货） */
    private static final String STATUS_PAID = "已付款";
    /** 已发货（待收货） */
    private static final String STATUS_SHIPPED = "已发货";
    /** 已完成 */
    private static final String STATUS_COMPLETED = "已完成";
    /** 已取消 */
    private static final String STATUS_CANCELLED = "已取消";

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Autowired
    private OrderReviewMapper orderReviewMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private UserCouponMapper userCouponMapper;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private UserService userService;

    @Autowired
    private ProductService productService;

    @Override
    public Order create(Order order) {
        // 订单号：毫秒时间 + 随机后缀，避免同秒同金额撞唯一键
        order.setOrderNo(OrderNoGenerator.generate());
        order.setStatus("待付款");
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        // 从user表查询并填充user_name
        if (order.getUserId() != null) {
            User user = userService.findById(order.getUserId());
            if (user != null) {
                order.setUserName(user.getUsername());
            }
        }

        // 从product表查询并填充product_name（这里假设订单中包含商品ID，实际需要根据订单项来处理）
        // 注意：实际业务中，订单可能包含多个商品，这里需要根据具体业务逻辑处理

        orderMapper.insert(order);
        return order;
    }

    @Override
    public Order findById(Integer id) {
        return orderMapper.selectById(id);
    }

    @Override
    public Order update(Order order) {
        order.setUpdatedAt(LocalDateTime.now());
        orderMapper.updateById(order);
        return order;
    }

    @Override
    @Transactional
    public void delete(Integer id) {
        // 订单明细一并删除，避免残留无法关联到任何订单的孤儿记录
        LambdaQueryWrapper<OrderItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderItem::getOrderId, id);
        orderItemMapper.delete(wrapper);
        orderMapper.deleteById(id);
        log.info("订单及其明细已删除: orderId={}", id);
    }

    @Override
    public List<Order> findByUserId(Integer userId) {
        return orderMapper.findByUserId(userId);
    }

    @Override
    public Order pay(Integer orderId, String payMethod) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        // 条件更新：只有仍处于「待付款」才能改成「已付款」
        // 并发支付、超时自动取消同时发生时，只有一方能改成功
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, STATUS_PENDING_PAYMENT)
                .set(Order::getStatus, STATUS_PAID)
                .set(payMethod != null, Order::getPayMethod, payMethod)
                .set(Order::getPaidAt, now)
                .set(Order::getUpdatedAt, now));
        if (updated == 0) {
            Order latest = orderMapper.selectById(orderId);
            if (latest != null && STATUS_PAID.equals(latest.getStatus())) {
                throw new BusinessException("订单已支付，请勿重复操作");
            }
            throw new BusinessException("订单状态已变更，请刷新后重试");
        }
        order.setStatus(STATUS_PAID);
        order.setPayMethod(payMethod != null ? payMethod : order.getPayMethod());
        order.setPaidAt(now);
        order.setUpdatedAt(now);
        notificationService.notifyOrder(order.getUserId(), "订单支付成功",
                "订单 " + order.getOrderNo() + " 已支付，我们会尽快为您发货", order.getId());
        return order;
    }

    @Override
    @Transactional
    public Order cancel(Integer orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        // 条件更新：只有抢到「待付款 → 已取消」的那一次才退还库存与优惠券
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, STATUS_PENDING_PAYMENT)
                .set(Order::getStatus, STATUS_CANCELLED)
                .set(Order::getUpdatedAt, now));
        if (updated == 0) {
            Order latest = orderMapper.selectById(orderId);
            if (latest != null && STATUS_CANCELLED.equals(latest.getStatus())) {
                // 已经取消过（例如定时任务先抢到），直接返回，绝不能再返还一次库存/优惠券
                return latest;
            }
            throw new BusinessException("订单状态不正确，只有待付款订单才能取消");
        }
        order.setStatus(STATUS_CANCELLED);
        order.setUpdatedAt(now);

        // 下单时已扣减库存，取消必须返还，否则库存只减不回
        restoreStock(order.getId());
        // 已占用的优惠券归还给用户
        userCouponMapper.releaseByOrderId(order.getId());
        notificationService.notifyOrder(order.getUserId(), "订单已取消",
                "订单 " + order.getOrderNo() + " 已取消，库存与优惠券已退回", order.getId());
        return order;
    }

    /**
     * 将订单内所有商品的库存按购买数量返还
     */
    private void restoreStock(Integer orderId) {
        LambdaQueryWrapper<OrderItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderItem::getOrderId, orderId);
        List<OrderItem> items = orderItemMapper.selectList(wrapper);
        for (OrderItem item : items) {
            if (item.getProductId() == null || item.getQuantity() == null) {
                continue;
            }
            productMapper.restoreStock(item.getProductId(), item.getQuantity());
            log.info("库存返还成功：orderId={}, productId={}, quantity={}",
                    orderId, item.getProductId(), item.getQuantity());
        }
    }

    @Override
    public Order ship(Integer orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        // 条件更新：只有仍处于「已付款」才能改成「已发货」，避免自动发货与商家发货重复触发
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, STATUS_PAID)
                .set(Order::getStatus, STATUS_SHIPPED)
                .set(Order::getUpdatedAt, now));
        if (updated == 0) {
            Order latest = orderMapper.selectById(orderId);
            if (latest != null && (STATUS_SHIPPED.equals(latest.getStatus())
                    || STATUS_COMPLETED.equals(latest.getStatus()))) {
                // 已经发过货了，幂等返回，不重复推送通知
                return latest;
            }
            throw new BusinessException("只有已付款订单才能发货");
        }
        order.setStatus(STATUS_SHIPPED);
        order.setUpdatedAt(now);
        notificationService.notifyOrder(order.getUserId(), "订单已发货",
                "订单 " + order.getOrderNo() + " 已发货，请注意查收", order.getId());
        return order;
    }

    @Override
    public Order confirmReceipt(Integer orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        // 条件更新：只有仍处于「已发货」才能改成「已完成」
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, STATUS_SHIPPED)
                .set(Order::getStatus, STATUS_COMPLETED)
                .set(Order::getUpdatedAt, now));
        if (updated == 0) {
            Order latest = orderMapper.selectById(orderId);
            if (latest != null && STATUS_COMPLETED.equals(latest.getStatus())) {
                // 重复确认收货，幂等返回
                return latest;
            }
            throw new BusinessException("只有已发货订单才能确认收货");
        }
        order.setStatus(STATUS_COMPLETED);
        order.setUpdatedAt(now);
        notificationService.notifyOrder(order.getUserId(), "订单已完成",
                "订单 " + order.getOrderNo() + " 已确认收货，期待您的评价", order.getId());
        return order;
    }

    @Override
    public boolean isReviewed(Integer orderId) {
        LambdaQueryWrapper<OrderReview> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderReview::getOrderId, orderId);
        return orderReviewMapper.selectCount(wrapper) > 0;
    }
}