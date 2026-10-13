package com.petshop.controller;

import com.petshop.common.Response;
import com.petshop.entity.Product;
import com.petshop.entity.ShoppingCart;
import com.petshop.service.ProductService;
import com.petshop.service.ShoppingCartService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/cart")
public class ShoppingCartController {

    private static final Logger log = LoggerFactory.getLogger(ShoppingCartController.class);

    @Autowired
    private ShoppingCartService shoppingCartService;

    @Autowired
    private ProductService productService;

    public static class CartItemResponse {
        private Integer id;
        private Integer userId;
        private Integer productId;
        private Integer quantity;
        private String productName;
        private Double price;
        private String imageUrl;
        /** 当前可售库存，前端据此限制加购数量 */
        private Integer stock;

        public CartItemResponse(ShoppingCart cart, Product product) {
            this.id = cart.getId();
            this.userId = cart.getUserId();
            this.productId = cart.getProductId();
            this.quantity = cart.getQuantity();
            if (product != null) {
                this.productName = product.getName();
                this.price = product.getPrice() != null ? product.getPrice().doubleValue() : 0.0;
                this.stock = product.getStock() != null ? product.getStock() : 0;

                // 图片通过接口按需加载，不在JSON中嵌入Base64数据
                this.imageUrl = "/images/product/" + product.getId();
            }
        }

        public Integer getId() { return id; }
        public Integer getUserId() { return userId; }
        public Integer getProductId() { return productId; }
        public Integer getQuantity() { return quantity; }
        public String getProductName() { return productName; }
        public Double getPrice() { return price; }
        public String getImageUrl() { return imageUrl; }
        public Integer getStock() { return stock; }
    }

    @GetMapping
    public Response<List<CartItemResponse>> getCartItems(Authentication authentication) {
        Integer userId = Integer.parseInt(authentication.getName());
        List<ShoppingCart> cartItems = shoppingCartService.findByUserId(userId);
        List<CartItemResponse> result = new ArrayList<>();
        
        for (ShoppingCart cart : cartItems) {
            Product product = productService.findById(cart.getProductId());
            result.add(new CartItemResponse(cart, product));
        }
        
        return Response.success(result);
    }

    @PostMapping
    public Response<ShoppingCart> addToCart(@RequestBody ShoppingCart shoppingCart, Authentication authentication) {
        Integer userId = Integer.parseInt(authentication.getName());

        if (shoppingCart.getProductId() == null) {
            return Response.error(400, "商品ID不能为空");
        }
        int addQuantity = shoppingCart.getQuantity() != null ? shoppingCart.getQuantity() : 1;
        if (addQuantity <= 0) {
            return Response.error(400, "商品数量必须大于0");
        }

        Product product = productService.findById(shoppingCart.getProductId());
        if (product == null) {
            return Response.error(404, "商品不存在或已下架");
        }

        // 购物车同一商品会累加数量，按「已有数量 + 本次数量」校验库存
        int existedQuantity = 0;
        for (ShoppingCart item : shoppingCartService.findByUserId(userId)) {
            if (shoppingCart.getProductId().equals(item.getProductId())) {
                existedQuantity = item.getQuantity() != null ? item.getQuantity() : 0;
                break;
            }
        }
        int stock = product.getStock() != null ? product.getStock() : 0;
        if (existedQuantity + addQuantity > stock) {
            return Response.error(400, "库存不足，当前仅剩 " + stock + " 件");
        }

        shoppingCart.setUserId(userId);
        shoppingCart.setQuantity(addQuantity);
        ShoppingCart addedItem = shoppingCartService.add(shoppingCart);
        log.info("商品添加到购物车成功: userId={}, productId={}, quantity={}", userId, shoppingCart.getProductId(), addQuantity);
        return Response.success("添加成功", addedItem);
    }

    @PutMapping("/{id}")
    public Response<ShoppingCart> updateCartItem(@PathVariable Integer id, @RequestBody ShoppingCart shoppingCart, Authentication authentication) {
        Integer userId = Integer.parseInt(authentication.getName());
        ShoppingCart existing = shoppingCartService.findById(id);
        if (existing == null || !existing.getUserId().equals(userId)) {
            return Response.error(404, "购物车项不存在");
        }

        if (shoppingCart.getQuantity() == null || shoppingCart.getQuantity() <= 0) {
            return Response.error(400, "商品数量必须大于0");
        }

        Product product = productService.findById(existing.getProductId());
        if (product == null) {
            return Response.error(404, "商品不存在或已下架");
        }
        int stock = product.getStock() != null ? product.getStock() : 0;
        if (shoppingCart.getQuantity() > stock) {
            return Response.error(400, "库存不足，当前仅剩 " + stock + " 件");
        }

        shoppingCart.setId(id);
        shoppingCart.setUserId(userId);
        // 本接口只允许改数量，不允许换商品
        shoppingCart.setProductId(existing.getProductId());
        ShoppingCart updatedItem = shoppingCartService.update(shoppingCart);
        log.info("购物车商品更新成功: {}", id);
        return Response.success(updatedItem);
    }

    @DeleteMapping("/{id}")
    public Response<Void> deleteCartItem(@PathVariable Integer id, Authentication authentication) {
        Integer userId = Integer.parseInt(authentication.getName());
        ShoppingCart existing = shoppingCartService.findById(id);
        if (existing == null || !existing.getUserId().equals(userId)) {
            return Response.error(404, "购物车项不存在");
        }
        shoppingCartService.delete(id);
        log.info("购物车商品删除成功: {}", id);
        return Response.success("删除成功", null);
    }

    @DeleteMapping
    public Response<Void> clearCart(Authentication authentication) {
        Integer userId = Integer.parseInt(authentication.getName());
        shoppingCartService.clear(userId);
        log.info("购物车清空成功: {}", userId);
        return Response.success("清空成功", null);
    }
}