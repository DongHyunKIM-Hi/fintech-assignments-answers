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
import com.practicefintech.portfolio.domain.price.model.dto.ExternalPriceQuote;
import com.practicefintech.portfolio.domain.price.model.dto.ExternalPricesResponse;
import com.practicefintech.portfolio.domain.price.service.PriceClient;
import com.practicefintech.portfolio.domain.product.repository.ProductRepository;
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
 * 포트폴리오 생성, 조회, 수정, 삭제와 수익률 계산.
 * 2단계에서 입력 검증(400, 404), 매입가 고정, 통화 환산, 금액 버림과 수익률 반올림,
 * 오늘 손익, 목록의 검색·정렬·페이지를 모두 채운다.
 * (README 6장 계산 규칙과 가정 G1 구성 변경, G2 오늘 손익, G3 통화 혼재)
 *
 * <p>이 단계에서 일부러 하지 않는 것 (3단계 몫)
 * <ul>
 *   <li><b>시세 보관</b>: 요청이 올 때마다 {@link PriceClient}로 시세와 환율을 받아 온다.
 *       그래서 시세 서버가 멈추면 조회(GET)도 500이 된다. 요구사항 R6과 시나리오 S11 위반이다.</li>
 *   <li><b>30초 주기 갱신</b>: 외부 호출이 요청 경로 안에 있어 주기 갱신이라는 개념이 아직 없다.</li>
 *   <li><b>시세를 못 받았을 때 보여 줄 내용</b>(가정 G4): 마지막 값을 들고 있지 않으니 고를 수 있는 선택지가 없다.</li>
 * </ul>
 */
@Service
@Transactional
public class PortfolioService {

    private static final Set<String> SORT_FIELDS = Set.of("name", "createdAt", "returnRate", "totalValue");

    private final PortfolioRepository portfolioRepository;
    private final ProductRepository productRepository;
    private final PriceClient priceClient;

    public PortfolioService(PortfolioRepository portfolioRepository, ProductRepository productRepository,
                             PriceClient priceClient) {
        this.portfolioRepository = portfolioRepository;
        this.productRepository = productRepository;
        this.priceClient = priceClient;
    }

    public PortfolioDetailResponse create(PortfolioRequest request) {
        validateNoDuplicateCodes(request);
        Map<String, Product> products = resolveProducts(request);
        Quotes quotes = loadQuotes();

        List<Holding> holdings = new ArrayList<>();
        for (HoldingRequest h : request.getHoldings()) {
            Product product = products.get(h.getCode());
            BigDecimal priceNative = requirePrice(product.getCode(), quotes);
            BigDecimal fxRate = requireFxIfNeeded(product, quotes);
            holdings.add(new Holding(h.getCode(), h.getQuantity(), priceNative, fxRate));
        }

        Portfolio portfolio = new Portfolio(request.getName(), LocalDateTime.now());
        portfolio.replaceHoldings(holdings);
        // 생성 시점에는 매입가 = 현재가이므로 평가금액 = 매입금액. 오늘 손익은 0부터 시작한다 (가정 G2).
        BigDecimal initialValue = computeTotalValue(portfolio, quotes).setScale(0, RoundingMode.DOWN);
        portfolio.setTodayBaseline(initialValue, LocalDate.now());

        portfolioRepository.save(portfolio);
        return toDetail(portfolio, quotes);
    }

    public PortfolioDetailResponse getDetail(Object rawId) {
        Portfolio portfolio = findOrThrow(rawId);
        return toDetail(portfolio, loadQuotes());
    }

    public PageResponse<PortfolioListItem> list(String query, String sort, int page, int size) {
        List<Portfolio> all = (query == null || query.isBlank())
                ? portfolioRepository.findAll()
                : portfolioRepository.findByNameContainingIgnoreCase(query);

        Quotes quotes = loadQuotes();
        List<PortfolioListItem> items = all.stream()
                .map(p -> {
                    Summary s = computeSummary(p, false, quotes);
                    return new PortfolioListItem(p.getId(), p.getName(), s.totalValue(), s.returnRate(), p.getCreatedAt());
                })
                .collect(Collectors.toCollection(ArrayList::new));

        sortItems(items, sort);

        // page*size가 int 범위를 넘을 수 있어 long으로 계산한 뒤 clamp한다.
        int fromIndex = (int) Math.min((long) page * size, items.size());
        int toIndex = (int) Math.min((long) fromIndex + size, items.size());
        List<PortfolioListItem> pageItems = items.subList(fromIndex, toIndex);
        int totalPages = size == 0 ? 0 : (int) Math.ceil(items.size() / (double) size);
        return new PageResponse<>(pageItems, page, size, items.size(), totalPages);
    }

    public PortfolioDetailResponse update(Object rawId, PortfolioRequest request) {
        Portfolio portfolio = findOrThrow(rawId);
        validateNoDuplicateCodes(request);
        Map<String, Product> products = resolveProducts(request);
        Quotes quotes = loadQuotes();

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
                BigDecimal priceNative = requirePrice(product.getCode(), quotes);
                BigDecimal fxRate = requireFxIfNeeded(product, quotes);
                newHoldings.add(new Holding(hr.getCode(), hr.getQuantity(), priceNative, fxRate));
            } else if (hr.getQuantity() == old.getQuantity()) {
                // 그대로: 매입가 유지.
                newHoldings.add(new Holding(hr.getCode(), hr.getQuantity(), old.getPurchasePriceNative(), old.getPurchaseFxRate()));
            } else if (hr.getQuantity() > old.getQuantity()) {
                // 수량 증가: 늘어난 만큼은 지금 시세로 사서 평균 단가를 새로 계산한다 (가정 G1).
                BigDecimal currentPriceNative = requirePrice(product.getCode(), quotes);
                BigDecimal currentFx = requireFxIfNeeded(product, quotes);
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
        return toDetail(portfolio, quotes);
    }

    public void delete(Object rawId) {
        Portfolio portfolio = findOrThrow(rawId);
        portfolioRepository.delete(portfolio);
    }

    // ---- 내부 계산 ----

    /**
     * 이번 요청에서 쓸 시세와 환율. 보관하지 않고 요청마다 새로 받아 온다.
     * 3단계에서는 이 자리가 {@code PriceSnapshotStore}(마지막 성공 값)로 바뀐다.
     */
    private record Quotes(Map<String, BigDecimal> prices, BigDecimal fxRate, Instant asOf) {
    }

    private record Summary(BigDecimal totalCost, BigDecimal totalValue, BigDecimal profit,
                           BigDecimal returnRate, BigDecimal todayProfit, Instant asOf) {
    }

    private Quotes loadQuotes() {
        // 시세 서버가 503이거나 응답이 늦으면 여기서 예외가 올라가 API가 500이 된다 (3단계에서 해결).
        ExternalPricesResponse response = priceClient.fetchPrices();
        Map<String, BigDecimal> prices = new HashMap<>();
        for (ExternalPriceQuote quote : response.getPrices()) {
            prices.put(quote.getCode(), quote.getPrice());
        }
        BigDecimal fxRate = priceClient.fetchFx().getRate();
        return new Quotes(prices, fxRate, response.getAsOf());
    }

    private Summary computeSummary(Portfolio portfolio, boolean allowBaselineUpdate, Quotes quotes) {
        BigDecimal totalCost = BigDecimal.ZERO;
        for (Holding h : portfolio.getHoldings()) {
            totalCost = totalCost.add(h.costInKrw());
        }
        BigDecimal totalValueRaw = computeTotalValue(portfolio, quotes);

        // 금액은 종목별로 더한 뒤 합계에서만 원 단위 미만을 버린다 (README 6장).
        BigDecimal roundedCost = totalCost.setScale(0, RoundingMode.DOWN);
        BigDecimal roundedValue = totalValueRaw.setScale(0, RoundingMode.DOWN);
        BigDecimal profit = roundedValue.subtract(roundedCost);
        // 수익률만 반올림(HALF_UP)한다. 금액에 반올림, 수익률에 버림을 쓰면 안 된다.
        BigDecimal returnRate = roundedCost.signum() == 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : profit.divide(roundedCost, 10, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, RoundingMode.HALF_UP);

        LocalDate today = LocalDate.now();
        if (allowBaselineUpdate && !today.equals(portfolio.getTodayBaselineDate())) {
            // 오늘 날짜로 처음 조회된 시점을 오늘 손익의 기준으로 삼는다. (가정 G2)
            portfolio.setTodayBaseline(roundedValue, today);
        }
        BigDecimal baseline = portfolio.getTodayBaselineValue() == null ? roundedCost : portfolio.getTodayBaselineValue();
        // DB에서 읽어 온 기준값은 컬럼 스케일(2)을 그대로 들고 오므로, 금액 규칙에 맞게 정수로 맞춘 뒤 뺀다.
        BigDecimal todayProfit = roundedValue.subtract(baseline.setScale(0, RoundingMode.DOWN));

        return new Summary(roundedCost, roundedValue, profit, returnRate, todayProfit, quotes.asOf());
    }

    private BigDecimal computeTotalValue(Portfolio portfolio, Quotes quotes) {
        BigDecimal total = BigDecimal.ZERO;
        for (Holding h : portfolio.getHoldings()) {
            BigDecimal currentPriceNative = requirePrice(h.getCode(), quotes);
            BigDecimal unitValueKrw = currentPriceNative;
            if (h.getPurchaseFxRate() != null) { // 달러 상품은 현재 환율로 환산한다 (가정 G3)
                unitValueKrw = currentPriceNative.multiply(requireFx(quotes));
            }
            total = total.add(unitValueKrw.multiply(BigDecimal.valueOf(h.getQuantity())));
        }
        return total;
    }

    private PortfolioDetailResponse toDetail(Portfolio portfolio, Quotes quotes) {
        Summary s = computeSummary(portfolio, true, quotes);

        List<HoldingView> holdingViews = portfolio.getHoldings().stream()
                .map(h -> {
                    int scale = h.getPurchaseFxRate() != null ? 2 : 0; // 달러 상품은 소수 둘째 자리, 원화는 정수
                    BigDecimal currentDisplay = requirePrice(h.getCode(), quotes).setScale(scale, RoundingMode.HALF_UP);
                    BigDecimal purchaseDisplay = h.getPurchasePriceNative().setScale(scale, RoundingMode.HALF_UP);
                    return new HoldingView(h.getCode(), h.getQuantity(), purchaseDisplay, currentDisplay);
                })
                .toList();

        PortfolioSummary summary = new PortfolioSummary(
                s.totalCost(), s.totalValue(), s.profit(), s.returnRate(), s.todayProfit(), s.asOf());

        return new PortfolioDetailResponse(portfolio.getId(), portfolio.getName(), portfolio.getCreatedAt(),
                holdingViews, summary);
    }

    private void sortItems(List<PortfolioListItem> items, String sort) {
        String field = "createdAt";
        boolean desc = true;
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
                desc = "desc".equalsIgnoreCase(parts[1]);
            }
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

    private BigDecimal requirePrice(String code, Quotes quotes) {
        BigDecimal price = quotes.prices().get(code);
        if (price == null) {
            // 생성·수정에서는 명세대로 503이지만, 조회(GET)에서도 503이 나간다. 3단계에서 해결한다.
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PRICE_NOT_READY", "아직 시세를 받아오지 못했습니다.");
        }
        return price;
    }

    private BigDecimal requireFxIfNeeded(Product product, Quotes quotes) {
        if (product.getCurrency() != CurrencyCode.USD) {
            return null;
        }
        return requireFx(quotes);
    }

    private BigDecimal requireFx(Quotes quotes) {
        if (quotes.fxRate() == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PRICE_NOT_READY", "아직 환율을 받아오지 못했습니다.");
        }
        return quotes.fxRate();
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
