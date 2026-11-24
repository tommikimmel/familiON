package com.familion.finance.recurring.infrastructure.web;

import com.familion.finance.recurring.domain.AdjustmentRule;
import com.familion.finance.transactions.application.api.Scope;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@JsonDeserialize(using = RecurringExpensePatchRequest.Deserializer.class)
public record RecurringExpensePatchRequest(
        @Pattern(regexp = ".*\\S.*", message = "Recurring expense name must not be blank")
        @Size(max = 120, message = "Recurring expense name must be at most 120 characters")
        String name,
        UUID categoryId,
        @JsonIgnore @Schema(hidden = true) boolean accountIdPresent,
        UUID accountId,
        Scope scope,
        @Positive(message = "Base amount must be greater than zero") BigDecimal baseAmount,
        @Pattern(regexp = ".*\\S.*", message = "Currency code must not be blank")
        @Size(max = 10, message = "Currency code must be at most 10 characters")
        String currencyCode,
        @Min(value = 1, message = "Due day must be between 1 and 31")
        @Max(value = 31, message = "Due day must be between 1 and 31")
        Integer dueDay,
        AdjustmentRule adjustmentRule,
        LocalDate startDate,
        @JsonIgnore @Schema(hidden = true) boolean endDatePresent,
        LocalDate endDate,
        Boolean isActive) {

    static final class Deserializer extends StdDeserializer<RecurringExpensePatchRequest> {

        Deserializer() {
            super(RecurringExpensePatchRequest.class);
        }

        @Override
        public RecurringExpensePatchRequest deserialize(JsonParser p, DeserializationContext ctxt)
                throws IOException {
            ObjectCodec codec = p.getCodec();
            JsonNode root = codec.readTree(p);
            if (root == null || !root.isObject()) {
                throw MismatchedInputException.from(
                        p, RecurringExpensePatchRequest.class, "Patch body must be a JSON object");
            }

            String name = readValue(root, "name", String.class, codec);
            UUID categoryId = readValue(root, "categoryId", UUID.class, codec);
            boolean accountIdPresent = root.has("accountId");
            UUID accountId =
                    accountIdPresent ? readValue(root, "accountId", UUID.class, codec) : null;
            Scope scope = readValue(root, "scope", Scope.class, codec);
            BigDecimal baseAmount = readValue(root, "baseAmount", BigDecimal.class, codec);
            String currencyCode = readValue(root, "currencyCode", String.class, codec);
            Integer dueDay = readValue(root, "dueDay", Integer.class, codec);
            AdjustmentRule adjustmentRule =
                    readValue(root, "adjustmentRule", AdjustmentRule.class, codec);
            LocalDate startDate = readValue(root, "startDate", LocalDate.class, codec);
            boolean endDatePresent = root.has("endDate");
            LocalDate endDate =
                    endDatePresent ? readValue(root, "endDate", LocalDate.class, codec) : null;
            Boolean isActive = readValue(root, "isActive", Boolean.class, codec);

            return new RecurringExpensePatchRequest(
                    name,
                    categoryId,
                    accountIdPresent,
                    accountId,
                    scope,
                    baseAmount,
                    currencyCode,
                    dueDay,
                    adjustmentRule,
                    startDate,
                    endDatePresent,
                    endDate,
                    isActive);
        }

        private static <T> T readValue(
                JsonNode root, String field, Class<T> type, ObjectCodec codec) throws IOException {
            JsonNode child = root.get(field);
            if (child == null || child.isNull()) {
                return null;
            }
            return codec.treeToValue(child, type);
        }
    }
}
