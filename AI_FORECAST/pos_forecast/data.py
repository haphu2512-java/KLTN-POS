"""Nguồn dữ liệu và chuẩn hóa chuỗi bán hàng theo ngày.

Hai nguồn được hỗ trợ:
- CsvDataSource: đọc file CSV (dùng cho demo, test, hoặc dữ liệu xuất từ hệ thống).
- MySqlDataSource: đọc trực tiếp CSDL của backend (bảng invoice, invoice_detail, inventory, product).
"""

import csv
import logging
import os
from collections import defaultdict
from dataclasses import dataclass
from datetime import date, datetime, timedelta
from pathlib import Path
from typing import Protocol

log = logging.getLogger(__name__)

REFUNDED = "REFUNDED"


@dataclass(frozen=True)
class Product:
    id: int
    barcode: str | None = None
    name: str | None = None
    category: str | None = None
    unit: str | None = None
    active: bool = True


class DataSource(Protocol):
    def products(self) -> list[Product]: ...

    def branch_ids(self) -> list[int]: ...

    def daily_sales(self, branch_id: int, start: date, end: date) -> dict[int, dict[date, float]]:
        """{product_id: {ngày: tổng số lượng bán}} trong khoảng [start, end]."""
        ...

    def stock(self, branch_id: int) -> dict[int, int]:
        """{product_id: tồn kho hiện tại} của chi nhánh."""
        ...

    def default_as_of(self) -> date:
        """Ngày cuối cùng của cửa sổ lịch sử khi người gọi không chỉ định."""
        ...


def fill_daily_series(sales_by_day: dict[date, float], start: date, end: date) -> list[float]:
    """Chuỗi số lượng bán liên tục từ start đến end, điền 0 cho ngày không có giao dịch."""
    days = (end - start).days + 1
    return [float(sales_by_day.get(start + timedelta(days=i), 0.0)) for i in range(days)]


def _parse_date(value: str) -> date:
    """Nhận "2026-10-20" hoặc "2026-10-20 14:30:00" (chỉ lấy phần ngày)."""
    return date.fromisoformat(value.strip()[:10])


def _parse_bool(value: str | None) -> bool:
    return (value or "true").strip().lower() not in ("0", "false", "no", "")


class CsvDataSource:
    """Đọc thư mục chứa sales.csv, products.csv, stock.csv (định dạng xem README)."""

    def __init__(self, directory: str | Path):
        directory = Path(directory)
        self._products = self._load_products(directory / "products.csv")
        self._sales = self._load_sales(directory / "sales.csv")
        self._stock = self._load_stock(directory / "stock.csv")

    @staticmethod
    def _load_products(path: Path) -> list[Product]:
        with path.open(encoding="utf-8-sig", newline="") as f:
            return [
                Product(
                    id=int(row["product_id"]),
                    barcode=row.get("barcode") or None,
                    name=row.get("name") or None,
                    category=row.get("category") or None,
                    unit=row.get("unit") or None,
                    active=_parse_bool(row.get("active")),
                )
                for row in csv.DictReader(f)
            ]

    @staticmethod
    def _load_sales(path: Path) -> dict[int, dict[int, dict[date, float]]]:
        """{branch_id: {product_id: {ngày: số lượng}}}. Dòng hoàn trả bị bỏ, dòng lỗi được ghi log."""
        sales: dict[int, dict[int, dict[date, float]]] = defaultdict(lambda: defaultdict(lambda: defaultdict(float)))
        skipped = 0
        with path.open(encoding="utf-8-sig", newline="") as f:
            for line_no, row in enumerate(csv.DictReader(f), start=2):
                if (row.get("status") or "").strip().upper() == REFUNDED:
                    continue
                try:
                    qty = float(row["quantity_sold"])
                    day = _parse_date(row["date"])
                    branch_id, product_id = int(row["branch_id"]), int(row["product_id"])
                except (KeyError, TypeError, ValueError):
                    skipped += 1
                    log.warning("sales.csv dòng %d không hợp lệ, bỏ qua: %s", line_no, row)
                    continue
                if qty < 0:
                    skipped += 1
                    log.warning("sales.csv dòng %d có số lượng âm (%s), bỏ qua", line_no, qty)
                    continue
                sales[branch_id][product_id][day] += qty
        if skipped:
            log.warning("Đã bỏ qua %d dòng bán hàng không hợp lệ trong %s", skipped, path)
        return sales

    @staticmethod
    def _load_stock(path: Path) -> dict[int, dict[int, int]]:
        stock: dict[int, dict[int, int]] = defaultdict(dict)
        with path.open(encoding="utf-8-sig", newline="") as f:
            for row in csv.DictReader(f):
                stock[int(row["branch_id"])][int(row["product_id"])] = int(row["quantity"])
        return stock

    def products(self) -> list[Product]:
        return list(self._products)

    def branch_ids(self) -> list[int]:
        return sorted(set(self._sales) | set(self._stock))

    def daily_sales(self, branch_id: int, start: date, end: date) -> dict[int, dict[date, float]]:
        return {
            product_id: {d: q for d, q in by_day.items() if start <= d <= end}
            for product_id, by_day in self._sales.get(branch_id, {}).items()
            if any(start <= d <= end for d in by_day)
        }

    def stock(self, branch_id: int) -> dict[int, int]:
        return dict(self._stock.get(branch_id, {}))

    def default_as_of(self) -> date:
        """Ngày bán gần nhất trong file, để dữ liệu mẫu cũ vẫn dự báo được."""
        days = [d for by_product in self._sales.values() for by_day in by_product.values() for d in by_day]
        return max(days) if days else date.today()


class MySqlDataSource:
    """Đọc trực tiếp CSDL MySQL của backend Spring Boot (cùng biến môi trường DB_* với docker-compose)."""

    SALES_SQL = (
        "SELECT d.product_id, DATE(i.created_at) AS sale_date, SUM(d.quantity) AS qty "
        "FROM invoice_detail d JOIN invoice i ON i.id = d.invoice_id "
        "WHERE i.branch_id = %s AND i.created_at >= %s AND i.created_at < %s "
        "AND (i.status IS NULL OR i.status <> 'REFUNDED') "
        "GROUP BY d.product_id, DATE(i.created_at)"
    )

    def __init__(self, host: str, port: int, user: str, password: str, database: str):
        self._params = dict(host=host, port=port, user=user, password=password, database=database,
                            charset="utf8mb4")

    @classmethod
    def from_env(cls) -> "MySqlDataSource":
        return cls(
            host=os.getenv("DB_HOST", "localhost"),
            port=int(os.getenv("DB_PORT", "3306")),
            user=os.getenv("DB_USERNAME") or os.getenv("DB_USER", "root"),
            password=os.getenv("DB_PASSWORD", ""),
            database=os.getenv("DB_NAME", "pos_inventory"),
        )

    def _query(self, sql: str, args: tuple = ()) -> list[tuple]:
        import pymysql  # chỉ cần khi dùng nguồn MySQL

        with pymysql.connect(**self._params) as conn, conn.cursor() as cur:
            cur.execute(sql, args)
            return list(cur.fetchall())

    def products(self) -> list[Product]:
        rows = self._query("SELECT id, barcode, name, category, unit, active FROM product")
        return [Product(id=r[0], barcode=r[1], name=r[2], category=r[3], unit=r[4], active=bool(r[5]))
                for r in rows]

    def branch_ids(self) -> list[int]:
        return [r[0] for r in self._query("SELECT id FROM branch ORDER BY id")]

    def daily_sales(self, branch_id: int, start: date, end: date) -> dict[int, dict[date, float]]:
        result: dict[int, dict[date, float]] = defaultdict(dict)
        for product_id, sale_date, qty in self._query(
                self.SALES_SQL, (branch_id, start, end + timedelta(days=1))):
            if isinstance(sale_date, datetime):
                sale_date = sale_date.date()
            result[int(product_id)][sale_date] = float(qty)
        return dict(result)

    def stock(self, branch_id: int) -> dict[int, int]:
        rows = self._query("SELECT product_id, quantity FROM inventory WHERE branch_id = %s", (branch_id,))
        return {int(r[0]): int(r[1]) for r in rows}

    def default_as_of(self) -> date:
        """Hôm qua: job chạy ban đêm chỉ dùng những ngày đã bán trọn vẹn."""
        return date.today() - timedelta(days=1)


def data_source_from_env() -> DataSource:
    """FORECAST_DATA_SOURCE=mysql để đọc CSDL, mặc định đọc CSV trong FORECAST_DATA_DIR."""
    if os.getenv("FORECAST_DATA_SOURCE", "csv").strip().lower() == "mysql":
        return MySqlDataSource.from_env()
    default_dir = Path(__file__).resolve().parent.parent / "sample_data"
    return CsvDataSource(os.getenv("FORECAST_DATA_DIR", str(default_dir)))
