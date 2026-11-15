package com.practicefintech.portfolio.domain.product.controller;

import com.practicefintech.portfolio.common.dto.PageResponse;
import com.practicefintech.portfolio.domain.product.model.response.ProductResponse;
import com.practicefintech.portfolio.domain.product.service.ProductService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 1단계는 파라미터를 하나도 받지 않는다. {@code query}, {@code type}, {@code currency},
 * {@code riskLevel}, {@code sort}, {@code page}, {@code size}는 2단계에서 받는다.
 * 파라미터 검증(400 INVALID_REQUEST)도 2단계 몫이다.
 */
@RestController
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/products")
    public PageResponse<ProductResponse> search() {
        return service.findAll();
    }
}
