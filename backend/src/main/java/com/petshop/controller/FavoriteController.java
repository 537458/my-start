package com.petshop.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.pagehelper.PageHelper;
import com.petshop.common.Response;
import com.petshop.entity.Favorite;
import com.petshop.entity.Product;
import com.petshop.mapper.FavoriteMapper;
import com.petshop.service.ProductService;
import com.petshop.util.AuthUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 商品收藏夹
 */
@RestController
@RequestMapping("/favorites")
public class FavoriteController {

    private static final Logger log = LoggerFactory.getLogger(FavoriteController.class);

    @Autowired
    private FavoriteMapper favoriteMapper;

    @Autowired
    private ProductService productService;

    /** 我的收藏列表，按页返回，客户端滑到底部再取下一页 */
    @GetMapping
    public Response<List<FavoriteItem>> list(@RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int size,
                                             Authentication authentication) {
        Integer userId = AuthUtils.resolveUserId(authentication);
        if (userId == null) {
            return Response.error(401, "请先登录");
        }
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);

        List<FavoriteItem> result = new ArrayList<>();
        int scanPage = safePage;
        // 商品被删后其收藏记录会被跳过，为了让客户端“本页不足即末页”的判断成立，
        // 这里把脏收藏记录直接清掉并继续往下扫描，直到凑满一页或确实没数据
        while (result.size() < safeSize) {
            // 同一秒内收藏多条时按 id 兜底排序，保证翻页不重不漏
            PageHelper.startPage(scanPage, safeSize);
            List<Favorite> favorites = favoriteMapper.selectList(new LambdaQueryWrapper<Favorite>()
                    .eq(Favorite::getUserId, userId)
                    .orderByDesc(Favorite::getCreatedAt)
                    .orderByDesc(Favorite::getId));
            if (favorites.isEmpty()) {
                break;
            }
            for (Favorite favorite : favorites) {
                Product product = productService.findById(favorite.getProductId());
                if (product == null) {
                    // 商品已下架/删除，收藏记录不再展示，顺手清理掉避免反复扫描
                    favoriteMapper.deleteById(favorite.getId());
                    continue;
                }
                FavoriteItem item = new FavoriteItem();
                item.setId(favorite.getId());
                item.setProductId(product.getId());
                item.setName(product.getName());
                item.setDescription(product.getDescription());
                item.setPrice(product.getPrice() != null ? product.getPrice().doubleValue() : 0.0);
                item.setStock(product.getStock() != null ? product.getStock() : 0);
                item.setCreatedAt(favorite.getCreatedAt() != null ? favorite.getCreatedAt().toString() : "");
                result.add(item);
            }
            if (favorites.size() < safeSize) {
                // 已经是最后一页，不用再扫
                break;
            }
            scanPage++;
            if (scanPage - safePage >= 5) {
                // 兜底：极端脏数据下不无限扫，下一页仍能继续取到剩余数据
                break;
            }
        }
        return Response.success(result);
    }

    /** 仅返回收藏的商品ID，用于列表页批量标记红心状态 */
    @GetMapping("/ids")
    public Response<List<Integer>> ids(Authentication authentication) {
        Integer userId = AuthUtils.resolveUserId(authentication);
        if (userId == null) {
            return Response.success(new ArrayList<>());
        }
        List<Favorite> favorites = favoriteMapper.selectList(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId));
        List<Integer> ids = new ArrayList<>();
        for (Favorite favorite : favorites) {
            ids.add(favorite.getProductId());
        }
        return Response.success(ids);
    }

    /** 单个商品是否已收藏 */
    @GetMapping("/check/{productId}")
    public Response<Boolean> check(@PathVariable Integer productId, Authentication authentication) {
        Integer userId = AuthUtils.resolveUserId(authentication);
        if (userId == null) {
            return Response.success(false);
        }
        return Response.success(isFavorited(userId, productId));
    }

    /** 收藏商品，重复收藏视为成功（幂等） */
    @PostMapping("/{productId}")
    public Response<Boolean> add(@PathVariable Integer productId, Authentication authentication) {
        Integer userId = AuthUtils.resolveUserId(authentication);
        if (userId == null) {
            return Response.error(401, "请先登录");
        }
        Product product = productService.findById(productId);
        if (product == null) {
            return Response.error(404, "商品不存在");
        }
        if (isFavorited(userId, productId)) {
            return Response.success("已收藏", true);
        }
        Favorite favorite = new Favorite();
        favorite.setUserId(userId);
        favorite.setProductId(productId);
        favorite.setCreatedAt(LocalDateTime.now());
        favoriteMapper.insert(favorite);
        log.info("收藏商品: userId={}, productId={}", userId, productId);
        return Response.success("收藏成功", true);
    }

    /** 取消收藏 */
    @DeleteMapping("/{productId}")
    public Response<Boolean> remove(@PathVariable Integer productId, Authentication authentication) {
        Integer userId = AuthUtils.resolveUserId(authentication);
        if (userId == null) {
            return Response.error(401, "请先登录");
        }
        favoriteMapper.delete(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getProductId, productId));
        log.info("取消收藏: userId={}, productId={}", userId, productId);
        return Response.success("已取消收藏", false);
    }

    private boolean isFavorited(Integer userId, Integer productId) {
        Long count = favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getProductId, productId));
        return count != null && count > 0;
    }

    /** 收藏项视图：只暴露列表展示所需的轻量字段，避免把商品二进制图带出去 */
    public static class FavoriteItem {
        private Integer id;
        private Integer productId;
        private String name;
        private String description;
        private Double price;
        private Integer stock;
        private String createdAt;

        public Integer getId() {
            return id;
        }

        public void setId(Integer id) {
            this.id = id;
        }

        public Integer getProductId() {
            return productId;
        }

        public void setProductId(Integer productId) {
            this.productId = productId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Double getPrice() {
            return price;
        }

        public void setPrice(Double price) {
            this.price = price;
        }

        public Integer getStock() {
            return stock;
        }

        public void setStock(Integer stock) {
            this.stock = stock;
        }

        public String getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(String createdAt) {
            this.createdAt = createdAt;
        }
    }
}
