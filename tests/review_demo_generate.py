#!/usr/bin/env python3
"""Rebuild the public, deterministic SurvEye review illustration (stdlib only).

Every row, response, weight, and point is invented. This is a software review
fixture, not an Enterprise Survey dataset or evidence about Sri Lanka.
Run from any directory; all outputs are placed beside this script.
"""

from __future__ import annotations

import csv
import html
import json
import random
from pathlib import Path


HERE = Path(__file__).resolve().parent
SEED = 23010904
N = 240
YESNO = [("1", "Yes"), ("0", "No")]
OBSTACLE = [("0", "No obstacle"), ("1", "Minor"), ("2", "Moderate"), ("3", "Major"), ("4", "Very severe")]
REGIONS = [("01", "Western"), ("1", "Central"), ("03", "Northern"), ("04", "Southern")]
SECTORS = [("1", "Manufacturing"), ("2", "Retail"), ("3", "Other services")]
SIZES = [("1", "Micro · 1–4 workers"), ("2", "Small · 5–19 workers"), ("3", "Medium · 20–99 workers")]


def question(name, label, kind="binary", choices=None):
    if choices is None:
        choices = YESNO if kind == "binary" else OBSTACLE if kind == "ordinal" else []
    return {"name": name, "label": label, "kind": kind, "choices": choices}


Q = question
SECTIONS = [
    ("Business profile", [
        Q("region", "Region", "category", REGIONS),
        Q("sector", "Main business sector", "category", SECTORS),
        Q("size", "Employment size group", "category", SIZES),
        Q("female_owner", "At least one woman among the owners"),
        Q("female_manager", "A woman is the top manager"),
        Q("registered", "Business is formally registered"),
        Q("business_age", "Years since this business began operating", "count"),
        Q("owner_experience", "Owner's years of experience in this sector", "count"),
        Q("sole_proprietor", "Business is a sole proprietorship"),
        Q("family_owned", "Business is majority family-owned"),
        Q("home_based", "Business operates from the owner's home"),
        Q("rented_premises", "Business rents its main premises"),
        Q("owned_premises", "Business owns its main premises"),
        Q("has_signage", "Business has a visible sign at its premises"),
        Q("keeps_accounts", "Business keeps written financial records"),
        Q("tax_registered", "Business is registered for income tax"),
        Q("member_association", "Business belongs to a business association"),
        Q("multiple_locations", "Business operates from more than one location"),
        Q("business_locations", "Number of business locations", "count"),
        Q("profile_note", "Business-profile note provided", "text"),
    ]),
    ("Sales and markets", [
        Q("sales", "Annual sales in the last completed fiscal year", "money"),
        Q("monthly_sales", "Sales in the last completed month", "money"),
        Q("sales_growth", "Change in annual sales compared with the prior year (%)", "signed"),
        Q("domestic_share", "Share of annual sales to domestic customers (%)", "percent"),
        Q("exporter", "Business exports directly"),
        Q("online_sales", "Business received an online order in the last 30 days"),
        Q("sells_government", "Business sold to a government client in the last year"),
        Q("sells_large_firms", "Business sells to large companies"),
        Q("customer_count", "Number of distinct customers in the last month", "count"),
        Q("largest_customer_share", "Share of sales to the largest customer (%)", "percent"),
        Q("has_website", "Business has its own website"),
        Q("uses_social_media", "Business uses social media to reach customers"),
        Q("accepts_digital_payment", "Business accepts digital payments"),
        Q("advertises", "Business paid for advertising in the last 30 days"),
        Q("new_product", "Business introduced a new product in the last year"),
        Q("new_market", "Business entered a new market in the last year"),
        Q("sales_channels", "Channels used to sell products or services", "multi", [("1", "Premises"), ("2", "Telephone"), ("3", "Social media"), ("4", "Website or marketplace")]),
        Q("competition_obstacle", "Competition as an obstacle to business", "ordinal"),
        Q("demand_obstacle", "Limited demand as an obstacle to business", "ordinal"),
        Q("sales_note", "Sales explanation provided", "text"),
    ]),
    ("Employment", [
        Q("employees", "Number of full-time permanent employees", "count"),
        Q("employment_change", "Change in number of employees over the last year", "signed"),
        Q("female_employees", "Number of women among permanent employees", "count"),
        Q("temporary_employees", "Number of temporary employees", "count"),
        Q("unpaid_family_workers", "Number of unpaid family workers", "count"),
        Q("vacancies", "Number of unfilled job vacancies", "count"),
        Q("hours_per_week", "Typical hours worked per employee per week", "count"),
        Q("monthly_wage", "Typical monthly wage for a permanent employee", "money"),
        Q("provides_training", "Business provided worker training in the last year"),
        Q("hired_last_year", "Business hired an employee in the last year"),
        Q("written_contracts", "Permanent employees have written contracts"),
        Q("social_protection", "Business contributes to employee social protection"),
        Q("paid_leave", "Employees are entitled to paid annual leave"),
        Q("safety_training", "Business provides workplace safety training"),
        Q("remote_work", "Some employees can work remotely"),
        Q("recruitment_days", "Days required to fill the most recent vacancy", "count"),
        Q("skills_obstacle", "Worker skills as an obstacle to business", "ordinal"),
        Q("labor_rules_obstacle", "Labor regulations as an obstacle to business", "ordinal"),
        Q("training_topics", "Topics covered in employee training", "multi", [("1", "Technical skills"), ("2", "Customer service"), ("3", "Digital tools"), ("4", "Workplace safety")]),
        Q("employment_note", "Employment explanation provided", "text"),
    ]),
    ("Finance", [
        Q("bank_account", "Business has a bank account"),
        Q("has_loan", "Business has a loan or line of credit"),
        Q("applied_loan", "Business applied for a loan in the last year"),
        Q("loan_approved", "Most recent loan application was approved"),
        Q("collateral_required", "Most recent loan required collateral"),
        Q("uses_mobile_money", "Business uses a mobile-money account"),
        Q("cash_flow_forecast", "Business prepares a cash-flow forecast"),
        Q("separate_finances", "Business and household finances are kept separate"),
        Q("has_insurance", "Business has commercial insurance"),
        Q("supplier_credit", "Business received supplier credit in the last month"),
        Q("credit_sales_share", "Share of sales made on credit (%)", "percent"),
        Q("loan_amount", "Amount of the most recent business loan", "money"),
        Q("interest_rate", "Annual interest rate on the most recent loan (%)", "percent"),
        Q("loan_processing_days", "Days to receive a loan decision", "count"),
        Q("cash_buffer_days", "Days the business could operate without new revenue", "count"),
        Q("late_payment_days", "Typical customer payment delay in days", "count"),
        Q("finance_obstacle", "Access to finance as an obstacle to business", "ordinal"),
        Q("finance_sources", "Sources used to finance working capital", "multi", [("1", "Own funds"), ("2", "Bank"), ("3", "Suppliers"), ("4", "Family or friends")]),
        Q("finance_note", "Finance explanation provided", "text"),
        Q("review_note", "Interview-review note provided", "text"),
    ]),
    ("Operations and infrastructure", [
        Q("power_outages", "Number of power outages in the last month", "count"),
        Q("outage_hours", "Total hours without electricity in the last month", "count"),
        Q("owns_generator", "Business owns or shares a generator"),
        Q("has_internet", "Business has an internet connection"),
        Q("internet_interruptions", "Number of internet interruptions in the last month", "count"),
        Q("water_interruptions", "Number of water interruptions in the last month", "count"),
        Q("delivery_days", "Typical domestic delivery time in days", "count"),
        Q("inventory_days", "Days of inventory normally held", "count"),
        Q("capacity_utilization", "Average capacity utilization (%)", "percent"),
        Q("uses_accounting_software", "Business uses accounting software"),
        Q("tracks_inventory", "Business keeps an inventory record"),
        Q("quality_certificate", "Business holds a recognized quality certificate"),
        Q("energy_efficiency", "Business invested in energy efficiency in the last year"),
        Q("recycles_waste", "Business separates waste for recycling"),
        Q("backup_records", "Business keeps a backup of its records"),
        Q("electricity_obstacle", "Electricity as an obstacle to business", "ordinal"),
        Q("transport_obstacle", "Transport as an obstacle to business", "ordinal"),
        Q("internet_obstacle", "Internet access as an obstacle to business", "ordinal"),
        Q("services_used", "External business services used in the last year", "multi", [("1", "Accounting"), ("2", "Legal"), ("3", "Marketing"), ("4", "IT support")]),
        Q("operations_note", "Operations explanation provided", "text"),
    ]),
    ("Business environment and outlook", [
        Q("tax_obstacle", "Tax rates as an obstacle to business", "ordinal"),
        Q("permits_obstacle", "Business permits as an obstacle to business", "ordinal"),
        Q("corruption_obstacle", "Corruption as an obstacle to business", "ordinal"),
        Q("land_obstacle", "Access to land as an obstacle to business", "ordinal"),
        Q("security_obstacle", "Crime and security as an obstacle to business", "ordinal"),
        Q("courts_obstacle", "Courts as an obstacle to business", "ordinal"),
        Q("permit_wait_days", "Days taken to obtain the latest operating permit", "count"),
        Q("management_regulation_share", "Share of management time spent on regulations (%)", "percent"),
        Q("inspection_count", "Number of official inspections in the last year", "count"),
        Q("plans_hiring", "Business plans to hire in the next 12 months"),
        Q("plans_investment", "Business plans to invest in equipment in the next 12 months"),
        Q("plans_expansion", "Business plans to expand to a new location"),
        Q("expects_sales_growth", "Business expects sales to grow in the next 12 months"),
        Q("expects_closure", "Owner expects the business to close in the next 12 months"),
        Q("digital_investment", "Business plans to invest in digital tools"),
        Q("planned_investment", "Planned equipment investment over the next year", "money"),
        Q("expected_sales_change", "Expected change in annual sales (%)", "signed"),
        Q("business_confidence", "Confidence about the next 12 months", "category", [("1", "Very pessimistic"), ("2", "Somewhat pessimistic"), ("3", "Neutral"), ("4", "Somewhat optimistic"), ("5", "Very optimistic")]),
        Q("support_needed", "Types of support that would help the business", "multi", [("1", "Finance"), ("2", "Skills"), ("3", "Market access"), ("4", "Digital tools")]),
        Q("outlook_note", "Business-outlook explanation provided", "text"),
    ]),
]


def write_questionnaire(questions):
    parts = ["<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><title>Synthetic SurvEye review questionnaire</title></head><body>",
             '<div class="questionnaire_title"><h1>Synthetic business survey · review illustration</h1></div>',
             '<p>PUBLIC SYNTHETIC SOFTWARE FIXTURE. All content is invented; this is not fieldwork evidence or a production questionnaire.</p>']
    for section, items in SECTIONS:
        parts.append('<section class="section"><div class="section_header"><h2>' + html.escape(section) + '</h2></div>')
        for item in items:
            kind = item["kind"]
            raw_type = "multi-select" if kind == "multi" else "single-select" if item["choices"] else "text" if kind == "text" else "numeric"
            parts.append('<div class="question-container"><div class="question"><div class="question-title"><span>C</span>' + html.escape(item["label"]) + '</div></div><div class="answer"><div class="question-meta"><div class="type">' + raw_type + '</div><div class="variable_name">' + item["name"] + '</div></div>')
            if item["choices"]:
                parts.append('<div class="answer-editor multi-option">')
                for value, label in item["choices"]:
                    parts.append('<div class="option"><div class="option-value"><span>' + html.escape(value) + '</span></div><div class="option-text"><label>' + html.escape(label) + '</label></div></div>')
                parts.append('</div>')
            parts.append('</div></div>')
        parts.append('</section>')
    parts.append('</body></html>')
    (HERE / "review_demo_questionnaire.html").write_text("\n".join(parts) + "\n", encoding="utf-8")


def generate_rows(questions):
    rng = random.Random(SEED)
    rows = []
    # These are deliberately displaced fictional coordinates near four cities.
    centers = [(6.91, 79.92), (7.30, 80.64), (9.75, 80.15), (6.08, 80.55)]
    for i in range(N):
        group = i % 4
        size = (i // 4) % 3
        sector = (i // 12) % 3
        workers = [rng.randint(1, 4), rng.randint(5, 19), rng.randint(20, 85)][size]
        sales = int((workers * rng.uniform(110000, 340000) + rng.uniform(90000, 500000)) // 1000 * 1000)
        row = {"synthetic_id": f"SYN-{i+1:03d}", "review_weight": round(0.65 + group * .3 + (i % 5) * .15, 2),
               "latitude": round(centers[group][0] + rng.uniform(-.045, .045), 5),
               "longitude": round(centers[group][1] + rng.uniform(-.045, .045), 5),
               "filter_only_note": "SYNTHETIC_FILTER_NOTE_" + str(i) if i % 3 == 0 else "",
               "table_only_note": "SYNTHETIC_TABLE_NOTE_" + str(i) if i % 3 == 0 else ""}
        for j, item in enumerate(questions):
            name, kind = item["name"], item["kind"]
            if name == "region": value = REGIONS[group][0]
            elif name == "sector": value = str(sector + 1)
            elif name == "size": value = str(size + 1)
            elif name == "employees": value = workers
            elif name == "female_employees": value = rng.randint(0, workers)
            elif name == "sales": value = sales
            elif name == "monthly_sales": value = int(sales / 12 // 1000 * 1000)
            elif name == "employment_change": value = [-5, 0, 5][i % 3]
            elif name == "bank_account": value = 1 if i % 10 < [9, 3, 6, 7][group] else 0
            elif name == "female_owner": value = 1 if (i // 4) % 10 < [5, 4, 3, 6][group] else 0
            elif kind == "binary": value = int(rng.random() < .25 + .12 * group + .07 * size)
            elif kind == "category": value = rng.choice(item["choices"])[0]
            elif kind == "ordinal": value = rng.choices([0, 1, 2, 3, 4], weights=[18, 27, 27, 20, 8])[0]
            elif kind == "percent": value = round(rng.uniform(0, 100), 1)
            elif kind == "signed": value = rng.choice([-20, -10, -5, 0, 5, 10, 20, 30])
            elif kind == "money": value = int(rng.uniform(60000, 2200000) // 1000 * 1000)
            elif kind == "count": value = rng.choice([0, 1, 2, 3, 5, 8, 12, 20, 30])
            elif kind == "text": value = "SYNTHETIC_PRIVATE_NOTE_" + name + "_" + str(i) if i % 3 == 0 else ""
            elif kind == "multi":
                for value_code, _ in item["choices"]:
                    row[name + "__" + value_code] = "" if (i + j) % 17 == 0 else int(rng.random() < .48)
                continue
            else: raise AssertionError(kind)
            # Keep core filters complete and signed employment fixture exactly balanced.
            if kind != "text" and name not in {"region", "sector", "size", "employment_change"} and (i + j * 3) % 23 == 0:
                value = ""
            row[name] = value
        rows.append(row)
    return rows


def write_config(questions):
    money = " ".join(q["name"] for q in questions if q["kind"] == "money")
    signed = " ".join(q["name"] for q in questions if q["kind"] == "signed")
    count = " ".join(q["name"] for q in questions if q["kind"] == "count")
    config = [
        ("mode", "build"), ("questionnaire", "tests/review_demo_questionnaire.html"),
        ("data", "tests/review_demo_data.csv"), ("output", "examples/surveye_review_dashboard.html"),
        ("status", "build/review-demo-status.tsv"), ("diagnostics", "build/review-demo-diagnostics.txt"),
        ("title", "Sri Lanka · synthetic survey preview"),
        ("subtitle", "240 invented interviews · six questionnaire sections · illustration only"),
        ("variables", " ".join(q["name"] for q in questions)),
        ("filters", "region sector size"), ("highlights", "sales employees bank_account female_owner"),
        ("continuous", signed), ("histograms", money), ("discrete", count),
        ("noautogroups", "1"), ("maxpanels", "0"), ("maxcategories", "20"),
        ("weight", "review_weight"), ("weighttype", "pweight"),
        ("usdvars", money), ("usdrate", "300"), ("currency", "LKR"),
        ("tableby", "region"), ("tablevars", "female_owner sales bank_account employees review_note"),
        ("tablestats", "share:1|median|share:1|median|auto"),
        ("tablelabels", "Women among owners|Median annual sales|Has a bank account|Median employees|Review note available"),
        ("tabletitle", "Business profile by region"),
        ("tablesubtitle", "Synthetic illustration. The all-region benchmark follows sector and size filters."),
        ("tabletotal", "All synthetic interviews"), ("tableweightlabel", "Illustrative weighted total"),
        ("compare", "bank_account female_owner online_sales provides_training"),
        ("compareby", "region"), ("comparelevels", "01|1|03|04"),
        ("comparetitle", "Business practices by region"),
        ("latitude", "latitude"), ("longitude", "longitude"), ("country", "Sri Lanka"),
        ("maptype", "points"), ("mapby", "region"), ("basemap", "osm"),
        ("maptitle", "Invented interview locations"),
        ("theme", "worldbank"), ("density", "compact"),
        ("keymessages", "Illustration only::Every response, weight, and map point is invented. These are not findings about Sri Lanka.|Try the dashboard::Filter a region or sector, open a question, compare groups, and switch weighted estimates or currency."),
        ("source", "Public synthetic SurvEye software-review fixture. No actual firms or respondents."),
        ("note", "Illustration only. All data are synthetic, including weights and locations. The LKR 300 per USD rate is a fixed demonstration assumption, not a current exchange rate. Negative employment changes are legitimate values."),
        ("replace", "1"),
    ]
    write_tsv("review_demo_config.tsv", config)
    privacy = [(key, value) for key, value in config if key not in {"output", "status", "diagnostics", "filters", "tablevars", "tablestats", "tablelabels"}]
    privacy += [("output", "build/review-demo-privacy.html"), ("status", "build/review-demo-privacy-status.tsv"),
                ("filters", "region sector size filter_only_note"),
                ("tablevars", "table_only_note"), ("tablestats", "auto"),
                ("tablelabels", "Synthetic note available"),
                ("datatype.filter_only_note", "text"), ("datalabel.filter_only_note", "Filter-only note provided"),
                ("datatype.table_only_note", "text"), ("datalabel.table_only_note", "Table-only note provided")]
    write_tsv("review_demo_privacy_config.tsv", privacy)


def write_tsv(name, rows):
    with (HERE / name).open("w", encoding="utf-8", newline="") as stream:
        csv.writer(stream, delimiter="\t", lineterminator="\n").writerows(rows)


def main():
    questions = [q for _, items in SECTIONS for q in items]
    assert len(SECTIONS) == 6 and all(len(items) == 20 for _, items in SECTIONS)
    assert len({q["name"] for q in questions}) == 120
    write_questionnaire(questions)
    rows = generate_rows(questions)
    with (HERE / "review_demo_data.csv").open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(rows[0]), lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)
    write_config(questions)
    refs = {"synthetic": True, "seed": SEED, "rows": N, "questions": len(questions), "sections": 6,
            "usd_rate_lkr_per_usd": 300, "employment_change": {"n": 240, "mean": 0, "min": -5, "max": 5},
            "notes_completion_unweighted_percent": 100 / 3,
            "forbidden_raw_note_prefixes_in_html": ["SYNTHETIC_PRIVATE_NOTE_", "SYNTHETIC_FILTER_NOTE_", "SYNTHETIC_TABLE_NOTE_"],
            "region_counts": {code: sum(row["region"] == code for row in rows) for code, _ in REGIONS}}
    refs["unweighted_highlights"] = {}
    for variable in ["sales", "employees", "bank_account", "female_owner"]:
        values = sorted(float(row[variable]) for row in rows if row[variable] != "")
        n = len(values)
        refs["unweighted_highlights"][variable] = {"valid_n": n, "missing_n": N - n, "mean": sum(values) / n,
            "median": (values[(n - 1) // 2] + values[n // 2]) / 2}
    (HERE / "review_demo_expected.json").write_text(json.dumps(refs, indent=2) + "\n", encoding="utf-8")
    print(f"Created public synthetic fixture: {N} rows, {len(questions)} questions, 6 sections. Seed {SEED}.")
    print("From repository root: java -jar surveye.jar --config tests/review_demo_config.tsv")


if __name__ == "__main__":
    main()
