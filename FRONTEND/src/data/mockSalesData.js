/**
 * Mock Historical Sales Data (Last 14 Days)
 * Used for AI Forecasting, Trend Visualization, and Restock Recommendations
 */

export const PAST_14_DAYS_LABELS = [
  '03/09', '04/09', '05/09', '06/09', '07/09', '08/09', '09/09',
  '10/09', '11/09', '12/09', '13/09', '14/09', '15/09', '16/09'
];

export const PRODUCT_SALES_HISTORY = {
  1: {
    // Sữa tươi Vinamilk
    dailySales: [14, 16, 12, 18, 19, 15, 17, 14, 18, 20, 19, 16, 21, 22],
    supplierName: 'Công ty CP Sữa Việt Nam (Vinamilk)'
  },
  2: {
    // Bánh mì sandwich Kinh Đô
    dailySales: [8, 10, 9, 12, 11, 8, 9, 10, 12, 14, 13, 9, 11, 12],
    supplierName: 'Công ty CP Mondelez Kinh Đô'
  },
  3: {
    // Coca-Cola Sleek
    dailySales: [25, 28, 30, 35, 42, 38, 27, 29, 34, 40, 45, 36, 31, 35],
    supplierName: 'Công ty TNHH Nước Giải Khát Coca-Cola'
  },
  4: {
    // Mì Hảo Hảo
    dailySales: [45, 52, 48, 60, 65, 50, 46, 55, 58, 64, 70, 56, 52, 60],
    supplierName: 'Công ty CP Acecook Việt Nam'
  },
  5: {
    // Dầu ăn Simply 1L
    dailySales: [3, 4, 2, 5, 6, 4, 3, 5, 4, 6, 7, 4, 5, 6],
    supplierName: 'Công ty TNHH Dầu Thực Vật Cái Lân (Simply)'
  },
  6: {
    // Nước tương Maggi
    dailySales: [4, 5, 3, 6, 7, 5, 4, 5, 6, 7, 8, 5, 6, 7],
    supplierName: 'Công ty TNHH Nestlé Việt Nam'
  },
  7: {
    // Khăn ướt Mamamy (Stock 0)
    dailySales: [6, 8, 7, 9, 10, 7, 6, 8, 9, 11, 10, 8, 9, 10],
    supplierName: 'Công ty CP Đông Hiệp'
  },
  8: {
    // Cà phê G7 3in1 (Stock 2)
    dailySales: [5, 6, 4, 7, 8, 6, 5, 7, 8, 9, 10, 7, 8, 9],
    supplierName: 'Tập đoàn Trung Nguyên'
  }
};

export const REVENUE_HISTORY_14_DAYS = [
  2450000, 2780000, 2390000, 3150000, 3620000, 2940000, 2680000,
  2980000, 3340000, 3890000, 4250000, 3120000, 3450000, 3820000
];
