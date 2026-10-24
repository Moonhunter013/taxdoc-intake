import pytest

from app.extraction import (
    TextractDocument,
    UnsupportedFormError,
    detect_form_type,
    extract_document,
)
from app.models import W2, Form1099NEC, FormType
from tests.fixtures import load_fixture


@pytest.mark.parametrize(
    ("name", "form_type", "model"),
    [("client-01_w2", FormType.W2, W2), ("client-09_1099nec", FormType.NEC_1099, Form1099NEC)],
)
def test_detects_form_and_routes_to_mapper(name, form_type, model):
    response = load_fixture(name)
    assert detect_form_type(TextractDocument(response)) is form_type
    assert isinstance(extract_document(response).document, model)


def test_unknown_document_is_rejected():
    with pytest.raises(UnsupportedFormError):
        extract_document({"Blocks": []})
