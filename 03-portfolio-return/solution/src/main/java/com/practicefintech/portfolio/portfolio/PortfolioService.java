package com.practicefintech.portfolio.portfolio;

import com.practicefintech.portfolio.common.ApiException;
import com.practicefintech.portfolio.common.PageResponse;
import com.practicefintech.portfolio.price.PriceSnapshotStore;
import com.practicefintech.portfolio.product.CurrencyCode;
import com.practicefintech.portfolio.product.Product;
import com.practicefintech.portfolio.product.ProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 포트폴리오 생성·조회·수정·삭제와 수익률 계산. 이 클래스가 계약 6장(계산 규칙)과
 * 가정 G1(구성 변경), G2(오늘 손익), G3(통화 혼재), G4(시세 실패)를 구현하는 지점이다.
 * 자세한 이유는 {@code docs/assumptions.md} 참고.
 */
@Service
@Transactional
public class PortfolioService {

    private static final Set<String> SORT_FIELDS = Set.of("name", "createdAt", "returnRate", "totalValue");

    private final PortfolioRepository portfolioRepository;
    private final ProductRepository productRepository;
    private final PriceSnapshotStore priceSnapshotStore;

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
        for (HoldingRequest h : request.holdings()) {
            Product product = products.get(h.code());
            BigDecimal priceNative = requirePrice(product);
            BigDecimal fxRate = requireFxIfNeeded(product);
            holdings.add(new Holding(h.code(), h.quantity(), priceNative, fxRate));
        }

        Portfolio portfolio = new Portfolio(request.name(), LocalDateTime.now());
        portfolio.replaceHoldings(holdings);
        // 생성 시점에는 매입가 = 현재가이므로 평가금액 = 매입금액. 오늘 손익은 0부터 시작한다 (가정 G2).
        BigDecimal initialValue = computeTotalValue(portfolio).setScale(0, RoundingMode.DOWN);
        portfolio.setTodayBaseline(initialValue, LocalDate.now());

        portfolioRepository.save(portfolio);
        return toDetail(portfolio);
    }

    public PortfolioDetailResponse getDetail(Object rawId) {
        Portfolio portfolio = findOrThrow(rawId);
        return toDetail(portfolio);
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
        Portfolio portfolio = findOrThrow(rawId);
        validateNoDuplicateCodes(request);
        Map<String, Product> products = resolveProducts(request);

        Map<String, Holding> oldByCode = new HashMap<>();
        for (Holding h : portfolio.getHoldings()) {
            oldByCode.put(h.getCode(), h);
        }

        List<Holding> newHoldings = new ArrayList<>();
        for (HoldingRequest hr : request.holdings()) {
            Product product = products.get(hr.code());
            Holding old = oldByCode.get(hr.code());

            if (old == null) {
                // 새로 추가된 종목: 지금 시세로 담는다.
                BigDecimal priceNative = requirePrice(product);
                BigDecimal fxRate = requireFxIfNeeded(product);
                newHoldings.add(new Holding(hr.code(), hr.quantity(), priceNative, fxRate));
            } else if (hr.quantity() == old.getQuantity()) {
                // 그대로: 매입가 유지.
                newHoldings.add(new Holding(hr.code(), hr.quantity(), old.getPurchasePriceNative(), old.getPurchaseFxRate()));
            } else if (hr.quantity() > old.getQuantity()) {
                // 수량 증가: 늘어난 만큼은 지금 시세로 사서 평균 단가를 새로 계산한다 (가정 G1).
                BigDecimal currentPriceNative = requirePrice(product);
                BigDecimal currentFx = requireFxIfNeeded(product);
                long addedQty = hr.quantity() - old.getQuantity();
                BigDecimal oldCostKrw = old.costInKrw();
                BigDecimal addedUnitCostKrw = currentFx == null ? currentPriceNative : currentPriceNative.multiply(currentFx);
                BigDecimal newCostKrw = oldCostKrw.add(addedUnitCostKrw.multiply(BigDecimal.valueOf(addedQty)));
                BigDecimal fxForBlend = currentFx == null ? BigDecimal.ONE : currentFx;
                BigDecimal blendedPriceNative = newCostKrw
                        .divide(BigDecimal.valueOf(hr.quantity()), 10, RoundingMode.HALF_UP)
                        .divide(fxForBlend, 10, RoundingMode.HALF_UP);
                newHoldings.add(new Holding(hr.code(), hr.quantity(), blendedPriceNative, currentFx));
            } else {
                // 수량 감소: 매입가는 그대로 두어 원가가 비례해서 줄어들게 한다 (가정 G1).
                newHoldings.add(new Holding(hr.code(), hr.quantity(), old.getPurchasePriceNative(), old.getPurchaseFxRate()));
            }
        }
        // request에 없는 기존 종목은 newHoldings에 담기지 않으므로 자연히 원가에서 제외된다.

        portfolio.setName(request.name());
        portfolio.replaceHoldings(newHoldings);
        portfolioRepository.save(portfolio);
        return toDetail(portfolio);
    }

    public void delete(Object rawId) {
        Portfolio portfolio = findOrThrow(rawId);
        portfolioRepository.delete(portfolio);
    }

    // ---- 내부 계산 ----

    private record Summary(BigDecimal totalCost, BigDecimal totalValue, BigDecimal profit, BigDecimal returnRate,
                            BigDecimal todayProfit, Instant asOf, boolean stale) {
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
        BigDecimal fxRate = priceSnapshotStore.getFxRate();

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
                s.totalCost, s.totalValue, s.profit, s.returnRate, s.todayProfit, s.asOf,
                s.stale ? Boolean.TRUE : null);

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
            case "name" -> Comparator.comparing(PortfolioListItem::name, String.CASE_INSENSITIVE_ORDER);
            case "returnRate" -> Comparator.comparing(PortfolioListItem::returnRate);
            case "totalValue" -> Comparator.comparing(PortfolioListItem::totalValue);
            default -> Comparator.comparing(PortfolioListItem::createdAt);
        };
        if (desc) {
            comparator = comparator.reversed();
        }
        items.sort(comparator);
    }

    private void validateNoDuplicateCodes(PortfolioRequest request) {
        Set<String> seen = new HashSet<>();
        for (HoldingRequest h : request.holdings()) {
            if (!seen.add(h.code())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "같은 code를 두 번 쓸 수 없습니다: " + h.code());
            }
        }
    }

    private Map<String, Product> resolveProducts(PortfolioRequest request) {
        Map<String, Product> result = new HashMap<>();
        for (HoldingRequest h : request.holdings()) {
            Product product = productRepository.findById(h.code())
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_PRODUCT",
                            "존재하지 않는 상품 코드입니다: " + h.code()));
            result.put(h.code(), product);
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

    private Portfolio findOrThrow(Object rawId) {
        Long id;
        try {
            id = rawId instanceof Long l ? l : Long.valueOf(String.valueOf(rawId));
        } catch (NumberFormatException e) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PORTFOLIO_NOT_FOUND", "포트폴리오를 찾을 수 없습니다: " + rawId);
        }
        return portfolioRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PORTFOLIO_NOT_FOUND", "포트폴리오를 찾을 수 없습니다: " + id));
    }
}
