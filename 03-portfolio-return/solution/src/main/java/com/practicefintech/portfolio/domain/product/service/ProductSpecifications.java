package com.practicefintech.portfolio.domain.product.service;

import com.practicefintech.portfolio.common.entity.Product;
import com.practicefintech.portfolio.common.enums.CurrencyCode;
import com.practicefintech.portfolio.common.enums.ProductType;
import org.springframework.data.jpa.domain.Specification;

/** 상품 검색·필터 조건을 동적으로 조합한다. 서로 다른 조건은 모두 AND로 묶인다. */
public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    public static Specification<Product> query(String query) {
        return (root, cq, cb) -> {
            String like = "%" + query.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("code")), like),
                    cb.like(cb.lower(root.get("name")), like),
                    cb.like(cb.lower(root.get("nameEn")), like)
            );
        };
    }

    public static Specification<Product> type(ProductType type) {
        return (root, cq, cb) -> cb.equal(root.get("type"), type);
    }

    public static Specification<Product> currency(CurrencyCode currency) {
        return (root, cq, cb) -> cb.equal(root.get("currency"), currency);
    }

    public static Specification<Product> riskLevel(int riskLevel) {
        return (root, cq, cb) -> cb.equal(root.get("riskLevel"), riskLevel);
    }
}
