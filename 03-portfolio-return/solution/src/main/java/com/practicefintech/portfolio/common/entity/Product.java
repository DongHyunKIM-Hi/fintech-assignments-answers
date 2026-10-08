package com.practicefintech.portfolio.common.entity;

import com.practicefintech.portfolio.common.enums.CurrencyCode;
import com.practicefintech.portfolio.common.enums.ProductType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 투자 상품. {@code data/products.csv}를 정리해서 저장한다 (원본 파일은 건드리지 않는다).
 * 코드가 중복으로 나온 경우 먼저 나온 행을 유지한다 ({@code ProductCsvLoader} 참고, 가정 G5).
 */
@Entity
@Getter
@Table(name = "products")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    @Id
    private String code;

    private String name;
    private String nameEn;

    @Enumerated(EnumType.STRING)
    private ProductType type;

    @Enumerated(EnumType.STRING)
    private CurrencyCode currency;

    /** 비어 있으면 null (가정 G5: 정렬에서는 맨 뒤로 보낸다). */
    private Integer riskLevel;

    private String issuer;
    private LocalDate listedDate;

    public Product(String code, String name, String nameEn, ProductType type, CurrencyCode currency,
                   Integer riskLevel, String issuer, LocalDate listedDate) {
        this.code = code;
        this.name = name;
        this.nameEn = nameEn;
        this.type = type;
        this.currency = currency;
        this.riskLevel = riskLevel;
        this.issuer = issuer;
        this.listedDate = listedDate;
    }
}
