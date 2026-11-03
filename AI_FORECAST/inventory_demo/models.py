"""Bước 3: các mô hình dự báo nhu cầu theo tuần.

Mọi mô hình có cùng giao diện:
    fit_predict(Y, X, origin, horizon) -> mảng [số chuỗi x horizon]
trong đó Y là ma trận nhu cầu đã làm sạch [chuỗi x tuần], mô hình CHỈ được nhìn Y[:, :origin]
(không rò rỉ tương lai). X là các biến ngoại sinh biết trước (lịch, khuyến mãi, giá) cho mọi tuần.

Danh sách:
* MA8        – trung bình 8 tuần gần nhất (baseline)
* SNaive     – lấy đúng tuần đó năm trước (baseline mùa vụ)
* ETS        – Holt có xu hướng tắt dần (statsmodels), fallback SES
* TSB        – Croston cải tiến (Teunter-Syntetos-Babai) cho hàng bán không đều
* LightGBM   – một mô hình chung cho mọi chuỗi, dự báo trực tiếp 1..12 tuần với đặc trưng lag,
               rolling, Tết/11-11/12-12, khuyến mãi, giá
"""
from __future__ import annotations

import warnings

import lightgbm as lgb
import numpy as np
import pandas as pd
from statsmodels.tsa.holtwinters import ExponentialSmoothing, SimpleExpSmoothing

MODEL_NAMES = ["MA8", "SNaive", "ETS", "TSB", "LightGBM"]


def ma8(Y: np.ndarray, X: dict, origin: int, horizon: int) -> np.ndarray:
    m = np.nanmean(Y[:, origin - 8:origin], axis=1)
    return np.repeat(m[:, None], horizon, axis=1)


def snaive(Y: np.ndarray, X: dict, origin: int, horizon: int) -> np.ndarray:
    idx = np.arange(origin, origin + horizon) - 52
    if idx.min() < 0:  # chưa đủ 1 năm lịch sử -> quay về MA8
        return ma8(Y, X, origin, horizon)
    return Y[:, idx].copy()


def ets(Y: np.ndarray, X: dict, origin: int, horizon: int) -> np.ndarray:
    out = np.zeros((Y.shape[0], horizon))
    for i in range(Y.shape[0]):
        y = Y[i, :origin]
        with warnings.catch_warnings():
            warnings.simplefilter("ignore")
            try:
                if (y > 0).mean() < 0.5 or y.sum() < 20:
                    raise ValueError("chuỗi quá thưa cho mô hình có xu hướng")
                fit = ExponentialSmoothing(y, trend="add", damped_trend=True,
                                           initialization_method="estimated").fit()
            except Exception:
                fit = SimpleExpSmoothing(y, initialization_method="estimated").fit()
            out[i] = fit.forecast(horizon)
    return np.clip(out, 0, None)


def tsb(Y: np.ndarray, X: dict, origin: int, horizon: int, alpha: float = 0.1, beta: float = 0.1) -> np.ndarray:
    """TSB: cập nhật riêng xác suất có bán (p) mỗi kỳ và cỡ đơn (z) khi có bán. Dự báo = p * z."""
    out = np.zeros((Y.shape[0], horizon))
    for i in range(Y.shape[0]):
        y = Y[i, :origin]
        nz = y[y > 0]
        if len(nz) == 0:
            continue
        p, z = (y > 0).mean(), nz[: max(1, min(len(nz), 4))].mean()
        for v in y:
            p = p + beta * ((v > 0) - p)
            if v > 0:
                z = z + alpha * (v - z)
        out[i] = p * z
    return out


# ------------------------------- LightGBM -------------------------------------
EXOG_COLS = ["promo_days", "discount", "price_ratio"]
CAL_COLS = ["weekofyear", "month", "tet_pre_days", "tet_post_days", "tet_week_code",
            "closed_days", "shopping_event", "holiday"]


def _features(Y: np.ndarray, X: dict, o: int, h: int) -> pd.DataFrame:
    """Đặc trưng cho mọi chuỗi tại gốc dự báo o, dự báo tuần t = o + h - 1."""
    t = o + h - 1
    hist = Y[:, :o]
    scale = np.maximum(np.nanmean(hist[:, -13:], axis=1), 0.1)
    f = {
        "h": np.full(Y.shape[0], h),
        "log_scale": np.log1p(scale),
        "zero_frac13": (hist[:, -13:] == 0).mean(axis=1),
        "roll4": hist[:, -4:].mean(axis=1) / scale,
        "roll8": hist[:, -8:].mean(axis=1) / scale,
        "roll26": hist[:, -26:].mean(axis=1) / scale,
        "std8": hist[:, -8:].std(axis=1) / scale,
        "promo_last": X["promo_days"][:, o - 1],
    }
    for k in (1, 2, 3, 4):
        f[f"lag{k}"] = hist[:, -k] / scale
    # Cùng tuần năm ngoái và mức trung bình năm ngoái quanh gốc -> tăng trưởng so với cùng kỳ.
    if t - 52 >= 0:
        f["ly_same_week"] = Y[:, t - 52] / scale
        f["ly_level"] = Y[:, max(0, o - 52 - 8):o - 52].mean(axis=1) / scale if o - 52 > 0 else np.nan
    else:
        f["ly_same_week"] = np.nan
        f["ly_level"] = np.nan
    for c in EXOG_COLS:
        f[c] = X[c][:, t]
    for c in CAL_COLS:
        f[c] = np.full(Y.shape[0], X["calendar"][c].iloc[t])
    f["category"] = X["category_code"]
    f["store_id"] = X["store_id"]
    return pd.DataFrame(f), scale


def lightgbm_model(Y: np.ndarray, X: dict, origin: int, horizon: int) -> np.ndarray:
    # Tập huấn luyện: mọi gốc o (>= 13 tuần lịch sử) và h sao cho tuần mục tiêu < origin.
    frames, targets = [], []
    for o in range(13, origin):
        for h in range(1, horizon + 1):
            t = o + h - 1
            if t >= origin:
                break
            feat, scale = _features(Y, X, o, h)
            frames.append(feat)
            targets.append(Y[:, t] / scale)
    train = pd.concat(frames, ignore_index=True)
    y = np.concatenate(targets)
    params = {"objective": "regression", "learning_rate": 0.05, "num_leaves": 31, "min_data_in_leaf": 40,
              "bagging_fraction": 0.8, "bagging_freq": 1, "feature_fraction": 0.8, "lambda_l2": 1.0,
              "seed": 0, "verbose": -1, "num_threads": 4}
    ds = lgb.Dataset(train, y, categorical_feature=["category", "store_id"], free_raw_data=False)
    model = lgb.train(params, ds, num_boost_round=400)
    out = np.zeros((Y.shape[0], horizon))
    for h in range(1, horizon + 1):
        feat, scale = _features(Y, X, origin, h)
        out[:, h - 1] = model.predict(feat) * scale
    return np.clip(out, 0, None)


MODELS = {"MA8": ma8, "SNaive": snaive, "ETS": ets, "TSB": tsb, "LightGBM": lightgbm_model}
