package com.practicefintech.refundrelay.refund.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record RefundListResponse(
        List<RefundDetailResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static RefundListResponse from(Page<com.practicefintech.refundrelay.refund.Refund> page) {
        List<RefundDetailResponse> items = page.getContent().stream()
                .map(RefundDetailResponse::from)
                .toList();
        return new RefundListResponse(items, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
