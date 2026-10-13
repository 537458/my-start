package com.petshop.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.github.pagehelper.PageHelper;
import com.petshop.common.Response;
import com.petshop.entity.Product;
import com.petshop.entity.User;
import com.petshop.mapper.ProductMapper;
import com.petshop.service.ProductService;
import com.petshop.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/products")
public class ProductController {

    private static final Logger log = LoggerFactory.getLogger(ProductController.class);

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private UserService userService;

    @GetMapping
    public Response<List<ProductDTO>> getProducts() {
        List<Product> products = productService.findAll();
        List<ProductDTO> dtos = products.stream().map(this::convertToDTO).collect(Collectors.toList());
        return Response.success(dtos);
    }

    @GetMapping("/category/{categoryId}")
    public Response<?> getProductsByCategory(@PathVariable Integer categoryId) {
        try {
            log.info("查询分类ID: {}", categoryId);
            List<Product> products = productService.findByCategoryId(categoryId);
            log.info("查询到商品数量: {}", products != null ? products.size() : 0);
            List<ProductDTO> dtos = products.stream().map(this::convertToDTO).collect(Collectors.toList());
            return Response.success(dtos);
        } catch (Exception e) {
            log.error("查询分类产品失败, categoryId={}", categoryId, e);
            return Response.error(500, "查询分类产品失败: " + e.getMessage());
        }
    }

    @GetMapping("/search")
    public Response<List<ProductDTO>> searchProducts(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "default") String sort,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        try {
            if (keyword == null || keyword.trim().isEmpty()) {
                return Response.error(400, "搜索关键字不能为空");
            }
            // 过长的关键字直接截断，避免构造超长 LIKE 拖慢查询
            String trimmedKeyword = keyword.trim();
            if (trimmedKeyword.length() > 50) {
                trimmedKeyword = trimmedKeyword.substring(0, 50);
            }
            // 分页参数兜底，避免 pageSize 传 100000 把整表拉回内存
            int safePage = Math.max(page, 1);
            int safePageSize = Math.min(Math.max(pageSize, 1), 50);
            log.debug("搜索商品: keyword={}, sort={}, page={}, pageSize={}", trimmedKeyword, sort, safePage, safePageSize);

            // 用嵌套条件把 OR 括起来，避免后续追加 AND 条件时优先级错乱
            QueryWrapper<Product> wrapper = new QueryWrapper<>();
            String finalKeyword = trimmedKeyword;
            wrapper.and(w -> w.like("name", finalKeyword).or().like("description", finalKeyword));

            // 排序
            switch (sort) {
                case "sales":
                    // 由于Product实体类中没有sales字段，使用id字段替代
                    wrapper.orderByDesc("id");
                    break;
                case "price":
                    wrapper.orderByAsc("price");
                    break;
                default:
                    wrapper.orderByDesc("id");
            }

            // 分页交给 PageHelper，不再手工拼 LIMIT/OFFSET
            PageHelper.startPage(safePage, safePageSize);
            List<Product> products = productMapper.selectList(wrapper);
            log.debug("搜索完成，返回 {} 个商品", products.size());
            List<ProductDTO> dtos = products.stream().map(this::convertToDTO).collect(Collectors.toList());
            return Response.success(dtos);
        } catch (Exception e) {
            log.error("商品搜索失败", e);
            throw e;
        }
    }

    @GetMapping("/{id}")
    public Response<ProductDTO> getProductById(@PathVariable Integer id) {
        Product product = productService.findById(id);
        if (product == null) {
            return Response.error(404, "商品不存在");
        }
        return Response.success(convertToDTO(product));
    }
    
    /**
     * 更新商品信息
     */
    @PutMapping("/{id}")
    public Response<ProductDTO> updateProduct(@PathVariable Integer id, @RequestBody Product product,
                                              Authentication authentication) {
        if (!isAdmin(authentication)) {
            return Response.error(403, "需要管理员权限");
        }
        try {
            log.info("更新商品，商品ID: {}", id);
            
            // 检查商品是否存在
            Product existing = productService.findById(id);
            if (existing == null) {
                return Response.error(404, "商品不存在");
            }
            
            // 设置ID确保更新正确的记录
            product.setId(id);
            
            // 更新商品
            Product updated = productService.update(product);
            log.info("商品更新成功，商品ID: {}, 库存: {}", id, updated.getStock());
            
            return Response.success("更新成功", convertToDTO(updated));
        } catch (Exception e) {
            log.error("更新商品失败，商品ID: {}", id, e);
            return Response.error(500, "更新商品失败: " + e.getMessage());
        }
    }

    /**
     * 获取商品图片（直接返回图片字节流）
     */
    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> getProductImage(@PathVariable Integer id) {
        Product product = productService.findById(id);
        if (product == null) {
            return ResponseEntity.notFound().build();
        }
        
        // 从 products 表的 image_data 字段获取图片
        byte[] imageData = null;
        try {
            imageData = productMapper.getImageDataById(id);
            log.info("从products表image_data字段获取图片，商品ID: {}, 结果: {}", id, imageData != null ? "存在，大小: " + imageData.length + " bytes" : "null");
        } catch (Exception e) {
            log.warn("获取image_data失败，商品ID: {}, 错误: {}", id, e.getMessage());
        }
        
        if (imageData == null || imageData.length == 0) {
            log.warn("商品ID: {} 没有可用的图片数据", id);
            return ResponseEntity.notFound().build();
        }
        
        // 检查图片大小，超过5MB则返回错误（避免内存溢出）
        if (imageData.length > 5 * 1024 * 1024) {
            log.error("图片数据过大，商品ID: {}, 大小: {} bytes，超过5MB限制", id, imageData.length);
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
        }
        
        // 检测图片类型
        String mimeType = detectImageType(imageData);
        log.info("返回图片，商品ID: {}, MIME类型: {}, 大小: {} bytes", id, mimeType, imageData.length);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(mimeType));
        headers.setContentLength(imageData.length);
        
        return new ResponseEntity<>(imageData, headers, HttpStatus.OK);
    }

    /**
     * 将Product实体转换为DTO
     * 图片通过 /images/product/{id} 接口按需加载，不在JSON中嵌入Base64数据，避免响应体积过大导致界面卡死
     */
    private ProductDTO convertToDTO(Product product) {
        ProductDTO dto = new ProductDTO();
        dto.setId(product.getId());
        dto.setName(product.getName());
        dto.setDescription(product.getDescription());
        dto.setPrice(product.getPrice());
        dto.setStock(product.getStock());
        dto.setCategoryId(product.getCategoryId());
        dto.setCreatedAt(product.getCreatedAt());
        dto.setUpdatedAt(product.getUpdatedAt());

        // 优先使用图片接口按需加载（避免在列表接口中嵌入BLOB/Base64导致卡顿）
        dto.setImageUrl("/images/product/" + product.getId());

        return dto;
    }
    
    /**
     * 检测图片MIME类型
     */
    private String detectImageType(byte[] imageData) {
        if (imageData == null || imageData.length < 4) {
            return "image/jpeg";
        }
        // JPEG: FF D8 FF
        if (imageData[0] == (byte) 0xFF && imageData[1] == (byte) 0xD8 && imageData[2] == (byte) 0xFF) {
            return "image/jpeg";
        }
        // PNG: 89 50 4E 47
        if (imageData[0] == (byte) 0x89 && imageData[1] == (byte) 0x50 && 
            imageData[2] == (byte) 0x4E && imageData[3] == (byte) 0x47) {
            return "image/png";
        }
        // GIF: 47 49 46 38
        if (imageData[0] == (byte) 0x47 && imageData[1] == (byte) 0x49 && 
            imageData[2] == (byte) 0x46 && imageData[3] == (byte) 0x38) {
            return "image/gif";
        }
        // WebP: 52 49 46 46 ... 57 45 42 50
        if (imageData.length > 12 && imageData[8] == (byte) 0x57 && imageData[9] == (byte) 0x45 && 
            imageData[10] == (byte) 0x42 && imageData[11] == (byte) 0x50) {
            return "image/webp";
        }
        // 默认返回JPEG
        return "image/jpeg";
    }

    /**
     * 商品DTO，用于返回给前端
     */
    public static class ProductDTO {
        private Integer id;
        private String name;
        private String description;
        private java.math.BigDecimal price;
        private String imageUrl;
        private Integer stock;
        private Integer categoryId;
        private java.time.LocalDateTime createdAt;
        private java.time.LocalDateTime updatedAt;
        
        // Getters and Setters
        public Integer getId() { return id; }
        public void setId(Integer id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public java.math.BigDecimal getPrice() { return price; }
        public void setPrice(java.math.BigDecimal price) { this.price = price; }
        public String getImageUrl() { return imageUrl; }
        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
        public Integer getStock() { return stock; }
        public void setStock(Integer stock) { this.stock = stock; }
        public Integer getCategoryId() { return categoryId; }
        public void setCategoryId(Integer categoryId) { this.categoryId = categoryId; }
        public java.time.LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(java.time.LocalDateTime createdAt) { this.createdAt = createdAt; }
        public java.time.LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(java.time.LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }

    /**
     * 检查库存
     */
    @PostMapping("/{id}/stock/check")
    public Response<StockCheckResult> checkStock(@PathVariable Integer id, @RequestBody StockCheckRequest request) {
        Product product = productService.findById(id);
        if (product == null) {
            return Response.error(404, "商品不存在");
        }
        
        int requestedQuantity = request.getQuantity();
        int currentStock = product.getStock();
        
        StockCheckResult result = new StockCheckResult();
        result.setCurrentStock(currentStock);
        result.setRequestedQuantity(requestedQuantity);
        
        if (currentStock >= requestedQuantity) {
            result.setAvailable(true);
            result.setMessage("库存充足");
        } else {
            result.setAvailable(false);
            result.setMessage("库存不足，当前库存: " + currentStock);
        }
        
        log.info("库存检查 - 商品ID: {}, 请求数量: {}, 当前库存: {}, 是否可用: {}", 
                id, requestedQuantity, currentStock, result.isAvailable());
        
        return Response.success(result);
    }

    /**
     * 获取商品库存
     */
    @GetMapping("/{id}/stock")
    public Response<StockInfo> getProductStock(@PathVariable Integer id) {
        Product product = productService.findById(id);
        if (product == null) {
            return Response.error(404, "商品不存在");
        }
        
        StockInfo stockInfo = new StockInfo();
        stockInfo.setProductId(id);
        stockInfo.setStock(product.getStock());
        
        return Response.success(stockInfo);
    }

    /**
     * 扣减库存
     */
    @PostMapping("/{id}/stock/deduct")
    public Response<StockDeductResult> deductStock(@PathVariable Integer id, @RequestBody StockDeductRequest request,
                                                   Authentication authentication) {
        if (!isAdmin(authentication)) {
            return Response.error(403, "需要管理员权限");
        }
        Product product = productService.findById(id);
        if (product == null) {
            return Response.error(404, "商品不存在");
        }
        
        int deductQuantity = request.getQuantity();
        int currentStock = product.getStock();
        
        if (currentStock < deductQuantity) {
            StockDeductResult result = new StockDeductResult();
            result.setSuccess(false);
            result.setMessage("库存不足，当前库存: " + currentStock);
            return Response.success(result);
        }
        
        // 乐观锁更新库存
        int updated = productMapper.updateStock(id, deductQuantity);
        
        StockDeductResult result = new StockDeductResult();
        if (updated > 0) {
            int newStock = currentStock - deductQuantity;
            result.setSuccess(true);
            result.setMessage("库存扣减成功");
            result.setNewStock(newStock);
            log.info("库存扣减成功 - 商品ID: {}, 扣减数量: {}, 新库存: {}", id, deductQuantity, newStock);
        } else {
            result.setSuccess(false);
            result.setMessage("库存更新失败，可能已被其他订单占用");
            log.warn("库存扣减失败 - 商品ID: {}, 扣减数量: {}", id, deductQuantity);
        }
        
        return Response.success(result);
    }

    /**
     * 管理员判定：从当前登录身份查库确认 role=1。
     * 不依赖 JWT 里的 role 声明，避免旧 token 或角色变更后权限不一致。
     */
    private boolean isAdmin(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return false;
        }
        try {
            Integer userId = Integer.parseInt(String.valueOf(authentication.getPrincipal()));
            User user = userService.findById(userId);
            return user != null && Integer.valueOf(1).equals(user.getRole());
        } catch (NumberFormatException e) {
            log.warn("管理员校验失败：无法解析用户ID - {}", authentication.getPrincipal());
            return false;
        }
    }

    // 内部类：请求和响应结构
    public static class StockCheckRequest {
        private int quantity;
        
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
    }
    
    public static class StockCheckResult {
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
    
    public static class StockInfo {
        private int productId;
        private int stock;
        
        public int getProductId() { return productId; }
        public void setProductId(int productId) { this.productId = productId; }
        public int getStock() { return stock; }
        public void setStock(int stock) { this.stock = stock; }
    }
    
    public static class StockDeductRequest {
        private int quantity;
        
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
    }
    
    public static class StockDeductResult {
        private boolean success;
        private String message;
        private int newStock;
        
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public int getNewStock() { return newStock; }
        public void setNewStock(int newStock) { this.newStock = newStock; }
    }
}