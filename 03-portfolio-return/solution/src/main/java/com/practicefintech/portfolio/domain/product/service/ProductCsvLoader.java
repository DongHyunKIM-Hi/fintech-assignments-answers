package com.practicefintech.portfolio.domain.product.service;

import com.practicefintech.portfolio.common.config.ProductsProperties;
import com.practicefintech.portfolio.common.entity.Product;
import com.practicefintech.portfolio.common.enums.CurrencyCode;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 서버 시작 시 {@code data/products.csv}(제공된 파일, 수정하지 않음)를 읽어 정리한 뒤 저장한다.
 * 정리 규칙(가정 G5): 통화 표기를 KRW/USD로 통일하고, 상장일을 세 형식 중 하나로 파싱하고,
 * 위험등급 빈 칸은 null로 두며(정렬 시 맨 뒤), 같은 코드가 두 번 나오면 "먼저 나온 행"을 유지하고
 * 나머지는 경고 로그만 남긴다(다른 결정도 가능 — docs/assumptions.md 참고).
 */
@Component
public class ProductCsvLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProductCsvLoader.class);

    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("yyyyMMdd"),
    };

    private final ProductsProperties properties;
    private final ProductRepository repository;

    public ProductCsvLoader(ProductsProperties properties, ProductRepository repository) {
        this.properties = properties;
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        Path path = Path.of(properties.getCsvPath());
        if (!Files.exists(path)) {
            log.warn("상품 데이터 파일을 찾을 수 없습니다: {} (상품 조회 API가 빈 목록을 반환합니다)", path.toAbsolutePath());
            return;
        }

        Map<String, Product> firstSeenByCode = new LinkedHashMap<>();
        int duplicateRows = 0;

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String header = reader.readLine();
            if (header == null) {
                log.warn("상품 데이터 파일이 비어 있습니다: {}", path);
                return;
            }
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] cols = line.split(",", -1);
                String code = cols[0].trim();
                String name = cols[1].trim();
                String nameEn = cols[2].trim();
                ProductType type = ProductType.valueOf(cols[3].trim());
                CurrencyCode currency = normalizeCurrency(cols[4].trim());
                Integer riskLevel = parseRiskLevel(cols[5].trim());
                String issuer = cols[6].trim();
                LocalDate listedDate = parseDate(cols[7].trim());

                Product product = new Product(code, name, nameEn, type, currency, riskLevel, issuer, listedDate);
                if (firstSeenByCode.containsKey(code)) {
                    duplicateRows++;
                    log.warn("중복된 상품 코드 발견: {} — 먼저 나온 행을 유지하고 이 행({})은 무시합니다.", code, name);
                } else {
                    firstSeenByCode.put(code, product);
                }
            }
        }

        repository.saveAll(firstSeenByCode.values());
        log.info("상품 데이터 로드 완료: 유일 코드 {}개 저장, 중복 행 {}개 무시", firstSeenByCode.size(), duplicateRows);
    }

    private CurrencyCode normalizeCurrency(String raw) {
        String v = raw.trim();
        if (v.equalsIgnoreCase("KRW") || v.equals("원")) {
            return CurrencyCode.KRW;
        }
        if (v.equalsIgnoreCase("USD") || v.equalsIgnoreCase("US$")) {
            return CurrencyCode.USD;
        }
        throw new IllegalStateException("알 수 없는 통화 표기: " + raw);
    }

    private Integer parseRiskLevel(String raw) {
        if (raw.isBlank()) {
            return null;
        }
        return Integer.valueOf(raw);
    }

    private LocalDate parseDate(String raw) {
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return LocalDate.parse(raw, fmt);
            } catch (DateTimeParseException ignored) {
                // 다음 형식 시도
            }
        }
        throw new IllegalStateException("알 수 없는 날짜 형식: " + raw);
    }
}
