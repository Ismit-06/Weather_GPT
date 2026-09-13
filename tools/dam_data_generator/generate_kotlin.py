import sys
from dams_india_data import DAMS_DATA

print(f"Count: {len(DAMS_DATA)}")
with open(r"e:/WORK/WeatherGPT/kotlin_dams.txt", "w", encoding="utf-8") as f:
    f.write("listOf(\n")
    for i, d in enumerate(DAMS_DATA, 1):
        warn_str = "true" if d["warn"] else "false"
        name = d["name"]
        state = d["state"]
        region = d["region"]
        district = d["district"]
        basin = d["basin"]
        lat = d["lat"]
        lon = d["lon"]
        frl = d["frl"]
        lvl = d["level"]
        cap = d["cap"]
        stg = d["storage"]
        pct = d["pct"]
        lpct = d["last_pct"]
        npct = d["norm_pct"]
        cca = d["cca"]
        mw = d["mw"]
        entry = f"""    DamItem(
        id = {i},
        name = "{name}",
        state = "{state}",
        region = "{region}",
        district = "{district}",
        basin = "{basin}",
        latitude = {lat},
        longitude = {lon},
        frl_m = {frl},
        current_level_m = {lvl},
        live_capacity_bcm = {cap},
        live_storage_bcm = {stg},
        storage_percent = {pct},
        last_year_storage_percent = {lpct},
        normal_storage_percent = {npct},
        irrigation_cca = {cca},
        hydel_mw = {mw},
        observation_date = "2026-09-10",
        source = "Central Water Commission (CWC)",
        source_type = "OFFICIAL_DATA",
        official_warning = {warn_str}
    ),
"""
        f.write(entry)
    f.write(")\n")
print("Generated kotlin_dams.txt successfully!")
