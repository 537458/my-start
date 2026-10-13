package com.petshop.service;

import com.petshop.entity.Product;

import java.util.List;

public interface ProductService {
    Product save(Product product);
    Product findById(Integer id);
    Product update(Product product);
    void delete(Integer id);
    List<Product> findAll();
    List<Product> findByCategoryId(Integer categoryId);
    List<Product> search(String keyword);
}