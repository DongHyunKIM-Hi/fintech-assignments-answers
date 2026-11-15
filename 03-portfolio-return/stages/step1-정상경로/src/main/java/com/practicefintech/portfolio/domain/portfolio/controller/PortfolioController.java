package com.practicefintech.portfolio.domain.portfolio.controller;

import com.practicefintech.portfolio.common.dto.PageResponse;
import com.practicefintech.portfolio.domain.portfolio.model.request.PortfolioRequest;
import com.practicefintech.portfolio.domain.portfolio.model.response.PortfolioDetailResponse;
import com.practicefintech.portfolio.domain.portfolio.model.response.PortfolioListItem;
import com.practicefintech.portfolio.domain.portfolio.service.PortfolioService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * 1단계는 흐름만 확인한다.
 *
 * <p>이 단계에서 일부러 하지 않는 것 (모두 2단계 몫)
 * <ul>
 *   <li>{@code @Valid}로 요청 본문을 검사하는 일 (400 INVALID_REQUEST)</li>
 *   <li>목록 조회의 {@code query}, {@code sort}, {@code page}, {@code size}</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/portfolios")
public class PortfolioController {

    private final PortfolioService service;

    public PortfolioController(PortfolioService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PortfolioDetailResponse create(@RequestBody PortfolioRequest request) {
        return service.create(request);
    }

    @GetMapping("/{id}")
    public PortfolioDetailResponse get(@PathVariable Long id) {
        return service.getDetail(id);
    }

    @GetMapping
    public PageResponse<PortfolioListItem> list() {
        return service.list();
    }

    @PutMapping("/{id}")
    public PortfolioDetailResponse update(@PathVariable Long id, @RequestBody PortfolioRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
