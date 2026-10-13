package com.petshop.service;

import com.petshop.entity.Order;

import java.util.List;

public interface OrderService {
    Order create(Order order);
    Order findById(Integer id);
    Order update(Order order);
    void delete(Integer id);
    List<Order> findByUserId(Integer userId);

    /** 支付订单：待付款 -> 已付款，并记录支付方式与支付时间 */
    Order pay(Integer orderId, String payMethod);
    Order cancel(Integer orderId);

    /** 商家发货：已付款 -> 已发货 */
    Order ship(Integer orderId);

    /** 买家确认收货：已发货 -> 已完成 */
    Order confirmReceipt(Integer orderId);

    /** 判断订单是否已全部评价 */
    boolean isReviewed(Integer orderId);
}