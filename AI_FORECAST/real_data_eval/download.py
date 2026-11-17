"""Tải dữ liệu gốc "The Complete Journey" (84.51°) từ GitHub của gói R `completejourney` (giấy phép CC0).

Đây là giao dịch mua hàng tạp hóa THẬT của 2.469 hộ gia đình tại một chuỗi siêu thị Mỹ trong năm 2017.
File gốc (~14 MB) được lưu ở data/raw/ và không đưa vào git; prepare.py gộp lại thành
data/sales_daily.csv.gz (đã có sẵn trong repo) nên chỉ cần chạy lại file này khi muốn tái lập từ đầu.
"""
from __future__ import annotations

import urllib.request
from pathlib import Path

BASE = "https://raw.githubusercontent.com/bradleyboehmke/completejourney/master/data/"
FILES = ["transactions.rds", "products.rda"]
RAW_DIR = Path(__file__).resolve().parent / "data" / "raw"


def main() -> None:
    RAW_DIR.mkdir(parents=True, exist_ok=True)
    for name in FILES:
        target = RAW_DIR / name
        if target.exists():
            print(f"đã có {target.name}")
            continue
        print(f"tải {name} ...")
        # Tải vào file tạm rồi mới đổi tên, để lần tải bị ngắt giữa chừng không bị coi là file hoàn chỉnh
        partial = target.with_name(target.name + ".part")
        urllib.request.urlretrieve(BASE + name, partial)
        partial.replace(target)
        print(f"  -> {target} ({target.stat().st_size / 1e6:.1f} MB)")


if __name__ == "__main__":
    main()
