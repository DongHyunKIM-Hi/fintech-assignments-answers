package com.practicefintech.portfolio.portfolio;

import com.practicefintech.portfolio.common.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/portfolios")
public class PortfolioController {

    private final PortfolioService service;

    public PortfolioController(PortfolioService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PortfolioDetailResponse create(@Valid @RequestBody PortfolioRequest request) {
        return service.create(request);
    }

    @GetMapping("/{id}")
    public PortfolioDetailResponse get(@PathVariable String id) {
        return service.getDetail(id);
    }

    @GetMapping
    public PageResponse<PortfolioListItem> list(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page, size 범위를 확인하세요.");
        }
        return service.list(query, sort, page, size);
    }

    @PutMapping("/{id}")
    public PortfolioDetailResponse update(@PathVariable String id, @Valid @RequestBody PortfolioRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        service.delete(id);
    }
}
