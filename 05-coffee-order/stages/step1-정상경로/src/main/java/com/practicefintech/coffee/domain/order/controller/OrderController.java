package com.practicefintech.coffee.domain.order.controller;

import com.practicefintech.coffee.domain.order.model.request.OrderRequest;
import com.practicefintech.coffee.domain.order.model.response.OrderResponse;
import com.practicefintech.coffee.domain.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /** 1단계에는 @Valid가 없다. (2단계에서 추가) */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse order(@RequestBody OrderRequest request) {
        return orderService.order(request.getUserId(), request.getMenuId());
    }
}
