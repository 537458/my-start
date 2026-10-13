package com.petshop.controller;

import com.petshop.common.Response;
import com.petshop.entity.Category;
import com.petshop.service.CategoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/categories")
public class CategoryController {

    private static final Logger log = LoggerFactory.getLogger(CategoryController.class);

    @Autowired
    private CategoryService categoryService;

    @GetMapping
    public Response<List<Category>> findAll() {
        List<Category> list = categoryService.findAll();
        log.info("返回分类列表: {}", list.stream().map(Category::getName).collect(java.util.stream.Collectors.toList()));
        return Response.success(list);
    }

    @GetMapping("/{id}")
    public Response<Category> findById(@PathVariable Integer id) {
        Category category = categoryService.findById(id);
        if (category == null) {
            return Response.error(404, "分类不存在");
        }
        return Response.success(category);
    }

    @PostMapping
    public Response<Category> create(@RequestBody Category category) {
        Category created = categoryService.save(category);
        log.info("分类创建成功: {}", category.getName());
        return Response.success("创建成功", created);
    }

    @PutMapping("/{id}")
    public Response<Category> update(@PathVariable Integer id, @RequestBody Category category) {
        category.setId(id);
        Category updated = categoryService.update(category);
        log.info("分类更新成功: {}", id);
        return Response.success(updated);
    }

    @DeleteMapping("/{id}")
    public Response<Void> delete(@PathVariable Integer id) {
        categoryService.delete(id);
        log.info("分类删除成功: {}", id);
        return Response.success("删除成功", null);
    }
}
