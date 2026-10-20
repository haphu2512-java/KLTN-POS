import math

import pytest

from pos_forecast.metrics import backtest, mae, rmse


def test_mae_and_rmse():
    actual, predicted = [10, 12, 8], [11, 10, 8]
    assert mae(actual, predicted) == pytest.approx(1.0)
    assert rmse(actual, predicted) == pytest.approx(math.sqrt(5 / 3))


def test_metrics_reject_mismatched_lengths():
    with pytest.raises(ValueError):
        mae([1, 2], [1])


def test_backtest_does_not_see_validation_data():
    seen = []

    def predict(train):
        seen.append(list(train))
        return 10.0

    sales = [10] * 7 + [12] * 7
    result = backtest(sales, predict, 7)
    assert seen == [[10] * 7]
    assert result["mae"] == 2.0
    assert result["rmse"] == 2.0
    assert result["validationMean"] == 12.0


def test_backtest_needs_enough_history():
    assert backtest([1] * 10, lambda s: 1.0, 7) is None
