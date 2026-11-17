"""Đánh giá mô hình dự báo ĐANG CHẠY TRONG HỆ THỐNG trên dữ liệu bán tạp hóa thật.

Mô phỏng đúng cách ForecastService làm việc: tại mỗi ngày gốc, lấy 30 ngày lịch sử (ngày không bán = 0),
dự báo một mức nhu cầu/ngày rồi dùng mức đó cho 7 ngày kế tiếp. Lặp lại cho mọi ngày gốc trong năm
(rolling origin) và mọi sản phẩm, nên mỗi mô hình được chấm trên ~690.000 điểm thay vì một cửa sổ 7 ngày.

Trả lời 4 câu hỏi:
1. Exponential Smoothing α = 0.35 và Moving Average 7 ngày sai bao nhiêu trên dữ liệu thật?
2. α nào tốt nhất, và dò α riêng cho từng sản phẩm có đáng không?
3. Công thức tồn kho an toàn (Z = 1.65, L = 2) có đạt mức phục vụ 95% như thiết kế không?
4. Nhãn độ tin cậy GOOD / FAIR / LOW có phản ánh đúng sai số về sau không?

Chạy: python evaluate.py   (đọc data/sales_daily.csv.gz, ghi outputs/)
"""
from __future__ import annotations

import sys
from pathlib import Path

import numpy as np
import pandas as pd

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT.parent))

from pos_forecast import models as pos_models  # noqa: E402
from pos_forecast.config import ForecastConfig  # noqa: E402

CFG = ForecastConfig()
HISTORY, HORIZON = CFG.history_days, CFG.review_period_days
ALPHAS = [0.05, 0.1, 0.15, 0.2, 0.25, 0.3, 0.35, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9]
OUT_DIR = ROOT / "outputs"


# ------------------------------------------------------------------ dữ liệu
def load_matrix() -> tuple[np.ndarray, pd.DataFrame, pd.DatetimeIndex]:
    """Ma trận Y [sản phẩm x ngày], đã điền 0 cho ngày không bán."""
    sales = pd.read_csv(ROOT / "data" / "sales_daily.csv.gz", parse_dates=["date"])
    products = pd.read_csv(ROOT / "data" / "products.csv")
    days = pd.date_range(sales["date"].min(), sales["date"].max(), freq="D")
    wide = (sales.pivot(index="product_id", columns="date", values="quantity_sold")
            .reindex(index=products["product_id"], columns=days).fillna(0.0))
    return wide.to_numpy(), products, days


# ------------------------------------------------------------------ mô hình (vector hóa)
def ses(hist: np.ndarray, alpha: float) -> np.ndarray:
    """Giống pos_forecast.models.exponential_smoothing, tính cho mọi sản phẩm cùng lúc."""
    level = hist[:, :CFG.ma_window].mean(axis=1)
    for k in range(hist.shape[1]):
        level = alpha * hist[:, k] + (1 - alpha) * level
    return level


def self_check(Y: np.ndarray) -> None:
    """Bản vector hóa phải cho đúng kết quả của công thức gốc trong pos_forecast (cũng là công thức của Java)."""
    rng = np.random.default_rng(0)
    for _ in range(50):
        i, o = rng.integers(Y.shape[0]), rng.integers(HISTORY, Y.shape[1])
        hist = Y[i:i + 1, o - HISTORY:o]
        for a in (0.1, 0.35, 0.9):
            assert abs(ses(hist, a)[0] - pos_models.exponential_smoothing(hist[0].tolist(), a, CFG.ma_window)) < 1e-9
        assert abs(hist[0, -7:].mean() - pos_models.moving_average(hist[0].tolist(), 7)) < 1e-9
        assert abs(hist[0].std(ddof=1) - pos_models.std_dev(hist[0].tolist())) < 1e-9


def run_backtest(Y: np.ndarray) -> tuple[dict[str, np.ndarray], np.ndarray, dict[str, np.ndarray]]:
    """Trả về dự báo của từng mô hình [sản phẩm x gốc x 7], thực tế cùng kích thước, và số liệu phụ theo gốc."""
    P, D = Y.shape
    origins = range(HISTORY, D - HORIZON + 1)
    names = [f"ES α={a:g}" for a in ALPHAS] + ["MA7", "Trung bình 30 ngày", "Hôm qua (naive)", "Cùng thứ, 4 tuần"]
    fc = {n: np.zeros((P, len(origins), HORIZON)) for n in names}
    actual = np.zeros((P, len(origins), HORIZON))
    extra = {k: np.zeros((P, len(origins))) for k in ("sigma", "label_rel")}
    val = CFG.validation_days
    for j, o in enumerate(origins):
        hist, actual[:, j] = Y[:, o - HISTORY:o], Y[:, o:o + HORIZON]
        for a in ALPHAS:
            fc[f"ES α={a:g}"][:, j] = ses(hist, a)[:, None]
        fc["MA7"][:, j] = hist[:, -7:].mean(axis=1)[:, None]
        fc["Trung bình 30 ngày"][:, j] = hist.mean(axis=1)[:, None]
        fc["Hôm qua (naive)"][:, j] = hist[:, -1][:, None]
        for h in range(HORIZON):  # ngày gốc + h có cùng thứ với các ngày cách đó 7, 14, 21, 28 ngày
            fc["Cùng thứ, 4 tuần"][:, j, h] = hist[:, [HISTORY + h - 7 * m for m in (1, 2, 3, 4)]].mean(axis=1)
        extra["sigma"][:, j] = hist.std(axis=1, ddof=1)
        # Nhãn độ tin cậy như hệ thống: học 23 ngày đầu, chấm 7 ngày cuối của chính cửa sổ lịch sử
        train, test = hist[:, :-val], hist[:, -val:]
        mae = np.abs(test - ses(train, CFG.alpha)[:, None]).mean(axis=1)
        mean = test.mean(axis=1)
        extra["label_rel"][:, j] = np.where(mean > 0, mae / np.where(mean > 0, mean, 1), np.where(mae == 0, 0, np.inf))
    return fc, actual, extra


# ------------------------------------------------------------------ chỉ số
def score(f: np.ndarray, a: np.ndarray) -> dict[str, float]:
    err = f - a
    weekly = np.abs(f.sum(axis=-1) - a.sum(axis=-1))
    return {
        "MAE": np.abs(err).mean(),
        "RMSE": np.sqrt((err ** 2).mean()),
        "WAPE ngày": np.abs(err).sum() / a.sum(),
        "WAPE tổng 7 ngày": weekly.sum() / a.sum(),
        "Bias": err.sum() / a.sum(),
    }


def main() -> None:
    OUT_DIR.mkdir(exist_ok=True)
    Y, products, days = load_matrix()
    self_check(Y)
    fc, actual, extra = run_backtest(Y)
    P, O, _ = actual.shape
    res: dict = {"n_products": P, "n_origins": O, "n_points": actual.size, "first": days[0], "last": days[-1]}

    # 1. So sánh mô hình
    table = pd.DataFrame({n: score(f, actual) for n, f in fc.items()}).T
    table.index.name = "Mô hình"
    table.to_csv(OUT_DIR / "model_comparison.csv", float_format="%.4f")
    res["table"] = table

    # Theo nhóm tốc độ bán
    mean_daily = Y.mean(axis=1)
    groups = {"Bán nhanh (≥ 10/ngày)": mean_daily >= 10, "Trung bình (3–10/ngày)": (mean_daily >= 3) & (mean_daily < 10),
              "Bán chậm (< 3/ngày)": mean_daily < 3}
    res["by_group"] = pd.DataFrame({
        g: {"Số sản phẩm": int(m.sum()), "Tỉ lệ ngày không bán": float((Y[m] == 0).mean()),
            **{f"WAPE tổng 7 ngày, {n}": score(fc[n][m], actual[m])["WAPE tổng 7 ngày"] for n in ("ES α=0.35", "MA7")},
            "WAPE ngày, ES α=0.35": score(fc["ES α=0.35"][m], actual[m])["WAPE ngày"]}
        for g, m in groups.items()}).T

    # 2. α theo từng sản phẩm: chọn trên nửa đầu năm, chấm trên nửa sau (mô hình chưa thấy khi chọn)
    half = O // 2
    es = np.stack([fc[f"ES α={a:g}"] for a in ALPHAS])            # [α, P, O, 7]
    mae_first = np.abs(es[:, :, :half] - actual[None, :, :half]).mean(axis=(2, 3))   # [α, P]
    mae_second = np.abs(es[:, :, half:] - actual[None, :, half:]).mean(axis=(2, 3))
    pick = mae_first.argmin(axis=0)
    i035 = ALPHAS.index(CFG.alpha)
    global_pick = int(mae_first.mean(axis=1).argmin())
    res["alpha"] = {
        "second_half_mae_035": mae_second[i035].mean(),
        "second_half_mae_per_product": mae_second[pick, np.arange(P)].mean(),
        "second_half_mae_global": mae_second[global_pick].mean(),
        "global_alpha": ALPHAS[global_pick],
        "second_half_mae_oracle": mae_second.min(axis=0).mean(),
        "stable_share": float((pick == mae_second.argmin(axis=0)).mean()),
    }
    best_full = np.abs(es - actual[None]).mean(axis=(2, 3)).argmin(axis=0)
    per_product = products[["product_id", "category", "product_type"]].assign(
        mean_daily=mean_daily.round(2), best_alpha=[ALPHAS[i] for i in best_full],
        mae_best=np.abs(es - actual[None]).mean(axis=(2, 3)).min(axis=0).round(3),
        mae_alpha_035=np.abs(fc["ES α=0.35"] - actual).mean(axis=(1, 2)).round(3))
    per_product.to_csv(OUT_DIR / "alpha_per_product.csv", index=False)
    res["best_alpha_counts"] = per_product["best_alpha"].value_counts().reindex(ALPHAS, fill_value=0)
    res["group_alpha"] = {g: ALPHAS[int(np.abs(es[:, m] - actual[None, m]).mean(axis=(1, 2, 3)).argmin())]
                          for g, m in groups.items()}

    # 3. Mức phục vụ thực tế của điểm đặt hàng lại: ROP có đủ cho nhu cầu trong L ngày chờ hàng không?
    L = CFG.lead_time_days
    level = fc["ES α=0.35"][:, :, 0]
    demand_lt = actual[:, :, :L].sum(axis=2)
    service = {}
    for z in (0.0, 1.28, CFG.z, 2.33):
        rop = level * L + z * extra["sigma"] * np.sqrt(L)
        service[z] = {"all": float((demand_lt <= rop).mean()),
                      **{g: float((demand_lt[m] <= rop[m]).mean()) for g, m in groups.items()}}
    res["service"] = service

    # 4. Nhãn độ tin cậy có báo trước được sai số 7 ngày tới không?
    rel = extra["label_rel"]
    labels = np.where(rel <= 0.3, "GOOD", np.where(rel <= 0.6, "FAIR", "LOW"))
    abs_err = np.abs(fc["ES α=0.35"] - actual).sum(axis=2)
    res["labels"] = {lab: {"share": float((labels == lab).mean()),
                           "forward_wape": float(abs_err[labels == lab].sum() / actual.sum(axis=2)[labels == lab].sum())}
                     for lab in ("GOOD", "FAIR", "LOW")}

    from report import write_chart, write_report  # tách riêng để file này chỉ còn phần tính toán
    write_chart(res, ALPHAS, OUT_DIR / "alpha_du_lieu_that.png")
    write_report(res, CFG, ALPHAS, OUT_DIR / "KET_QUA.md")
    print(table.round(4).to_string())
    print(f"Đã ghi {OUT_DIR / 'KET_QUA.md'}")


if __name__ == "__main__":
    main()
