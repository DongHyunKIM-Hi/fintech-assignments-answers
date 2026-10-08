package com.practicefintech.warmup.coupon;

import com.practicefintech.warmup.common.ApiException;
import com.practicefintech.warmup.common.ErrorCode;
import com.practicefintech.warmup.coupon.dto.CouponView;
import com.practicefintech.warmup.coupon.dto.IssueCouponRequest;
import com.practicefintech.warmup.coupon.dto.UseCouponRequest;
import com.practicefintech.warmup.coupon.dto.UseCouponResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users/{userId}/coupons")
public class CouponController {

    private final CouponStore couponStore;
    private final CouponService couponService;

    public CouponController(CouponStore couponStore, CouponService couponService) {
        this.couponStore = couponStore;
        this.couponService = couponService;
    }

    @PostMapping
    public ResponseEntity<Void> issue(@PathVariable String userId, @RequestBody IssueCouponRequest request) {
        CouponType type = parseType(request.type());
        couponService.issue(userId, type);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public List<CouponView> list(@PathVariable String userId) {
        return couponStore.findByUserId(userId).stream()
                .map(c -> new CouponView(c.id(), c.type().name()))
                .toList();
    }

    @PostMapping("/use")
    public UseCouponResponse use(@PathVariable String userId, @RequestBody UseCouponRequest request) {
        return couponService.use(userId, request.amount());
    }

    private CouponType parseType(String type) {
        try {
            return CouponType.valueOf(type);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST, "type이 올바르지 않습니다: " + type);
        }
    }
}
