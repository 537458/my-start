package com.petshop.api;

import com.petshop.model.LoginData;
import com.petshop.model.Order;
import com.petshop.model.Product;
import com.petshop.model.ShoppingCart;
import com.petshop.model.User;
import com.petshop.model.Address;

import java.util.List;
import java.util.Map;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.QueryMap;

/**
 * API接口定义
 */
public interface ApiService {

    // 用户相关接口
    @POST("auth/register")
    Call<Response<User>> register(@Body User user);

    @POST("auth/login")
    Call<Response<LoginData>> login(@Body Map<String, String> params);

    @GET("users/me")
    Call<Response<User>> getUserInfo();

    @PUT("users/me")
    Call<Response<User>> updateUserInfo(@Body User user);
    
    @Multipart
    @POST("users/me/avatar")
    Call<Response<Map<String, String>>> uploadAvatar(@Part MultipartBody.Part avatar);

    /** 恢复默认头像：清空服务端已保存的头像 */
    @DELETE("users/me/avatar")
    Call<Response<Map<String, String>>> resetAvatar();
    
    @PUT("users/me/password")
    Call<Response<Void>> updatePassword(@Body Map<String, String> params);

    // 找回密码（免登录）：参数走表单而不是 URL，避免新密码出现在请求日志里
    @FormUrlEncoded
    @POST("auth/reset-password/send-code")
    Call<Response<Void>> sendResetCode(@Field("phone") String phone);

    @FormUrlEncoded
    @POST("auth/reset-password")
    Call<Response<Void>> resetPassword(@Field("phone") String phone,
                                       @Field("code") String code,
                                       @Field("newPassword") String newPassword);

    // 商品相关接口
    @GET("products")
    Call<Response<List<Product>>> getProducts();

    @GET("products/{id}")
    Call<Response<Product>> getProductDetail(@Path("id") int id);

    // 库存相关接口
    @GET("products/{id}/stock")
    Call<Response<StockInfo>> getProductStock(@Path("id") int id);

    @POST("products/{id}/stock/check")
    Call<Response<StockCheckResult>> checkStock(@Path("id") int id, @Body Map<String, Integer> request);

    @POST("products/{id}/stock/deduct")
    Call<Response<StockDeductResult>> deductStock(@Path("id") int id, @Body Map<String, Object> request);

    @POST("products/{id}/stock/lock")
    Call<Response<StockLockResult>> lockStock(@Path("id") int id, @Body Map<String, Object> request);

    @POST("products/{id}/stock/unlock")
    Call<Response<Void>> unlockStock(@Path("id") int id, @Body Map<String, String> request);

    @GET("products/category/{categoryId}")
    Call<Response<List<Product>>> getProductsByCategory(@Path("categoryId") int categoryId);

    @GET("products/search")
    Call<Response<List<Product>>> searchProducts(
            @Query("keyword") String keyword,
            @Query("sort") String sort,
            @Query("page") int page,
            @Query("pageSize") int pageSize
    );

    // 分类相关接口
    @GET("categories")
    Call<Response<List<Category>>> getCategories();

    // 购物车相关接口
    @GET("cart")
    Call<Response<List<CartItemSimple>>> getCart();

    @POST("cart")
    Call<Response<ShoppingCart>> addToCart(@Body ShoppingCart cart);

    @PUT("cart/{id}")
    Call<Response<com.petshop.model.ShoppingCart>> updateCartItem(@Path("id") int id, @Body com.petshop.model.ShoppingCart cart);

    @DELETE("cart/{id}")
    Call<Response<Void>> deleteCartItem(@Path("id") int id);

    @DELETE("cart")
    Call<Response<Void>> clearCart();

    // 订单相关接口
    @POST("orders")
    Call<Response<BackendOrder>> createOrder(@Body Map<String, Object> orderData);

    @GET("orders")
    Call<Response<List<BackendOrder>>> getOrders(@Query("page") int page,
                                                 @Query("size") int size,
                                                 @Query("status") String status);

    @GET("orders/{id}")
    Call<Response<BackendOrder>> getOrderDetail(@Path("id") int id);

    @POST("orders/{id}/pay")
    Call<Response<BackendOrder>> payOrder(@Path("id") int id, @Body Map<String, Object> payData);

    // 确认收货：已发货 -> 已完成
    @POST("orders/{id}/confirm")
    Call<Response<BackendOrder>> confirmReceipt(@Path("id") int id);

    // 商家发货：已付款 -> 已发货
    @POST("orders/{id}/ship")
    Call<Response<BackendOrder>> shipOrder(@Path("id") int id);

    @POST("orders/{id}/cancel")
    Call<Response<BackendOrder>> cancelOrder(@Path("id") int id);

    @PUT("orders/{id}")
    Call<Response<BackendOrder>> updateOrder(@Path("id") int id, @Body BackendOrder order);

    @DELETE("orders/{id}")
    Call<Response<Void>> deleteOrder(@Path("id") int id);

    // AI助手相关接口
    @POST("ai/chat")
    Call<Response<AIChatResponse>> chatWithAI(@Body Map<String, Object> params);

    // 商品评价相关接口
    @POST("reviews")
    Call<Response<Void>> createReview(@Body Map<String, Object> params);

    @GET("reviews/product/{productId}")
    Call<Response<List<ProductReview>>> getProductReviews(@Path("productId") int productId,
                                                        @Query("page") int page,
                                                        @Query("size") int size);

    @GET("reviews/product/{productId}/summary")
    Call<Response<ReviewSummary>> getProductReviewSummary(@Path("productId") int productId);

    // 商品收藏相关接口
    @GET("favorites")
    Call<Response<List<FavoriteItem>>> getFavorites(@Query("page") int page, @Query("size") int size);

    @GET("favorites/ids")
    Call<Response<List<Integer>>> getFavoriteIds();

    @GET("favorites/check/{productId}")
    Call<Response<Boolean>> isFavorited(@Path("productId") int productId);

    @POST("favorites/{productId}")
    Call<Response<Boolean>> addFavorite(@Path("productId") int productId);

    @DELETE("favorites/{productId}")
    Call<Response<Boolean>> removeFavorite(@Path("productId") int productId);

    // 优惠券相关接口
    @GET("coupons/templates")
    Call<Response<List<CouponItem>>> getCouponTemplates();

    @POST("coupons/{couponId}/receive")
    Call<Response<Void>> receiveCoupon(@Path("couponId") int couponId);

    @GET("coupons/my")
    Call<Response<List<CouponItem>>> getMyCoupons(@Query("status") String status);

    @GET("coupons/usable")
    Call<Response<List<CouponItem>>> getUsableCoupons(@Query("amount") double amount);

    // 站内消息相关接口
    @GET("notifications")
    Call<Response<List<NotificationItem>>> getNotifications(@Query("page") int page, @Query("size") int size);

    @GET("notifications/unread-count")
    Call<Response<Integer>> getUnreadCount();

    @PUT("notifications/{id}/read")
    Call<Response<Void>> markNotificationRead(@Path("id") int id);

    @PUT("notifications/read-all")
    Call<Response<Void>> markAllNotificationsRead();

    // 宠物社区相关接口
    /** 社区广场；keyword 为空时不过滤，非空时按作者用户名或帖子内容模糊匹配 */
    @GET("community/posts")
    Call<Response<List<CommunityPostItem>>> getCommunityPosts(@Query("page") int page,
                                                             @Query("size") int size,
                                                             @Query("keyword") String keyword);

    @GET("community/posts/mine")
    Call<Response<List<CommunityPostItem>>> getMyCommunityPosts(@Query("page") int page,
                                                               @Query("size") int size,
                                                               @Query("keyword") String keyword);

    /** 发帖：文字走 part "content"，图片走同名多值的 images，visibility 0 公开 / 1 仅自己可见 */
    @Multipart
    @POST("community/posts")
    Call<Response<Integer>> createCommunityPost(@Part("content") RequestBody content,
                                                @Part("visibility") RequestBody visibility,
                                                @Part List<MultipartBody.Part> images);

    /** 编辑自己的动态：改文字与可见范围 */
    @PUT("community/posts/{id}")
    Call<Response<Void>> updateCommunityPost(@Path("id") int postId,
                                             @Body Map<String, Object> payload);

    /**
     * 编辑配图：keepIndexes 是保留的旧图序号（逗号分隔，空串表示全部删除），
     * images 是新增图片，两者合计最多 6 张
     */
    @Multipart
    @PUT("community/posts/{id}/images")
    Call<Response<Integer>> updateCommunityPostImages(@Path("id") int postId,
                                                      @Part("keepIndexes") RequestBody keepIndexes,
                                                      @Part List<MultipartBody.Part> images);

    @DELETE("community/posts/{id}")
    Call<Response<Void>> deleteCommunityPost(@Path("id") int postId);

    @POST("community/posts/{id}/like")
    Call<Response<CommunityLikeResult>> likeCommunityPost(@Path("id") int postId);

    @DELETE("community/posts/{id}/like")
    Call<Response<CommunityLikeResult>> unlikeCommunityPost(@Path("id") int postId);

    @GET("community/posts/{id}/comments")
    Call<Response<List<CommunityCommentItem>>> getCommunityComments(@Path("id") int postId,
                                                                    @Query("page") int page,
                                                                    @Query("size") int size);

    @POST("community/posts/{id}/comments")
    Call<Response<Integer>> addCommunityComment(@Path("id") int postId,
                                                @Body Map<String, Object> payload);

    // 服务器连通性自检：/health 返回原始 Map，没有统一的 Response 包装
    @GET("health")
    Call<Map<String, String>> checkHealth();

    // 我的宠物相关接口
    @GET("pets")
    Call<Response<List<PetItem>>> getPets();

    /** 品种字典：大类代码 -> 品种名列表 */
    @GET("pets/breeds")
    Call<Response<Map<String, List<String>>>> getPetBreeds();

    /** 新增宠物；avatar 为空列表表示先不传照片 */
    @Multipart
    @POST("pets")
    Call<Response<PetItem>> createPet(@Part("name") RequestBody name,
                                      @Part("species") RequestBody species,
                                      @Part("breed") RequestBody breed,
                                      @Part("gender") RequestBody gender,
                                      @Part("birthday") RequestBody birthday,
                                      @Part("weight") RequestBody weight,
                                      @Part("note") RequestBody note,
                                      @Part List<MultipartBody.Part> avatar);

    /** 编辑宠物；removeAvatar 为 true 时清掉原头像 */
    @Multipart
    @PUT("pets/{id}")
    Call<Response<PetItem>> updatePet(@Path("id") int petId,
                                      @Part("name") RequestBody name,
                                      @Part("species") RequestBody species,
                                      @Part("breed") RequestBody breed,
                                      @Part("gender") RequestBody gender,
                                      @Part("birthday") RequestBody birthday,
                                      @Part("weight") RequestBody weight,
                                      @Part("note") RequestBody note,
                                      @Part("removeAvatar") RequestBody removeAvatar,
                                      @Part List<MultipartBody.Part> avatar);

    @DELETE("pets/{id}")
    Call<Response<Void>> deletePet(@Path("id") int petId);

    // 地址管理相关接口
    @GET("addresses")
    Call<Response<List<Address>>> getAddresses();

    @GET("addresses/{id}")
    Call<Response<Address>> getAddress(@Path("id") int id);

    @POST("addresses")
    Call<Response<Address>> createAddress(@Body Address address);

    @PUT("addresses/{id}")
    Call<Response<Address>> updateAddress(@Path("id") int id, @Body Address address);

    @DELETE("addresses/{id}")
    Call<Response<Void>> deleteAddress(@Path("id") int id);

    @PUT("addresses/{id}/default")
    Call<Response<Void>> setDefaultAddress(@Path("id") int id);

    // 通用响应类
    class Response<T> {
        private Integer code;
        private String message;
        private T data;
        private Long timestamp;

        public Integer getCode() {
            return code;
        }

        public void setCode(Integer code) {
            this.code = code;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public T getData() {
            return data;
        }

        public void setData(T data) {
            this.data = data;
        }

        public Long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(Long timestamp) {
            this.timestamp = timestamp;
        }
    }

    // 分页结果类
    class PageResult<T> {
        private List<T> list;
        private int total;
        private int page;
        private int pageSize;

        public List<T> getList() {
            return list;
        }

        public void setList(List<T> list) {
            this.list = list;
        }

        public int getTotal() {
            return total;
        }

        public void setTotal(int total) {
            this.total = total;
        }

        public int getPage() {
            return page;
        }

        public void setPage(int page) {
            this.page = page;
        }

        public int getPageSize() {
            return pageSize;
        }

        public void setPageSize(int pageSize) {
            this.pageSize = pageSize;
        }
    }

    // 分类类
    class Category {
        private int id;
        private String name;
        private String icon;

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getIcon() {
            return icon;
        }

        public void setIcon(String icon) {
            this.icon = icon;
        }
    }

    // 购物车项类（简化版）
    static class CartItemSimple {
        private int id;
        private int userId;
        private int productId;
        private int quantity;
        private String productName;
        private double price;
        private String imageUrl;
        private boolean selected;
        /** 当前可售库存，用于限制加购数量 */
        private int stock;

        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public int getUserId() { return userId; }
        public void setUserId(int userId) { this.userId = userId; }
        public int getProductId() { return productId; }
        public void setProductId(int productId) { this.productId = productId; }
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
        public String getProductName() { return productName != null ? productName : "商品"; }
        public void setProductName(String productName) { this.productName = productName; }
        public double getPrice() { return price; }
        public void setPrice(double price) { this.price = price; }
        public String getImageUrl() { return imageUrl; }
        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
        public boolean isSelected() { return selected; }
        public void setSelected(boolean selected) { this.selected = selected; }
        public int getStock() { return stock; }
        public void setStock(int stock) { this.stock = stock; }
    }
    
    // 后端订单类（包含订单项）
    static class BackendOrder {
        private Integer id;
        private Integer userId;
        private String orderNo;
        private double totalPrice;
        private String status;
        private String address;
        private String phone;
        private String receiver;
        private String createdAt;
        private String updatedAt;
        private List<OrderItemSimple> items;
        /** 是否已评价 */
        private Boolean reviewed;
        /** 优惠券抵扣金额 */
        private double discountAmount;
        /** 支付方式编码：WECHAT / ALIPAY / BANK / COD */
        private String payMethod;
        /** 支付方式中文名，直接展示 */
        private String payMethodLabel;
        /** 支付完成时间 */
        private String paidAt;
        
        public Integer getId() { return id; }
        public void setId(Integer id) { this.id = id; }
        public Integer getUserId() { return userId; }
        public void setUserId(Integer userId) { this.userId = userId; }
        public String getOrderNo() { return orderNo; }
        public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
        public double getTotalPrice() { return totalPrice; }
        public void setTotalPrice(double totalPrice) { this.totalPrice = totalPrice; }
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
        public String getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
        public List<OrderItemSimple> getItems() { return items; }
        public void setItems(List<OrderItemSimple> items) { this.items = items; }
        public Boolean getReviewed() { return reviewed; }
        public void setReviewed(Boolean reviewed) { this.reviewed = reviewed; }
        public double getDiscountAmount() { return discountAmount; }
        public void setDiscountAmount(double discountAmount) { this.discountAmount = discountAmount; }
        public String getPayMethod() { return payMethod; }
        public void setPayMethod(String payMethod) { this.payMethod = payMethod; }
        public String getPayMethodLabel() { return payMethodLabel; }
        public void setPayMethodLabel(String payMethodLabel) { this.payMethodLabel = payMethodLabel; }
        public String getPaidAt() { return paidAt; }
        public void setPaidAt(String paidAt) { this.paidAt = paidAt; }
    }

    // 订单项简化类
    static class OrderItemSimple {
        private int productId;
        private String productName;
        private int quantity;
        private double price;
        private String imageUrl;

        public int getProductId() { return productId; }
        public void setProductId(int productId) { this.productId = productId; }
        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
        public double getPrice() { return price; }
        public void setPrice(double price) { this.price = price; }
        public String getImageUrl() { return imageUrl; }
        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    }

    // 购物车项类
    class CartItem {
        private int id;
        private int productId;
        private String productName;
        private String productImage;
        private float productPrice;
        private int quantity;
        private boolean selected;

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public int getProductId() {
            return productId;
        }

        public void setProductId(int productId) {
            this.productId = productId;
        }

        public String getProductName() {
            return productName;
        }

        public void setProductName(String productName) {
            this.productName = productName;
        }

        public String getProductImage() {
            return productImage;
        }

        public void setProductImage(String productImage) {
            this.productImage = productImage;
        }

        public float getProductPrice() {
            return productPrice;
        }

        public void setProductPrice(float productPrice) {
            this.productPrice = productPrice;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }

        public boolean isSelected() {
            return selected;
        }

        public void setSelected(boolean selected) {
            this.selected = selected;
        }
    }

    // AI聊天响应类
    class AIChatResponse {
        private String response;

        public String getResponse() {
            return response;
        }

        public void setResponse(String response) {
            this.response = response;
        }
    }

    // 商品评价类
    class ProductReview {
        private int id;
        private int productId;
        private String userName;
        private int rating;
        private String content;
        private String createdAt;

        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public int getProductId() { return productId; }
        public void setProductId(int productId) { this.productId = productId; }
        public String getUserName() { return userName; }
        public void setUserName(String userName) { this.userName = userName; }
        public int getRating() { return rating; }
        public void setRating(int rating) { this.rating = rating; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    }

    // 商品评分汇总类
    class ReviewSummary {
        private double avgRating;
        private int count;

        public double getAvgRating() { return avgRating; }
        public void setAvgRating(double avgRating) { this.avgRating = avgRating; }
        public int getCount() { return count; }
        public void setCount(int count) { this.count = count; }
    }

    // 收藏项
    class FavoriteItem {
        private int id;
        private int productId;
        private String name;
        private String description;
        private double price;
        private int stock;
        private String createdAt;

        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public int getProductId() { return productId; }
        public void setProductId(int productId) { this.productId = productId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public double getPrice() { return price; }
        public void setPrice(double price) { this.price = price; }
        public int getStock() { return stock; }
        public void setStock(int stock) { this.stock = stock; }
        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    }

    // 优惠券项（券模板与我的券共用同一结构）
    class CouponItem {
        private int id;
        private String name;
        private String type;
        private double amount;
        private double discount;
        private double minAmount;
        private String description;
        private String endTime;
        private String status;
        private Integer userCouponId;
        private boolean received;
        private double discountAmount;

        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public double getAmount() { return amount; }
        public void setAmount(double amount) { this.amount = amount; }
        public double getDiscount() { return discount; }
        public void setDiscount(double discount) { this.discount = discount; }
        public double getMinAmount() { return minAmount; }
        public void setMinAmount(double minAmount) { this.minAmount = minAmount; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getEndTime() { return endTime; }
        public void setEndTime(String endTime) { this.endTime = endTime; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Integer getUserCouponId() { return userCouponId; }
        public void setUserCouponId(Integer userCouponId) { this.userCouponId = userCouponId; }
        public boolean isReceived() { return received; }
        public void setReceived(boolean received) { this.received = received; }
        public double getDiscountAmount() { return discountAmount; }
        public void setDiscountAmount(double discountAmount) { this.discountAmount = discountAmount; }
    }

    // 站内消息项
    class NotificationItem {
        private int id;
        private String type;
        private String title;
        private String content;
        private Integer relatedId;
        private boolean read;
        private String createdAt;

        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public Integer getRelatedId() { return relatedId; }
        public void setRelatedId(Integer relatedId) { this.relatedId = relatedId; }
        public boolean isRead() { return read; }
        public void setRead(boolean read) { this.read = read; }
        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    }

    // 库存信息类
    class StockInfo {
        private int productId;
        private int stock;
        private int lockedStock;
        private String updateTime;

        public int getProductId() { return productId; }
        public void setProductId(int productId) { this.productId = productId; }
        public int getStock() { return stock; }
        public void setStock(int stock) { this.stock = stock; }
        public int getLockedStock() { return lockedStock; }
        public void setLockedStock(int lockedStock) { this.lockedStock = lockedStock; }
        public String getUpdateTime() { return updateTime; }
        public void setUpdateTime(String updateTime) { this.updateTime = updateTime; }
    }

    // 库存检查结果类
    class StockCheckResult {
        private boolean available;
        private int currentStock;
        private int requestedQuantity;
        private String message;

        public boolean isAvailable() { return available; }
        public void setAvailable(boolean available) { this.available = available; }
        public int getCurrentStock() { return currentStock; }
        public void setCurrentStock(int currentStock) { this.currentStock = currentStock; }
        public int getRequestedQuantity() { return requestedQuantity; }
        public void setRequestedQuantity(int requestedQuantity) { this.requestedQuantity = requestedQuantity; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    // 库存扣减结果类
    class StockDeductResult {
        private boolean success;
        private int productId;
        private int beforeStock;
        private int afterStock;
        private int deductedQuantity;
        private String transactionId;
        private String message;

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public int getProductId() { return productId; }
        public void setProductId(int productId) { this.productId = productId; }
        public int getBeforeStock() { return beforeStock; }
        public void setBeforeStock(int beforeStock) { this.beforeStock = beforeStock; }
        public int getAfterStock() { return afterStock; }
        public void setAfterStock(int afterStock) { this.afterStock = afterStock; }
        public int getDeductedQuantity() { return deductedQuantity; }
        public void setDeductedQuantity(int deductedQuantity) { this.deductedQuantity = deductedQuantity; }
        public String getTransactionId() { return transactionId; }
        public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    // 库存锁定结果类
    class StockLockResult {
        private boolean success;
        private String lockId;
        private int productId;
        private int lockedQuantity;
        private long expireTime;
        private String message;

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getLockId() { return lockId; }
        public void setLockId(String lockId) { this.lockId = lockId; }
        public int getProductId() { return productId; }
        public void setProductId(int productId) { this.productId = productId; }
        public int getLockedQuantity() { return lockedQuantity; }
        public void setLockedQuantity(int lockedQuantity) { this.lockedQuantity = lockedQuantity; }
        public long getExpireTime() { return expireTime; }
        public void setExpireTime(long expireTime) { this.expireTime = expireTime; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    // 社区动态项
    class CommunityPostItem {
        /** 可见范围：0 公开 / 1 仅自己可见 */
        public static final int VISIBILITY_PUBLIC = 0;
        public static final int VISIBILITY_PRIVATE = 1;

        private int id;
        private int userId;
        private String userName;
        private boolean hasAvatar;
        private String content;
        private int imageCount;
        private int likeCount;
        private int commentCount;
        private boolean liked;
        private boolean mine;
        private int visibility;
        private String createdAt;
        /** 最后更新时间，配图变化后客户端用它作为图片缓存版本号 */
        private String updatedAt;

        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public int getUserId() { return userId; }
        public void setUserId(int userId) { this.userId = userId; }
        public String getUserName() { return userName; }
        public void setUserName(String userName) { this.userName = userName; }
        public boolean hasAvatar() { return hasAvatar; }
        public void setHasAvatar(boolean hasAvatar) { this.hasAvatar = hasAvatar; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public int getImageCount() { return imageCount; }
        public void setImageCount(int imageCount) { this.imageCount = imageCount; }
        public int getLikeCount() { return likeCount; }
        public void setLikeCount(int likeCount) { this.likeCount = likeCount; }
        public int getCommentCount() { return commentCount; }
        public void setCommentCount(int commentCount) { this.commentCount = commentCount; }
        public boolean isLiked() { return liked; }
        public void setLiked(boolean liked) { this.liked = liked; }
        public boolean isMine() { return mine; }
        public void setMine(boolean mine) { this.mine = mine; }
        public int getVisibility() { return visibility; }
        public void setVisibility(int visibility) { this.visibility = visibility; }
        /** 是否被作者设为了仅自己可见 */
        public boolean isPrivate() { return visibility == VISIBILITY_PRIVATE; }
        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
        public String getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
    }

    // 社区评论项
    class CommunityCommentItem {
        private int id;
        private int userId;
        private String userName;
        private boolean hasAvatar;
        private String content;
        private String createdAt;

        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public int getUserId() { return userId; }
        public void setUserId(int userId) { this.userId = userId; }
        public String getUserName() { return userName; }
        public void setUserName(String userName) { this.userName = userName; }
        public boolean hasAvatar() { return hasAvatar; }
        public void setHasAvatar(boolean hasAvatar) { this.hasAvatar = hasAvatar; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    }

    // 社区点赞结果
    class CommunityLikeResult {
        private boolean liked;
        private int likeCount;

        public boolean isLiked() { return liked; }
        public void setLiked(boolean liked) { this.liked = liked; }
        public int getLikeCount() { return likeCount; }
        public void setLikeCount(int likeCount) { this.likeCount = likeCount; }
    }

    // 我的宠物项
    class PetItem {
        public static final String SPECIES_CAT = "cat";
        public static final String SPECIES_DOG = "dog";
        public static final String SPECIES_OTHER = "other";

        public static final String GENDER_MALE = "male";
        public static final String GENDER_FEMALE = "female";
        public static final String GENDER_UNKNOWN = "unknown";

        private int id;
        private String name;
        /** cat / dog / other */
        private String species;
        /** 具体品种，如 狸花猫、比格犬 */
        private String breed;
        /** male / female / unknown */
        private String gender;
        /** yyyy-MM-dd，可为空 */
        private String birthday;
        private Double weight;
        private String note;
        private boolean hasAvatar;
        /** 最后更新时间，同时作为头像缓存版本号 */
        private String updatedAt;

        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getSpecies() { return species; }
        public void setSpecies(String species) { this.species = species; }
        public String getBreed() { return breed; }
        public void setBreed(String breed) { this.breed = breed; }
        public String getGender() { return gender; }
        public void setGender(String gender) { this.gender = gender; }
        public String getBirthday() { return birthday; }
        public void setBirthday(String birthday) { this.birthday = birthday; }
        public Double getWeight() { return weight; }
        public void setWeight(Double weight) { this.weight = weight; }
        public String getNote() { return note; }
        public void setNote(String note) { this.note = note; }
        public boolean isHasAvatar() { return hasAvatar; }
        public void setHasAvatar(boolean hasAvatar) { this.hasAvatar = hasAvatar; }
        public String getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

        /** 大类中文名 */
        public String getSpeciesLabel() {
            if (SPECIES_CAT.equals(species)) {
                return "猫";
            }
            if (SPECIES_DOG.equals(species)) {
                return "狗";
            }
            return "其他";
        }

        /** 性别中文名 */
        public String getGenderLabel() {
            if (GENDER_MALE.equals(gender)) {
                return "弟弟";
            }
            if (GENDER_FEMALE.equals(gender)) {
                return "妹妹";
            }
            return "未知";
        }
    }
}
