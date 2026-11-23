package com.familion.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

class JacksonConfigTest {

    private ObjectMapper objectMapper;

    record MoneySample(BigDecimal amount, BigDecimal scientificLarge, BigDecimal scientificSmall, LocalDate date) {}

    @BeforeEach
    void setUp() {
        Jackson2ObjectMapperBuilder builder = Jackson2ObjectMapperBuilder.json();
        new JacksonConfig().familionJacksonCustomizer().customize(builder);
        this.objectMapper = builder.build();
    }

    @Test
    void serializesBigDecimalAsPlainStringAndLocalDateAsIso() throws Exception {
        MoneySample sample = new MoneySample(
                new BigDecimal("36000.5000"),
                new BigDecimal("1E+7"),
                new BigDecimal("0.0000001"),
                LocalDate.of(2026, 5, 14));

        String json = objectMapper.writeValueAsString(sample);
        JsonNode root = objectMapper.readTree(json);

        assertThat(objectMapper.isEnabled(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN)).isTrue();
        assertThat(root.get("amount").isTextual()).isTrue();
        assertThat(root.get("amount").asText()).isEqualTo("36000.5000");
        assertThat(root.get("scientificLarge").isTextual()).isTrue();
        assertThat(root.get("scientificLarge").asText()).isEqualTo("10000000");
        assertThat(root.get("scientificSmall").isTextual()).isTrue();
        assertThat(root.get("scientificSmall").asText()).isEqualTo("0.0000001");
        assertThat(root.get("date").isTextual()).isTrue();
        assertThat(root.get("date").asText()).isEqualTo("2026-05-14");

        MoneySample deserialized = objectMapper.readValue(json, MoneySample.class);
        assertThat(deserialized.amount()).isEqualTo(new BigDecimal("36000.5000"));
        assertThat(deserialized.date()).isEqualTo(LocalDate.of(2026, 5, 14));
    }
}
