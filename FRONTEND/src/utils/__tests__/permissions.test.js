import { describe, it, expect } from 'vitest';
import { PERMS, permissionsFor, homePathFor } from '../permissions';

describe('phân quyền theo vai trò', () => {
  it('thu ngân chỉ bán hàng, xem tồn kho và khách hàng', () => {
    const p = permissionsFor('CASHIER');
    expect([...p].sort()).toEqual([PERMS.CUSTOMER_VIEW, PERMS.INVENTORY_VIEW, PERMS.POS_SELL].sort());
    expect(homePathFor(p)).toBe('/');
  });

  it('quản lý không quản lý tài khoản, giờ ca, sửa khuyến mãi', () => {
    const p = permissionsFor('MANAGER');
    expect(p.has(PERMS.ACCOUNT_MANAGE)).toBe(false);
    expect(p.has(PERMS.SHIFTS_SCHEDULE)).toBe(false);
    expect(p.has(PERMS.PROMOTION_MANAGE)).toBe(false);
    expect(p.has(PERMS.AUDIT_VIEW)).toBe(true);
  });

  it('kho vào trang Kho hàng, vai trò lạ không có quyền', () => {
    expect(homePathFor(permissionsFor('WAREHOUSE'))).toBe('/inventory');
    expect(permissionsFor('HACKER').size).toBe(0);
  });

  it('demo ngoại tuyến mở thêm màn hình trình diễn', () => {
    expect(permissionsFor('CASHIER', { offlineDemo: true }).has(PERMS.REPORT_VIEW)).toBe(true);
  });
});
