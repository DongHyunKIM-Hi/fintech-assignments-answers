package com.practicefintech.portfolio.domain.product.service;

import com.practicefintech.portfolio.common.dto.PageResponse;
import com.practicefintech.portfolio.common.entity.Product;
import com.practicefintech.portfolio.domain.price.model.dto.ExternalPriceQuote;
import com.practicefintech.portfolio.domain.price.service.PriceClient;
import com.practicefintech.portfolio.domain.product.model.response.ProductResponse;
import com.practicefintech.portfolio.domain.product.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 상품 조회.
 *
 * <p>이 단계에서 일부러 하지 않는 것 (모두 2단계 몫)
 * <ul>
 *   <li>검색({@code query}), 필터({@code type}, {@code currency}, {@code riskLevel}),
 *       정렬({@code sort}), 페이지({@code page}, {@code size}) — 지금은 저장된 행을 전부 돌려준다.</li>
 * </ul>
 *
 * <p>시세는 요청이 올 때마다 시세 서버에서 받아 온다. 보관하지 않기 때문에
 * 시세 서버가 멈추면 이 조회도 함께 실패한다. (요구사항 R6 위반 — 3단계에서 스냅샷으로 해결)
 */
@Service
public class ProductService {

    private final ProductRepository repository;
    private final PriceClient priceClient;

    public ProductService(ProductRepository repository, PriceClient priceClient) {
        this.repository = repository;
        this.priceClient = priceClient;
    }

    public PageResponse<ProductResponse> findAll() {
        Map<String, BigDecimal> prices = new HashMap<>();
        for (ExternalPriceQuote quote : priceClient.fetchPrices().getPrices()) {
            prices.put(quote.getCode(), quote.getPrice());
        }

        List<Product> products = repository.findAll();
        List<ProductResponse> items = products.stream()
                .map(p -> new ProductResponse(p.getCode(), p.getName(), p.getNameEn(), p.getType(), p.getCurrency(),
                        p.getRiskLevel(), p.getIssuer(), p.getListedDate(), prices.get(p.getCode())))
                .toList();

        // 페이지 처리가 없으므로 한 페이지에 전부 담는다.
        return new PageResponse<>(items, 0, items.size(), items.size(), 1);
    }
}
