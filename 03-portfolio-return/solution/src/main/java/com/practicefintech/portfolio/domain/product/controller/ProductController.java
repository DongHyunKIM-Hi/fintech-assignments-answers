package com.practicefintech.portfolio.domain.product.controller;

import com.practicefintech.portfolio.common.dto.PageResponse;
import com.practicefintech.portfolio.common.enums.CurrencyCode;
import com.practicefintech.portfolio.common.enums.ProductType;
import com.practicefintech.portfolio.domain.product.model.response.ProductResponse;
import com.practicefintech.portfolio.domain.product.service.ProductService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/products")
    public PageResponse<ProductResponse> search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) ProductType type,
            @RequestParam(required = false) CurrencyCode currency,
            @RequestParam(required = false) Integer riskLevel,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page, size 범위를 확인하세요.");
        }
        return service.search(query, type, currency, riskLevel, sort, page, size);
    }
}
