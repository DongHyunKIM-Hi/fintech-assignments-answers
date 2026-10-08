package com.practicefintech.portfolio.domain.product.service;

import com.practicefintech.portfolio.common.dto.PageResponse;
import com.practicefintech.portfolio.common.entity.Product;
import com.practicefintech.portfolio.common.enums.CurrencyCode;
import com.practicefintech.portfolio.common.enums.ProductType;
import com.practicefintech.portfolio.domain.price.service.PriceSnapshotStore;
import com.practicefintech.portfolio.domain.product.model.response.ProductResponse;
import com.practicefintech.portfolio.domain.product.repository.ProductRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class ProductService {

    private static final Set<String> SORT_FIELDS = Set.of("code", "name", "listedDate", "riskLevel");

    private final ProductRepository repository;
    private final PriceSnapshotStore priceSnapshotStore;

    public ProductService(ProductRepository repository, PriceSnapshotStore priceSnapshotStore) {
        this.repository = repository;
        this.priceSnapshotStore = priceSnapshotStore;
    }

    public PageResponse<ProductResponse> search(String query, ProductType type, CurrencyCode currency,
                                                 Integer riskLevel, String sort, int page, int size) {
        Specification<Product> spec = Specification.allOf();
        if (query != null && !query.isBlank()) {
            spec = spec.and(ProductSpecifications.query(query));
        }
        if (type != null) {
            spec = spec.and(ProductSpecifications.type(type));
        }
        if (currency != null) {
            spec = spec.and(ProductSpecifications.currency(currency));
        }
        if (riskLevel != null) {
            spec = spec.and(ProductSpecifications.riskLevel(riskLevel));
        }

        Sort sortOrder = parseSort(sort);
        var pageResult = repository.findAll(spec, PageRequest.of(page, size, sortOrder));

        List<ProductResponse> items = pageResult.getContent().stream()
                .map(p -> new ProductResponse(p.getCode(), p.getName(), p.getNameEn(), p.getType(), p.getCurrency(),
                        p.getRiskLevel(), p.getIssuer(), p.getListedDate(), priceSnapshotStore.getPrice(p.getCode())))
                .toList();

        return new PageResponse<>(items, page, size, pageResult.getTotalElements(), pageResult.getTotalPages());
    }

    private Sort parseSort(String sort) {
        String field = "code";
        Sort.Direction direction = Sort.Direction.ASC;
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",");
            if (!SORT_FIELDS.contains(parts[0])) {
                throw new IllegalArgumentException("지원하지 않는 정렬 필드입니다: " + parts[0]);
            }
            field = parts[0];
            if (parts.length > 1) {
                direction = "desc".equalsIgnoreCase(parts[1]) ? Sort.Direction.DESC : Sort.Direction.ASC;
            }
        }
        // 위험등급이 비어 있는(null) 상품은 정렬에서 맨 뒤로 보낸다 (가정 G5).
        Sort.Order order = new Sort.Order(direction, field, Sort.NullHandling.NULLS_LAST);
        return Sort.by(order);
    }
}
