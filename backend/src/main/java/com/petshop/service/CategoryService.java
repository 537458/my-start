package com.petshop.service;

import com.petshop.entity.Category;

import java.util.List;

public interface CategoryService {
    Category save(Category category);
    Category findById(Integer id);
    Category update(Category category);
    void delete(Integer id);
    List<Category> findAll();
}
