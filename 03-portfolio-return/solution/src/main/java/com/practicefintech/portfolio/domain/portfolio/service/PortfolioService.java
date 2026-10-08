package com.practicefintech.portfolio.domain.portfolio.service;

import com.practicefintech.portfolio.common.dto.PageResponse;
import com.practicefintech.portfolio.common.entity.Holding;
import com.practicefintech.portfolio.common.entity.Portfolio;
import com.practicefintech.portfolio.common.entity.Product;
import com.practicefintech.portfolio.common.enums.CurrencyCode;
import com.practicefintech.portfolio.common.exception.ApiException;
import com.practicefintech.portfolio.domain.portfolio.model.request.HoldingRequest;
import com.practicefintech.portfolio.domain.portfolio.model.request.PortfolioRequest;
import com.practicefintech.portfolio.domain.portfolio.model.response.HoldingView;
import com.practicefintech.portfolio.domain.portfolio.model.response.PortfolioDetailResponse;
import com.practicefintech.portfolio.domain.portfolio.model.response.PortfolioListItem;
import com.practicefintech.portfolio.domain.portfolio.model.response.PortfolioSummary;
import com.practicefintech.portfolio.domain.portfolio.repository.PortfolioRepository;
import com.practicefintech.portfolio.domain.price.service.PriceSnapshotStore;
import com.practicefintech.portfolio.domain.product.repository.ProductRepository;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 포트폴리오 생성·조회·수정·삭제와 수익률 계산. 이 클래스가 계약 6장(계산 규칙)과
 * 가정 G1(구성 변경), G2(오늘 손익), G3(통화 혼재), G4(시세 실패)를 구현하는 지점이다.
 * 자세한 이유는 {@code docs/assumptions.md} 참고.
 *
 * 락의 폭: 포트폴리오 ID 단위. {@link #portfolioLocks}는 같은 포트폴리오를 가리키는
 * update/delete/조회(오늘 기준값 갱신 포함)가 겹치지 않도록 한다. 그렇지 않으면 같은
 * 포트폴리오를 거의 동시에 두 번 수정했을 때 한쪽이 조용히 덮여 사라지는 lost update가
 * 생길 수 있다. 서로 다른 포트폴리오끼리는 완전히 병렬로 처리된다.
 */
@Service
@Transactional
public class PortfolioService {

    private static final Set<String> SORT_FIELDS = Set.of("name", "createdAt", "returnRate", "totalValue");

    private final PortfolioRepository portfolioRepository;
    private final ProductRepository productRepository;
    private final PriceSnapshotStore priceSnapshotStore;
    private final ConcurrentHashMap<Long, Object> portfolioLocks = new ConcurrentHashMap<>();

    public PortfolioService(PortfolioRepository portfolioRepository, ProductRepository productRepository,
                             PriceSnapshotStore priceSnapshotStore) {
        this.portfolioRepository = portfolioRepository;
        this.productRepository = productRepository;
        this.priceSnapshotStore = priceSnapshotStore;
    }

    public PortfolioDetailResponse create(PortfolioRequest request) {
        validateNoDuplicateCodes(request);
        Map<String, Product> products = resolveProducts(request);

        List<Holding> holdings = new ArrayList<>();
        for (HoldingRequest h : request.getHoldings()) {
            Product product = products.get(h.getCode());
            BigDecimal priceNative = requirePrice(product);
            BigDecimal fxRate = requireFxIfNeeded(product);
            holdings.add(new Holding(h.getCode(), h.getQuantity(), priceNative, fxRate));
        }

        Portfolio portfolio = new Portfolio(request.getName(), LocalDateTime.now());
        portfolio.replaceHoldings(holdings);
        // 생성 시점에는 매입가 = 현재가이므로 평가금액 = 매입금액. 오늘 손익은 0부터 시작한다 (가정 G2).
        BigDecimal initialValue = computeTotalValue(portfolio).setScale(0, RoundingMode.DOWN);
        portfolio.setTodayBaseline(initialValue, LocalDate.now());

        portfolioRepository.save(portfolio);
        return toDetail(portfolio);
    }

    public PortfolioDetailResponse getDetail(Object rawId) {
        Long id = parseId(rawId);
        // toDetail()이 "오늘 기준값"을 처음 조회 시점에 갱신할 수 있어(G2), update/delete와
        // 같은 락을 쓴다. 그렇지 않으면 조회와 수정이 겹칠 때 기준값이 꼬일 수 있다.
        synchronized (lockFor(id)) {
            Portfolio portfolio = findOrThrow(id);
            return toDetail(portfolio);
        }
    }

    public PageResponse<PortfolioListItem> list(String query, String sort, int page, int size) {
        List<Portfolio> all = (query == null || query.isBlank())
                ? portfolioRepository.findAll()
                : portfolioRepository.findByNameContainingIgnoreCase(query);

        List<PortfolioListItem> items = all.stream()
                .map(p -> {
                    Summary s = computeSummary(p, false);
                    return new PortfolioListItem(p.getId(), p.getName(), s.totalValue, s.returnRate, p.getCreatedAt());
                })
                .collect(Collectors.toCollection(ArrayList::new));

        sortItems(items, sort);

        int fromIndex = Math.min(page * size, items.size());
        int toIndex = Math.min(fromIndex + size, items.size());
        List<PortfolioListItem> pageItems = items.subList(fromIndex, toIndex);
        int totalPages = size == 0 ? 0 : (int) Math.ceil(items.size() / (double) size);
        return new PageResponse<>(pageItems, page, size, items.size(), totalPages);
    }

    public PortfolioDetailResponse update(Object rawId, PortfolioRequest request) {
        Long id = parseId(rawId);
        synchronized (lockFor(id)) {
            Portfolio portfolio = findOrThrow(id);
            validateNoDuplicateCodes(request);
            Map<String, Product> products = resolveProducts(request);

            Map<String, Holding> oldByCode = new HashMap<>();
            for (Holding h : portfolio.getHoldings()) {
                oldByCode.put(h.getCode(), h);
            }

            List<Holding> newHoldings = new ArrayList<>();
            for (HoldingRequest hr : request.getHoldings()) {
                Product product = products.get(hr.getCode());
                Holding old = oldByCode.get(hr.getCode());

                if (old == null) {
                    // 새로 추가된 종목: 지금 시세로 담는다.
                    BigDecimal priceNative = requirePrice(product);
                    BigDecimal fxRate = requireFxIfNeeded(product);
                    newHoldings.add(new Holding(hr.getCode(), hr.getQuantity(), priceNative, fxRate));
                } else if (hr.getQuantity() == old.getQuantity()) {
                    // 그대로: 매입가 유지.
                    newHoldings.add(new Holding(hr.getCode(), hr.getQuantity(), old.getPurchasePriceNative(), old.getPurchaseFxRate()));
                } else if (hr.getQuantity() > old.getQuantity()) {
                    // 수량 증가: 늘어난 만큼은 지금 시세로 사서 평균 단가를 새로 계산한다 (가정 G1).
                    BigDecimal currentPriceNative = requirePrice(product);
                    BigDecimal currentFx = requireFxIfNeeded(product);
                    long addedQty = hr.getQuantity() - old.getQuantity();
                    BigDecimal oldCostKrw = old.costInKrw();
                    BigDecimal addedUnitCostKrw = currentFx == null ? currentPriceNative : currentPriceNative.multiply(currentFx);
                    BigDecimal newCostKrw = oldCostKrw.add(addedUnitCostKrw.multiply(BigDecimal.valueOf(addedQty)));
                    BigDecimal fxForBlend = currentFx == null ? BigDecimal.ONE : currentFx;
                    BigDecimal blendedPriceNative = newCostKrw
                            .divide(BigDecimal.valueOf(hr.getQuantity()), 10, RoundingMode.HALF_UP)
                            .divide(fxForBlend, 10, RoundingMode.HALF_UP);
                    newHoldings.add(new Holding(hr.getCode(), hr.getQuantity(), blendedPriceNative, currentFx));
                } else {
                    // 수량 감소: 매입가는 그대로 두어 원가가 비례해서 줄어들게 한다 (가정 G1).
                    newHoldings.add(new Holding(hr.getCode(), hr.getQuantity(), old.getPurchasePriceNative(), old.getPurchaseFxRate()));
                }
            }
            // request에 없는 기존 종목은 newHoldings에 담기지 않으므로 자연히 원가에서 제외된다.

            portfolio.setName(request.getName());
            portfolio.replaceHoldings(newHoldings);
            portfolioRepository.save(portfolio);
            return toDetail(portfolio);
        }
    }

    public void delete(Object rawId) {
        Long id = parseId(rawId);
        synchronized (lockFor(id)) {
            Portfolio portfolio = findOrThrow(id);
            portfolioRepository.delete(portfolio);
        }
    }

    // ---- 내부 계산 ----

    @Getter
    private static class Summary {
        private final BigDecimal totalCost;
        private final BigDecimal totalValue;
        private final BigDecimal profit;
        private final BigDecimal returnRate;
        private final BigDecimal todayProfit;
        private final Instant asOf;
        private final boolean stale;

        Summary(BigDecimal totalCost, BigDecimal totalValue, BigDecimal profit, BigDecimal returnRate,
                BigDecimal todayProfit, Instant asOf, boolean stale) {
            this.totalCost = totalCost;
            this.totalValue = totalValue;
            this.profit = profit;
            this.returnRate = returnRate;
            this.todayProfit = todayProfit;
            this.asOf = asOf;
            this.stale = stale;
        }
    }

    private Summary computeSummary(Portfolio portfolio, boolean allowBaselineUpdate) {
        BigDecimal totalCost = BigDecimal.ZERO;
        for (Holding h : portfolio.getHoldings()) {
            totalCost = totalCost.add(h.costInKrw());
        }
        BigDecimal totalValueRaw = computeTotalValue(portfolio);

        BigDecimal roundedCost = totalCost.setScale(0, RoundingMode.DOWN);
        BigDecimal roundedValue = totalValueRaw.setScale(0, RoundingMode.DOWN);
        BigDecimal profit = roundedValue.subtract(roundedCost);
        BigDecimal returnRate = roundedCost.signum() == 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : profit.divide(roundedCost, 10, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, RoundingMode.HALF_UP);

        LocalDate today = LocalDate.now();
        if (allowBaselineUpdate && !today.equals(portfolio.getTodayBaselineDate())) {
            // 오늘 날짜로 처음 조회된 시점 = "오늘"의 기준으로 삼는다 (가정 G2).
            portfolio.setTodayBaseline(roundedValue, today);
        }
        BigDecimal baseline = portfolio.getTodayBaselineValue() == null ? roundedCost : portfolio.getTodayBaselineValue();
        BigDecimal todayProfit = roundedValue.subtract(baseline);

        Instant asOf = priceSnapshotStore.getAsOf();
        boolean stale = priceSnapshotStore.isStale();
        return new Summary(roundedCost, roundedValue, profit, returnRate, todayProfit, asOf, stale);
    }

    private BigDecimal computeTotalValue(Portfolio portfolio) {
        BigDecimal fxRate = priceSnapshotStore.getFxRate();
        BigDecimal total = BigDecimal.ZERO;
        for (Holding h : portfolio.getHoldings()) {
            BigDecimal currentPriceNative = priceSnapshotStore.getPrice(h.getCode());
            if (currentPriceNative == null) {
                // 시세가 한 번도 없었던 예외적인 경우: 매입가로 대체해 계산을 이어간다 (G4 보수적 대응).
                currentPriceNative = h.getPurchasePriceNative();
            }
            boolean isUsd = h.getPurchaseFxRate() != null;
            BigDecimal unitValueKrw = currentPriceNative;
            if (isUsd) {
                BigDecimal fx = fxRate != null ? fxRate : h.getPurchaseFxRate();
                unitValueKrw = currentPriceNative.multiply(fx);
            }
            total = total.add(unitValueKrw.multiply(BigDecimal.valueOf(h.getQuantity())));
        }
        return total;
    }

    private PortfolioDetailResponse toDetail(Portfolio portfolio) {
        Summary s = computeSummary(portfolio, true);

        List<HoldingView> holdingViews = portfolio.getHoldings().stream()
                .map(h -> {
                    int scale = h.getPurchaseFxRate() != null ? 2 : 0; // 달러 상품은 소수 둘째 자리, 원화는 정수
                    BigDecimal current = priceSnapshotStore.getPrice(h.getCode());
                    BigDecimal currentDisplay = (current != null ? current : h.getPurchasePriceNative())
                            .setScale(scale, RoundingMode.HALF_UP);
                    BigDecimal purchaseDisplay = h.getPurchasePriceNative().setScale(scale, RoundingMode.HALF_UP);
                    return new HoldingView(h.getCode(), h.getQuantity(), purchaseDisplay, currentDisplay);
                })
                .toList();

        PortfolioSummary summary = new PortfolioSummary(
                s.getTotalCost(), s.getTotalValue(), s.getProfit(), s.getReturnRate(), s.getTodayProfit(), s.getAsOf(),
                s.isStale() ? Boolean.TRUE : null);

        return new PortfolioDetailResponse(portfolio.getId(), portfolio.getName(), portfolio.getCreatedAt(),
                holdingViews, summary);
    }

    private void sortItems(List<PortfolioListItem> items, String sort) {
        String field = "createdAt";
        boolean desc = true;
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",");
            if (!SORT_FIELDS.contains(parts[0])) {
                throw new IllegalArgumentException("지원하지 않는 정렬 필드입니다: " + parts[0]);
            }
            field = parts[0];
            desc = parts.length <= 1 || "desc".equalsIgnoreCase(parts[1]);
        }
        Comparator<PortfolioListItem> comparator = switch (field) {
            case "name" -> Comparator.comparing(PortfolioListItem::getName, String.CASE_INSENSITIVE_ORDER);
            case "returnRate" -> Comparator.comparing(PortfolioListItem::getReturnRate);
            case "totalValue" -> Comparator.comparing(PortfolioListItem::getTotalValue);
            default -> Comparator.comparing(PortfolioListItem::getCreatedAt);
        };
        if (desc) {
            comparator = comparator.reversed();
        }
        items.sort(comparator);
    }

    private void validateNoDuplicateCodes(PortfolioRequest request) {
        Set<String> seen = new HashSet<>();
        for (HoldingRequest h : request.getHoldings()) {
            if (!seen.add(h.getCode())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "같은 code를 두 번 쓸 수 없습니다: " + h.getCode());
            }
        }
    }

    private Map<String, Product> resolveProducts(PortfolioRequest request) {
        Map<String, Product> result = new HashMap<>();
        for (HoldingRequest h : request.getHoldings()) {
            Product product = productRepository.findById(h.getCode())
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_PRODUCT",
                            "존재하지 않는 상품 코드입니다: " + h.getCode()));
            result.put(h.getCode(), product);
        }
        return result;
    }

    private BigDecimal requirePrice(Product product) {
        BigDecimal price = priceSnapshotStore.getPrice(product.getCode());
        if (price == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PRICE_NOT_READY", "아직 시세를 받아오지 못했습니다.");
        }
        return price;
    }

    private BigDecimal requireFxIfNeeded(Product product) {
        if (product.getCurrency() != CurrencyCode.USD) {
            return null;
        }
        BigDecimal fx = priceSnapshotStore.getFxRate();
        if (fx == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PRICE_NOT_READY", "아직 환율을 받아오지 못했습니다.");
        }
        return fx;
    }

    private Long parseId(Object rawId) {
        try {
            return rawId instanceof Long l ? l : Long.valueOf(String.valueOf(rawId));
        } catch (NumberFormatException e) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PORTFOLIO_NOT_FOUND", "포트폴리오를 찾을 수 없습니다: " + rawId);
        }
    }

    private Portfolio findOrThrow(Long id) {
        return portfolioRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PORTFOLIO_NOT_FOUND", "포트폴리오를 찾을 수 없습니다: " + id));
    }

    private Object lockFor(Long id) {
        return portfolioLocks.computeIfAbsent(id, k -> new Object());
    }
}
