package com.petshop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.petshop.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.type.JdbcType;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {
    
    @Select("SELECT image_data FROM products WHERE id = #{id}")
    @Results({
        @Result(column = "image_data", property = "imageData", jdbcType = JdbcType.BLOB, typeHandler = org.apache.ibatis.type.BlobTypeHandler.class)
    })
    byte[] getImageDataById(@Param("id") Integer id);
    
    @Select("SELECT img_data FROM products WHERE id = #{id}")
    @Results({
        @Result(column = "img_data", property = "imgData", jdbcType = JdbcType.BLOB, typeHandler = org.apache.ibatis.type.BlobTypeHandler.class)
    })
    byte[] getImgDataById(@Param("id") Integer id);
    
    /**
     * 乐观锁更新库存
     * @param id 商品ID
     * @param quantity 扣减数量
     * @return 更新成功的行数
     */
    @org.apache.ibatis.annotations.Update("UPDATE products SET stock = stock - #{quantity} WHERE id = #{id} AND stock >= #{quantity}")
    int updateStock(@Param("id") Integer id, @Param("quantity") Integer quantity);

    /**
     * 返还库存（订单取消、超时未支付时调用）
     * @param id 商品ID
     * @param quantity 返还数量
     * @return 更新成功的行数
     */
    @org.apache.ibatis.annotations.Update("UPDATE products SET stock = stock + #{quantity} WHERE id = #{id}")
    int restoreStock(@Param("id") Integer id, @Param("quantity") Integer quantity);
}