import html
from pathlib import Path

def get_web_app_html() -> str:
    static_file = Path(__file__).resolve().parent / "static" / "index.html"
    if static_file.exists():
        return static_file.read_text(encoding="utf-8")
    return """<!DOCTYPE html>
<html lang="en">
<head><meta charset="UTF-8"><title>WeatherGPT Web App</title></head>
<body style="font-family:sans-serif; text-align:center; padding: 50px;">
    <h1>WeatherGPT Web App</h1>
    <p>Application loading...</p>
</body>
</html>"""
