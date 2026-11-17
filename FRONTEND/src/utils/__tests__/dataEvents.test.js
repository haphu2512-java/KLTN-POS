import { describe, it, expect, vi } from 'vitest';
import { DATA_EVENTS, emitDataChange, onDataChange } from '../dataEvents';

describe('dataEvents', () => {
  it('gọi đúng người nghe và ngừng gọi sau khi hủy đăng ký', () => {
    const handler = vi.fn();
    const off = onDataChange(DATA_EVENTS.STOCK_IN_CHANGED, handler);
    emitDataChange(DATA_EVENTS.STOCK_IN_CHANGED, { branchId: 1 });
    expect(handler).toHaveBeenCalledWith({ branchId: 1 });
    off();
    emitDataChange(DATA_EVENTS.STOCK_IN_CHANGED, { branchId: 1 });
    expect(handler).toHaveBeenCalledTimes(1);
  });

  it('một người nghe lỗi không chặn người nghe khác', () => {
    const bad = vi.fn(() => { throw new Error('x'); });
    const good = vi.fn();
    const spy = vi.spyOn(console, 'error').mockImplementation(() => {});
    const off1 = onDataChange('t', bad);
    const off2 = onDataChange('t', good);
    emitDataChange('t');
    expect(good).toHaveBeenCalled();
    off1(); off2(); spy.mockRestore();
  });
});
