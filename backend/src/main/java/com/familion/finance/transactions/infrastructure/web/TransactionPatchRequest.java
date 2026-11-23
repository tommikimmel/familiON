package com.familion.finance.transactions.infrastructure.web;

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
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@JsonDeserialize(using = TransactionPatchRequest.Deserializer.class)
public record TransactionPatchRequest(
        @Positive(message = "Amount must be greater than zero") BigDecimal amount,
        LocalDate transactionDate,
        @JsonIgnore @Schema(hidden = true) boolean categoryIdPresent,
        UUID categoryId,
        @JsonIgnore @Schema(hidden = true) boolean descriptionPresent,
        @Size(max = 255, message = "Description must be at most 255 characters") String description,
        @JsonIgnore @Schema(hidden = true) boolean notesPresent,
        String notes,
        UUID accountId,
        UUID attributedToUserId,
        Scope scope) {

    static final class Deserializer extends StdDeserializer<TransactionPatchRequest> {

        Deserializer() {
            super(TransactionPatchRequest.class);
        }

        @Override
        public TransactionPatchRequest deserialize(JsonParser p, DeserializationContext ctxt)
                throws IOException {
            ObjectCodec codec = p.getCodec();
            JsonNode root = codec.readTree(p);
            if (root == null || !root.isObject()) {
                throw MismatchedInputException.from(
                        p, TransactionPatchRequest.class, "Patch body must be a JSON object");
            }

            BigDecimal amount = readValue(root, "amount", BigDecimal.class, codec);
            LocalDate transactionDate = readValue(root, "transactionDate", LocalDate.class, codec);
            boolean categoryIdPresent = root.has("categoryId");
            UUID categoryId =
                    categoryIdPresent ? readValue(root, "categoryId", UUID.class, codec) : null;
            boolean descriptionPresent = root.has("description");
            String description = descriptionPresent
                    ? readValue(root, "description", String.class, codec)
                    : null;
            boolean notesPresent = root.has("notes");
            String notes = notesPresent ? readValue(root, "notes", String.class, codec) : null;
            UUID accountId = readValue(root, "accountId", UUID.class, codec);
            UUID attributedToUserId = readValue(root, "attributedToUserId", UUID.class, codec);
            Scope scope = readValue(root, "scope", Scope.class, codec);

            return new TransactionPatchRequest(
                    amount,
                    transactionDate,
                    categoryIdPresent,
                    categoryId,
                    descriptionPresent,
                    description,
                    notesPresent,
                    notes,
                    accountId,
                    attributedToUserId,
                    scope);
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
