"""Sinh DỮ LIỆU MẪU bán hàng + tồn kho theo ngày cho demo dự báo.

Đây KHÔNG phải dữ liệu thật. Tên/giá sản phẩm lấy từ danh mục bhx_products_scraped.json của
dự án cho sát thực tế, còn số lượng bán và tồn kho được mô phỏng có chủ đích để có đủ các
"tình huống khó" mà một hệ thống thật hay gặp:

* xu hướng tăng/giảm, chu kỳ ngày trong tuần, mùa nóng / mùa mưa;
* Tết Nguyên Đán (mua sắm tăng mạnh 3 tuần trước Tết, nghỉ mùng 1-3, giảm sau Tết), 11/11, 12/12;
* khuyến mãi giảm giá (có độ co giãn giá) và hiện tượng "mua tích trữ" làm giảm bán sau khuyến mãi;
* SKU bán chậm, không đều (intermittent);
* hết hàng (stockout): số bán = min(nhu cầu, hàng có sẵn) nên số bán thấp hơn nhu cầu thật;
* lỗi dữ liệu: mất bản ghi ngày, ô số lượng trống, nhập sai (gõ thừa số 0).

Nhu cầu thật (true_demand) được lưu riêng trong data/_true_demand.csv.gz chỉ để kiểm chứng
bước làm sạch; pipeline dự báo không dùng file này.

Chạy: python generate_data.py
"""
from __future__ import annotations

import json

import numpy as np
import pandas as pd

import config as C


def pick_products(rng: np.random.Generator) -> pd.DataFrame:
    """Chọn N_SKU sản phẩm (không tươi sống) từ danh mục và gán tham số nhu cầu."""
    raw = json.loads(C.CATALOG_FILE.read_text(encoding="utf-8"))
    cat = pd.DataFrame(raw)
    cat = cat[~cat["category"].isin(C.EXCLUDED_CATEGORIES)]
    cat = cat[(cat["sale_price"] > 0) & (cat["cost_price"] > 0)].drop_duplicates("name")
    prods = cat.sample(n=C.N_SKU, random_state=C.SEED).reset_index(drop=True)
    prods.insert(0, "sku", [f"SP{i + 1:04d}" for i in range(len(prods))])
    prods = prods.rename(columns={"sale_price": "list_price"}).drop(columns=["image_url"])

    n = len(prods)
    # Hàng rẻ bán nhiều hơn hàng đắt; độ phân tán lớn để doanh thu có dạng Pareto (ABC).
    base = np.exp(rng.normal(1.0, 0.9, n)) * (30000 / prods["list_price"]) ** 0.35
    intermittent = rng.choice(n, size=12, replace=False)           # 12 SKU bán rất chậm
    base[intermittent] = rng.uniform(0.01, 0.08, size=12)
    prods["base_daily"] = np.clip(base, 0.01, 60)
    prods["growth_per_year"] = rng.normal(0.05, 0.15, n)            # có SKU tăng, có SKU giảm
    prods["elasticity"] = rng.uniform(1.5, 3.5, n)
    # Độ "nhiễu" khác nhau giữa các SKU (tham số k của Negative Binomial, nhỏ = biến động mạnh).
    prods["dispersion_k"] = rng.choice([0.8, 1.5, 3.0, 6.0], size=n)
    prods["store2_factor"] = rng.uniform(0.55, 1.1, n)
    prods["pack_size"] = [C.PACK_SIZE.get(c, C.DEFAULT_PACK) for c in prods["category"]]
    # Hàng đắt, bán chậm thì nhập lẻ từng đơn vị.
    prods.loc[prods["base_daily"] < 0.5, "pack_size"] = 1
    return prods


def promo_calendar(prods: pd.DataFrame, dates: pd.DatetimeIndex, rng: np.random.Generator) -> np.ndarray:
    """Ma trận % giảm giá [sku x ngày] dùng chung cho cả chuỗi. Bao gồm cả 12 tuần tương lai
    (kế hoạch khuyến mãi được biết trước, mô hình được phép dùng)."""
    disc = np.zeros((len(prods), len(dates)))
    years = (dates[-1] - dates[0]).days / 365
    for i in range(len(prods)):
        for _ in range(rng.poisson(5 * years)):
            start = rng.integers(0, len(dates) - 7)
            disc[i, start:start + 7] = rng.choice([0.1, 0.15, 0.2, 0.25, 0.3])
    # Sự kiện 11/11 & 12/12: 40% SKU giảm giá 3 ngày (10-12).
    for (m, d) in C.SHOPPING_DAYS:
        for day in dates[(dates.month == m) & (dates.day == d)]:
            idx = np.where((dates >= day - pd.Timedelta(days=1)) & (dates <= day + pd.Timedelta(days=1)))[0]
            chosen = rng.random(len(prods)) < 0.4
            disc[np.ix_(chosen, idx)] = np.maximum(disc[np.ix_(chosen, idx)], rng.choice([0.15, 0.2, 0.3]))
    return disc


def demand_multipliers(prods: pd.DataFrame, dates: pd.DatetimeIndex) -> np.ndarray:
    """Hệ số mùa vụ/lễ [sku x ngày] (chưa gồm khuyến mãi)."""
    n, T = len(prods), len(dates)
    t_years = (dates - C.START_DATE).days.values / 365
    weekday = np.array([0.9, 0.9, 0.95, 0.95, 1.05, 1.2, 1.25])[dates.weekday]
    month = dates.month.values
    mult = np.ones((n, T)) * weekday
    for i, row in prods.iterrows():
        mult[i] *= (1 + row["growth_per_year"]) ** t_years
        if row["category"] in C.DRINK_CATS:
            mult[i] *= np.where(np.isin(month, C.HOT_MONTHS), 1.3, np.where(np.isin(month, C.RAINY_MONTHS), 0.9, 1.0))
        if row["category"] in C.RAINY_CATS:
            mult[i] *= np.where(np.isin(month, C.RAINY_MONTHS), 1.2, 1.0)
        for tet in C.TET_DATES:
            off = (dates - tet).days.values
            before = (off >= -21) & (off < 0)
            ramp = 1 + (21 + off) / 21  # 1 -> 2 khi tới gần Tết
            if row["category"] in C.TET_CATS:
                mult[i] *= np.where(before, 1 + 1.2 * ramp - 0.2, 1.0)    # tới ~3 lần
            else:
                mult[i] *= np.where(before, 1.25, 1.0)
            mult[i] *= np.where((off >= C.TET_CLOSED_DAYS) & (off < 10), 0.6, 1.0)
    for (m, d) in C.SHOPPING_DAYS + C.PUBLIC_HOLIDAYS:
        mult[:, (dates.month == m) & (dates.day == d)] *= 1.3
    return mult


def is_closed(dates: pd.DatetimeIndex) -> np.ndarray:
    closed = np.zeros(len(dates), dtype=bool)
    for tet in C.TET_DATES:
        off = (dates - tet).days.values
        closed |= (off >= 0) & (off < C.TET_CLOSED_DAYS)
    return closed


def simulate_store(i: int, store: int, prods: pd.DataFrame, lam: np.ndarray, disc_row: np.ndarray,
                   price_row: np.ndarray, closed: np.ndarray, dates: pd.DatetimeIndex,
                   rng: np.random.Generator, special: dict) -> tuple[list[dict], dict | None]:
    """Mô phỏng 1 SKU tại 1 cửa hàng: nhu cầu ngẫu nhiên + chính sách nhập hàng 'theo cảm tính'
    của cửa hàng (trung bình 28 ngày bán x số tuần dự trữ tùy SKU) -> sinh ra cả hết hàng lẫn tồn dư."""
    p = prods.iloc[i]
    k = float(p["dispersion_k"])  # Negative Binomial: var = mu + mu^2/k
    cover_weeks = special.get("cover", rng.uniform(0.9, 3.0))
    pack = int(p["pack_size"])
    stock = float(np.ceil(lam[:28].mean() * 7 * (cover_weeks + 1)))
    pipeline: dict[int, float] = {}
    sold_hist: list[float] = []
    rows, pending = [], None
    for t, day in enumerate(dates):
        received = pipeline.pop(t, 0.0)
        opening = stock
        stock += received
        if closed[t]:
            sold_hist.append(0.0)
            continue  # nghỉ Tết: không có dòng dữ liệu
        mu = lam[t]
        demand = rng.negative_binomial(k, k / (k + mu)) if mu > 0 else 0
        sold = min(demand, stock)
        stock -= sold
        sold_hist.append(sold)
        # Thứ Hai: đặt hàng, hàng về sau LEAD_TIME (thỉnh thoảng trễ 3 ngày).
        if day.weekday() == 0 and t >= 7:
            avg = np.mean(sold_hist[-28:])
            on_order = sum(pipeline.values())
            target = avg * 7 * (cover_weeks + 1)
            if special.get("supplier_out_from") is not None and t >= special["supplier_out_from"]:
                target = 0  # nhà cung cấp hết hàng -> không đặt được
            if special.get("bulk_buy_at") == t:
                target = avg * 7 * 20  # mua lô lớn vì được chiết khấu -> tồn dư
            qty = max(0.0, target - stock - on_order)
            qty = float(np.ceil(qty / pack) * pack) if qty > 0 else 0.0
            if qty > 0:
                eta = t + C.LEAD_TIME_DAYS + (3 if rng.random() < 0.1 else 0)
                if eta < len(dates):
                    pipeline[eta] = pipeline.get(eta, 0.0) + qty
                else:
                    pending = {"store_id": store, "sku": p["sku"], "on_order_qty": qty,
                               "eta": dates[0] + pd.Timedelta(days=eta)}
        rows.append({
            "date": day, "store_id": store, "sku": p["sku"], "qty_sold": sold,
            "opening_stock": opening, "received_qty": received, "closing_stock": stock,
            "unit_price": round(price_row[t] * (1 - disc_row[t]), -2), "list_price": price_row[t],
            "promo_flag": int(disc_row[t] > 0), "discount_pct": disc_row[t], "true_demand": demand,
        })
    return rows, pending


def main() -> None:
    rng = np.random.default_rng(C.SEED)
    C.DATA_DIR.mkdir(exist_ok=True)
    prods = pick_products(rng)

    future_end = C.END_DATE + pd.Timedelta(weeks=C.HORIZON_WEEKS)
    all_dates = pd.date_range(C.START_DATE, future_end, freq="D")
    hist = all_dates <= C.END_DATE
    dates = all_dates[hist]

    disc_all = promo_calendar(prods, all_dates, rng)
    # Tăng giá niêm yết 3-8% từ 01/07/2025 cho một nửa số SKU.
    price_all = np.tile(prods["list_price"].values[:, None], (1, len(all_dates))).astype(float)
    up = rng.random(len(prods)) < 0.5
    price_all[np.ix_(up, all_dates >= "2025-07-01")] *= (1 + rng.uniform(0.03, 0.08, up.sum()))[:, None]
    price_all = np.round(price_all, -2)

    mult = demand_multipliers(prods, dates)
    disc = disc_all[:, hist]
    # Hiệu ứng khuyến mãi: lift = (1 - giảm giá)^(-độ co giãn); 7 ngày sau KM bán giảm 15%.
    lift = (1 - disc) ** (-prods["elasticity"].values[:, None])
    post = np.zeros_like(disc, dtype=bool)
    ended = (disc[:, :-1] > 0) & (disc[:, 1:] == 0)
    for i, t in zip(*np.where(ended)):
        post[i, t + 1:t + 8] = True
    price_effect = (price_all[:, hist] / prods["list_price"].values[:, None]) ** (-0.5)
    closed = is_closed(dates)

    # Một vài kịch bản đặc biệt để phần cảnh báo có cái để bắt.
    special_sku = rng.choice(len(prods), size=10, replace=False)
    specials: dict[tuple[int, int], dict] = {}
    for j in special_sku[:5]:
        specials[(j, 1)] = {"bulk_buy_at": len(dates) - 7 * 5}           # thứ Hai 5 tuần trước: mua lô lớn
    for j in special_sku[5:]:
        specials[(j, 1 + int(j % 2))] = {"supplier_out_from": len(dates) - 7 * 3}  # NCC hết hàng 3 tuần

    rows, pendings = [], []
    for i in range(len(prods)):
        for store in C.STORES:
            base = prods.at[i, "base_daily"] * (1 if store == 1 else prods.at[i, "store2_factor"])
            lam = base * mult[i] * lift[i] * np.where(post[i], 0.85, 1.0) * price_effect[i]
            r, pend = simulate_store(i, store, prods, lam, disc[i], price_all[i, hist], closed, dates, rng,
                                     specials.get((i, store), {}))
            rows += r
            if pend:
                pendings.append(pend)
    df = pd.DataFrame(rows)

    # ---- Cài lỗi dữ liệu như hệ thống thật ----
    drop = rng.random(len(df)) < 0.01                       # 1% ngày mất bản ghi
    df = df[~drop].reset_index(drop=True)
    nan_mask = rng.random(len(df)) < 0.005                  # 0.5% ô số lượng bị trống
    typo_mask = (rng.random(len(df)) < 0.001) & (df["qty_sold"] > 0)  # 0.1% gõ thừa số 0
    df["qty_sold"] = df["qty_sold"].astype(float)
    df.loc[typo_mask, "qty_sold"] *= 10
    df.loc[nan_mask, "qty_sold"] = np.nan

    truth = df[["date", "store_id", "sku", "true_demand"]]
    df = df.drop(columns=["true_demand"])
    df.to_csv(C.DATA_DIR / "sales_daily.csv.gz", index=False)
    truth.to_csv(C.DATA_DIR / "_true_demand.csv.gz", index=False)
    prods[["sku", "barcode", "name", "category", "unit", "cost_price", "list_price", "pack_size"]].to_csv(
        C.DATA_DIR / "products.csv", index=False)

    # Kế hoạch giá & khuyến mãi cho 12 tuần tới (đã biết trước).
    fut = ~hist
    plan = pd.DataFrame({
        "date": np.repeat(all_dates[fut], len(prods)),
        "sku": np.tile(prods["sku"].values, fut.sum()),
        "discount_pct": disc_all[:, fut].T.ravel(),
        "list_price": price_all[:, fut].T.ravel(),
    })
    plan.to_csv(C.DATA_DIR / "promo_plan_future.csv", index=False)
    pd.DataFrame(pendings, columns=["store_id", "sku", "on_order_qty", "eta"]).to_csv(
        C.DATA_DIR / "on_order.csv", index=False)
    print(f"Đã sinh {len(df):,} dòng bán hàng cho {len(prods)} SKU x {len(C.STORES)} cửa hàng "
          f"({dates[0].date()} -> {dates[-1].date()}) vào {C.DATA_DIR}")


if __name__ == "__main__":
    main()
