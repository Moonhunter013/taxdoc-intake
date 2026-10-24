import pytest

from app.security import MissingHmacKeyError, tin_hash, tin_kind, tin_last4


def test_hash_is_stable_and_keyed():
    a = tin_hash("000-14-2201", "key-1")
    assert a == tin_hash("000-14-2201", "key-1")
    assert a != tin_hash("000-14-2201", "key-2")  # useless without the key
    assert len(a) == 64


def test_ssn_and_ein_with_same_digits_hash_differently():
    assert tin_hash("123-45-6789", "k") != tin_hash("12-3456789", "k")


def test_missing_key_is_refused():
    with pytest.raises(MissingHmacKeyError):
        tin_hash("000-14-2201", "")


def test_kind_and_last4():
    assert tin_kind("000-14-2201") == "ssn"
    assert tin_kind("00-0412201") == "ein"
    assert tin_last4("00-0412201") == "2201"
    with pytest.raises(ValueError):
        tin_kind("12345")
