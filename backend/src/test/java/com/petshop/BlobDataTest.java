package com.petshop;

import com.petshop.entity.Product;
import com.petshop.mapper.ProductMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Blob;
import java.util.Base64;

@SpringBootTest
public class BlobDataTest {

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    public void testBlobWriteAndRead() {
        // 使用一个存在的商品ID（从日志中看到有ID如10730, 10732, 10735等）
        int productId = 10730;
        
        // 1x1红色像素PNG图片的Base64编码
        String base64Image = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFBQIAX8jx0gAAAABJRU5ErkJggg==";
        byte[] imageData = Base64.getDecoder().decode(base64Image);
        
        System.out.println("测试图片数据长度: " + imageData.length + " bytes");
        System.out.println("使用商品ID: " + productId);
        
        // 测试写入BLOB数据到products表
        try {
            String sql = "UPDATE products SET image_data = ? WHERE id = ?";
            int rowsUpdated = jdbcTemplate.update(sql, new javax.sql.rowset.serial.SerialBlob(imageData), productId);
            System.out.println("成功更新 " + rowsUpdated + " 行数据");
        } catch (Exception e) {
            System.out.println("写入BLOB数据失败: " + e.getMessage());
            e.printStackTrace();
            return;
        }
        
        // 测试从products表读取BLOB数据
        try {
            String sql = "SELECT image_data FROM products WHERE id = ?";
            byte[] result = jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
                Blob blob = rs.getBlob("image_data");
                if (blob != null && blob.length() > 0) {
                    return blob.getBytes(1, (int) blob.length());
                }
                return null;
            }, productId);
            
            if (result != null) {
                System.out.println("成功读取BLOB数据，长度: " + result.length + " bytes");
                System.out.println("数据匹配: " + java.util.Arrays.equals(imageData, result));
            } else {
                System.out.println("读取BLOB数据失败，返回null");
            }
        } catch (Exception e) {
            System.out.println("读取BLOB数据失败: " + e.getMessage());
            e.printStackTrace();
        }
        
        // 测试使用MyBatis Plus读取
        try {
            Product product = productMapper.selectById(productId);
            if (product != null && product.getImageData() != null) {
                System.out.println("MyBatis Plus读取成功，数据长度: " + product.getImageData().length + " bytes");
            } else {
                System.out.println("MyBatis Plus读取失败");
            }
        } catch (Exception e) {
            System.out.println("MyBatis Plus读取失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}