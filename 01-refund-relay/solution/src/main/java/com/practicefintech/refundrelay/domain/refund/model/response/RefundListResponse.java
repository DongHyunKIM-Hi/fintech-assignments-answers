package com.practicefintech.refundrelay.domain.refund.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
@AllArgsConstructor
public class RefundListResponse {

    private List<RefundDetailResponse> items;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    public static RefundListResponse from(Page<com.practicefintech.refundrelay.common.entity.Refund> page) {
        List<RefundDetailResponse> items = page.getContent().stream()
                .map(RefundDetailResponse::from)
                .toList();
        return new RefundListResponse(items, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
