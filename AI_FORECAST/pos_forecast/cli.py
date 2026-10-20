"""Chạy dự báo từ dòng lệnh, dùng cho job ban đêm (cron) hoặc demo.

Ví dụ:
    python -m pos_forecast.cli --branch 1 --days 7 --out forecast.json
    python -m pos_forecast.cli --all-branches --only-restock
"""

import argparse
import json
import logging
import sys
from datetime import date

from .config import ForecastConfig
from .data import CsvDataSource, data_source_from_env
from .service import ForecastService


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Dự báo nhu cầu và đề xuất nhập hàng")
    parser.add_argument("--branch", type=int, default=1, help="ID chi nhánh (mặc định 1)")
    parser.add_argument("--all-branches", action="store_true", help="Chạy cho mọi chi nhánh")
    parser.add_argument("--days", type=int, default=None, help="Số ngày dự báo (mặc định 7)")
    parser.add_argument("--algorithm", choices=["EXP_SMOOTH", "MOVING_AVG"], default=None)
    parser.add_argument("--as-of", type=date.fromisoformat, default=None, help="Ngày cuối lịch sử, YYYY-MM-DD")
    parser.add_argument("--data-dir", default=None, help="Thư mục CSV (bỏ qua FORECAST_DATA_SOURCE)")
    parser.add_argument("--only-restock", action="store_true", help="Chỉ in sản phẩm cần nhập")
    parser.add_argument("--out", default=None, help="Ghi JSON ra file thay vì in ra màn hình")
    args = parser.parse_args(argv)

    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    source = CsvDataSource(args.data_dir) if args.data_dir else data_source_from_env()
    service = ForecastService(source, ForecastConfig.from_env())

    branches = source.branch_ids() if args.all_branches else [args.branch]
    output = {
        str(branch): service.recommendations(branch, args.days, args.algorithm, args.as_of, args.only_restock)
        for branch in branches
    }
    for branch, results in output.items():
        restock = sum(1 for r in results if r["suggestedRestockQty"] > 0)
        logging.info("Chi nhánh %s: %d sản phẩm, %d cần nhập hàng", branch, len(results), restock)

    text = json.dumps(output, ensure_ascii=False, indent=2)
    if args.out:
        with open(args.out, "w", encoding="utf-8") as f:
            f.write(text)
    else:
        sys.stdout.write(text + "\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
