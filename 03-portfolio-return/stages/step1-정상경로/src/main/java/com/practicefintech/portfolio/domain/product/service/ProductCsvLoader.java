package com.practicefintech.portfolio.domain.product.service;

import com.practicefintech.portfolio.common.config.ProductsProperties;
import com.practicefintech.portfolio.common.entity.Product;
import com.practicefintech.portfolio.common.enums.ProductType;
import com.practicefintech.portfolio.domain.product.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 서버 시작 시 {@code data/products.csv}를 읽어 저장한다.
 * 1단계는 <b>읽은 줄을 그대로 한 행씩</b> 저장한다. 정리는 하지 않는다.
 *
 * <p>이 단계에서 일부러 하지 않는 것 (모두 2단계 몫)
 * <ul>
 *   <li>같은 코드가 두 번 나오는 행을 하나로 줄이는 일</li>
 *   <li>통화 표기(KRW, krw, 원, USD, usd, US$)를 KRW, USD로 통일하는 일</li>
 *   <li>상장일 형식(yyyy-MM-dd, yyyy/MM/dd, yyyyMMdd)을 하나로 맞추는 일</li>
 *   <li>빈 위험등급을 null로 바꾸고 정렬에서 맨 뒤로 보내는 일</li>
 * </ul>
 * 그래서 1단계의 상품 조회 응답에는 CSV의 지저분한 값이 그대로 나온다.
 */
@Component
public class ProductCsvLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProductCsvLoader.class);

    private final ProductsProperties properties;
    private final ProductRepository repository;

    public ProductCsvLoader(ProductsProperties properties, ProductRepository repository) {
        this.properties = properties;
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        Path path = Path.of(properties.getCsvPath());
        List<Product> products = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            reader.readLine(); // 헤더
            String line;
            while ((line = reader.readLine()) != null) {
                String[] cols = line.split(",", -1);
                // 정리 없이 그대로 담는다. 중복 코드도 거르지 않는다.
                products.add(new Product(
                        cols[0].trim(),                     // code
                        cols[1].trim(),                     // name
                        cols[2].trim(),                     // nameEn
                        ProductType.valueOf(cols[3].trim()), // type (CSV가 이미 ETF/FUND/BOND로 깨끗하다)
                        cols[4].trim(),                     // currency — 원본 문자열
                        cols[5].trim(),                     // riskLevel — 원본 문자열
                        cols[6].trim(),                     // issuer
                        cols[7].trim()));                   // listedDate — 원본 문자열
            }
        }

        repository.saveAll(products);
        log.info("상품 데이터 로드 완료: {}행 저장 (중복 코드도 그대로 저장했다)", products.size());
    }
}
