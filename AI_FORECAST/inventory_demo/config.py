"""Cấu hình & giả định cho demo dự báo nhu cầu / tối ưu tồn kho.

Toàn bộ dữ liệu là DỮ LIỆU MẪU tự sinh (không phải số liệu thật của cửa hàng).
Mọi giả định nghiệp vụ được gom ở đây để dễ đổi và để in ra sheet "Giả định" trong Excel.
"""
from __future__ import annotations

from pathlib import Path
import pandas as pd

ROOT = Path(__file__).resolve().parent
DATA_DIR = ROOT / "data"
OUT_DIR = ROOT / "outputs"
CHART_DIR = OUT_DIR / "charts"

# Danh mục sản phẩm thật của dự án (cào từ Bách Hóa Xanh) – chỉ dùng tên, giá, nhóm hàng.
CATALOG_FILE = ROOT.parents[1] / "bhx_products_scraped.json"

SEED = 2026

# ---- Bối cảnh (thay cho các placeholder trong đề bài) ----
INDUSTRY = "Bán lẻ tạp hóa / bách hóa (cửa hàng dùng KLTN POS)"
N_SKU = 80
STORES = {1: "Chi nhánh 1 - Quận 7", 2: "Chi nhánh 2 - Thủ Đức"}
START_DATE = pd.Timestamp("2024-10-28")   # thứ Hai
END_DATE = pd.Timestamp("2026-10-25")     # Chủ nhật -> đúng 104 tuần trọn
LEAD_TIME_DAYS = 7          # từ lúc đặt tới lúc hàng về
REVIEW_PERIOD_DAYS = 7      # đặt hàng mỗi thứ Hai
SERVICE_LEVEL = 0.95        # mức phục vụ chu kỳ (cycle service level)
HORIZON_WEEKS = 12          # dự báo 12 tuần tới

# Ngày Tết Nguyên Đán (mùng 1) trong và sau giai đoạn dữ liệu.
TET_DATES = [pd.Timestamp("2025-01-29"), pd.Timestamp("2026-02-17"), pd.Timestamp("2027-02-06")]
TET_CLOSED_DAYS = 3         # cửa hàng nghỉ mùng 1-3 (không có giao dịch)
# Ngày lễ/sự kiện mua sắm có khuyến mãi lớn: 11/11, 12/12, 30/4-1/5, 2/9.
SHOPPING_DAYS = [(11, 11), (12, 12)]
PUBLIC_HOLIDAYS = [(4, 30), (5, 1), (9, 2)]
RAINY_MONTHS = [5, 6, 7, 8, 9, 10, 11]   # mùa mưa miền Nam
HOT_MONTHS = [3, 4, 5]

# ---- Phân loại ----
ABC_CUTOFFS = (0.80, 0.95)  # A: 80% doanh thu đầu, B: 15% tiếp, C: 5% cuối
XYZ_CUTOFFS = (0.5, 1.0)    # CV nhu cầu tuần: X < 0.5 <= Y < 1.0 <= Z

# ---- Backtest ----
BACKTEST_ORIGINS = [68, 80, 92]   # tuần bắt đầu dự báo (chỉ số 0..103), mỗi lần 12 tuần
OVERSTOCK_WEEKS = 8               # tồn kho > 8 tuần bán -> tồn kho quá mức

# Nhóm hàng tươi sống không đưa vào demo (hạn dùng ngắn, cần mô hình khác).
EXCLUDED_CATEGORIES = {
    "Trái cây tươi", "Củ, quả tươi", "Cá, hải sản tươi", "Thịt & Món mặn", "Rau lá tươi",
    "Rau tươi", "Thịt gà, vịt", "Rau sống & Salad", "Thịt heo", "Thịt bò", "Trứng gà, vịt, cút",
    "Tiệc tại gia", "Tráng miệng", "Món canh & Lẩu",
}

# Nhóm hàng theo kiểu mùa vụ (dùng khi sinh dữ liệu mẫu).
DRINK_CATS = {"Nước ngọt", "Bia & Nước có cồn", "Nước tăng lực", "Nước khoáng, nước suối", "Nước ép trái cây"}
TET_CATS = {"Bia & Nước có cồn", "Nước ngọt", "Bánh quy", "Kẹo các loại", "Kẹo cứng", "Socola",
            "Bánh Chocopie", "Hạt khô & Hạt dinh dưỡng", "Hạt khô", "Dầu ăn", "Nước mắm", "Bánh bông lan",
            "Trà khô, trà túi lọc", "Kẹo dẻo, kẹo marshmallow"}
RAINY_CATS = {"Mì ăn liền", "Cháo gói, cháo tươi", "Phở, bún ăn liền", "Hủ tiếu, miến", "Cà phê hoà tan"}

# Quy cách đóng gói theo nhóm hàng (số lượng đặt làm tròn lên bội số). Mặc định 6.
PACK_SIZE = {
    "Nước ngọt": 24, "Nước khoáng, nước suối": 24, "Bia & Nước có cồn": 24, "Nước tăng lực": 24,
    "Mì ăn liền": 30, "Sữa tươi": 12, "Sữa chua": 12, "Sữa chua uống": 12,
}
DEFAULT_PACK = 6
