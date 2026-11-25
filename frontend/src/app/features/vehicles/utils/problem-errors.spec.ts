import { HttpErrorResponse } from '@angular/common/http';
import { parseProblemError } from './problem-errors';

describe('parseProblemError', () => {
  it('extracts field errors and detail from an RFC 7807 response', () => {
    const err = new HttpErrorResponse({
      status: 422,
      error: {
        status: 422,
        title: 'Unprocessable Entity',
        detail: 'El odómetro no puede ser menor al último registrado.',
        errors: [
          {
            field: 'odometerKm',
            message: 'Debe ser mayor o igual a 45.200 km.',
          },
        ],
      },
    });

    const parsed = parseProblemError(err, 'Fallback');
    expect(parsed.status).toBe(422);
    expect(parsed.message).toBe('El odómetro no puede ser menor al último registrado.');
    expect(parsed.fieldErrors['odometerKm']).toBe('Debe ser mayor o igual a 45.200 km.');
  });

  it('attaches 422 odometer detail to odometerKm when field errors array is absent', () => {
    const err = new HttpErrorResponse({
      status: 422,
      error: {
        status: 422,
        detail: 'El odómetro ingresado (40000 km) es menor al actual.',
      },
    });

    const parsed = parseProblemError(err, 'Fallback');
    expect(parsed.fieldErrors['odometerKm']).toBe(
      'El odómetro ingresado (40000 km) es menor al actual.',
    );
    expect(parsed.fieldErrors['currentOdometerKm']).toBe(
      'El odómetro ingresado (40000 km) es menor al actual.',
    );
  });

  it('returns friendly network message on status 0', () => {
    const err = new HttpErrorResponse({ status: 0 });
    const parsed = parseProblemError(err, 'Fallback');
    expect(parsed.message).toContain('conectar');
  });
});
