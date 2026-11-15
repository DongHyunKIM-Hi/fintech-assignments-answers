package com.practicefintech.portfolio.common.entity;

import com.practicefintech.portfolio.common.enums.ProductType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 투자 상품. 1단계는 {@code data/products.csv}의 한 줄을 그대로 한 행으로 저장한다.
 *
 * <p>이 단계에서 일부러 하지 않는 것
 * <ul>
 *   <li><b>중복 코드 정리</b>: 식별자를 code가 아니라 자동 증가 id로 두었기 때문에,
 *       같은 code가 두 번 나오면 두 행이 모두 저장되고 조회에도 두 번 나온다.
 *       (CSV 130행 가운데 10개 코드가 두 번씩 나온다.)</li>
 *   <li><b>통화 표기 정리</b>: KRW, krw, 원, USD, usd, US$가 섞인 값을 문자열 그대로 담는다.</li>
 *   <li><b>상장일 형식 정리</b>: yyyy-MM-dd, yyyy/MM/dd, yyyyMMdd가 섞인 값을 문자열 그대로 담는다.</li>
 *   <li><b>빈 위험등급 처리</b>: 빈 칸을 문자열 그대로 담는다.</li>
 * </ul>
 * 2단계에서 code를 식별자로 되돌리고, 통화는 {@code CurrencyCode}, 상장일은 {@code LocalDate},
 * 위험등급은 {@code Integer}(빈 칸이면 null)로 정규화한다.
 */
@Entity
@Getter
@Table(name = "products")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String code;
    private String name;
    private String nameEn;

    @Enumerated(EnumType.STRING)
    private ProductType type;

    /** CSV 원본 문자열. 2단계에서 CurrencyCode로 정규화한다. */
    private String currency;

    /** CSV 원본 문자열. 빈 칸이면 "". 2단계에서 Integer(null 허용)로 바꾼다. */
    private String riskLevel;

    private String issuer;

    /** CSV 원본 문자열. 2단계에서 LocalDate로 파싱한다. */
    private String listedDate;

    public Product(String code, String name, String nameEn, ProductType type, String currency,
                   String riskLevel, String issuer, String listedDate) {
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
