"""Ghi biểu đồ và báo cáo KET_QUA.md từ kết quả của evaluate.py."""
from __future__ import annotations

from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402

INK, MUTED, GRID, SURFACE = "#0b0b0b", "#52514e", "#e4e3df", "#fcfcfb"
BLUE, BLUE_LIGHT, ORANGE = "#2a78d6", "#9ec5f4", "#eb6834"


def pct(x: float, digits: int = 1) -> str:
    return f"{x * 100:.{digits}f}%".replace(".", ",")


def num(x: float, digits: int = 2) -> str:
    return f"{x:.{digits}f}".replace(".", ",")


def write_chart(res: dict, alphas: list[float], path: Path) -> None:
    table = res["table"]
    wape = [table.loc[f"ES α={a:g}", "WAPE tổng 7 ngày"] * 100 for a in alphas]
    plt.rcParams.update({"font.family": "DejaVu Sans", "font.size": 10, "text.color": INK,
                         "axes.labelcolor": MUTED, "xtick.color": MUTED, "ytick.color": MUTED})
    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(11, 4.2), facecolor=SURFACE)
    for ax in (ax1, ax2):
        ax.set_facecolor(SURFACE)
        for side in ("top", "right", "left"):
            ax.spines[side].set_visible(False)
        ax.spines["bottom"].set_color(GRID)
        ax.grid(axis="y", color=GRID, linewidth=0.8)
        ax.set_axisbelow(True)
        ax.tick_params(length=0)

    ax1.plot(alphas, wape, color=BLUE, linewidth=2, marker="o", markersize=4)
    i = alphas.index(0.35)
    best = min(range(len(alphas)), key=lambda k: wape[k])
    ax1.scatter([alphas[i]], [wape[i]], s=70, color=ORANGE, zorder=3, edgecolor=SURFACE, linewidth=2)
    ax1.annotate(f"α = 0,35 (đang dùng): {num(wape[i], 1)}%", (alphas[i], wape[i]), xytext=(10, -14),
                 textcoords="offset points", color=INK)
    ax1.annotate(f"α = {num(alphas[best], 2).rstrip('0')}: {num(wape[best], 1)}%", (alphas[best], wape[best]),
                 xytext=(8, -16), textcoords="offset points", color=INK)
    ax1.set_ylim(bottom=min(wape) - 4)
    ax1.xaxis.set_major_formatter(lambda x, _: f"{x:g}".replace(".", ","))
    ax1.set_title("α càng lớn, sai số tổng 7 ngày càng cao", loc="left", fontsize=12, fontweight="bold")
    ax1.set_xlabel("Hệ số làm mịn α của Exponential Smoothing")
    ax1.set_ylabel("WAPE tổng 7 ngày (%)")

    counts = res["best_alpha_counts"]
    ax2.bar([f"{a:g}".replace(".", ",") for a in alphas], counts.values, color=BLUE, width=0.7)
    for k, v in enumerate(counts.values):
        if v:
            ax2.text(k, v + 2, str(int(v)), ha="center", color=INK, fontsize=9)
    ax2.set_title("Phần lớn sản phẩm hợp với α nhỏ", loc="left", fontsize=12, fontweight="bold")
    ax2.set_xlabel("α cho MAE nhỏ nhất của từng sản phẩm")
    ax2.set_ylabel("Số sản phẩm")
    fig.text(0.01, 0.005, f"Dữ liệu thật: {res['n_products']} sản phẩm tạp hóa, {res['n_origins']} ngày gốc, "
             "The Complete Journey (84.51°), 2017", color=MUTED, fontsize=8.5)
    fig.tight_layout(rect=(0, 0.03, 1, 1))
    fig.savefig(path, dpi=160, facecolor=SURFACE)
    plt.close(fig)


def write_report(res: dict, cfg, alphas: list[float], path: Path) -> None:
    t, a, s, lab = res["table"], res["alpha"], res["service"], res["labels"]
    es35, ma7 = t.loc[f"ES α={cfg.alpha:g}"], t.loc["MA7"]
    vn = lambda name: name.replace(".", ",")  # noqa: E731
    best_name = t["MAE"].idxmin()
    best = t.loc[best_name]
    best_weekly_name = t["WAPE tổng 7 ngày"].idxmin()
    gain_mae = 1 - best["MAE"] / es35["MAE"]
    gain_weekly = 1 - t.loc[best_weekly_name, "WAPE tổng 7 ngày"] / es35["WAPE tổng 7 ngày"]
    small = int(res["best_alpha_counts"].loc[[x for x in alphas if x <= 0.1]].sum())

    lines = [
        "# Kiểm chứng mô hình dự báo trên dữ liệu bán hàng thật",
        "",
        "> Dữ liệu: **The Complete Journey** của 84.51° (qua gói R `completejourney`, giấy phép CC0): giao dịch mua "
        "hàng tạp hóa thật của 2.469 hộ gia đình tại một chuỗi siêu thị Mỹ, từ "
        f"{res['first']:%d/%m/%Y} đến {res['last']:%d/%m/%Y}. Đây **không phải** dữ liệu của cửa hàng Việt Nam và "
        "chỉ là mẫu theo hộ gia đình (không phải toàn bộ doanh số cửa hàng), nên dùng để kiểm chứng **phương pháp**, "
        "không dùng để khẳng định con số cho một cửa hàng cụ thể.",
        "",
        f"Phạm vi: {res['n_products']} sản phẩm bán đều nhất, {res['n_origins']} ngày gốc dự báo, mỗi lần dùng "
        f"{cfg.history_days} ngày lịch sử để dự báo {cfg.review_period_days} ngày kế tiếp, tổng cộng "
        + f"{res['n_points']:,}".replace(",", ".") + " điểm so sánh.",
        "",
        "## 1. Kết quả chính",
        "",
        f"- **Mô hình đang chạy (Exponential Smoothing α = {num(cfg.alpha)})**: mỗi ngày lệch trung bình "
        f"{num(es35['MAE'])} đơn vị/sản phẩm (MAE), RMSE {num(es35['RMSE'])}. Tổng nhu cầu 7 ngày, con số quyết định "
        f"lượng đặt hàng, sai {pct(es35['WAPE tổng 7 ngày'])}. Độ lệch hệ thống gần bằng 0 (Bias {pct(es35['Bias'], 2)}), "
        "tức mô hình không dự báo thừa hay thiếu một cách có hệ thống.",
        f"- **Moving Average 7 ngày** gần như ngang: MAE {num(ma7['MAE'])}, tổng 7 ngày sai {pct(ma7['WAPE tổng 7 ngày'])}.",
        f"- **α nhỏ tốt hơn trên dữ liệu này.** Tốt nhất là {vn(best_name)} (MAE {num(best['MAE'])}, thấp hơn "
        f"{pct(gain_mae)}); tính theo tổng 7 ngày thì {vn(best_weekly_name)} sai "
        f"{pct(t.loc[best_weekly_name, 'WAPE tổng 7 ngày'])}, thấp hơn {pct(gain_weekly)} so với α = {num(cfg.alpha)}. "
        f"{small}/{res['n_products']} sản phẩm có α tốt nhất ≤ 0,1.",
        f"- **Dò α riêng cho từng sản phẩm không đáng công.** Chọn α từng sản phẩm trên nửa đầu năm rồi chấm trên nửa "
        f"sau: MAE {num(a['second_half_mae_per_product'], 3)}, so với {num(a['second_half_mae_global'], 3)} khi dùng "
        f"một α chung = {num(a['global_alpha'])} và {num(a['second_half_mae_035'], 3)} với α = {num(cfg.alpha)}. "
        f"α tốt nhất của một sản phẩm chỉ giữ nguyên giữa hai nửa năm ở {pct(a['stable_share'], 0)} số sản phẩm.",
        f"- **Tồn kho an toàn với Z = {num(cfg.z)} chỉ đạt mức phục vụ {pct(s[cfg.z]['all'])}**, thấp hơn mục tiêu 95%. "
        f"Muốn đạt khoảng 95% trên dữ liệu này cần Z = 2,33 ({pct(s[2.33]['all'])}).",
        f"- **Nhãn độ tin cậy xếp đúng thứ tự nhưng ngưỡng quá chặt**: chỉ {pct(lab['GOOD']['share'])} số lần được "
        f"GOOD, {pct(lab['LOW']['share'])} bị LOW.",
        "",
        "![α trên dữ liệu thật](alpha_du_lieu_that.png)",
        "",
        "## 2. So sánh mô hình",
        "",
        "Mọi mô hình đều chỉ nhìn 30 ngày trước ngày gốc. *WAPE ngày* = tổng sai lệch từng ngày / tổng số bán. "
        "*WAPE tổng 7 ngày* = sai lệch của tổng 7 ngày / tổng số bán. *Bias* dương là dự báo cao hơn thực tế.",
        "",
        "| Mô hình | MAE | RMSE | WAPE ngày | WAPE tổng 7 ngày | Bias |",
        "|---|---|---|---|---|---|",
    ]
    for name, r in t.iterrows():
        mark = " (đang dùng)" if name == f"ES α={cfg.alpha:g}" else ""
        lines.append(f"| {name.replace('.', ',')}{mark} | {num(r['MAE'], 3)} | {num(r['RMSE'], 3)} | {pct(r['WAPE ngày'])} | "
                     f"{pct(r['WAPE tổng 7 ngày'])} | {pct(r['Bias'], 2)} |")
    lines += [
        "",
        "Nhận xét:",
        "",
        "- Sai số từng ngày cao (trên 60%) là bản chất của dữ liệu đếm nhỏ: phần lớn sản phẩm chỉ bán vài đơn vị mỗi "
        "ngày nên dao động ngẫu nhiên lớn so với mức bán. Gộp theo tuần thì sai số giảm còn khoảng 40%.",
        "- \"Hôm qua (naive)\" kém nhất, nên việc làm mịn là cần thiết. Nhưng làm mịn nhiều (α nhỏ, hoặc trung bình cả "
        "30 ngày) lại tốt hơn làm mịn ít, vì nhu cầu hàng tạp hóa thiết yếu khá ổn định quanh một mức.",
        "- \"Cùng thứ, 4 tuần\" (dự báo riêng cho từng thứ trong tuần) không tốt hơn mức ngang ở cấp từng sản phẩm, "
        "vì 4 điểm dữ liệu cho mỗi thứ quá ít so với độ nhiễu.",
        "",
        "## 3. Theo tốc độ bán",
        "",
        "| Nhóm | Số sản phẩm | Ngày không bán | WAPE ngày (ES 0,35) | WAPE tổng 7 ngày (ES 0,35) | WAPE tổng 7 ngày (MA7) | α tốt nhất |",
        "|---|---|---|---|---|---|---|",
    ]
    for g, r in res["by_group"].iterrows():
        lines.append(f"| {g} | {int(r['Số sản phẩm'])} | {pct(r['Tỉ lệ ngày không bán'])} | {pct(r['WAPE ngày, ES α=0.35'])} | "
                     f"{pct(r['WAPE tổng 7 ngày, ES α=0.35'])} | {pct(r['WAPE tổng 7 ngày, MA7'])} | "
                     f"{num(res['group_alpha'][g])} |")
    lines += [
        "",
        "Hàng bán càng chậm thì càng khó dự báo từng ngày, đúng với cờ \"bán ngắt quãng\" của hệ thống.",
        "",
        "## 4. Mức phục vụ thực tế của điểm đặt hàng lại",
        "",
        f"Tại mỗi ngày gốc tính ROP = d̄ × L + Z × σ × √L (L = {cfg.lead_time_days} ngày) rồi kiểm tra nhu cầu thật trong "
        f"{cfg.lead_time_days} ngày kế tiếp có vượt ROP không. Tỉ lệ không vượt là mức phục vụ đạt được.",
        "",
        "| Z | Mức phục vụ theo lý thuyết | Đạt được trên dữ liệu thật |",
        "|---|---|---|",
    ]
    theory = {0.0: "50%", 1.28: "90%", cfg.z: "95%", 2.33: "99%"}
    for z, r in s.items():
        mark = " (đang dùng)" if z == cfg.z else ""
        lines.append(f"| {num(z)}{mark} | {theory[z]} | {pct(r['all'])} |")
    lines += [
        "",
        "Thực tế thấp hơn lý thuyết vì công thức giả định nhu cầu phân phối chuẩn và dự báo không sai, trong khi số bán "
        "theo ngày lệch phải (thỉnh thoảng có ngày bán vọt) và mức dự báo cũng có sai số. Kết quả gần như giống nhau ở "
        "cả ba nhóm tốc độ bán.",
        "",
        "## 5. Nhãn độ tin cậy",
        "",
        "Nhãn được tính như hệ thống (học 23 ngày, chấm 7 ngày cuối của cửa sổ lịch sử), rồi so với sai số thật của "
        "7 ngày sau đó.",
        "",
        "| Nhãn | Tỉ lệ số lần | WAPE ngày của 7 ngày sau đó |",
        "|---|---|---|",
    ]
    for name in ("GOOD", "FAIR", "LOW"):
        lines.append(f"| {name} | {pct(lab[name]['share'])} | {pct(lab[name]['forward_wape'])} |")
    lines += [
        "",
        "Nhãn có giá trị cảnh báo (GOOD thật sự sai ít hơn LOW), nhưng ngưỡng 30% / 60% tính trên sai số từng ngày "
        "là quá chặt với hàng bán vài đơn vị mỗi ngày.",
        "",
        "## 6. Ý nghĩa với hệ thống",
        "",
        f"1. **α = {num(cfg.alpha)} chạy được nhưng không phải tối ưu trên dữ liệu thật này**; α khoảng 0,1 tốt hơn. "
        "Vì α là tham số cấu hình (`ai.forecast.alpha`), có thể hạ xuống mà không sửa code.",
        "2. **Không cần dò α theo từng sản phẩm mỗi đêm**: một α chung chọn bằng backtest cho kết quả tương đương và "
        "ổn định hơn.",
        "3. **Nếu muốn mức phục vụ thật sự 95%**, nên tăng Z (`ai.forecast.service-level-z`) hoặc tính σ từ sai số dự "
        "báo thay cho độ lệch chuẩn số bán.",
        "4. **Nên nới ngưỡng nhãn độ tin cậy** hoặc tính trên tổng 7 ngày thay vì từng ngày.",
        "",
        "## 7. Giới hạn của phép kiểm chứng",
        "",
        "- Dữ liệu Mỹ năm 2017, không có Tết, không có thói quen mua sắm của Việt Nam.",
        "- Là mẫu 2.469 hộ gia đình gộp trên nhiều cửa hàng: số bán mỗi ngày nhỏ và nhiễu hơn doanh số đầy đủ của một "
        "cửa hàng, điều này có lợi cho α nhỏ. Với cửa hàng thật, α tốt nhất có thể lớn hơn và phải đo lại.",
        "- Chỉ có 1 năm nên không kiểm chứng được mùa vụ theo năm; không có tồn kho nên không biết ngày nào hết hàng.",
        "- Chưa dùng dữ liệu khuyến mãi của bộ dữ liệu.",
        "",
        "## 8. Chạy lại",
        "",
        "```bash",
        "cd AI_FORECAST/real_data_eval",
        "pip install -r requirements.txt",
        "python evaluate.py            # dùng data/sales_daily.csv.gz có sẵn",
        "python download.py && python prepare.py   # chỉ khi muốn dựng lại dữ liệu từ nguồn gốc",
        "```",
        "",
    ]
    path.write_text("\n".join(lines), encoding="utf-8")
