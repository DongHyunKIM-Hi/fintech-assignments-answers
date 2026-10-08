package com.practicefintech.portfolio.product;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "products")
public record ProductsProperties(String csvPath) {
}
