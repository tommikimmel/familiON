import {
  buildFuelPayloadAmounts,
  createInitialFuelCalcState,
  hasCompleteFuelAmounts,
  updateFuelField,
} from './fuel-math';

describe('fuel-math', () => {
  it('computes totalAmount when liters and pricePerLiter are entered', () => {
    let state = createInitialFuelCalcState();
    state = updateFuelField(state, 'liters', '40');
    expect(hasCompleteFuelAmounts(state)).toBe(false);
    expect(state.computedField).toBeNull();

    state = updateFuelField(state, 'pricePerLiter', '1250');
    expect(state.computedField).toBe('totalAmount');
    expect(state.totalAmount).toBe('50000');
    expect(hasCompleteFuelAmounts(state)).toBe(true);
    expect(buildFuelPayloadAmounts(state)).toEqual({
      liters: '40',
      pricePerLiter: '1250',
    });
  });

  it('computes liters when totalAmount and pricePerLiter are entered', () => {
    let state = createInitialFuelCalcState();
    state = updateFuelField(state, 'totalAmount', '50000');
    state = updateFuelField(state, 'pricePerLiter', '1250');

    expect(state.computedField).toBe('liters');
    expect(state.liters).toBe('40');
    expect(buildFuelPayloadAmounts(state)).toEqual({
      totalAmount: '50000',
      pricePerLiter: '1250',
    });
  });

  it('computes pricePerLiter when liters and totalAmount are entered', () => {
    let state = createInitialFuelCalcState();
    state = updateFuelField(state, 'liters', '40');
    state = updateFuelField(state, 'totalAmount', '48000');

    expect(state.computedField).toBe('pricePerLiter');
    expect(state.pricePerLiter).toBe('1200');
  });

  it('respects last-edited-wins when editing the previously auto-computed field', () => {
    let state = createInitialFuelCalcState();
    state = updateFuelField(state, 'liters', '40');
    state = updateFuelField(state, 'pricePerLiter', '1200');
    expect(state.totalAmount).toBe('48000');
    expect(state.computedField).toBe('totalAmount');

    // User now edits totalAmount -> pricePerLiter + totalAmount win, liters recomputes.
    state = updateFuelField(state, 'totalAmount', '60000');
    expect(state.computedField).toBe('liters');
    expect(state.liters).toBe('50');
    expect(state.pricePerLiter).toBe('1200');
    expect(state.totalAmount).toBe('60000');
    expect(buildFuelPayloadAmounts(state)).toEqual({
      pricePerLiter: '1200',
      totalAmount: '60000',
    });
  });

  it('accepts decimal comma input and clears computed field if one manual field is erased', () => {
    let state = createInitialFuelCalcState();
    state = updateFuelField(state, 'liters', '35,5');
    state = updateFuelField(state, 'pricePerLiter', '1200,50');
    expect(state.computedField).toBe('totalAmount');
    expect(state.totalAmount).toBe('42617.75');
    expect(buildFuelPayloadAmounts(state)).toEqual({
      liters: '35.5',
      pricePerLiter: '1200.50',
    });

    state = updateFuelField(state, 'liters', '');
    expect(state.computedField).toBeNull();
    expect(state.totalAmount).toBe('');
    expect(hasCompleteFuelAmounts(state)).toBe(false);
  });
});
