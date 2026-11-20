import sys, collections, xml.etree.ElementTree as ET

path = sys.argv[1]
try:
    root = ET.parse(path).getroot()
except Exception as e:
    print(f"::warning::Lint report not found ({e})")
    sys.exit(0)

issues = root.findall("issue")
counts = collections.Counter()
shown = collections.Counter()
for i in issues:
    sev = i.get("severity", "Warning")
    iid = i.get("id", "?")
    counts[(sev, iid)] += 1
    loc = i.find("location")
    f = loc.get("file", "") if loc is not None else ""
    line = loc.get("line", "1") if loc is not None else "1"
    if "/app/" in f:
        f = "app/" + f.split("/app/", 1)[1]
    kind = "error" if sev in ("Error", "Fatal") else "warning"
    if shown[kind] < 10:
        shown[kind] += 1
        msg = i.get("message", "").replace("\n", " ")
        print(f"::{kind} file={f},line={line},title=Lint {iid}::{msg}")
summary = "; ".join(f"{s} {iid} x{n}" for (s, iid), n in sorted(counts.items(), key=lambda x: (-x[1], x[0])))
print(f"::notice title=Lint summary::{len(issues)} issues. {summary}"[:3000])
