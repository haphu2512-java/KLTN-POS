/**
 * Mock Product Database for POS testing & offline mode
 * Conforms to database schema in StepByStep_Implementation.md
 */

export const INITIAL_PRODUCTS = [
  {
    id: 1,
    barcode: '8934567890123',
    name: 'Sữa tươi tiệt trùng Vinamilk 100% Không Đường 1L',
    category: 'Sữa & Bơ',
    unit: 'Hộp',
    costPrice: 28000,
    salePrice: 36000,
    stock: 45,
    minThreshold: 10
  },
  {
    id: 2,
    barcode: '8935001234567',
    name: 'Bánh mì sandwich bơ sữa Kinh Đô 275g',
    category: 'Bánh kẹo',
    unit: 'Gói',
    costPrice: 19000,
    salePrice: 24000,
    stock: 18,
    minThreshold: 10
  },
  {
    id: 3,
    barcode: '8936009876543',
    name: 'Nước ngọt Coca-Cola Sleek 320ml',
    category: 'Nước giải khát',
    unit: 'Lon',
    costPrice: 8500,
    salePrice: 11000,
    stock: 120,
    minThreshold: 24
  },
  {
    id: 4,
    barcode: '8934673123456',
    name: 'Mì gói Hảo Hảo tôm chua cay 75g',
    category: 'Thực phẩm khô',
    unit: 'Gói',
    costPrice: 3800,
    salePrice: 5000,
    stock: 240,
    minThreshold: 30
  },
  {
    id: 5,
    barcode: '8935212345678',
    name: 'Dầu ăn đậu nành Simply 1L',
    category: 'Gia vị & Dầu ăn',
    unit: 'Chai',
    costPrice: 48000,
    salePrice: 59000,
    stock: 4, // Cảnh báo sắp hết (min: 10)
    minThreshold: 10
  },
  {
    id: 6,
    barcode: '8935049500018',
    name: 'Nước tương Maggi Đậm Đặc 700ml',
    category: 'Gia vị & Dầu ăn',
    unit: 'Chai',
    costPrice: 26000,
    salePrice: 32000,
    stock: 14,
    minThreshold: 8
  },
  {
    id: 7,
    barcode: '8934822001017',
    name: 'Khăn ướt em bé không mùi Mamamy 80 tờ',
    category: 'Hóa mỹ phẩm',
    unit: 'Gói',
    costPrice: 32000,
    salePrice: 42000,
    stock: 0, // Đã hết hàng
    minThreshold: 5
  },
  {
    id: 8,
    barcode: '8934602001234',
    name: 'Cà phê hòa tan G7 3in1 hộp 18 gói x 16g',
    category: 'Cà phê & Trà',
    unit: 'Hộp',
    costPrice: 48000,
    salePrice: 58000,
    stock: 2, // Cảnh báo sắp hết (min: 8)
    minThreshold: 8
  }
];

export const INITIAL_CUSTOMERS = [
  { id: 1, name: 'Khách lẻ', phone: '', points: 0 },
  { id: 2, name: 'Nguyễn Thị Mai', phone: '0908123456', points: 140 },
  { id: 3, name: 'Trần Văn Hùng', phone: '0912345678', points: 85 },
  { id: 4, name: 'Lê Hoàng Nam', phone: '0987654321', points: 320 }
];

export const INITIAL_SUPPLIERS = [
  { id: 1, name: 'Công ty CP Sữa Việt Nam (Vinamilk)', phone: '02854155555', address: 'Số 10 Tân Trào, Q.7, TP.HCM' },
  { id: 2, name: 'Công ty TNHH Nước Giải Khát Coca-Cola', phone: '02838961000', address: 'Xa lộ Hà Nội, Q.Thủ Đức, TP.HCM' },
  { id: 3, name: 'Công ty CP Acecook Việt Nam', phone: '02838154064', address: 'KCN Tân Bình, Q.Tân Phú, TP.HCM' },
  { id: 4, name: 'Công ty TNHH Dầu Thực Vật Cái Lân (Simply)', phone: '02438641155', address: 'KCN Hiệp Phước, Nhà Bè, TP.HCM' },
  { id: 5, name: 'Công ty TNHH Quốc Tế Unilever Việt Nam', phone: '02854135686', address: 'Khu A, Đô thị mới Nam TP, Q.7, TP.HCM' }
];

export const INITIAL_BRANCHES = [
  { id: 1, name: 'Chi nhánh Quận 1 (Trụ sở)', address: '128 Nguyễn Trãi, P. Bến Thành, Q.1' },
  { id: 2, name: 'Chi nhánh Quận 3', address: '245 Cách Mạng Tháng 8, P.4, Q.3' },
  { id: 3, name: 'Chi nhánh Bình Thạnh', address: '82 Phan Đăng Lưu, P.5, Q. Bình Thạnh' }
];

export const INITIAL_STOCK_IN_HISTORY = [
  {
    id: 'NK-2026-001',
    supplierName: 'Công ty CP Sữa Việt Nam (Vinamilk)',
    branchName: 'Chi nhánh Quận 1 (Trụ sở)',
    createdDate: '15/09/2026 09:30',
    employeeName: 'Nguyễn Văn A',
    totalItems: 40,
    totalCost: 1120000,
    items: [
      { productId: 1, productName: 'Sữa tươi tiệt trùng Vinamilk 1L', quantity: 40, unitCost: 28000 }
    ]
  },
  {
    id: 'NK-2026-002',
    supplierName: 'Công ty CP Acecook Việt Nam',
    branchName: 'Chi nhánh Quận 1 (Trụ sở)',
    createdDate: '15/09/2026 14:15',
    employeeName: 'Trần Thị Thu',
    totalItems: 200,
    totalCost: 760000,
    items: [
      { productId: 4, productName: 'Mì gói Hảo Hảo tôm chua cay 75g', quantity: 200, unitCost: 3800 }
    ]
  }
];
