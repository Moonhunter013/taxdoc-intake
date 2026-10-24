"""Generate synthetic, filled IRS W-2 and 1099-NEC PDFs for the fictional clients in clients.json.

Usage (from the samples/ folder):
    uv run --with pypdf --with reportlab python generate_forms.py
    uv run --with pypdf --with reportlab python generate_forms.py --list-fields  # inspect PDFs

Inputs:
    clients.json                 fictional clients (base inputs only: gross pay, deferrals, rates)
    blank-forms/fw2.pdf          official IRS Form W-2 (2026)
    blank-forms/f1099nec.pdf     official IRS Form 1099-NEC (Rev. December 2026)

Outputs:
    forms/<client>_w2[_n].pdf, forms/<client>_1099nec[_n].pdf
        single page, flattened, watermarked "SYNTHETIC TEST DATA"
    ground-truth/<client>.json
        the exact values written on each form; Week 1 extraction tests compare against these

All names, SSNs (area 000) and EINs (prefix 00) are fictional and can never be issued.
"""

from __future__ import annotations

import argparse
import io
import json
from dataclasses import dataclass
from pathlib import Path

from pypdf import PdfReader, PdfWriter
from pypdf.generic import RectangleObject
from reportlab.lib.colors import Color
from reportlab.pdfgen import canvas

HERE = Path(__file__).parent
BLANK = HERE / "blank-forms"
OUT_FORMS = HERE / "forms"
OUT_TRUTH = HERE / "ground-truth"

# Tax year 2026 payroll constants (SSA: 2026 wage base is $184,500).
# The planning service will load the same values from configuration, not hard-code them.
SS_WAGE_BASE = 184_500
SS_RATE = 0.062
MEDICARE_RATE = 0.0145
ADDL_MEDICARE_RATE = 0.009
ADDL_MEDICARE_THRESHOLD = 200_000  # employer withholding threshold, regardless of filing status


def money(x: float) -> float:
    return round(x + 1e-9, 2)


def fmt(x: float) -> str:
    return f"{x:,.2f}"


# --------------------------------------------------------------------------------------
# Box calculations (pure functions, easy to unit test)
# --------------------------------------------------------------------------------------


def w2_boxes(w: dict) -> dict:
    """Derive W-2 box values from gross pay and pre-tax deductions.

    Simplifications (documented on purpose):
      * 401(k) deferrals reduce box 1 but not boxes 3/5.
      * HSA contributions via cafeteria plan reduce boxes 1, 3 and 5 (reported as code W).
      * Social Security wages are capped per employer, which is why client-10 ends up with
        excess SS withholding across two employers.
    """
    gross, k401, hsa = w["gross"], w["deferral_401k"], w["hsa_pretax"]
    box1 = gross - k401 - hsa
    box5 = gross - hsa
    box3 = min(box5, SS_WAGE_BASE)
    box12 = []
    if k401:
        box12.append({"code": "D", "amount": money(k401)})
    if hsa:
        box12.append({"code": "W", "amount": money(hsa)})
    state_wages = box1 if w["state"] else 0
    return {
        "employer_name": w["employer"],
        "employer_ein": w["ein"],
        "employer_address": w["employer_address"],
        "box1_wages": money(box1),
        "box2_federal_withholding": money(box1 * w["fed_withholding_rate"]),
        "box3_ss_wages": money(box3),
        "box4_ss_tax": money(box3 * SS_RATE),
        "box5_medicare_wages": money(box5),
        "box6_medicare_tax": money(
            box5 * MEDICARE_RATE + max(0, box5 - ADDL_MEDICARE_THRESHOLD) * ADDL_MEDICARE_RATE
        ),
        "box12": box12,
        "box13_retirement_plan": k401 > 0,
        "box15_state": w["state"],
        "box15_state_id": w["state_id"],
        "box16_state_wages": money(state_wages),
        "box17_state_tax": money(state_wages * w["state_withholding_rate"]),
    }


def nec_boxes(n: dict) -> dict:
    return {
        "payer_name": n["payer"],
        "payer_tin": n["ein"],
        "payer_address": n["payer_address"],
        "box1a_nonemployee_comp": money(n["amount"]),
        "box4_federal_withholding": 0.0,
        "box6_state": n["state"],
        "box6_state_id": n["state_id"],
        "box7_state_income": money(n["amount"]) if n["state"] else 0.0,
    }


def split_address(addr: str) -> tuple[str, str, str, str]:
    """'418 Juniper Hollow Rd, Boulder, CO 80302' -> (street, city, state, zip)."""
    street, city, state_zip = (part.strip() for part in addr.split(","))
    state, zip_code = state_zip.split()
    return street, city, state, zip_code


# --------------------------------------------------------------------------------------
# PDF field maps (from --list-fields against the 2026 IRS PDFs)
# --------------------------------------------------------------------------------------


@dataclass(frozen=True)
class FormSpec:
    blank: str
    page_index: int  # which page of the IRS PDF to use
    crop_bottom: float  # W-2 pages hold two copies; crop to the top one


# W-2: page index 3 = "Copy B - To Be Filed With Employee's FEDERAL Tax Return", top copy.
W2_SPEC = FormSpec("fw2.pdf", page_index=3, crop_bottom=385)
W2 = {
    "ssn": "f2_01[0]",
    "ein": "f2_02[0]",
    "employer": "f2_03[0]",
    "first_name": "f2_05[0]",
    "last_name": "f2_06[0]",
    "employee_address": "f2_08[0]",
    "box1": "f2_09[0]",
    "box2": "f2_10[0]",
    "box3": "f2_11[0]",
    "box4": "f2_12[0]",
    "box5": "f2_13[0]",
    "box6": "f2_14[0]",
    "box12a_code": "f2_20[0]",
    "box12a_amount": "f2_21[0]",
    "box12b_code": "f2_22[0]",
    "box12b_amount": "f2_23[0]",
    "box13_retirement": "c2_3[0]",
    "box15_state": "f2_31[0]",
    "box15_state_id": "f2_32[0]",
    "box16": "f2_35[0]",
    "box17": "f2_37[0]",
}

# 1099-NEC (Rev. December 2026): page index 3 = "Copy B For Recipient".
NEC_SPEC = FormSpec("f1099nec.pdf", page_index=3, crop_bottom=395)
NEC = {
    "calendar_year": "f2_1[0]",
    "payer_name": "f2_2[0]",
    "payer_street": "f2_3[0]",
    "payer_city": "f2_5[0]",
    "payer_state": "f2_7[0]",
    "payer_country": "f2_8[0]",
    "payer_zip": "f2_9[0]",
    "payer_tin": "f2_10[0]",
    "recipient_tin": "f2_11[0]",
    "recipient_name": "f2_12[0]",
    "recipient_street": "f2_13[0]",
    "recipient_city": "f2_15[0]",
    "recipient_state": "f2_16[0]",
    "recipient_country": "f2_17[0]",
    "recipient_zip": "f2_18[0]",
    "box1a": "f2_20[0]",
    "box4": "f2_26[0]",
    "box5": "f2_27[0]",
    "box6": "f2_29[0]",
    "box7": "f2_31[0]",
}


# --------------------------------------------------------------------------------------
# PDF writing
# --------------------------------------------------------------------------------------


def overlay(box: RectangleObject, checkmarks: list[list[float]]):
    """Margin labels plus an X in each checked box (pypdf does not flatten checkbox appearances)."""
    left, bottom = float(box.left), float(box.bottom)
    width, height = float(box.width), float(box.height)
    buf = io.BytesIO()
    c = canvas.Canvas(buf, pagesize=(left + width, bottom + height))
    c.setStrokeColor(Color(0, 0, 0.55))
    c.setLineWidth(1.2)
    for x1, y1, x2, y2 in checkmarks:
        c.line(x1 + 2, y1 + 2, x2 - 2, y2 - 2)
        c.line(x1 + 2, y2 - 2, x2 - 2, y1 + 2)
    # Watermark text goes only in the page margins. An earlier version drew a large diagonal
    # watermark across the form, and Textract read it as text ("SYNTHE", "TIC"), losing
    # names and addresses underneath. Test data must not be degraded by its own labelling.
    label = "SYNTHETIC TEST DATA - fictional person and business - NOT A REAL TAX DOCUMENT"
    c.setFillColor(Color(0.85, 0.1, 0.1, alpha=0.9))
    c.setFont("Helvetica-Bold", 8)
    c.drawString(left + 36, bottom + height - 12, label)
    c.drawString(left + 36, bottom + 6, label)
    c.save()
    buf.seek(0)
    return PdfReader(buf).pages[0]


def fill(spec: FormSpec, values: dict[str, str], out: Path) -> None:
    writer = PdfWriter(clone_from=BLANK / spec.blank)
    # Keep only the page we want.
    for i in reversed(range(len(writer.pages))):
        if i != spec.page_index:
            writer.remove_page(i)
    page = writer.pages[0]

    text_values = {k: v for k, v in values.items() if isinstance(v, str)}
    checked = {k for k, v in values.items() if v is True}
    checkmarks = [
        [float(x) for x in a.get_object()["/Rect"]]
        for a in page.get("/Annots", [])
        if a.get_object().get("/T") in checked
    ]
    # flatten=True draws every field's appearance into the page content so the result
    # looks like a printed form (no editable fields), which is what Textract will see.
    writer.update_page_form_field_values(page, text_values, auto_regenerate=False, flatten=True)
    writer.remove_annotations(subtypes="/Widget")
    if "/AcroForm" in writer.root_object:
        del writer.root_object["/AcroForm"]

    if spec.crop_bottom:
        box = RectangleObject(
            [0, spec.crop_bottom, float(page.mediabox.right), float(page.mediabox.top)]
        )
        page.mediabox = box
        page.cropbox = box
    page.merge_page(overlay(page.mediabox, checkmarks))

    out.parent.mkdir(parents=True, exist_ok=True)
    with out.open("wb") as fh:
        writer.write(fh)


def w2_values(client: dict, b: dict, year: int) -> dict[str, str]:
    first, last = client["name"].rsplit(" ", 1)
    street, city, state, zip_code = split_address(client["address"])
    e_street, e_city, e_state, e_zip = split_address(b["employer_address"])
    v = {
        W2["ssn"]: client["ssn"],
        W2["ein"]: b["employer_ein"],
        W2["employer"]: f"{b['employer_name']}\n{e_street}\n{e_city}, {e_state} {e_zip}",
        W2["first_name"]: first,
        W2["last_name"]: last,
        W2["employee_address"]: f"{street}\n{city}, {state} {zip_code}",
        W2["box1"]: fmt(b["box1_wages"]),
        W2["box2"]: fmt(b["box2_federal_withholding"]),
        W2["box3"]: fmt(b["box3_ss_wages"]),
        W2["box4"]: fmt(b["box4_ss_tax"]),
        W2["box5"]: fmt(b["box5_medicare_wages"]),
        W2["box6"]: fmt(b["box6_medicare_tax"]),
        W2["box13_retirement"]: b["box13_retirement_plan"],
    }
    for slot, item in zip(("a", "b"), b["box12"], strict=False):
        v[W2[f"box12{slot}_code"]] = item["code"]
        v[W2[f"box12{slot}_amount"]] = fmt(item["amount"])
    if b["box15_state"]:
        v[W2["box15_state"]] = b["box15_state"]
        v[W2["box15_state_id"]] = b["box15_state_id"]
        v[W2["box16"]] = fmt(b["box16_state_wages"])
        v[W2["box17"]] = fmt(b["box17_state_tax"])
    return v


def nec_values(client: dict, b: dict, year: int) -> dict[str, str]:
    p_street, p_city, p_state, p_zip = split_address(b["payer_address"])
    r_street, r_city, r_state, r_zip = split_address(client["address"])
    v = {
        NEC["calendar_year"]: str(year),
        NEC["payer_name"]: b["payer_name"],
        NEC["payer_street"]: p_street,
        NEC["payer_city"]: p_city,
        NEC["payer_state"]: p_state,
        NEC["payer_country"]: "US",
        NEC["payer_zip"]: p_zip,
        NEC["payer_tin"]: b["payer_tin"],
        NEC["recipient_tin"]: client["ssn"],
        NEC["recipient_name"]: client["name"],
        NEC["recipient_street"]: r_street,
        NEC["recipient_city"]: r_city,
        NEC["recipient_state"]: r_state,
        NEC["recipient_country"]: "US",
        NEC["recipient_zip"]: r_zip,
        NEC["box1a"]: fmt(b["box1a_nonemployee_comp"]),
        NEC["box4"]: fmt(b["box4_federal_withholding"]),
    }
    if b["box6_state"]:
        v[NEC["box5"]] = fmt(0)
        v[NEC["box6"]] = f"{b['box6_state']} {b['box6_state_id']}"
        v[NEC["box7"]] = fmt(b["box7_state_income"])
    return v


def suffix(i: int, total: int) -> str:
    return f"_{i + 1}" if total > 1 else ""


def generate() -> None:
    data = json.loads((HERE / "clients.json").read_text())
    year = data["tax_year"]
    OUT_TRUTH.mkdir(exist_ok=True)
    for client in data["clients"]:
        truth = {
            "client_id": client["id"],
            "tax_year": year,
            "taxpayer_name": client["name"],
            "taxpayer_ssn": client["ssn"],
            "documents": [],
        }
        for i, w in enumerate(client["w2"]):
            boxes = w2_boxes(w)
            name = f"{client['id']}_w2{suffix(i, len(client['w2']))}.pdf"
            fill(W2_SPEC, w2_values(client, boxes, year), OUT_FORMS / name)
            truth["documents"].append({"file": name, "form": "W-2", **boxes})
        for i, n in enumerate(client["nec"]):
            boxes = nec_boxes(n)
            name = f"{client['id']}_1099nec{suffix(i, len(client['nec']))}.pdf"
            fill(NEC_SPEC, nec_values(client, boxes, year), OUT_FORMS / name)
            truth["documents"].append({"file": name, "form": "1099-NEC", **boxes})
        (OUT_TRUTH / f"{client['id']}.json").write_text(json.dumps(truth, indent=2) + "\n")
        print(f"{client['id']}: {len(truth['documents'])} document(s)")


def list_fields() -> None:
    for name in ("fw2.pdf", "f1099nec.pdf"):
        path = BLANK / name
        if not path.exists():
            print(f"missing {path}")
            continue
        reader = PdfReader(path)
        print(f"== {name}: {len(reader.pages)} pages")
        for i, page in enumerate(reader.pages):
            text = (page.extract_text() or "")[:80].replace("\n", " ")
            annots = [a.get_object() for a in page.get("/Annots", []) or []]
            fields = [(a.get("/T"), [round(float(x)) for x in a.get("/Rect", [])]) for a in annots]
            print(f"  page {i}: {len(fields)} fields | {text}")
            for t, rect in fields:
                if t:
                    print(f"      {t} {rect}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--list-fields", action="store_true")
    args = parser.parse_args()
    if args.list_fields:
        list_fields()
    else:
        generate()


if __name__ == "__main__":
    main()
