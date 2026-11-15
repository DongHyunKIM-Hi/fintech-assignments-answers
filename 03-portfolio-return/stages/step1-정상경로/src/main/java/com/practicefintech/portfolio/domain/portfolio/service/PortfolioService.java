package com.practicefintech.portfolio.domain.portfolio.service;

import com.practicefintech.portfolio.common.dto.PageResponse;
import com.practicefintech.portfolio.common.entity.Holding;
import com.practicefintech.portfolio.common.entity.Portfolio;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 포트폴리오 생성, 조회, 수정, 삭제. 1단계는 "설계한 흐름이 도는지"만 확인한다.
 *
 * <p>이 단계에서 일부러 하지 않는 것
 * <ul>
 *   <li><b>입력 검증</b>: 이름 길이, 보유 상품 개수, 수량 범위, 같은 code 중복, 존재하지 않는 상품 코드를
 *       아무것도 보지 않는다. 그래서 없는 코드를 담으면 400 UNKNOWN_PRODUCT가 아니라 500이 난다. (2단계)</li>
 *   <li><b>없는 포트폴리오 처리</b>: 404 PORTFOLIO_NOT_FOUND 대신 500이 난다. (2단계)</li>
 *   <li><b>매입가 고정</b>: 매입가를 저장하지 않아 매입금액을 현재가로 다시 계산한다.
 *       그래서 시세가 올라도 <b>수익률이 늘 0</b>이다. (시나리오 S7, S8 — 2단계)</li>
 *   <li><b>통화 환산</b>: 달러 상품의 가격을 그대로 원으로 더한다. 환율을 곱하지 않아
 *       평가금액이 1,300배가량 작게 나온다. (가정 G3 — 2단계)</li>
 *   <li><b>금액 버림과 수익률 반올림</b>: 하지 않는다. 달러 상품이 섞이면 소수점이 그대로 나온다. (2단계)</li>
 *   <li><b>오늘 손익</b>: 기준값을 두지 않아 늘 0이다. (가정 G2 — 2단계)</li>
 *   <li><b>목록의 검색, 정렬, 페이지</b>: 저장된 포트폴리오를 전부 그대로 돌려준다. (2단계)</li>
 *   <li><b>시세 서버 장애 대비</b>: 조회할 때마다 시세 서버를 부르기 때문에 서버가 멈추면
 *       GET도 500이 난다. (요구사항 R6 — 3단계)</li>
 * </ul>
 */
@Service
@Transactional
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;
    private final PriceClient priceClient;

    public PortfolioService(PortfolioRepository portfolioRepository, PriceClient priceClient) {
        this.portfolioRepository = portfolioRepository;
        this.priceClient = priceClient;
    }

    public PortfolioDetailResponse create(PortfolioRequest request) {
        List<Holding> holdings = new ArrayList<>();
        for (HoldingRequest h : request.getHoldings()) {
            // 검증 없이 그대로 담는다. 매입가도 남기지 않는다.
            holdings.add(new Holding(h.getCode(), h.getQuantity()));
        }
        Portfolio portfolio = new Portfolio(request.getName(), LocalDateTime.now());
        portfolio.replaceHoldings(holdings);
        portfolioRepository.save(portfolio);
        return toDetail(portfolio, loadQuotes());
    }

    public PortfolioDetailResponse getDetail(Long id) {
        // 없는 id면 NoSuchElementException이 올라가 500이 된다. 404로 바꾸는 일은 2단계 몫이다.
        Portfolio portfolio = portfolioRepository.findById(id).orElseThrow();
        return toDetail(portfolio, loadQuotes());
    }

    public PageResponse<PortfolioListItem> list() {
        Quotes quotes = loadQuotes();
        List<PortfolioListItem> items = portfolioRepository.findAll().stream()
                .map(p -> {
                    Summary s = computeSummary(p, quotes);
                    return new PortfolioListItem(p.getId(), p.getName(), s.totalValue, s.returnRate, p.getCreatedAt());
                })
                .toList();
        // 검색, 정렬, 페이지가 없으므로 한 페이지에 전부 담는다.
        return new PageResponse<>(items, 0, items.size(), items.size(), 1);
    }

    public PortfolioDetailResponse update(Long id, PortfolioRequest request) {
        Portfolio portfolio = portfolioRepository.findById(id).orElseThrow();
        List<Holding> newHoldings = new ArrayList<>();
        for (HoldingRequest h : request.getHoldings()) {
            newHoldings.add(new Holding(h.getCode(), h.getQuantity()));
        }
        portfolio.setName(request.getName());
        portfolio.replaceHoldings(newHoldings);
        portfolioRepository.save(portfolio);
        return toDetail(portfolio, loadQuotes());
    }

    public void delete(Long id) {
        Portfolio portfolio = portfolioRepository.findById(id).orElseThrow();
        portfolioRepository.delete(portfolio);
    }

    // ---- 내부 계산 ----

    /** 이번 요청에서 쓸 시세. 보관하지 않고 요청마다 새로 받아 온다. */
    private record Quotes(Map<String, BigDecimal> prices, java.time.Instant asOf) {
    }

    private record Summary(BigDecimal totalCost, BigDecimal totalValue, BigDecimal profit,
                           BigDecimal returnRate, BigDecimal todayProfit, java.time.Instant asOf) {
    }

    private Quotes loadQuotes() {
        ExternalPricesResponse response = priceClient.fetchPrices();
        Map<String, BigDecimal> prices = new HashMap<>();
        for (ExternalPriceQuote quote : response.getPrices()) {
            prices.put(quote.getCode(), quote.getPrice());
        }
        return new Quotes(prices, response.getAsOf());
    }

    private Summary computeSummary(Portfolio portfolio, Quotes quotes) {
        BigDecimal totalValue = BigDecimal.ZERO;
        for (Holding h : portfolio.getHoldings()) {
            // 달러 상품이어도 환율을 곱하지 않는다. 시세가 없는 코드면 NullPointerException이 난다.
            BigDecimal price = quotes.prices().get(h.getCode());
            totalValue = totalValue.add(price.multiply(BigDecimal.valueOf(h.getQuantity())));
        }
        // 매입가를 저장하지 않아 매입금액도 현재가로 계산한다. 그래서 평가금액과 늘 같다.
        BigDecimal totalCost = totalValue;
        BigDecimal profit = totalValue.subtract(totalCost);   // 늘 0
        BigDecimal returnRate = BigDecimal.ZERO;              // 매입가가 없어 계산할 수 없다
        BigDecimal todayProfit = BigDecimal.ZERO;             // 기준값이 없어 계산할 수 없다
        return new Summary(totalCost, totalValue, profit, returnRate, todayProfit, quotes.asOf());
    }

    private PortfolioDetailResponse toDetail(Portfolio portfolio, Quotes quotes) {
        Summary s = computeSummary(portfolio, quotes);
        List<HoldingView> holdingViews = portfolio.getHoldings().stream()
                .map(h -> {
                    BigDecimal current = quotes.prices().get(h.getCode());
                    // 매입가를 저장하지 않으므로 현재가를 그대로 넣는다. 시세가 바뀌면 매입가도 따라 바뀐다.
                    return new HoldingView(h.getCode(), h.getQuantity(), current, current);
                })
                .toList();
        PortfolioSummary summary = new PortfolioSummary(
                s.totalCost(), s.totalValue(), s.profit(), s.returnRate(), s.todayProfit(), s.asOf());
        return new PortfolioDetailResponse(portfolio.getId(), portfolio.getName(), portfolio.getCreatedAt(),
                holdingViews, summary);
    }
}
