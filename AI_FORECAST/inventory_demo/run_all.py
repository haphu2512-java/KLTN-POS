"""Chạy toàn bộ quy trình: dữ liệu mẫu -> làm sạch -> ABC-XYZ -> backtest -> dự báo -> tồn kho -> xuất kết quả.

    python run_all.py              # dùng dữ liệu đã có trong data/, tự sinh nếu chưa có
    python run_all.py --regenerate # sinh lại dữ liệu mẫu trước khi chạy
"""
from __future__ import annotations

import argparse
import time

import numpy as np
import pandas as pd

import analysis as an
import cleaning as cl
import config as C
import generate_data
import outputs as out


def assumptions() -> list[tuple[str, str]]:
    return [
        ("Dữ liệu", "DỮ LIỆU MẪU tự sinh, có chủ đích chứa xu hướng, mùa vụ, Tết, 11/11, 12/12, khuyến mãi, "
                    "hết hàng, hàng bán chậm và lỗi nhập liệu. Tên/giá sản phẩm lấy từ danh mục Bách Hóa Xanh của dự án."),
        ("Ngành hàng", C.INDUSTRY + "; bỏ qua hàng tươi sống (rau, thịt, cá, trái cây) vì hạn dùng ngắn."),
        ("Quy mô", f"{C.N_SKU} SKU x {len(C.STORES)} chi nhánh; dữ liệu theo ngày "
                   f"{C.START_DATE:%d/%m/%Y}–{C.END_DATE:%d/%m/%Y} (104 tuần), dự báo theo tuần."),
        ("Lead time", f"{C.LEAD_TIME_DAYS} ngày từ lúc đặt đến lúc hàng về; đặt hàng mỗi thứ Hai "
                      f"(chu kỳ xem xét {C.REVIEW_PERIOD_DAYS} ngày)."),
        ("Mức phục vụ", f"{C.SERVICE_LEVEL:.0%} (xác suất không hết hàng trong một chu kỳ), hệ số z = 1,645."),
        ("Tầm dự báo", f"{C.HORIZON_WEEKS} tuần, khoảng tin cậy 95% lấy từ sai số backtest của chính mô hình "
                       "(giả định sai số phân phối chuẩn, tăng dần theo tầm dự báo)."),
        ("Mùa vụ", "Tết Nguyên Đán (3 tuần trước Tết tăng mạnh, nghỉ mùng 1–3), 11/11, 12/12, 30/4–1/5, 2/9, "
                   "mùa nóng tháng 3–5, mùa mưa tháng 5–11."),
        ("Khuyến mãi & giá", "Lịch khuyến mãi và giá bán 12 tuần tới được coi là đã biết trước (kế hoạch marketing)."),
        ("Làm sạch", "Ngày hết hàng: nhu cầu = max(số bán, nhu cầu kỳ vọng từ các ngày sạch quanh đó). Ngày mất dữ liệu, "
                     "ô trống, giá trị vô lý (bán > tồn) hoặc ngoại lai thống kê: thay bằng nhu cầu kỳ vọng."),
        ("ABC-XYZ", "ABC theo doanh thu 52 tuần (A: 80% đầu, B: 15% tiếp, C: 5% cuối); XYZ theo hệ số biến thiên "
                    "nhu cầu tuần (X < 0,5 ≤ Y < 1,0 ≤ Z)."),
        ("Backtest", f"3 gốc thời gian (tuần {', '.join(str(o) for o in C.BACKTEST_ORIGINS)}), mỗi gốc dự báo "
                     "12 tuần, không xáo trộn dữ liệu. Đánh giá trên nhu cầu đã làm sạch."),
        ("Tồn kho", "SS = z·σ·√L; ROP = dự báo trong L + SS; mức tồn mục tiêu S = dự báo trong (L+R) + z·σ·√(L+R); "
                    "SL đặt = S − (tồn + hàng đang về), làm tròn lên theo quy cách thùng. σ là độ lệch chuẩn sai số dự báo 1 tuần."),
        ("Cảnh báo", f"Nguy cơ hết hàng: tồn + hàng đang về < ROP (rất cao nếu < nhu cầu trong lead time). "
                     f"Tồn kho quá mức: đủ bán > {C.OVERSTOCK_WEEKS} tuần."),
        ("Giới hạn", "Chưa tính hạn sử dụng, sức chứa kho, chiết khấu theo số lượng, chi phí đặt hàng (EOQ) và "
                     "ảnh hưởng thay thế giữa các sản phẩm."),
    ]


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--regenerate", action="store_true", help="sinh lại dữ liệu mẫu")
    args = ap.parse_args()
    t0 = time.time()
    C.OUT_DIR.mkdir(exist_ok=True)
    C.CHART_DIR.mkdir(exist_ok=True)

    if args.regenerate or not (C.DATA_DIR / "sales_daily.csv.gz").exists():
        print("[0] Sinh dữ liệu mẫu")
        generate_data.main()

    print("[1] Làm sạch dữ liệu, khôi phục nhu cầu bị che bởi hết hàng")
    sales, products = cl.load_raw()
    daily = cl.clean_daily(sales)
    weekly = cl.to_weekly(daily)
    quality = cl.quality_summary(daily)
    check = cl.validate_against_truth(daily)

    print("[2] Phân loại ABC-XYZ")
    seg = an.abc_xyz(weekly, products)
    series, Y, X = an.build_panel(weekly, products)

    print("[3-4] Backtest theo thời gian")
    bt = an.backtest(Y, X, series)
    ev = an.evaluate(bt, seg)

    print("[5] Dự báo 12 tuần tới")
    fl, s_final = an.final_forecast(Y, X, series, seg, ev["selection"], bt)

    print("[6-7] Tồn kho an toàn, ROP, đề xuất đặt hàng, cảnh báo")
    inv = an.inventory_plan(s_final, daily)

    # ---------------- Các con số cho báo cáo ----------------
    wk = weekly.merge(products[["sku", "list_price"]].rename(columns={"list_price": "lp"}), on="sku")
    last52 = wk[wk["week_idx"] >= 52]
    prev52 = wk[wk["week_idx"] < 52]
    tot = weekly.groupby("week_start")["demand"].sum()
    normal = tot[(X["calendar"]["tet_pre_days"].values[:len(tot)] == 0) &
                 (X["calendar"]["tet_post_days"].values[:len(tot)] == 0)].median()
    dly = daily[~daily["closed"]]
    wkend = dly["date"].dt.weekday >= 5
    growth = Y[:, 52:].sum(axis=1) / np.maximum(Y[:, :52].sum(axis=1), 1) - 1
    g_sku = pd.Series(Y[:, 52:].sum(axis=1), index=series["sku"]).groupby(level=0).sum() / \
        pd.Series(Y[:, :52].sum(axis=1), index=series["sku"]).groupby(level=0).sum().clip(lower=1) - 1
    bys = ev["by_segment"].merge(ev["selection"], left_on=["segment", "model"], right_on=["segment", "best_model"],
                                 suffixes=("", "_sel"))
    lost_units = (daily["demand"] - daily["qty_sold_clean"]).where(daily["stockout"]).clip(lower=0)
    facts = {
        "fc_start": X["week_start"][Y.shape[1]], "fc_end": X["week_start"][-1] + pd.Timedelta(days=6),
        "n_order": int((inv["order_qty"] > 0).sum()), "order_value": float(inv["order_value"].sum()),
        "n_urgent": int((inv["alert_level"] >= 2).sum()), "n_out": int((inv["alert"] == "Đang hết hàng").sum()),
        "excess_value": float(inv.loc[inv["alert_level"] == 1, "excess_value"].sum()),
        "stockout_rate": float(dly["stockout"].mean()),
        "lost_revenue": float((lost_units * daily["unit_price"].fillna(0)).sum()),
        "rev_last": float(last52["revenue"].sum()),
        "yoy": float(last52["revenue"].sum() / prev52["revenue"].sum() - 1),
        "n_growing": int((g_sku > 0.10).sum()), "n_declining": int((g_sku < -0.10).sum()),
        "tet_peak_ratio": float(tot.max() / normal),
        "weekend_uplift": float(dly.loc[wkend, "demand"].mean() / dly.loc[~wkend, "demand"].mean() - 1),
        "bias_raw": check.get("bias_raw_stockout_weeks", np.nan),
        "bias_clean": check.get("bias_clean_stockout_weeks", np.nan),
        "missing_record": int(daily["missing_record"].sum()), "missing_qty": int(daily["missing_qty"].sum()),
        "outliers": int(daily["outlier"].sum()), "n_z": int((seg["XYZ"] == "Z").sum()),
        "wape_ax": float(bys.loc[bys["segment"] == "AX", "WAPE"].iloc[0]) if (bys["segment"] == "AX").any() else np.nan,
        "wape_cz": float(bys.loc[bys["segment"].str.endswith("Z"), "WAPE"].min()) if bys["segment"].str.endswith("Z").any() else np.nan,
        "n_supplier_issue": int(((inv["on_hand"] <= 0) & (inv["on_order"] <= 0)).sum()),
    }
    del growth
    ho = ev["holdout"]
    kpis = [
        ("LƯU Ý", "DỮ LIỆU MẪU - không phải số liệu thật"),
        ("Giai đoạn dữ liệu", f"{C.START_DATE:%d/%m/%Y} – {C.END_DATE:%d/%m/%Y}"),
        ("Giai đoạn dự báo", f"{facts['fc_start']:%d/%m/%Y} – {facts['fc_end']:%d/%m/%Y}"),
        ("Số SKU x chi nhánh", f"{C.N_SKU} x {len(C.STORES)}"),
        ("Doanh thu 52 tuần gần nhất", out.vnd(facts["rev_last"])),
        ("Tăng trưởng doanh thu so với 52 tuần trước", out.pct(facts["yoy"])),
        ("Tỷ lệ ngày hết hàng (lịch sử)", out.pct(facts["stockout_rate"])),
        ("Doanh thu ước tính mất do hết hàng (2 năm)", out.vnd(facts["lost_revenue"])),
        ("WAPE mô hình chọn theo nhóm (kiểm tra gốc cuối)", out.pct(ho.loc["Chọn theo nhóm", "WAPE"])),
        ("WAPE baseline trung bình 8 tuần (kiểm tra gốc cuối)", out.pct(ho.loc["MA8", "WAPE"])),
        ("Số dòng cần đặt hàng tuần này", facts["n_order"]),
        ("Giá trị đặt hàng đề xuất (giá vốn)", out.vnd(facts["order_value"])),
        ("Số SKU-chi nhánh nguy cơ hết hàng", facts["n_urgent"]),
        ("Số SKU-chi nhánh đang hết hàng", facts["n_out"]),
        ("Số SKU-chi nhánh tồn kho quá mức / không luân chuyển", int((inv["alert_level"] == 1).sum())),
        ("Vốn tồn dư ước tính", out.vnd(facts["excess_value"])),
    ]

    R = {"weekly": weekly, "X": X, "backtest": bt, "eval": ev, "forecast_long": fl, "series_final": s_final,
         "inventory": inv, "segments": seg, "quality": quality, "kpis": kpis, "facts": facts,
         "assumptions": assumptions()}

    print("[8] Xuất Excel, biểu đồ, báo cáo")
    top10 = seg.sort_values("revenue_52w", ascending=False)["sku"].head(10).tolist()
    out.chart_overview(R, C.CHART_DIR / "tong_quan_nhu_cau.png")
    out.chart_models(R, C.CHART_DIR / "so_sanh_mo_hinh.png")
    out.chart_top10(R, top10)
    out.write_excel(R, C.OUT_DIR / "ket_qua_du_bao_ton_kho.xlsx")
    out.write_report(R, C.OUT_DIR / "BAO_CAO_QUAN_LY.md")
    print(f"Xong trong {time.time() - t0:.0f}s. Kết quả ở {C.OUT_DIR}")


if __name__ == "__main__":
    main()
