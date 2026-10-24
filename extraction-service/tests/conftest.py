"""Shared pytest fixtures and hooks."""

from collections import defaultdict

import pytest

ACCURACY_KEY = pytest.StashKey[dict]()


@pytest.fixture
def accuracy(request) -> dict:
    """Per-field [correct, total] counts, shared across the run via pytest's config stash."""
    stash = request.config.stash
    if ACCURACY_KEY not in stash:
        stash[ACCURACY_KEY] = defaultdict(lambda: [0, 0])
    return stash[ACCURACY_KEY]


def pytest_terminal_summary(terminalreporter, config):
    """Print per-field extraction accuracy if the accuracy suite ran."""
    results = config.stash.get(ACCURACY_KEY, None)
    if not results:
        return
    terminalreporter.section("extraction accuracy by field")
    width = max(len(field) for field in results)
    total_ok = total = 0
    for field in sorted(results):
        ok, n = results[field]
        total_ok, total = total_ok + ok, total + n
        flag = "" if ok == n else "   <-- check"
        terminalreporter.write_line(f"{field:<{width}}  {ok:>2}/{n:<2}{flag}")
    terminalreporter.write_line(f"{'ALL FIELDS':<{width}}  {total_ok}/{total}")
