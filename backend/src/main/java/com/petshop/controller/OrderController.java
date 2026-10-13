package com.petshop.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.petshop.common.OrderNoGenerator;
import com.petshop.common.PayMethod;
import com.petshop.common.Response;
import com.petshop.entity.Order;
import com.petshop.entity.OrderItem;
import com.petshop.entity.Product;
import com.petshop.entity.ShoppingCart;
import com.petshop.exception.BusinessException;
import com.petshop.mapper.OrderItemMapper;
import com.petshop.mapper.OrderMapper;
import com.petshop.service.OrderService;
import com.petshop.service.ProductService;
import com.petshop.service.ShoppingCartService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    /** 订单状态：待付款 */
    private static final String ORDER_STATUS_PENDING_PAYMENT = "待付款";
    /** 订单状态：已付款 */
    private static final String ORDER_STATUS_PAID = "已付款";

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Autowired
    private ShoppingCartService shoppingCartService;

    @Autowired
    private ProductService productService;

    @Autowired
    private com.petshop.service.UserService userService;

    @Autowired
    private com.petshop.mapper.ProductMapper productMapper;

    @Autowired
    private com.petshop.service.CouponService couponService;

    @Autowired
    private com.petshop.service.NotificationService notificationService;

    public static class OrderResponse {
        private Integer id;
        private Integer userId;
        private String userName;
        private String productName;
        private String orderNo;
        private Double totalPrice;
        /** 优惠券抵扣金额 */
        private Double discountAmount;
        private String status;
        private String address;
        private String phone;
        private String receiver;
        private String createdAt;
        private List<OrderItemResponse> items;
        /** 是否已评价 */
        private Boolean reviewed;
        /** 支付方式编码：WECHAT / ALIPAY / BANK / COD */
        private String payMethod;
        /** 支付方式中文名，前端直接展示 */
        private String payMethodLabel;
        /** 支付完成时间 */
        private String paidAt;

        public Integer getId() { return id; }
        public void setId(Integer id) { this.id = id; }
        public Integer getUserId() { return userId; }
        public void setUserId(Integer userId) { this.userId = userId; }
        public String getUserName() { return userName; }
        public void setUserName(String userName) { this.userName = userName; }
        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }
        public String getOrderNo() { return orderNo; }
        public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
        public Double getTotalPrice() { return totalPrice; }
        public void setTotalPrice(Double totalPrice) { this.totalPrice = totalPrice; }
        public Double getDiscountAmount() { return discountAmount; }
        public void setDiscountAmount(Double discountAmount) { this.discountAmount = discountAmount; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public String getReceiver() { return receiver; }
        public void setReceiver(String receiver) { this.receiver = receiver; }
        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
        public List<OrderItemResponse> getItems() { return items; }
        public void setItems(List<OrderItemResponse> items) { this.items = items; }
        public Boolean getReviewed() { return reviewed; }
        public void setReviewed(Boolean reviewed) { this.reviewed = reviewed; }
        public String getPayMethod() { return payMethod; }
        public void setPayMethod(String payMethod) { this.payMethod = payMethod; }
        public String getPayMethodLabel() { return payMethodLabel; }
        public void setPayMethodLabel(String payMethodLabel) { this.payMethodLabel = payMethodLabel; }
        public String getPaidAt() { return paidAt; }
        public void setPaidAt(String paidAt) { this.paidAt = paidAt; }
    }

    public static class OrderItemResponse {
        private Integer productId;
        private String productName;
        private Integer quantity;
        private Double price;
        private String imageUrl;

        public Integer getProductId() { return productId; }
        public void setProductId(Integer productId) { this.productId = productId; }
        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
        public Double getPrice() { return price; }
        public void setPrice(Double price) { this.price = price; }
        public String getImageUrl() { return imageUrl; }
        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    }
    
    /**
     * 获取商品图片URL
     * 图片通过接口按需加载
     */
    private String getProductImageUrl(Product product) {
        if (product == null) {
            return null;
        }
        return "/images/product/" + product.getId();
    }

    public static class CreateOrderRequest {
        private String receiver;
        private String phone;
        private String address;
        private List<CartItemRequest> items;

        public String getReceiver() { return receiver; }
        public void setReceiver(String receiver) { this.receiver = receiver; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
        public List<CartItemRequest> getItems() { return items; }
        public void setItems(List<CartItemRequest> items) { this.items = items; }
    }

    public static class CartItemRequest {
        private Integer productId;
        private Integer quantity;

        public Integer getProductId() { return productId; }
        public void setProductId(Integer productId) { this.productId = productId; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
    }

    @GetMapping
    public Response<List<OrderResponse>> getOrders(Authentication authentication,
                                                 @RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "10") int size,
                                                 @RequestParam(required = false) String status) {
        if (authentication == null || authentication.getPrincipal() == null) {
            log.warn("获取订单列表失败：用户未认证");
            return Response.error(401, "请先登录");
        }
        
        Integer userId;
        try {
            Object principal = authentication.getPrincipal();
            log.info("Authentication principal: {}, 类型: {}", principal, principal != null ? principal.getClass().getName() : "null");
            
            if (principal instanceof Integer) {
                userId = (Integer) principal;
            } else if (principal instanceof String) {
                userId = Integer.parseInt((String) principal);
            } else {
                log.error("获取订单列表失败：无效的principal类型 - {}", principal != null ? principal.getClass().getName() : "null");
                return Response.error(401, "用户信息无效");
            }
        } catch (NumberFormatException e) {
            log.error("获取订单列表失败：无效的userId", e);
            return Response.error(401, "用户信息无效");
        }
        
        // 分页参数兜底，避免 size 过大或为负数导致全表扫描
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);
        log.info("获取订单列表：userId={}, page={}, size={}, status={}", userId, safePage, safeSize, status);

        // 使用PageHelper进行分页查询
        com.github.pagehelper.PageHelper.startPage(safePage, safeSize);
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getUserId, userId);
        // 支持按状态筛选，多个状态用逗号分隔（如「已付款,已发货」）
        if (status != null && !status.trim().isEmpty()) {
            List<String> statusList = new ArrayList<>();
            for (String item : Arrays.asList(status.split(","))) {
                String trimmed = item.trim();
                if (!trimmed.isEmpty()) {
                    statusList.add(trimmed);
                }
            }
            if (!statusList.isEmpty()) {
                wrapper.in(Order::getStatus, statusList);
            }
        }
        wrapper.orderByDesc(Order::getCreatedAt);
        List<Order> orders = orderMapper.selectList(wrapper);
        
        log.info("查询到 {} 个订单", orders.size());
        
        List<OrderResponse> result = new ArrayList<>();
        for (Order order : orders) {
            OrderResponse resp = new OrderResponse();
            resp.setId(order.getId());
            resp.setUserId(order.getUserId());
            resp.setUserName(order.getUserName());
            resp.setProductName(order.getProductName());
            resp.setOrderNo(order.getOrderNo());
            resp.setTotalPrice(order.getTotalPrice() != null ? order.getTotalPrice().doubleValue() : 0.0);
            resp.setDiscountAmount(order.getDiscountAmount() != null ? order.getDiscountAmount().doubleValue() : 0.0);
            resp.setStatus(order.getStatus());
            resp.setAddress(order.getAddress());
            resp.setPhone(order.getPhone());
            resp.setReceiver(order.getReceiver());
            resp.setCreatedAt(order.getCreatedAt() != null ? order.getCreatedAt().toString() : "");
            applyPayInfo(resp, order);
            
            List<OrderItemResponse> items = new ArrayList<>();
            LambdaQueryWrapper<OrderItem> itemWrapper = new LambdaQueryWrapper<>();
            itemWrapper.eq(OrderItem::getOrderId, order.getId());
            List<OrderItem> orderItems = orderItemMapper.selectList(itemWrapper);
            
            StringBuilder productNames = new StringBuilder();
            for (OrderItem item : orderItems) {
                Product product = productService.findById(item.getProductId());
                if (product != null) {
                    OrderItemResponse itemResp = new OrderItemResponse();
                    itemResp.setProductId(item.getProductId());
                    itemResp.setProductName(product.getName());
                    itemResp.setQuantity(item.getQuantity());
                    itemResp.setPrice(item.getPrice() != null ? item.getPrice().doubleValue() : 0.0);
                    // 图片通过接口按需加载
                    itemResp.setImageUrl(getProductImageUrl(product));
                    items.add(itemResp);

                    if (productNames.length() > 0) {
                        productNames.append(", ");
                    }
                    productNames.append(product.getName());
                }
            }
            resp.setItems(items);

            // 订单级商品名摘要，前端列表可直接展示
            if (productNames.length() > 0) {
                resp.setProductName(productNames.toString());
            }
            
            result.add(resp);
        }
        
        return Response.success(result);
    }

    @GetMapping("/{id}")
    public Response<OrderResponse> getOrderById(@PathVariable Integer id, Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            log.warn("获取订单详情失败：用户未认证");
            return Response.error(401, "请先登录");
        }
        
        Integer userId;
        try {
            Object principal = authentication.getPrincipal();
                    
            if (principal instanceof Integer) {
                userId = (Integer) principal;
            } else if (principal instanceof String) {
                userId = Integer.parseInt((String) principal);
            } else {
                log.error("获取订单详情失败：无效的 principal 类型");
                return Response.error(401, "用户信息无效");
            }
        } catch (NumberFormatException e) {
            log.error("获取订单详情失败：无效的 userId", e);
            return Response.error(401, "用户信息无效");
        }
        
        Order order = orderService.findById(id);
        if (order == null || !order.getUserId().equals(userId)) {
            return Response.error(404, "订单不存在");
        }
        
        OrderResponse resp = new OrderResponse();
        resp.setId(order.getId());
        resp.setUserId(order.getUserId());
        resp.setUserName(order.getUserName());
        resp.setProductName(order.getProductName());
        resp.setOrderNo(order.getOrderNo());
        resp.setTotalPrice(order.getTotalPrice() != null ? order.getTotalPrice().doubleValue() : 0.0);
        resp.setDiscountAmount(order.getDiscountAmount() != null ? order.getDiscountAmount().doubleValue() : 0.0);
        resp.setStatus(order.getStatus());
        resp.setAddress(order.getAddress());
        resp.setPhone(order.getPhone());
        resp.setReceiver(order.getReceiver());
        resp.setCreatedAt(order.getCreatedAt() != null ? order.getCreatedAt().toString() : "");
        applyPayInfo(resp, order);
        
        List<OrderItemResponse> items = new ArrayList<>();
        LambdaQueryWrapper<OrderItem> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.eq(OrderItem::getOrderId, order.getId());
        List<OrderItem> orderItems = orderItemMapper.selectList(itemWrapper);
        
        log.info("查询到 {} 个订单项", orderItems.size());
        
        for (OrderItem item : orderItems) {
            Product product = productService.findById(item.getProductId());
            if (product != null) {
                OrderItemResponse itemResp = new OrderItemResponse();
                itemResp.setProductId(item.getProductId());
                itemResp.setProductName(product.getName());
                itemResp.setQuantity(item.getQuantity());
                itemResp.setPrice(item.getPrice() != null ? item.getPrice().doubleValue() : 0.0);
                // 如果imageUrl为空，使用图片接口URL
                // 图片通过接口按需加载
                itemResp.setImageUrl(getProductImageUrl(product));
                items.add(itemResp);
                log.info("添加商品: {}", product.getName());
            } else {
                log.warn("商品不存在: productId={}", item.getProductId());
            }
        }
        resp.setItems(items);
        resp.setReviewed(orderService.isReviewed(order.getId()));
        log.info("订单详情返回 {} 个商品", items.size());
        
        return Response.success(resp);
    }

    @PostMapping
    @Transactional
    public Response<OrderResponse> createOrder(@RequestBody Map<String, Object> request, Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            log.warn("创建订单失败：用户未认证");
            return Response.error(401, "请先登录");
        }
        
        Integer userId;
        try {
            Object principal = authentication.getPrincipal();
                    
            if (principal instanceof Integer) {
                userId = (Integer) principal;
            } else if (principal instanceof String) {
                userId = Integer.parseInt((String) principal);
            } else {
                log.error("创建订单失败：无效的 principal 类型");
                return Response.error(401, "用户信息无效");
            }
        } catch (NumberFormatException e) {
            log.error("创建订单失败：无效的 userId", e);
            return Response.error(401, "用户信息无效");
        }
                
        log.info("创建订单：userId={}", userId);
        
        String receiver = request.get("receiver") != null ? request.get("receiver").toString() : "用户";
        String phone = request.get("phone") != null ? request.get("phone").toString() : "13800138000";
        String address = request.get("address") != null ? request.get("address").toString() : "默认地址";
        
        Order order = new Order();
        order.setUserId(userId);
        order.setStatus("待付款");
        order.setReceiver(receiver);
        order.setPhone(phone);
        order.setAddress(address);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        order.setExpiresAt(LocalDateTime.now().plusMinutes(15)); // 设置为15分钟过期
        
        // 用于存储商家ID（从商品中获取）
        Integer merchantId = null;
        
        // 先处理订单项，计算总价并检查库存
        List<Map<String, Object>> items = (List<Map<String, Object>>) request.get("items");
        BigDecimal calculatedTotalPrice = BigDecimal.ZERO;
        List<OrderItem> orderItemsToInsert = new ArrayList<>();
        
        if (items != null && !items.isEmpty()) {
            for (Map<String, Object> item : items) {
                Integer productId = Integer.parseInt(item.get("productId").toString());
                Integer quantity = Integer.parseInt(item.get("quantity").toString());
                
                if (quantity <= 0) {
                    log.warn("商品数量无效：productId={}, quantity={}", productId, quantity);
                    throw new IllegalArgumentException("商品数量必须大于0");
                }
                
                Product product = productService.findById(productId);
                if (product == null) {
                    log.error("商品不存在：productId={}", productId);
                    throw new IllegalArgumentException("商品不存在: " + productId);
                }
                
                // 检查库存
                if (product.getStock() == null || product.getStock() < quantity) {
                    log.warn("库存不足：productId={}, 请求={}, 库存={}", productId, quantity, product.getStock());
                    throw new IllegalArgumentException("商品库存不足: " + product.getName());
                }
                
                // 获取商品的商家ID（取第一个商品的商家ID作为订单商家ID）
                if (merchantId == null && product.getMerchantId() != null) {
                    merchantId = product.getMerchantId();
                    log.info("订单关联商家ID: {}", merchantId);
                }
                
                // 计算该项总价
                BigDecimal itemTotal = product.getPrice().multiply(new BigDecimal(quantity));
                calculatedTotalPrice = calculatedTotalPrice.add(itemTotal);
                
                // 准备订单项
                OrderItem orderItem = new OrderItem();
                orderItem.setProductId(productId);
                orderItem.setQuantity(quantity);
                orderItem.setPrice(product.getPrice());
                orderItem.setCreatedAt(LocalDateTime.now());
                orderItem.setUpdatedAt(LocalDateTime.now());
                orderItemsToInsert.add(orderItem);
            }
        } else {
            // 从购物车获取商品
            List<ShoppingCart> cartItems = shoppingCartService.findByUserId(userId);
            if (cartItems.isEmpty()) {
                return Response.error(400, "购物车为空");
            }
            
            for (ShoppingCart cart : cartItems) {
                Product product = productService.findById(cart.getProductId());
                if (product == null) {
                    log.warn("商品不存在：productId={}", cart.getProductId());
                    continue;
                }
                
                // 检查库存
                if (product.getStock() == null || product.getStock() < cart.getQuantity()) {
                    log.warn("库存不足：productId={}, 请求={}, 库存={}", cart.getProductId(), cart.getQuantity(), product.getStock());
                    throw new IllegalArgumentException("商品库存不足: " + product.getName());
                }
                
                // 获取商品的商家ID（取第一个商品的商家ID作为订单商家ID）
                if (merchantId == null && product.getMerchantId() != null) {
                    merchantId = product.getMerchantId();
                    log.info("订单关联商家ID: {}", merchantId);
                }
                
                // 计算该项总价
                BigDecimal itemTotal = product.getPrice().multiply(new BigDecimal(cart.getQuantity()));
                calculatedTotalPrice = calculatedTotalPrice.add(itemTotal);
                
                // 准备订单项
                OrderItem orderItem = new OrderItem();
                orderItem.setProductId(cart.getProductId());
                orderItem.setQuantity(cart.getQuantity());
                orderItem.setPrice(product.getPrice());
                orderItem.setCreatedAt(LocalDateTime.now());
                orderItem.setUpdatedAt(LocalDateTime.now());
                orderItemsToInsert.add(orderItem);
            }
        }
        
        // 设置订单的商家ID
        order.setMerchantId(merchantId);
        
        // 设置计算后的总价
        if (calculatedTotalPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return Response.error(400, "订单金额异常");
        }

        // 优惠券抵扣：抵扣金额一律由服务端按券规则重算，不信任客户端传值
        BigDecimal discountAmount = BigDecimal.ZERO;
        Integer userCouponId = null;
        Object couponIdValue = request.get("couponId");
        if (couponIdValue != null && !couponIdValue.toString().isEmpty()) {
            try {
                userCouponId = Integer.parseInt(couponIdValue.toString());
            } catch (NumberFormatException e) {
                return Response.error(400, "优惠券参数无效");
            }
            try {
                discountAmount = couponService.computeDiscount(userCouponId, userId, calculatedTotalPrice);
            } catch (BusinessException e) {
                return Response.error(400, e.getMessage());
            }
        }
        BigDecimal payablePrice = calculatedTotalPrice.subtract(discountAmount);
        if (payablePrice.compareTo(BigDecimal.ZERO) < 0) {
            payablePrice = BigDecimal.ZERO;
        }
        order.setTotalPrice(payablePrice);
        order.setDiscountAmount(discountAmount);
        order.setUserCouponId(userCouponId);

        // 订单号：毫秒时间 + 随机后缀，避免同秒同金额撞唯一键
        order.setOrderNo(OrderNoGenerator.generate());
        
        // 从user表查询并填充user_name
        com.petshop.entity.User user = userService.findById(userId);
        if (user != null) {
            order.setUserName(user.getUsername());
        }

        // 从product表查询并填充product_name（合并所有商品名称）
        StringBuilder productNames = new StringBuilder();
        for (OrderItem orderItem : orderItemsToInsert) {
            Product product = productService.findById(orderItem.getProductId());
            if (product != null) {
                if (productNames.length() > 0) {
                    productNames.append(", ");
                }
                productNames.append(product.getName());
            }
        }
        if (productNames.length() > 0) {
            order.setProductName(productNames.toString());
        }
        // 插入订单
        orderMapper.insert(order);
        // 订单落库后才能把券绑定到订单，核销失败会回滚整笔下单
        couponService.markUsed(userCouponId, userId, order.getId());
        notificationService.notifyOrder(userId, "订单已创建",
                "订单 " + order.getOrderNo() + " 已提交，请在 15 分钟内完成支付", order.getId());
        log.info("订单创建成功，ID: {}, 总价: {}, 抵扣: {}", order.getId(), payablePrice, discountAmount);
        
        // 批量插入订单项并扣减库存（使用乐观锁）
        for (OrderItem orderItem : orderItemsToInsert) {
            orderItem.setOrderId(order.getId());
            orderItemMapper.insert(orderItem);
            
            // 使用乐观锁扣减库存
            int updated = productMapper.updateStock(orderItem.getProductId(), orderItem.getQuantity());
            if (updated == 0) {
                log.error("库存扣减失败（乐观锁冲突）：productId={}, 扣减数量={}", 
                    orderItem.getProductId(), orderItem.getQuantity());
                throw new BusinessException("商品库存不足，请稍后重试");
            }
            log.info("库存扣减成功：productId={}, 扣减数量={}", 
                orderItem.getProductId(), orderItem.getQuantity());
        }
        
        // 如果从购物车创建，清空购物车
        if (items == null || items.isEmpty()) {
            List<ShoppingCart> cartItems = shoppingCartService.findByUserId(userId);
            for (ShoppingCart cart : cartItems) {
                shoppingCartService.delete(cart.getId());
            }
            log.info("购物车已清空：userId={}", userId);
        }
        
        // 构建包含商品信息的订单响应
        OrderResponse resp = new OrderResponse();
        resp.setId(order.getId());
        resp.setUserId(order.getUserId());
        resp.setUserName(order.getUserName());
        resp.setProductName(order.getProductName());
        resp.setOrderNo(order.getOrderNo());
        resp.setTotalPrice(order.getTotalPrice() != null ? order.getTotalPrice().doubleValue() : 0.0);
        resp.setDiscountAmount(order.getDiscountAmount() != null ? order.getDiscountAmount().doubleValue() : 0.0);
        resp.setStatus(order.getStatus());
        resp.setAddress(order.getAddress());
        resp.setPhone(order.getPhone());
        resp.setReceiver(order.getReceiver());
        resp.setCreatedAt(order.getCreatedAt() != null ? order.getCreatedAt().toString() : "");
        
        List<OrderItemResponse> orderItems = new ArrayList<>();
        LambdaQueryWrapper<OrderItem> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.eq(OrderItem::getOrderId, order.getId());
        List<OrderItem> orderItemList = orderItemMapper.selectList(itemWrapper);
        
        for (OrderItem item : orderItemList) {
            Product product = productService.findById(item.getProductId());
            if (product != null) {
                OrderItemResponse itemResp = new OrderItemResponse();
                itemResp.setProductId(item.getProductId());
                itemResp.setProductName(product.getName());
                itemResp.setQuantity(item.getQuantity());
                itemResp.setPrice(item.getPrice() != null ? item.getPrice().doubleValue() : 0.0);
                // 图片通过接口按需加载
                itemResp.setImageUrl(getProductImageUrl(product));
                orderItems.add(itemResp);
            }
        }
        resp.setItems(orderItems);
        
        log.info("订单创建成功: userId={}, orderNo={}, 商品数量={}", userId, order.getOrderNo(), orderItems.size());
        return Response.success("订单创建成功", resp);
    }

    /**
     * 订单支付：待付款 -> 已付款。
     * 请求体可选 {"payMethod":"WECHAT|ALIPAY|BANK|COD"}，不传时默认微信支付，
     * 兼容尚未升级的旧客户端。重复支付直接返回当前订单，保证按钮重复点击不会报错。
     */
    @PostMapping("/{id}/pay")
    public Response<OrderResponse> payOrder(@PathVariable Integer id,
                                            @RequestBody(required = false) Map<String, Object> body,
                                            Authentication authentication) {
        Integer userId = resolveUserId(authentication);
        if (userId == null) {
            log.warn("支付订单失败：用户未认证");
            return Response.error(401, "请先登录");
        }
        Order order = orderService.findById(id);
        if (order == null || !order.getUserId().equals(userId)) {
            return Response.error(404, "订单不存在");
        }

        // 幂等：已支付过的订单直接返回，避免重复点击报“状态不正确”
        if (ORDER_STATUS_PAID.equals(order.getStatus())) {
            return Response.success("订单已支付", buildOrderResponse(order));
        }
        if (!ORDER_STATUS_PENDING_PAYMENT.equals(order.getStatus())) {
            return Response.error(400, "当前订单状态不支持支付");
        }

        Object rawPayMethod = body != null ? body.get("payMethod") : null;
        String payMethod = rawPayMethod != null ? rawPayMethod.toString().trim().toUpperCase(java.util.Locale.ROOT) : "";
        if (payMethod.isEmpty()) {
            payMethod = PayMethod.WECHAT;
        } else if (!PayMethod.isSupported(payMethod)) {
            return Response.error(400, "不支持的支付方式");
        }

        Order paidOrder = orderService.pay(id, payMethod);
        log.info("订单支付成功: userId={}, orderNo={}, payMethod={}", userId, paidOrder.getOrderNo(), payMethod);
        return Response.success("支付成功", buildOrderResponse(paidOrder));
    }

    @PostMapping("/{id}/cancel")
    public Response<Order> cancelOrder(@PathVariable Integer id, Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            log.warn("取消订单失败：用户未认证");
            return Response.error(401, "请先登录");
        }

        
        Integer userId;
        try {
            Object principal = authentication.getPrincipal();
            log.info("Authentication principal: {}, 类型: {}", principal, principal != null ? principal.getClass().getName() : "null");
            
            if (principal instanceof Integer) {
                userId = (Integer) principal;
            } else if (principal instanceof String) {
                userId = Integer.parseInt((String) principal);
            } else {
                log.error("取消订单失败：无效的principal类型 - {}", principal != null ? principal.getClass().getName() : "null");
                return Response.error(401, "用户信息无效");
            }
        } catch (NumberFormatException e) {
            log.error("取消订单失败：无效的userId", e);
            return Response.error(401, "用户信息无效");
        }
        
        Order order = orderService.findById(id);
        if (order == null || !order.getUserId().equals(userId)) {
            return Response.error(404, "订单不存在");
        }
        Order canceledOrder = orderService.cancel(id);
        log.info("订单取消成功: userId={}, orderNo={}", userId, canceledOrder.getOrderNo());
        return Response.success("订单取消成功", canceledOrder);
    }

    /**
     * 商家发货：已付款 -> 已发货。
     * 订单已关联商家时仅该商家可发货；历史商品未关联商家（merchantId 为空）时
     * 允许下单用户触发，保证单商家模式下订单状态可以走完。
     */
    @PostMapping("/{id}/ship")
    public Response<OrderResponse> shipOrder(@PathVariable Integer id, Authentication authentication) {
        Integer userId = resolveUserId(authentication);
        if (userId == null) {
            return Response.error(401, "请先登录");
        }
        Order order = orderService.findById(id);
        if (order == null) {
            return Response.error(404, "订单不存在");
        }
        boolean isMerchant = order.getMerchantId() != null && order.getMerchantId().equals(userId);
        boolean isLegacyDemoOrder = order.getMerchantId() == null && order.getUserId().equals(userId);
        if (!isMerchant && !isLegacyDemoOrder) {
            return Response.error(403, "无权操作该订单");
        }
        Order shipped = orderService.ship(id);
        log.info("订单已发货: userId={}, orderNo={}", userId, shipped.getOrderNo());
        return Response.success("发货成功", buildOrderResponse(shipped));
    }

    /**
     * 买家确认收货：已发货 -> 已完成
     */
    @PostMapping("/{id}/confirm")
    public Response<OrderResponse> confirmReceipt(@PathVariable Integer id, Authentication authentication) {
        Integer userId = resolveUserId(authentication);
        if (userId == null) {
            return Response.error(401, "请先登录");
        }
        Order order = orderService.findById(id);
        if (order == null || !order.getUserId().equals(userId)) {
            return Response.error(404, "订单不存在");
        }
        Order completed = orderService.confirmReceipt(id);
        log.info("订单已确认收货: userId={}, orderNo={}", userId, completed.getOrderNo());
        return Response.success("确认收货成功", buildOrderResponse(completed));
    }

    /**
     * 解析 JWT 中的用户ID
     */
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

    /**
     * 将订单实体转换为接口响应（含商品明细与评价状态）
     */
    private OrderResponse buildOrderResponse(Order order) {
        OrderResponse resp = new OrderResponse();
        resp.setId(order.getId());
        resp.setUserId(order.getUserId());
        resp.setUserName(order.getUserName());
        resp.setProductName(order.getProductName());
        resp.setOrderNo(order.getOrderNo());
        resp.setTotalPrice(order.getTotalPrice() != null ? order.getTotalPrice().doubleValue() : 0.0);
        resp.setDiscountAmount(order.getDiscountAmount() != null ? order.getDiscountAmount().doubleValue() : 0.0);
        resp.setStatus(order.getStatus());
        resp.setAddress(order.getAddress());
        resp.setPhone(order.getPhone());
        resp.setReceiver(order.getReceiver());
        resp.setCreatedAt(order.getCreatedAt() != null ? order.getCreatedAt().toString() : "");

        List<OrderItemResponse> items = new ArrayList<>();
        LambdaQueryWrapper<OrderItem> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.eq(OrderItem::getOrderId, order.getId());
        for (OrderItem item : orderItemMapper.selectList(itemWrapper)) {
            Product product = productService.findById(item.getProductId());
            if (product != null) {
                OrderItemResponse itemResp = new OrderItemResponse();
                itemResp.setProductId(item.getProductId());
                itemResp.setProductName(product.getName());
                itemResp.setQuantity(item.getQuantity());
                itemResp.setPrice(item.getPrice() != null ? item.getPrice().doubleValue() : 0.0);
                itemResp.setImageUrl(getProductImageUrl(product));
                items.add(itemResp);
            }
        }
        resp.setItems(items);
        resp.setReviewed(orderService.isReviewed(order.getId()));
        applyPayInfo(resp, order);
        return resp;
    }

    /**
     * 填充支付方式与支付时间：未支付的订单这两个字段为空，前端据此隐藏支付方式
     */
    private void applyPayInfo(OrderResponse resp, Order order) {
        String code = order.getPayMethod();
        resp.setPayMethod(code);
        resp.setPayMethodLabel(code != null && !code.isEmpty() ? PayMethod.labelOf(code) : null);
        resp.setPaidAt(order.getPaidAt() != null ? order.getPaidAt().toString() : "");
    }

    @PutMapping("/{id}")
    public Response<Order> updateOrder(@PathVariable Integer id, @RequestBody Order order, Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            log.warn("更新订单失败：用户未认证");
            return Response.error(401, "请先登录");
        }
        
        Integer userId;
        try {
            Object principal = authentication.getPrincipal();
                    
            if (principal instanceof Integer) {
                userId = (Integer) principal;
            } else if (principal instanceof String) {
                userId = Integer.parseInt((String) principal);
            } else {
                log.error("更新订单失败：无效的 principal 类型");
                return Response.error(401, "用户信息无效");
            }
        } catch (NumberFormatException e) {
            log.error("更新订单失败：无效的 userId", e);
            return Response.error(401, "用户信息无效");
        }
        
        Order existing = orderService.findById(id);
        if (existing == null || !existing.getUserId().equals(userId)) {
            return Response.error(404, "订单不存在");
        }

        // 只允许修改收货信息：status / totalPrice / discountAmount / userCouponId
        // 均由服务端订单流程控制，客户端传了也一律丢弃。
        Order patch = new Order();
        patch.setId(id);
        patch.setUserId(userId);
        patch.setReceiver(order.getReceiver());
        patch.setPhone(order.getPhone());
        patch.setAddress(order.getAddress());
        orderService.update(patch);

        log.info("订单收货信息更新成功: userId={}, orderId={}", userId, id);
        return Response.success(orderService.findById(id));
    }

    @DeleteMapping("/{id}")
    public Response<Void> deleteOrder(@PathVariable Integer id, Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            log.warn("删除订单失败：用户未认证");
            return Response.error(401, "请先登录");
        }
        
        Integer userId;
        try {
            Object principal = authentication.getPrincipal();
                    
            if (principal instanceof Integer) {
                userId = (Integer) principal;
            } else if (principal instanceof String) {
                userId = Integer.parseInt((String) principal);
            } else {
                log.error("删除订单失败：无效的 principal 类型");
                return Response.error(401, "用户信息无效");
            }
        } catch (NumberFormatException e) {
            log.error("删除订单失败：无效的 userId", e);
            return Response.error(401, "用户信息无效");
        }
        
        Order order = orderService.findById(id);
        if (order == null || !order.getUserId().equals(userId)) {
            return Response.error(404, "订单不存在");
        }
        // 只允许删除已完结的订单；进行中的订单需先取消/收货，
        // 否则订单项与库存记录会变成无法追溯的孤儿数据
        if (!"已完成".equals(order.getStatus()) && !"已取消".equals(order.getStatus())) {
            return Response.error(400, "只有已完成或已取消的订单才能删除");
        }
        orderService.delete(id);
        log.info("订单删除成功: userId={}, orderId={}", userId, id);
        return Response.success("订单删除成功", null);
    }
    
    /**
     * 全局异常处理 - 参数错误
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Response<Void> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("参数错误: {}", e.getMessage());
        return Response.error(400, e.getMessage());
    }
    
    /**
     * 全局异常处理 - 业务异常
     */
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Response<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Response.error(400, e.getMessage());
    }
    
    /**
     * 全局异常处理 - 运行时异常
     */
    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Response<Void> handleRuntimeException(RuntimeException e) {
        log.error("运行时异常", e);
        return Response.error(500, "服务器内部错误，请稍后重试");
    }
}
