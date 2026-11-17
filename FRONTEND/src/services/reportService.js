import api from './api';

export const reportService = {
  /** Tải file Excel báo cáo (nhiều trang) và lưu về máy */
  async downloadExcel(branchId, days = 30) {
    const blob = await api.get('/reports/export', {
      params: { branchId: branchId || undefined, days },
      responseType: 'blob',
    });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `BaoCao_${branchId ? `CN${branchId}` : 'TatCa'}_${days}ngay_${new Date().toISOString().slice(0, 10)}.xlsx`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  },
  async getRevenueReport(branchId, days = 7) {
    const res = await api.get('/reports/revenue', {
      params: { branchId: branchId || undefined, days }
    });
    return res?.data !== undefined ? res.data : res;
  },

  async getTopProducts(branchId, days = 30, limit = 10) {
    const res = await api.get('/reports/top-products', {
      params: { branchId: branchId || undefined, days, limit }
    });
    const items = res?.data !== undefined ? res.data : res;
    return Array.isArray(items) ? items : [];
  },
};
