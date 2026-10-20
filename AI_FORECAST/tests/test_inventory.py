import math

import pytest

from pos_forecast.inventory import (URGENCY_HIGH, URGENCY_LOW, URGENCY_MEDIUM, classify_urgency,
                                    order_quantity, reorder_point, safety_stock)


def test_safety_stock_formula():
    # SS = Z × σ × √L = 1.65 × 4 × √2
    assert safety_stock(4, 2, 1.65) == pytest.approx(1.65 * 4 * math.sqrt(2))


def test_reorder_point_formula():
    assert reorder_point(10, 2, 9.33) == pytest.approx(29.33)


def test_order_quantity_rounds_up():
    # ROP 29.33 + 10 × 7 − 50 = 49.33 → 50
    assert order_quantity(29.33, 10, 7, 50) == 50


def test_order_quantity_never_negative():
    assert order_quantity(20, 5, 7, 500) == 0


def test_order_quantity_ignores_float_noise():
    # 0.1 + 0.2 − 0.3 = 5.5e-17 trong dấu phẩy động, không được làm tròn lên thành 1
    assert order_quantity(0.1 + 0.2, 0, 7, 0.3) == 0


@pytest.mark.parametrize("stock, expected", [
    (0, URGENCY_HIGH),      # hết hàng
    (15, URGENCY_HIGH),     # ≤ d̄ × L = 20: hết trước khi hàng về
    (25, URGENCY_MEDIUM),   # ≤ ROP = 30
    (31, URGENCY_LOW),
])
def test_classify_urgency(stock, expected):
    assert classify_urgency(True, stock, 10, 2, 30) == expected


def test_classify_urgency_without_history():
    assert classify_urgency(False, 0, 0, 2, 0) == URGENCY_HIGH
    assert classify_urgency(False, 10, 0, 2, 0) == URGENCY_LOW
