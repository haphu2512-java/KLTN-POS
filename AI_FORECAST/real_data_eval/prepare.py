"""Chuẩn hóa dữ liệu gốc về dạng doanh số theo ngày của từng sản phẩm, giống cái ForecastService đọc từ MySQL.

Đầu ra:
* data/sales_daily.csv.gz  – date, product_id, quantity_sold (chỉ ngày có bán; ngày không bán sẽ được điền 0 khi đánh giá)
* data/products.csv        – product_id, department, category, product_type, package_size, selling_days, total_qty

Cách chọn sản phẩm: bỏ xăng dầu, phiếu giảm giá và các dòng số lượng bất thường (> 100 đơn vị một dòng,
thường là hàng bán theo cân/lít), rồi lấy N_PRODUCTS sản phẩm có nhiều ngày bán nhất. Số bán được cộng trên
mọi cửa hàng của chuỗi, coi như một chi nhánh.
"""
from __future__ import annotations

from pathlib import Path

import pandas as pd
import pyreadr

ROOT = Path(__file__).resolve().parent
RAW_DIR = ROOT / "data" / "raw"
DATA_DIR = ROOT / "data"

N_PRODUCTS = 300
EXCLUDED_DEPARTMENTS = {"FUEL", "COUPON", "MISCELLANEOUS", "CHARITABLE CONT", "POSTAL CENTER", "TRAVEL & LEISURE"}
MAX_QTY_PER_LINE = 100


def main() -> None:
    tx = pyreadr.read_r(str(RAW_DIR / "transactions.rds"))[None]
    products = pyreadr.read_r(str(RAW_DIR / "products.rda"))["products"]
    tx = tx.merge(products, on="product_id", how="left")
    tx["date"] = tx["transaction_timestamp"].dt.normalize()
    # Giao dịch sau nửa đêm ngày 31/12 rơi sang 01/01/2018: bỏ để có đúng 365 ngày trọn.
    tx = tx[tx["date"] <= "2017-12-31"]
    n_raw = len(tx)

    tx = tx[(tx["quantity"] > 0) & ~tx["department"].isin(EXCLUDED_DEPARTMENTS)]
    bad = tx.groupby("product_id")["quantity"].max()
    tx = tx[~tx["product_id"].isin(bad[bad > MAX_QTY_PER_LINE].index)]

    stats = tx.groupby("product_id").agg(selling_days=("date", "nunique"), total_qty=("quantity", "sum"))
    top = stats.sort_values(["selling_days", "total_qty"], ascending=False).head(N_PRODUCTS)
    tx = tx[tx["product_id"].isin(top.index)]

    daily = (tx.groupby(["date", "product_id"], as_index=False)["quantity"].sum()
             .rename(columns={"quantity": "quantity_sold"}).sort_values(["product_id", "date"]))
    daily["quantity_sold"] = daily["quantity_sold"].astype(int)
    daily["date"] = daily["date"].dt.strftime("%Y-%m-%d")
    daily.to_csv(DATA_DIR / "sales_daily.csv.gz", index=False)

    info = (products[products["product_id"].isin(top.index)]
            .rename(columns={"product_category": "category"})
            [["product_id", "department", "category", "product_type", "package_size"]]
            .merge(top.reset_index(), on="product_id").sort_values("selling_days", ascending=False))
    info["total_qty"] = info["total_qty"].astype(int)
    info.to_csv(DATA_DIR / "products.csv", index=False)

    print(f"{n_raw:,} dòng giao dịch gốc -> {len(daily):,} dòng (sản phẩm, ngày) của {len(top)} sản phẩm")
    print(f"số ngày bán: nhiều nhất {top['selling_days'].max()}, ít nhất {top['selling_days'].min()}")


if __name__ == "__main__":
    main()
