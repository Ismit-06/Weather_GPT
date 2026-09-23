@echo off
echo Starting WeatherGPT Main Backend on http://0.0.0.0:8000...
cd /d %~dp0
call venv\Scripts\activate.bat
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
pause
