package com.simucredito.configuration.infrastructure.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExchangeRateService {

    private static final String API_URL = "https://api.decolecta.com/v1/tipo-cambio/sunat";
    // NOTA: En producción, mueve esta key a application.properties
    private static final String API_KEY = "sk_11886.D3GP3KwhY9v63VMNta939T8M7ixKA6dN";

    public ExchangeRateDTO getCurrentExchangeRate() {
        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + API_KEY);
            headers.set("Content-Type", "application/json");

            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<ExchangeRateResponse> response = restTemplate.exchange(
                    API_URL,
                    HttpMethod.GET,
                    entity,
                    ExchangeRateResponse.class
            );

            if (response.getBody() != null) {
                ExchangeRateResponse body = response.getBody();
                return ExchangeRateDTO.builder()
                        .buyPrice(new BigDecimal(body.getBuyPrice()))
                        .sellPrice(new BigDecimal(body.getSellPrice()))
                        .date(body.getDate())
                        .build();
            }
        } catch (Exception e) {
            log.error("Error fetching exchange rate from DeColecta", e);
            // Fallback o relanzar excepción personalizada
            throw new RuntimeException("No se pudo obtener el tipo de cambio actual.");
        }
        return null;
    }

    // Clases internas para mapeo
    @Data
    private static class ExchangeRateResponse {
        @JsonProperty("buy_price")
        private String buyPrice;

        @JsonProperty("sell_price")
        private String sellPrice;

        @JsonProperty("base_currency")
        private String baseCurrency;

        @JsonProperty("quote_currency")
        private String quoteCurrency;

        private String date;
    }

    @Data
    @lombok.Builder
    public static class ExchangeRateDTO {
        private BigDecimal buyPrice;
        private BigDecimal sellPrice;
        private String date;
    }
}