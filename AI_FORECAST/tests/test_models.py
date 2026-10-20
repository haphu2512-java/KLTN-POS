import math

import pytest

from pos_forecast.models import exponential_smoothing, moving_average, std_dev, zero_ratio


def test_moving_average_uses_last_window_days():
    assert moving_average([100, 1, 2, 3, 4, 5, 6, 7], 7) == 4


def test_moving_average_shorter_than_window():
    assert moving_average([2, 4], 7) == 3


def test_empty_series_forecasts_zero():
    assert moving_average([], 7) == 0
    assert exponential_smoothing([], 0.35) == 0


def test_exponential_smoothing_matches_formula():
    sales = [10, 20, 30]
    expected = sum(sales) / 3  # khởi tạo bằng trung bình các ngày đầu
    for y in sales:
        expected = 0.35 * y + 0.65 * expected
    assert exponential_smoothing(sales, 0.35, init_window=7) == pytest.approx(expected)


def test_exponential_smoothing_constant_series():
    assert exponential_smoothing([8] * 30, 0.35) == pytest.approx(8)


def test_exponential_smoothing_weights_recent_days_more():
    rising = [5] * 20 + [15] * 5
    assert exponential_smoothing(rising, 0.35) > moving_average(rising, len(rising))


def test_exponential_smoothing_rejects_bad_alpha():
    with pytest.raises(ValueError):
        exponential_smoothing([1, 2], 0)


def test_std_dev_counts_zero_days():
    assert std_dev([0, 0, 6, 0]) == pytest.approx(3.0)
    assert std_dev([5]) == 0
    assert std_dev([2, 4, 4, 4, 5, 5, 7, 9]) == pytest.approx(math.sqrt(32 / 7))


def test_zero_ratio():
    assert zero_ratio([0, 1, 0, 2]) == 0.5
    assert zero_ratio([]) == 0
