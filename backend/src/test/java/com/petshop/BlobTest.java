package com.petshop;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Blob;
import java.sql.SQLException;
import java.util.Base64;

public class BlobTest {

    public static void main(String[] args) {
        ApplicationContext context = SpringApplication.run(PetShopApplication.class, args);
        JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
        
        // 测试图片（一个简单的1x1红色像素的PNG图片，Base64编码）
        String base64Image = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFBQIAX8jx0gAAAABJRU5ErkJggg==";
        byte[] imageData = Base64.getDecoder().decode(base64Image);
        
        System.out.println("测试图片数据长度: " + imageData.length + " bytes");
        
        // 将图片数据写入数据库（更新一个现有商品）
        try {
            String sql = "UPDATE products SET image_data = ? WHERE id = 1";
            jdbcTemplate.update(sql, new javax.sql.rowset.serial.SerialBlob(imageData));
            System.out.println("成功将BLOB数据写入数据库");
        } catch (SQLException e) {
            System.out.println("写入BLOB数据失败: " + e.getMessage());
            e.printStackTrace();
        }
        
        // 测试读取BLOB数据
        try {
            String sql = "SELECT image_data FROM products WHERE id = 1";
            byte[] result = jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
                Blob blob = rs.getBlob("image_data");
                if (blob != null && blob.length() > 0) {
                    return blob.getBytes(1, (int) blob.length());
                }
                return null;
            });
            
            if (result != null) {
                System.out.println("成功从数据库读取BLOB数据，长度: " + result.length + " bytes");
                System.out.println("数据匹配: " + java.util.Arrays.equals(imageData, result));
            } else {
                System.out.println("读取BLOB数据失败，返回null");
            }
        } catch (Exception e) {
            System.out.println("读取BLOB数据失败: " + e.getMessage());
            e.printStackTrace();
        }
        
        // 关闭应用
        SpringApplication.exit(context, () -> 0);
    }
}