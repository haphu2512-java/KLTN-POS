import json

from pos_forecast.parity import FIXTURE, build_cases


def test_parity_fixture_is_up_to_date():
    """File đối chiếu phải khớp với code Python hiện tại; nếu đổi công thức hãy chạy python -m pos_forecast.parity."""
    assert json.loads(FIXTURE.read_text(encoding="utf-8")) == build_cases()


def test_parity_fixture_covers_edge_cases():
    cases = build_cases()
    assert any(not c["expected"]["hasHistory"] for c in cases)
    assert any(c["expected"]["intermittent"] for c in cases)
    assert {c["expected"]["urgency"] for c in cases} == {"HIGH", "MEDIUM", "LOW"}
