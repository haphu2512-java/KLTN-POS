import { describe, it, expect } from 'vitest';
import { PERMS, ROLES, permissionsFor, homePathFor, normalizeRole } from '../permissions';

describe('phân quyền theo vai trò', () => {
  it('hệ thống chỉ có 3 vai trò', () => {
    expect(ROLES).toEqual(['ADMIN', 'MANAGER', 'STAFF']);
  });

  it('nhân viên chỉ bán hàng, xem tồn kho và khách hàng', () => {
    const p = permissionsFor('STAFF');
    expect([...p].sort()).toEqual([PERMS.CUSTOMER_VIEW, PERMS.INVENTORY_VIEW, PERMS.POS_SELL].sort());
    expect(homePathFor(p)).toBe('/');
  });

  it('quản lý làm nghiệp vụ kho nhưng không quản lý tài khoản, giờ ca, sửa khuyến mãi', () => {
    const p = permissionsFor('MANAGER');
    expect(p.has(PERMS.INVENTORY_EDIT)).toBe(true);
    expect(p.has(PERMS.FORECAST_VIEW)).toBe(true);
    expect(p.has(PERMS.SUPPLIER_VIEW)).toBe(true);
    expect(p.has(PERMS.ACCOUNT_MANAGE)).toBe(false);
    expect(p.has(PERMS.SHIFTS_SCHEDULE)).toBe(false);
    expect(p.has(PERMS.PROMOTION_MANAGE)).toBe(false);
    expect(p.has(PERMS.AUDIT_VIEW)).toBe(true);
  });

  it('phiên cũ còn lưu CASHIER / WAREHOUSE được coi là STAFF; vai trò lạ không có quyền', () => {
    expect(normalizeRole('CASHIER')).toBe('STAFF');
    expect(normalizeRole('WAREHOUSE')).toBe('STAFF');
    expect(permissionsFor('WAREHOUSE')).toEqual(permissionsFor('STAFF'));
    expect(permissionsFor('HACKER').size).toBe(0);
  });

  it('demo ngoại tuyến mở thêm màn hình trình diễn', () => {
    expect(permissionsFor('STAFF', { offlineDemo: true }).has(PERMS.REPORT_VIEW)).toBe(true);
  });
});
