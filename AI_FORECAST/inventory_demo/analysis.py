"""Bước 2, 4, 5, 6, 7: phân loại ABC-XYZ, backtest, chọn mô hình, dự báo kèm khoảng tin cậy,
tính safety stock / ROP / số lượng đặt và cảnh báo."""
from __future__ import annotations

import numpy as np
import pandas as pd
from scipy.stats import norm

import config as C
from cleaning import week_calendar
from models import MODEL_NAMES, MODELS


# ------------------------------ Ma trận dữ liệu -----------------------------------
def build_panel(weekly: pd.DataFrame, products: pd.DataFrame) -> tuple[pd.DataFrame, np.ndarray, dict]:
    """Chuyển bảng tuần thành ma trận Y [chuỗi x tuần] và các biến ngoại sinh X (gồm 12 tuần tương lai)."""
    n_hist = int(weekly["week_idx"].max()) + 1
    n_all = n_hist + C.HORIZON_WEEKS
    series = weekly[["store_id", "sku"]].drop_duplicates().sort_values(["store_id", "sku"]).reset_index(drop=True)
    series = series.merge(products[["sku", "name", "category", "cost_price", "list_price", "pack_size"]], on="sku")
    key = {(r.store_id, r.sku): i for i, r in series.iterrows()}
    S = len(series)
    Y = np.zeros((S, n_hist))
    X = {c: np.zeros((S, n_all)) for c in ["promo_days", "discount"]}
    X["price_ratio"] = np.ones((S, n_all))
    rows = weekly.assign(i=[key[(a, b)] for a, b in zip(weekly["store_id"], weekly["sku"])])
    Y[rows["i"], rows["week_idx"]] = rows["demand"]
    for c in ["promo_days", "discount", "price_ratio"]:
        X[c][rows["i"], rows["week_idx"]] = rows[c]

    # Kế hoạch khuyến mãi tương lai (biết trước) -> đặc trưng tuần.
    plan = pd.read_csv(C.DATA_DIR / "promo_plan_future.csv", parse_dates=["date"])
    plan["week_idx"] = ((plan["date"] - C.START_DATE).dt.days // 7).astype(int)
    plan["promo"] = (plan["discount_pct"] > 0).astype(int)
    pw = plan.groupby(["sku", "week_idx"]).agg(promo_days=("promo", "sum"), discount=("discount_pct", "mean")).reset_index()
    pw["price_ratio"] = 1 - pw["discount"]
    for _, r in series.iterrows():
        sub = pw[pw["sku"] == r["sku"]]
        i = key[(r["store_id"], r["sku"])]
        for c in ["promo_days", "discount", "price_ratio"]:
            X[c][i, sub["week_idx"].values] = sub[c].values

    weeks = pd.date_range(C.START_DATE, periods=n_all, freq="7D")
    X["calendar"] = week_calendar(weeks)
    X["week_start"] = weeks
    X["category_code"] = series["category"].astype("category").cat.codes.values
    X["store_id"] = series["store_id"].values
    return series, Y, X


# ------------------------------ ABC - XYZ -----------------------------------------
def abc_xyz(weekly: pd.DataFrame, products: pd.DataFrame) -> pd.DataFrame:
    """ABC theo doanh thu 52 tuần gần nhất; XYZ theo hệ số biến thiên (CV) nhu cầu tuần (gộp 2 cửa hàng)."""
    last = weekly[weekly["week_idx"] >= weekly["week_idx"].max() - 51]
    rev = last.groupby("sku")["revenue"].sum().sort_values(ascending=False)
    share = rev.cumsum() / rev.sum()
    a, b = C.ABC_CUTOFFS
    abc = pd.Series(np.where(share.shift(fill_value=0) < a, "A", np.where(share.shift(fill_value=0) < b, "B", "C")),
                    index=rev.index)
    dem = last.groupby(["sku", "week_start"])["demand"].sum().unstack()
    cv = dem.std(axis=1) / dem.mean(axis=1).replace(0, np.nan)
    x, y = C.XYZ_CUTOFFS
    xyz = pd.Series(np.where(cv < x, "X", np.where(cv < y, "Y", "Z")), index=cv.index)
    # Phân loại kiểu nhu cầu theo Syntetos-Boylan (ADI, CV^2 của cỡ đơn khác 0).
    nz = dem.where(dem > 0)
    adi = dem.shape[1] / (dem > 0).sum(axis=1).replace(0, np.nan)
    cv2 = (nz.std(axis=1) / nz.mean(axis=1)) ** 2
    pattern = np.where(adi < 1.32, np.where(cv2 < 0.49, "Đều (smooth)", "Thất thường (erratic)"),
                       np.where(cv2 < 0.49, "Ngắt quãng (intermittent)", "Cục bộ (lumpy)"))
    out = pd.DataFrame({"revenue_52w": rev, "revenue_share": rev / rev.sum(), "cum_share": share, "ABC": abc})
    out = out.join(pd.DataFrame({"weekly_mean": dem.mean(axis=1), "CV": cv, "XYZ": xyz, "ADI": adi,
                                 "CV2_size": cv2, "demand_pattern": pattern}))
    out["segment"] = out["ABC"] + out["XYZ"]
    out = out.reset_index().rename(columns={"index": "sku"})
    return products[["sku", "name", "category"]].merge(out, on="sku").sort_values("revenue_52w", ascending=False)


# ------------------------------ Backtest ------------------------------------------
def backtest(Y: np.ndarray, X: dict, series: pd.DataFrame, log=print) -> pd.DataFrame:
    """Backtest cuốn chiếu theo thời gian: mỗi gốc chỉ học từ quá khứ, dự báo 12 tuần kế tiếp."""
    H = C.HORIZON_WEEKS
    recs = []
    for o in C.BACKTEST_ORIGINS:
        actual = Y[:, o:o + H]
        for name in MODEL_NAMES:
            fc = MODELS[name](Y, X, o, H)
            log(f"  backtest gốc tuần {o:>3} | {name:<8} | WAPE {np.abs(fc - actual).sum() / actual.sum():.1%}")
            for h in range(H):
                recs.append(pd.DataFrame({"series": np.arange(len(series)), "origin": o, "h": h + 1,
                                          "model": name, "actual": actual[:, h], "forecast": fc[:, h]}))
    bt = pd.concat(recs, ignore_index=True)
    bt["error"] = bt["forecast"] - bt["actual"]
    return bt.merge(series[["store_id", "sku"]], left_on="series", right_index=True)


def metrics(df: pd.DataFrame) -> pd.Series:
    a = df["actual"].sum()
    return pd.Series({
        "MAE": df["error"].abs().mean(),
        "WAPE": df["error"].abs().sum() / a if a else np.nan,
        "Bias": df["error"].sum() / a if a else np.nan,   # >0: dự báo cao hơn thực tế
        "n_points": len(df),
    })


def select_models(bt: pd.DataFrame, seg: pd.DataFrame, origins: list[int]) -> pd.DataFrame:
    """Chọn mô hình có WAPE thấp nhất cho từng nhóm ABC-XYZ (hòa thì chọn |Bias| nhỏ hơn)."""
    d = bt[bt["origin"].isin(origins)].merge(seg[["sku", "segment"]], on="sku")
    m = d.groupby(["segment", "model"]).apply(metrics, include_groups=False).reset_index()
    m["abs_bias"] = m["Bias"].abs()
    best = m.sort_values(["segment", "WAPE", "abs_bias"]).groupby("segment").head(1)
    return best[["segment", "model", "WAPE", "Bias"]].rename(columns={"model": "best_model"})


def evaluate(bt: pd.DataFrame, seg: pd.DataFrame) -> dict:
    """Bảng so sánh mô hình + đánh giá trung thực của chiến lược 'chọn theo nhóm':
    chọn trên 2 gốc đầu, chấm điểm trên gốc cuối (dữ liệu mô hình chưa thấy khi chọn)."""
    overall = bt.groupby("model").apply(metrics, include_groups=False).reindex(MODEL_NAMES)
    d = bt.merge(seg[["sku", "segment", "ABC", "XYZ"]], on="sku")
    by_seg = d.groupby(["segment", "model"]).apply(metrics, include_groups=False).reset_index()
    by_abc = d.groupby(["ABC", "model"]).apply(metrics, include_groups=False).reset_index()

    early, last = C.BACKTEST_ORIGINS[:-1], C.BACKTEST_ORIGINS[-1]
    pick = select_models(bt, seg, early)
    holdout = d[d["origin"] == last]
    combo = holdout.merge(pick[["segment", "best_model"]], on="segment")
    combo = combo[combo["model"] == combo["best_model"]]
    holdout_tbl = holdout.groupby("model").apply(metrics, include_groups=False).reindex(MODEL_NAMES)
    holdout_tbl.loc["Chọn theo nhóm"] = metrics(combo)
    final_pick = select_models(bt, seg, C.BACKTEST_ORIGINS)
    return {"overall": overall, "by_segment": by_seg, "by_abc": by_abc,
            "holdout": holdout_tbl, "selection": final_pick}


# ------------------------------ Dự báo cuối ---------------------------------------
def final_forecast(Y: np.ndarray, X: dict, series: pd.DataFrame, seg: pd.DataFrame, selection: pd.DataFrame,
                   bt: pd.DataFrame, log=print) -> tuple[pd.DataFrame, pd.DataFrame]:
    """Huấn luyện lại trên toàn bộ 104 tuần, dự báo 12 tuần, khoảng tin cậy 95% từ sai số backtest."""
    H, o = C.HORIZON_WEEKS, Y.shape[1]
    s = series.merge(seg[["sku", "segment", "ABC", "XYZ"]], on="sku", how="left")
    s = s.merge(selection[["segment", "best_model"]], on="segment", how="left")
    s["best_model"] = s["best_model"].fillna("ETS")
    fc = np.zeros((len(s), H))
    for name in s["best_model"].unique():
        log(f"  dự báo cuối bằng {name}")
        idx = np.where(s["best_model"] == name)[0]
        fc[idx] = MODELS[name](Y, X, o, H)[idx]

    # Độ lệch chuẩn sai số của mô hình được chọn cho từng chuỗi, nhân hệ số theo tầm dự báo h.
    chosen = bt.merge(s[["store_id", "sku", "best_model"]], on=["store_id", "sku"])
    chosen = chosen[chosen["model"] == chosen["best_model"]]
    rmse = chosen.groupby("series")["error"].apply(lambda e: np.sqrt(np.mean(e ** 2)))
    sigma = rmse.reindex(range(len(s))).fillna(rmse.median()).values
    chosen = chosen.assign(z=chosen["error"] / chosen["series"].map(rmse).replace(0, np.nan))
    h_factor = chosen.groupby("h")["z"].std().reindex(range(1, H + 1)).fillna(1).values
    sigma_h = sigma[:, None] * h_factor[None, :]
    s["sigma_week"] = sigma * h_factor[0]

    z = norm.ppf(0.975)
    weeks = X["week_start"][o:o + H]
    long = []
    for h in range(H):
        long.append(pd.DataFrame({
            "store_id": s["store_id"], "sku": s["sku"], "name": s["name"], "segment": s["segment"],
            "model": s["best_model"], "week": h + 1, "week_start": weeks[h],
            "forecast": fc[:, h], "lo95": np.clip(fc[:, h] - z * sigma_h[:, h], 0, None),
            "hi95": fc[:, h] + z * sigma_h[:, h],
        }))
    return pd.concat(long, ignore_index=True), s.assign(**{f"f{h + 1}": fc[:, h] for h in range(H)})


# ------------------------------ Tồn kho -------------------------------------------
def inventory_plan(s: pd.DataFrame, daily: pd.DataFrame) -> pd.DataFrame:
    """Safety stock, ROP, mức tồn mục tiêu và số lượng đặt cho chính sách xem xét định kỳ hằng tuần.

    * L = lead time (tuần), R = chu kỳ đặt hàng (tuần), z = hệ số mức phục vụ.
    * σ = độ lệch chuẩn sai số dự báo 1 tuần (từ backtest) – đo đúng rủi ro "dự báo sai" chứ không
      phải chỉ biến động nhu cầu.
    * SS  = z · σ · √L
    * ROP = nhu cầu dự báo trong L + SS                (dưới mức này là phải đặt ngay)
    * S   = nhu cầu dự báo trong (L + R) + z · σ · √(L+R)   (mức tồn mục tiêu sau khi đặt)
    * Q   = S − (tồn hiện có + hàng đang về), làm tròn lên theo quy cách thùng.
    """
    L, R = C.LEAD_TIME_DAYS / 7, C.REVIEW_PERIOD_DAYS / 7
    z = norm.ppf(C.SERVICE_LEVEL)
    fcols = [f"f{h}" for h in range(1, C.HORIZON_WEEKS + 1)]
    last = (daily.dropna(subset=["closing_stock"]).sort_values("date")
            .groupby(["store_id", "sku"]).tail(1)[["store_id", "sku", "closing_stock"]])
    oo = pd.read_csv(C.DATA_DIR / "on_order.csv").groupby(["store_id", "sku"])["on_order_qty"].sum().reset_index()
    p = s.merge(last, on=["store_id", "sku"], how="left").merge(oo, on=["store_id", "sku"], how="left")
    p["on_hand"] = p["closing_stock"].fillna(0)
    p["on_order"] = p["on_order_qty"].fillna(0)
    p["inv_position"] = p["on_hand"] + p["on_order"]

    def demand_over(weeks: float) -> pd.Series:
        full = int(np.floor(weeks))
        tot = p[fcols[:full]].sum(axis=1) if full else 0
        frac = weeks - full
        return tot + (p[fcols[full]] * frac if frac else 0)

    p["avg_weekly_fc"] = p[fcols].mean(axis=1)
    p["demand_LT"] = demand_over(L)
    p["safety_stock"] = np.ceil(z * p["sigma_week"] * np.sqrt(L))
    p["ROP"] = np.ceil(p["demand_LT"] + p["safety_stock"])
    p["order_up_to"] = np.ceil(demand_over(L + R) + z * p["sigma_week"] * np.sqrt(L + R))
    raw_q = (p["order_up_to"] - p["inv_position"]).clip(lower=0)
    p["order_qty"] = np.where(raw_q > 0, np.ceil(raw_q / p["pack_size"]) * p["pack_size"], 0)
    p["order_value"] = p["order_qty"] * p["cost_price"]
    p["weeks_of_cover"] = p["inv_position"] / p["avg_weekly_fc"].replace(0, np.nan)
    sigma_L = (p["sigma_week"] * np.sqrt(L)).replace(0, np.nan)
    # Xác suất hết hàng trước khi lô hàng đặt hôm nay về (giả định sai số chuẩn).
    p["stockout_prob"] = pd.Series(1 - norm.cdf((p["inv_position"] - p["demand_LT"]) / sigma_L), index=p.index).fillna(0)
    p["excess_qty"] = (p["inv_position"] - C.OVERSTOCK_WEEKS * p["avg_weekly_fc"]).clip(lower=0)
    p["excess_value"] = p["excess_qty"] * p["cost_price"]
    p["alert"], p["alert_level"], p["action"] = zip(*[_alert(r) for r in p.itertuples()])
    return p


def _alert(r) -> tuple[str, int, str]:
    """Trả về (nội dung cảnh báo, mức độ 3=khẩn..0=ổn, hành động)."""
    if r.on_hand <= 0 and r.avg_weekly_fc > 0.5:
        return "Đang hết hàng", 3, f"Đặt ngay {int(r.order_qty)} và xin giao gấp"
    if r.inv_position < r.demand_LT:
        return "Nguy cơ hết hàng rất cao", 3, f"Đặt ngay {int(r.order_qty)}; cân nhắc điều chuyển từ chi nhánh khác"
    if r.inv_position < r.ROP:
        return "Nguy cơ hết hàng", 2, f"Đặt {int(r.order_qty)} trong lần đặt hàng tuần này"
    if r.avg_weekly_fc < 0.05 and r.on_hand > 0:
        return "Hàng không luân chuyển", 1, "Ngừng nhập, xả hàng/khuyến mãi"
    if r.weeks_of_cover > C.OVERSTOCK_WEEKS:
        return "Tồn kho quá mức", 1, f"Ngừng nhập; dư khoảng {int(r.excess_qty)} đơn vị, cân nhắc KM hoặc điều chuyển"
    return "Bình thường", 0, (f"Đặt {int(r.order_qty)} theo lịch" if r.order_qty > 0 else "Chưa cần đặt")
