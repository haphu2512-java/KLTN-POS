"""Bước 1: khám phá & làm sạch dữ liệu, khôi phục nhu cầu thật bị che bởi hết hàng.

Ý tưởng chính: số bán = min(nhu cầu, hàng có sẵn). Ngày hết hàng thì số bán chỉ là "chặn dưới"
của nhu cầu, nếu đưa thẳng vào mô hình sẽ dự báo thấp -> nhập ít -> lại hết hàng (vòng luẩn quẩn).
Ta ước lượng nhu cầu kỳ vọng của từng ngày từ các ngày "sạch" xung quanh rồi thay thế.
"""
from __future__ import annotations

import numpy as np
import pandas as pd

import config as C


def load_raw() -> tuple[pd.DataFrame, pd.DataFrame]:
    sales = pd.read_csv(C.DATA_DIR / "sales_daily.csv.gz", parse_dates=["date"])
    products = pd.read_csv(C.DATA_DIR / "products.csv")
    return sales, products


def closed_days(dates: pd.DatetimeIndex) -> np.ndarray:
    """Ngày cửa hàng nghỉ (mùng 1-3 Tết) – biết trước, nhu cầu = 0, không phải dữ liệu thiếu."""
    closed = np.zeros(len(dates), dtype=bool)
    for tet in C.TET_DATES:
        off = (dates - tet).days.values
        closed |= (off >= 0) & (off < C.TET_CLOSED_DAYS)
    return closed


def clean_daily(sales: pd.DataFrame) -> pd.DataFrame:
    """Trả về bảng theo ngày đầy đủ lịch, có cột `demand` (nhu cầu đã làm sạch) và các cờ chất lượng."""
    dates = pd.date_range(C.START_DATE, C.END_DATE, freq="D")
    keys = sales[["store_id", "sku"]].drop_duplicates()
    full = keys.merge(pd.DataFrame({"date": dates}), how="cross")
    df = full.merge(sales, on=["store_id", "sku", "date"], how="left")
    df = df.sort_values(["store_id", "sku", "date"]).reset_index(drop=True)

    closed_map = pd.Series(closed_days(dates), index=dates)
    df["closed"] = df["date"].map(closed_map)
    df["missing_record"] = df["opening_stock"].isna() & ~df["closed"]
    df["missing_qty"] = df["qty_sold"].isna() & ~df["missing_record"] & ~df["closed"]

    available = df["opening_stock"] + df["received_qty"]
    # Lỗi vật lý: bán nhiều hơn số hàng đang có -> chắc chắn nhập sai.
    df["outlier"] = df["qty_sold"] > available + 1e-9
    df["stockout"] = (df["closing_stock"] <= 0) & ~df["missing_record"] & ~df["closed"]
    df["promo_flag"] = df["promo_flag"].fillna(0)

    g = df.groupby(["store_id", "sku"], sort=False)
    clean_q = df["qty_sold"].where(~(df["outlier"] | df["stockout"] | df["missing_record"] |
                                     df["missing_qty"] | df["closed"]))

    # Phát hiện thêm ngoại lai thống kê (ngày không KM): > trung vị 28 ngày + 8 x MAD.
    med = clean_q.groupby([df["store_id"], df["sku"]]).transform(
        lambda s: s.rolling(29, center=True, min_periods=7).median())
    mad = (clean_q - med).abs().groupby([df["store_id"], df["sku"]]).transform(
        lambda s: s.rolling(29, center=True, min_periods=7).median())
    stat_out = (clean_q > med + 8 * mad.clip(lower=1) + 5) & (df["promo_flag"] == 0)
    df["outlier"] |= stat_out.fillna(False)
    clean_q = clean_q.where(~stat_out.fillna(False))

    # Chỉ số ngày trong tuần của từng chuỗi (từ ngày sạch, không KM).
    nonpromo = clean_q.where(df["promo_flag"] == 0)
    wd = df["date"].dt.weekday
    wd_mean = nonpromo.groupby([df["store_id"], df["sku"], wd]).transform("mean")
    all_mean = nonpromo.groupby([df["store_id"], df["sku"]]).transform("mean")
    wd_idx = (wd_mean / all_mean).fillna(1).clip(0.5, 2)

    # Mức nền: trung bình trượt 29 ngày (canh giữa) của các ngày sạch không KM, đã khử ngày-trong-tuần.
    level = (nonpromo / wd_idx).groupby([df["store_id"], df["sku"]]).transform(
        lambda s: s.rolling(29, center=True, min_periods=5).mean())
    level = level.groupby([df["store_id"], df["sku"]]).transform(lambda s: s.ffill().bfill()).fillna(0)

    # Hệ số tăng do KM của từng chuỗi (giới hạn 1..4 để tránh ước lượng cực đoan).
    expected_np = level * wd_idx
    ratio = (clean_q / expected_np.replace(0, np.nan)).where(df["promo_flag"] == 1)
    promo_lift = ratio.groupby([df["store_id"], df["sku"]]).transform("median").fillna(1.5).clip(1, 4)
    df["expected"] = expected_np * np.where(df["promo_flag"] == 1, promo_lift, 1.0)

    demand = df["qty_sold"].copy()
    fill = df["missing_record"] | df["missing_qty"] | df["outlier"]
    demand[fill] = df.loc[fill, "expected"]
    so = df["stockout"] & ~fill
    demand[so] = np.maximum(df.loc[so, "qty_sold"], df.loc[so, "expected"])  # nhu cầu bị chặn
    demand[df["closed"]] = 0.0
    df["demand"] = demand.fillna(0.0)
    df["qty_sold_clean"] = df["qty_sold"].where(~df["outlier"]).fillna(0)  # dùng tính doanh thu
    df["revenue"] = df["qty_sold_clean"] * df["unit_price"].fillna(0)
    del g
    return df


def week_calendar(week_starts: pd.DatetimeIndex) -> pd.DataFrame:
    """Đặc trưng lịch cho mỗi tuần (bắt đầu thứ Hai) – dùng cho cả quá khứ lẫn tương lai."""
    rows = []
    for ws in week_starts:
        days = pd.date_range(ws, periods=7, freq="D")
        tet_pre, tet_post, tet_week = 0, 0, 0
        for tet in C.TET_DATES:
            off = (days - tet).days.values
            tet_pre += int(((off >= -21) & (off < 0)).sum())
            tet_post += int(((off >= 0) & (off < 10)).sum())
            dist = (ws - tet).days // 7
            if -6 <= dist <= 3:
                tet_week = dist + 10  # mã hóa vị trí tuần quanh Tết (4..13), 0 = xa Tết
        rows.append({
            "week_start": ws,
            "weekofyear": ws.isocalendar().week,
            "month": (ws + pd.Timedelta(days=3)).month,
            "tet_pre_days": tet_pre,
            "tet_post_days": tet_post,
            "tet_week_code": tet_week,
            "closed_days": int(closed_days(days).sum()),
            "shopping_event": int(any((d.month, d.day) in C.SHOPPING_DAYS for d in days)),
            "holiday": int(any((d.month, d.day) in C.PUBLIC_HOLIDAYS for d in days)),
        })
    return pd.DataFrame(rows)


def to_weekly(daily: pd.DataFrame) -> pd.DataFrame:
    """Gộp về tuần (thứ Hai-Chủ nhật) cho từng (cửa hàng, SKU)."""
    d = daily.copy()
    d["week_start"] = d["date"] - pd.to_timedelta(d["date"].dt.weekday, unit="D")
    d["price_ratio"] = d["unit_price"] / d["list_price"]
    w = d.groupby(["store_id", "sku", "week_start"]).agg(
        demand=("demand", "sum"),
        sales=("qty_sold_clean", "sum"),
        revenue=("revenue", "sum"),
        promo_days=("promo_flag", "sum"),
        discount=("discount_pct", "mean"),
        price_ratio=("price_ratio", "mean"),
        list_price=("list_price", "mean"),
        stockout_days=("stockout", "sum"),
    ).reset_index()
    w["discount"] = w["discount"].fillna(0)
    w["price_ratio"] = w["price_ratio"].fillna(1)
    w["list_price"] = w.groupby(["store_id", "sku"])["list_price"].transform(lambda s: s.ffill().bfill())
    w["week_idx"] = ((w["week_start"] - C.START_DATE).dt.days // 7).astype(int)
    return w


def quality_summary(daily: pd.DataFrame) -> pd.DataFrame:
    """Bảng tóm tắt chất lượng dữ liệu theo SKU-cửa hàng."""
    open_days = ~daily["closed"]
    s = daily[open_days].groupby(["store_id", "sku"]).agg(
        days=("date", "size"),
        missing_record=("missing_record", "sum"),
        missing_qty=("missing_qty", "sum"),
        outliers=("outlier", "sum"),
        stockout_days=("stockout", "sum"),
        sales_raw=("qty_sold_clean", "sum"),
        demand_clean=("demand", "sum"),
    ).reset_index()
    s["stockout_rate"] = s["stockout_days"] / s["days"]
    s["lost_sales_est"] = (s["demand_clean"] - s["sales_raw"]).clip(lower=0)
    return s


def validate_against_truth(daily: pd.DataFrame) -> dict:
    """So sánh với nhu cầu thật (chỉ có vì là dữ liệu mẫu) để chứng minh làm sạch có tác dụng."""
    path = C.DATA_DIR / "_true_demand.csv.gz"
    if not path.exists():
        return {}
    truth = pd.read_csv(path, parse_dates=["date"])
    m = daily.merge(truth, on=["date", "store_id", "sku"], how="inner")
    m["week_start"] = m["date"] - pd.to_timedelta(m["date"].dt.weekday, unit="D")
    w = m.groupby(["store_id", "sku", "week_start"])[["true_demand", "qty_sold_clean", "demand"]].sum()
    so_weeks = m.groupby(["store_id", "sku", "week_start"])["stockout"].sum() > 0
    ws = w[so_weeks]
    tot = ws["true_demand"].sum()
    return {
        "true_total": float(w["true_demand"].sum()),
        "raw_total": float(w["qty_sold_clean"].sum()),
        "clean_total": float(w["demand"].sum()),
        # Trên các tuần có hết hàng: sai lệch tổng so với nhu cầu thật.
        "bias_raw_stockout_weeks": float((ws["qty_sold_clean"].sum() - tot) / tot),
        "bias_clean_stockout_weeks": float((ws["demand"].sum() - tot) / tot),
    }
