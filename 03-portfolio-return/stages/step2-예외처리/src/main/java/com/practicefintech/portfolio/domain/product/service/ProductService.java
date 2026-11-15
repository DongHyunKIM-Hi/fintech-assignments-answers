package com.practicefintech.portfolio.domain.product.service;

import com.practicefintech.portfolio.common.dto.PageResponse;
import com.practicefintech.portfolio.common.entity.Product;
import com.practicefintech.portfolio.common.enums.CurrencyCode;
import com.practicefintech.portfolio.common.enums.ProductType;
import com.practicefintech.portfolio.domain.price.model.dto.ExternalPriceQuote;
import com.practicefintech.portfolio.domain.price.service.PriceClient;
import com.practicefintech.portfolio.domain.product.model.response.ProductResponse;
import com.practicefintech.portfolio.domain.product.repository.ProductRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 상품 조회. 검색, 필터, 정렬, 페이지와 파라미터 검증까지 2단계에서 모두 채운다.
 *
 * <p>이 단계에서 일부러 하지 않는 것: 시세를 보관하지 않는다.
 * 조회가 올 때마다 {@link PriceClient}로 시세 서버를 부르기 때문에, 시세 서버가 멈추면
 * 이 조회도 함께 500이 된다. (요구사항 R6 위반 — 3단계에서 스냅샷으로 해결)
 */
@Service
public class ProductService {

    private static final Set<String> SORT_FIELDS = Set.of("code", "name", "listedDate", "riskLevel");

    private final ProductRepository repository;
    private final PriceClient priceClient;

    public ProductService(ProductRepository repository, PriceClient priceClient) {
        this.repository = repository;
        this.priceClient = priceClient;
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

        // page*size가 너무 크면 Spring Data가 offset 계산에서 예외를 던지므로, 미리 빈 페이지로 응답한다.
        if ((long) page * size > Integer.MAX_VALUE) {
            return new PageResponse<>(List.of(), page, size, 0, 0);
        }

        var pageResult = repository.findAll(spec, PageRequest.of(page, size, sortOrder));

        // 조회할 때마다 시세 서버를 부른다. 3단계에서 PriceSnapshotStore를 읽는 것으로 바뀐다.
        Map<String, BigDecimal> prices = new HashMap<>();
        for (ExternalPriceQuote quote : priceClient.fetchPrices().getPrices()) {
            prices.put(quote.getCode(), quote.getPrice());
        }

        List<ProductResponse> items = pageResult.getContent().stream()
                .map(p -> new ProductResponse(p.getCode(), p.getName(), p.getNameEn(), p.getType(), p.getCurrency(),
                        p.getRiskLevel(), p.getIssuer(), p.getListedDate(), prices.get(p.getCode())))
                .toList();

        return new PageResponse<>(items, page, size, pageResult.getTotalElements(), pageResult.getTotalPages());
    }

    private Sort parseSort(String sort) {
        String field = "code";
        Sort.Direction direction = Sort.Direction.ASC;
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",");
            if (parts.length > 2 || !SORT_FIELDS.contains(parts[0])) {
                throw new IllegalArgumentException("지원하지 않는 정렬 형식입니다: " + sort);
            }
            field = parts[0];
            if (parts.length > 1) {
                if (!"asc".equalsIgnoreCase(parts[1]) && !"desc".equalsIgnoreCase(parts[1])) {
                    throw new IllegalArgumentException("정렬 방향은 asc 또는 desc여야 합니다: " + parts[1]);
                }
                direction = "desc".equalsIgnoreCase(parts[1]) ? Sort.Direction.DESC : Sort.Direction.ASC;
            }
        }
        // 위험등급이 비어 있는(null) 상품은 정렬에서 맨 뒤로 보낸다 (가정 G5).
        Sort.Order order = new Sort.Order(direction, field, Sort.NullHandling.NULLS_LAST);
        // code를 2차 키로 둬야 같은 값끼리의 순서가 페이지마다 달라지지 않는다.
        if (field.equals("code")) {
            return Sort.by(order);
        }
        return Sort.by(order, Sort.Order.asc("code"));
    }
}
