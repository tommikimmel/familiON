package com.familion.family.infrastructure.web;

import com.familion.family.domain.FamilyCatalog;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidZoneIdValidator implements ConstraintValidator<ValidZoneId, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        return FamilyCatalog.isValidTimezone(value);
    }
}
