package com.familion.family.allowances.infrastructure.web;

import com.familion.family.allowances.domain.AllowanceAdjustmentRule;
import com.familion.family.allowances.domain.AllowanceFrequency;
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
import java.util.UUID;

@JsonDeserialize(using = AllowancePatchRequest.Deserializer.class)
public record AllowancePatchRequest(
        UUID childUserId,
        @JsonIgnore @Schema(hidden = true) boolean sourceAccountIdPresent,
        UUID sourceAccountId,
        UUID targetAccountId,
        @Positive(message = "Amount must be greater than zero") BigDecimal amount,
        @Pattern(regexp = ".*\\S.*", message = "Currency code must not be blank")
        @Size(max = 10, message = "Currency code must be at most 10 characters")
        String currencyCode,
        AllowanceFrequency frequency,
        @Min(value = 1, message = "Day of period must be between 1 and 31")
        @Max(value = 31, message = "Day of period must be between 1 and 31")
        Integer dayOfPeriod,
        AllowanceAdjustmentRule adjustmentRule,
        Boolean isActive) {

    static final class Deserializer extends StdDeserializer<AllowancePatchRequest> {

        Deserializer() {
            super(AllowancePatchRequest.class);
        }

        @Override
        public AllowancePatchRequest deserialize(JsonParser p, DeserializationContext ctxt)
                throws IOException {
            ObjectCodec codec = p.getCodec();
            JsonNode root = codec.readTree(p);
            if (root == null || !root.isObject()) {
                throw MismatchedInputException.from(
                        p, AllowancePatchRequest.class, "Patch body must be a JSON object");
            }

            UUID childUserId = readValue(root, "childUserId", UUID.class, codec);
            boolean sourceAccountIdPresent = root.has("sourceAccountId");
            UUID sourceAccountId = sourceAccountIdPresent
                    ? readValue(root, "sourceAccountId", UUID.class, codec)
                    : null;
            UUID targetAccountId = readValue(root, "targetAccountId", UUID.class, codec);
            BigDecimal amount = readValue(root, "amount", BigDecimal.class, codec);
            String currencyCode = readValue(root, "currencyCode", String.class, codec);
            AllowanceFrequency frequency =
                    readValue(root, "frequency", AllowanceFrequency.class, codec);
            Integer dayOfPeriod = readValue(root, "dayOfPeriod", Integer.class, codec);
            AllowanceAdjustmentRule adjustmentRule =
                    readValue(root, "adjustmentRule", AllowanceAdjustmentRule.class, codec);
            Boolean isActive = readValue(root, "isActive", Boolean.class, codec);

            return new AllowancePatchRequest(
                    childUserId,
                    sourceAccountIdPresent,
                    sourceAccountId,
                    targetAccountId,
                    amount,
                    currencyCode,
                    frequency,
                    dayOfPeriod,
                    adjustmentRule,
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
