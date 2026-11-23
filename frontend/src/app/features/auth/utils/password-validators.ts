import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

export const PASSWORD_MIN_LENGTH = 10;

const LETTER_PATTERN = /[A-Za-zÀ-ÿ]/;
const DIGIT_PATTERN = /\d/;

export const strongPasswordValidator: ValidatorFn = (
  control: AbstractControl,
): ValidationErrors | null => {
  const value = typeof control.value === 'string' ? control.value : '';
  if (!value) {
    return null;
  }

  const errors: ValidationErrors = {};
  if (value.length < PASSWORD_MIN_LENGTH) {
    errors['passwordMinLength'] = { requiredLength: PASSWORD_MIN_LENGTH, actualLength: value.length };
  }
  if (!LETTER_PATTERN.test(value)) {
    errors['passwordLetter'] = true;
  }
  if (!DIGIT_PATTERN.test(value)) {
    errors['passwordNumber'] = true;
  }

  return Object.keys(errors).length > 0 ? errors : null;
};

export function passwordsMatchValidator(
  passwordControlName = 'password',
  confirmControlName = 'confirmPassword',
): ValidatorFn {
  return (group: AbstractControl): ValidationErrors | null => {
    const password = group.get(passwordControlName)?.value;
    const confirm = group.get(confirmControlName)?.value;
    if (!confirm || password === confirm) {
      return null;
    }
    return { passwordMismatch: true };
  };
}

export function describePasswordError(errors: ValidationErrors | null): string | null {
  if (!errors) {
    return null;
  }
  if (errors['required']) {
    return 'Ingresa una contraseña.';
  }
  if (errors['passwordMinLength']) {
    return `La contraseña debe tener al menos ${PASSWORD_MIN_LENGTH} caracteres.`;
  }
  if (errors['passwordLetter'] && errors['passwordNumber']) {
    return 'La contraseña debe incluir al menos una letra y un número.';
  }
  if (errors['passwordLetter']) {
    return 'La contraseña debe incluir al menos una letra.';
  }
  if (errors['passwordNumber']) {
    return 'La contraseña debe incluir al menos un número.';
  }
  return null;
}
