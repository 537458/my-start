package com.petshop.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.pagehelper.PageHelper;
import com.petshop.common.Response;
import com.petshop.entity.Order;
import com.petshop.entity.OrderItem;
import com.petshop.entity.OrderReview;
import com.petshop.entity.User;
import com.petshop.mapper.OrderItemMapper;
import com.petshop.mapper.OrderReviewMapper;
import com.petshop.service.OrderService;
import com.petshop.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 商品评价接口。
 * 评价在订单完成后提交，一单只评价一次，按商品维度落库（order_reviews）。
 */
@RestController
@RequestMapping("/reviews")
public class ReviewController {

    private static final Logger log = LoggerFactory.getLogger(ReviewController.class);

    /** 只有已完成的订单可以评价 */
    private static final String STATUS_COMPLETED = "已完成";

    @Autowired
    private OrderReviewMapper orderReviewMapper;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Autowired
    private UserService userService;

    public static class ReviewItemRequest {
        private Integer productId;
        private Integer rating;

        public Integer getProductId() { return productId; }
        public void setProductId(Integer productId) { this.productId = productId; }
        public Integer getRating() { return rating; }
        public void setRating(Integer rating) { this.rating = rating; }
    }

    public static class CreateReviewRequest {
        private Integer orderId;
        private String content;
        private List<ReviewItemRequest> items;

        public Integer getOrderId() { return orderId; }
        public void setOrderId(Integer orderId) { this.orderId = orderId; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public List<ReviewItemRequest> getItems() { return items; }
        public void setItems(List<ReviewItemRequest> items) { this.items = items; }
    }

    public static class ReviewResponse {
        private Integer id;
        private Integer productId;
        private String userName;
        private Integer rating;
        private String content;
        private String createdAt;

        public Integer getId() { return id; }
        public void setId(Integer id) { this.id = id; }
        public Integer getProductId() { return productId; }
        public void setProductId(Integer productId) { this.productId = productId; }
        public String getUserName() { return userName; }
        public void setUserName(String userName) { this.userName = userName; }
        public Integer getRating() { return rating; }
        public void setRating(Integer rating) { this.rating = rating; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    }

    public static class ReviewSummary {
        private double avgRating;
        private int count;

        public double getAvgRating() { return avgRating; }
        public void setAvgRating(double avgRating) { this.avgRating = avgRating; }
        public int getCount() { return count; }
        public void setCount(int count) { this.count = count; }
    }

    /**
     * 提交订单评价
     */
    @PostMapping
    @Transactional
    public Response<Void> createReview(@RequestBody CreateReviewRequest request, Authentication authentication) {
        Integer userId = resolveUserId(authentication);
        if (userId == null) {
            return Response.error(401, "请先登录");
        }
        if (request == null || request.getOrderId() == null) {
            return Response.error(400, "订单信息缺失");
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            return Response.error(400, "请至少评价一个商品");
        }

        Order order = orderService.findById(request.getOrderId());
        if (order == null || !order.getUserId().equals(userId)) {
            return Response.error(404, "订单不存在");
        }
        if (!STATUS_COMPLETED.equals(order.getStatus())) {
            return Response.error(400, "订单完成后才能评价");
        }
        if (orderService.isReviewed(order.getId())) {
            return Response.error(400, "该订单已评价，请勿重复提交");
        }

        List<OrderReview> toInsert = new ArrayList<>();
        // 只能评价本单实际买过的商品，否则任何人都能给任意商品刷好评
        Set<Integer> orderProductIds = new HashSet<>();
        LambdaQueryWrapper<OrderItem> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.eq(OrderItem::getOrderId, order.getId());
        for (OrderItem orderItem : orderItemMapper.selectList(itemWrapper)) {
            if (orderItem.getProductId() != null) {
                orderProductIds.add(orderItem.getProductId());
            }
        }
        for (ReviewItemRequest item : request.getItems()) {
            if (item.getProductId() == null) {
                continue;
            }
            if (!orderProductIds.contains(item.getProductId())) {
                log.warn("评价商品不属于该订单: orderId={}, productId={}", order.getId(), item.getProductId());
                return Response.error(400, "评价的商品不在此订单中");
            }
            int rating = item.getRating() == null ? 5 : item.getRating();
            if (rating < 1 || rating > 5) {
                return Response.error(400, "评分必须在 1-5 星之间");
            }
            OrderReview review = new OrderReview();
            review.setOrderId(order.getId());
            review.setUserId(userId);
            review.setProductId(item.getProductId());
            review.setRating(rating);
            review.setContent(request.getContent());
            review.setCreatedAt(LocalDateTime.now());
            review.setUpdatedAt(LocalDateTime.now());
            toInsert.add(review);
        }
        if (toInsert.isEmpty()) {
            return Response.error(400, "没有可评价的商品");
        }

        for (OrderReview review : toInsert) {
            orderReviewMapper.insert(review);
        }
        log.info("订单评价成功: orderId={}, userId={}, 评价商品数={}", order.getId(), userId, toInsert.size());
        return Response.success("评价成功", null);
    }

    /**
     * 商品评价列表，按页返回，客户端滑到底部再取下一页
     */
    @GetMapping("/product/{productId}")
    public Response<List<ReviewResponse>> getProductReviews(@PathVariable Integer productId,
                                                            @RequestParam(defaultValue = "1") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);
        LambdaQueryWrapper<OrderReview> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderReview::getProductId, productId);
        wrapper.orderByDesc(OrderReview::getCreatedAt);
        // 同一秒内多条评价按 id 兜底排序，保证翻页不重不漏
        wrapper.orderByDesc(OrderReview::getId);
        PageHelper.startPage(safePage, safeSize);
        List<OrderReview> reviews = orderReviewMapper.selectList(wrapper);

        List<ReviewResponse> result = new ArrayList<>();
        for (OrderReview review : reviews) {
            ReviewResponse resp = new ReviewResponse();
            resp.setId(review.getId());
            resp.setProductId(review.getProductId());
            resp.setRating(review.getRating());
            resp.setContent(review.getContent());
            resp.setCreatedAt(review.getCreatedAt() != null ? review.getCreatedAt().toString() : "");
            resp.setUserName(buildMaskedUserName(review.getUserId()));
            result.add(resp);
        }
        return Response.success(result);
    }

    /**
     * 商品评分汇总（评分 + 评价数）
     */
    @GetMapping("/product/{productId}/summary")
    public Response<ReviewSummary> getProductSummary(@PathVariable Integer productId) {
        LambdaQueryWrapper<OrderReview> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderReview::getProductId, productId);
        List<OrderReview> reviews = orderReviewMapper.selectList(wrapper);

        ReviewSummary summary = new ReviewSummary();
        summary.setCount(reviews.size());
        if (!reviews.isEmpty()) {
            int total = 0;
            for (OrderReview review : reviews) {
                total += review.getRating() == null ? 0 : review.getRating();
            }
            summary.setAvgRating(Math.round(total * 10.0 / reviews.size()) / 10.0);
        }
        return Response.success(summary);
    }

    /**
     * 订单评价记录（用于判断订单是否已评价）
     */
    @GetMapping("/order/{orderId}")
    public Response<List<ReviewResponse>> getOrderReviews(@PathVariable Integer orderId, Authentication authentication) {
        Integer userId = resolveUserId(authentication);
        if (userId == null) {
            return Response.error(401, "请先登录");
        }
        Order order = orderService.findById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            return Response.error(404, "订单不存在");
        }
        LambdaQueryWrapper<OrderReview> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderReview::getOrderId, orderId);
        List<OrderReview> reviews = orderReviewMapper.selectList(wrapper);

        List<ReviewResponse> result = new ArrayList<>();
        for (OrderReview review : reviews) {
            ReviewResponse resp = new ReviewResponse();
            resp.setId(review.getId());
            resp.setProductId(review.getProductId());
            resp.setRating(review.getRating());
            resp.setContent(review.getContent());
            resp.setCreatedAt(review.getCreatedAt() != null ? review.getCreatedAt().toString() : "");
            result.add(resp);
        }
        return Response.success(result);
    }

    /**
     * 用户名脱敏展示：保留首字符，其余用 * 代替
     */
    private String buildMaskedUserName(Integer userId) {
        if (userId == null) {
            return "匿名用户";
        }
        User user = userService.findById(userId);
        String name = user != null ? user.getUsername() : null;
        if (name == null || name.isEmpty()) {
            return "匿名用户";
        }
        if (name.length() == 1) {
            return name + "***";
        }
        return name.charAt(0) + "***";
    }

    private Integer resolveUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        try {
            if (principal instanceof Integer) {
                return (Integer) principal;
            }
            if (principal instanceof String) {
                return Integer.parseInt((String) principal);
            }
        } catch (NumberFormatException e) {
            log.error("解析用户ID失败", e);
        }
        return null;
    }
}
