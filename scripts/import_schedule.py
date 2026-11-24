#!/usr/bin/env python3
"""Validate a roster workbook and write the app's versioned schedule JSON."""

import argparse
import calendar
from datetime import date, timedelta
import json
from pathlib import Path
import re
import subprocess
from xml.etree import ElementTree as ET
from zipfile import ZipFile

GROUPS = {"blue": "Blå", "red": "Röd", "yellow": "Gul", "green": "Grön"}
SLOTS = ("FM", "EM", "NA")
# These ranges are printed as colored week cells in the five-shift PDFs.
PDF_VACATION = {
    2026: {
        "blue": [("2026-07-20", "2026-08-09")],
        "red": [("2026-06-08", "2026-06-28")],
        "yellow": [("2026-08-10", "2026-08-30")],
        "green": [("2026-06-29", "2026-07-19")],
    },
    2027: {
        "blue": [("2027-06-07", "2027-06-27")],
        "red": [("2027-06-28", "2027-07-18")],
        "yellow": [("2027-07-19", "2027-08-08")],
        "green": [("2027-08-09", "2027-08-29")],
    },
}


def make_schedule(schedule, year, days, groups, times):
    if schedule not in ("five", "six"):
        raise ValueError("Schedule must be five or six")
    day_count = 366 if calendar.isleap(year) else 365
    expected = {str(date(year, 1, 1) + timedelta(days=i)) for i in range(day_count)}
    if expected != days.keys():
        raise ValueError(f"Missing dates: {sorted(expected - days.keys())}; extra dates: {sorted(days.keys() - expected)}")

    teams = "ABCDE" if schedule == "five" else "ABCDEF"
    for stamp, day in days.items():
        slots = day["slots"]
        weekend = date.fromisoformat(stamp).weekday() >= 5
        required = {"FM", "NA"} if weekend else set(SLOTS)
        if slots and set(slots) != required:
            raise ValueError(f"{stamp}: expected {sorted(required)}, got {sorted(slots)}")
        if any(v not in tuple(teams) for v in slots.values()) or len(set(slots.values())) != len(slots):
            raise ValueError(f"{stamp}: invalid or repeated crew")
        if not slots and (schedule == "six" or not day.get("closure")):
            raise ValueError(f"{stamp}: missing assignments or explicit closure")

    for key in ("cutoff", "weekdayEm", "weekdayNa", "weekendNa"):
        if not re.fullmatch(r"(?:[01][0-9]|2[0-3]):[0-5][0-9]", times[key]):
            raise ValueError(f"{key} must be HH:MM")
    cutoff = minutes(times["cutoff"])
    em, na, weekend = [(minutes(times[key]) - cutoff) % 1440 for key in ("weekdayEm", "weekdayNa", "weekendNa")]
    if not (0 < em < na and 0 < weekend):
        raise ValueError("Shift times must be in order after the cutoff")

    if schedule == "six" and groups:
        raise ValueError("6-skift has no color-based vacation groups")
    vacation = []
    for group_id, periods in groups.items():
        if group_id not in GROUPS:
            raise ValueError(f"Unknown vacation color {group_id}")
        last_end = None
        for start, end in sorted(periods):
            start_date, end_date = date.fromisoformat(start), date.fromisoformat(end)
            if start_date > end_date or start_date.year != year or end_date.year != year or (last_end and start_date <= last_end):
                raise ValueError(f"Invalid or overlapping vacation dates for {group_id}: {start} to {end}")
            last_end = end_date
        vacation.append({"id": group_id, "label": GROUPS[group_id], "periods": sorted(periods)})
    return {"schemaVersion": 1, "id": schedule, "year": year, "label": "5-skift" if schedule == "five" else "6-skift",
            "teams": list(teams), "times": times, "vacation": vacation, "days": dict(sorted(days.items()))}


def minutes(clock):
    hour, minute = map(int, clock.split(":"))
    return hour * 60 + minute


def from_pdf(path, schedule, year):
    if year not in PDF_VACATION:
        raise ValueError("The PDF reader supports the supplied 2026 and 2027 rosters. Use a workbook for other years.")
    root = ET.fromstring(subprocess.check_output(["pdftotext", "-bbox", str(path), "-"]))
    pages = root.findall(".//{*}page")
    if len(pages) != 2:
        raise ValueError("Expected two half-year PDF pages")
    days = {}
    for half, page in enumerate(pages):
        words = [(n.text or "", float(n.attrib["xMin"]), float(n.attrib["yMin"]))
                 for n in page.findall("{*}word")]
        date_columns = sorted((x, y) for value, x, y in words if value == "Dat")
        if len(date_columns) != 6:
            raise ValueError("Expected six monthly tables per page")
        header_y = date_columns[0][1]
        title = {value for value, _, y in words if y < header_y}
        if str(year) not in title or ("Energi" in title) != (schedule == "six"):
            raise ValueError(f"Page {half + 1}: title does not match {schedule} {year}")
        for i, (left, header_y) in enumerate(date_columns):
            right = date_columns[i + 1][0] - 4 if i < 5 else 780
            month = 6 * half + i + 1
            headings = {value: x for value, x, y in words
                        if left - 3 <= x < right and abs(y - header_y) < 0.8 and value in ("Fm", "Em", "Natt", "v")}
            if headings.keys() != {"Fm", "Em", "Natt", "v"}:
                raise ValueError(f"Month {month}: missing headings")
            date_numbers = [(int(value), y) for value, x, y in words
                            if abs(x - left) < 3 and y > header_y + 4 and value.isdecimal()]
            for day_num, row_y in date_numbers:
                day = date(year, month, day_num)
                stamp = str(day)
                if stamp in days:
                    raise ValueError(f"Duplicate date {stamp}")
                row = sorted([(value, x) for value, x, y in words
                              if headings["Fm"] - 3 <= x < right and abs(y-row_y) < 1.2], key=lambda x: x[1])
                entry = {"slots": {}}
                holiday = []
                notes = []
                for value, x in row:
                    if x >= headings["v"] - 10:
                        if not value.isdecimal():
                            notes.append(value)
                        continue
                    nearest = min(zip(("Fm", "Em", "Natt"), SLOTS),
                                  key=lambda h: abs(x - (headings[h[0]] + 2)))
                    if len(value) == 1 and value in "ABCDEF" and abs(x - (headings[nearest[0]] + 2)) < 9:
                        if nearest[1] in entry["slots"]:
                            raise ValueError(f"{stamp}: two crews in {nearest[1]}")
                        entry["slots"][nearest[1]] = value
                    else:
                        holiday.append(value)
                if holiday:
                    if entry["slots"]:
                        raise ValueError(f"{stamp}: unexpected shift text {holiday}")
                    entry["closure"] = " ".join(holiday)
                if notes:
                    entry["note"] = " ".join(notes)
                days[stamp] = entry
    times = {"cutoff": "06:00", "weekdayEm": "14:00" if schedule == "five" else "13:00",
             "weekdayNa": "22:00" if schedule == "five" else "21:00", "weekendNa": "18:00"}
    return make_schedule(schedule, year, days, PDF_VACATION[year] if schedule == "five" else {}, times)


def read_workbook(path):
    with ZipFile(path) as book:
        root = ET.fromstring(book.read("xl/workbook.xml"))
        rels = ET.fromstring(book.read("xl/_rels/workbook.xml.rels"))
        targets = {n.attrib["Id"]: n.attrib["Target"] for n in rels
                   if n.attrib["Type"].endswith("/worksheet")}
        strings = []
        if "xl/sharedStrings.xml" in book.namelist():
            table = ET.fromstring(book.read("xl/sharedStrings.xml"))
            strings = ["".join(t.text or "" for t in item.findall(".//{*}t")) for item in table]
        workbook_properties = root.find("{*}workbookPr")
        date_1904 = workbook_properties is not None and workbook_properties.get("date1904") in ("1", "true")
        result = {}
        for sheet in root.findall(".//{*}sheet"):
            relation = next(value for key, value in sheet.attrib.items() if key.endswith("}id"))
            target = targets.get(relation)
            if not target:
                continue
            entry = target.lstrip("/") if target.startswith("/") else f"xl/{target}"
            rows = []
            for row in ET.fromstring(book.read(entry)).findall(".//{*}row"):
                cells = {}
                for cell in row.findall("{*}c"):
                    position = re.match(r"[A-Z]+", cell.attrib["r"]).group()
                    kind = cell.get("t")
                    value = cell.findtext("{*}v", "")
                    if kind == "s":
                        value = strings[int(value)]
                    if kind == "inlineStr":
                        value = "".join(t.text or "" for t in cell.findall(".//{*}t"))
                    cells[position] = value.strip()
                if any(cells.values()):
                    rows.append(cells)
            result[sheet.attrib["name"]] = rows
        return result, date_1904


def records(workbook, sheet, required):
    rows = workbook.get(sheet, [])
    if not rows or not set(required).issubset(rows[0].values()):
        raise ValueError(f"{sheet}: expected columns {', '.join(required)}")
    return [{name: row.get(i, "") for i, name in rows[0].items()} for row in rows[1:]]


def iso_date(value, date_1904):
    if value.isdecimal():
        origin = date(1904, 1, 1) if date_1904 else date(1899, 12, 30)
        return str(origin + timedelta(days=int(value)))
    return str(date.fromisoformat(value))


def from_workbook(path):
    book, date_1904 = read_workbook(path)
    props = {r["key"]: r["value"] for r in records(book, "Schema", ("key", "value"))}
    schedule, year = props["schedule"], int(props["year"])
    for key in ("cutoff", "weekdayEm", "weekdayNa", "weekendNa"):
        raw = props[key]
        if re.fullmatch(r"0(?:\.\d+)?", raw):
            minutes_of_day = round(float(raw) * 1440)
            hour, minute = divmod(minutes_of_day, 60)
            props[key] = f"{hour:02}:{minute:02}"
    days = {}
    for row in records(book, "Dagar", ("date", *SLOTS, "closure", "note")):
        stamp = iso_date(row["date"], date_1904)
        if stamp in days:
            raise ValueError(f"Duplicate date {stamp}")
        days[stamp] = {"slots": {k: row[k] for k in SLOTS if row[k]},
                       **{k: row[k] for k in ("closure", "note") if row[k]}}
    groups = {}
    if "Semester" in book:
        for row in records(book, "Semester", ("color", "from", "to")):
            groups.setdefault(row["color"], []).append((iso_date(row["from"], date_1904), iso_date(row["to"], date_1904)))
    return make_schedule(schedule, year, days, groups, props)


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("source", type=Path)
    p.add_argument("--schedule", choices=["five", "six"], help="required for the PDF reader")
    p.add_argument("--year", type=int, default=2027)
    p.add_argument("--output-directory", type=Path, default=Path("data"))
    args = p.parse_args()
    try:
        if args.source.suffix.lower() == ".pdf" and args.schedule:
            roster = from_pdf(args.source, args.schedule, args.year)
        elif args.source.suffix.lower() == ".xlsx":
            roster = from_workbook(args.source)
        else:
            raise ValueError("Use an XLSX workbook or specify --schedule and --year with a supported PDF")
        out = args.output_directory / f"{roster['id']}-{roster['year']}.json"
        times = ("cutoff", "weekdayEm", "weekdayNa", "weekendNa")
        roster["times"] = {key: roster["times"][key] for key in times}
        out.parent.mkdir(parents=True, exist_ok=True)
        temp = out.with_suffix(".tmp")
        temp.write_text(json.dumps(roster, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
        temp.replace(out)
        print(f"{out}: {len(roster['days'])} days")
    except (ValueError, KeyError) as error:
        p.exit(1, f"{error}\n")


if __name__ == "__main__":
    main()
