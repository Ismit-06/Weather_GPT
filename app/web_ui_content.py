import html

WEB_APP_HTML = """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>WeatherGPT • AI Meteorological Intelligence</title>
    <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
    <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&display=swap" rel="stylesheet">
    <style>
        :root {
            --bg-canvas: #EFF2F6;
            --surface-white: #FFFFFF;
            --primary-navy: #0F2942;
            --ocean-blue: #0284C7;
            --electric-blue: #2563EB;
            --slate-primary: #0F172A;
            --slate-secondary: #475569;
            --slate-muted: #64748B;
            --border-glass: #D6DFE8;
            --danger-red: #DC2626;
            --warning-amber: #D97706;
            --success-green: #059669;
            --card-shadow: 0 10px 30px rgba(15, 41, 66, 0.05);
        }
        * { margin: 0; padding: 0; box-sizing: border-box; font-family: 'Plus Jakarta Sans', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; }
        body { background-color: var(--bg-canvas); color: var(--slate-primary); min-height: 100vh; display: flex; flex-direction: column; align-items: center; justify-content: center; }

        /* Smartphone Frame Mockup Container */
        .device-wrapper {
            width: 100%;
            max-width: 480px;
            height: 100vh;
            max-height: 940px;
            background: var(--bg-canvas);
            border: 1px solid var(--border-glass);
            display: flex;
            flex-direction: column;
            position: relative;
            box-shadow: 0 25px 60px rgba(15, 41, 66, 0.15);
            overflow: hidden;
        }

        @media (min-width: 520px) {
            .device-wrapper {
                border-radius: 40px;
                height: 92vh;
                margin: 20px 0;
            }
        }

        /* Top Header Navigation Bar */
        .quiet-header {
            background: rgba(255, 255, 255, 0.9);
            backdrop-filter: blur(12px);
            border-bottom: 1px solid var(--border-glass);
            padding: 14px 18px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            z-index: 100;
        }

        .brand-box {
            display: flex;
            align-items: center;
            gap: 10px;
            cursor: pointer;
        }

        .brand-box img {
            width: 36px;
            height: 36px;
            border-radius: 10px;
            object-fit: contain;
        }

        .brand-box h1 {
            font-size: 18px;
            font-weight: 800;
            color: var(--primary-navy);
            letter-spacing: -0.5px;
        }

        .header-actions {
            display: flex;
            align-items: center;
            gap: 8px;
        }

        .icon-btn {
            width: 36px;
            height: 36px;
            border-radius: 10px;
            border: 1px solid var(--border-glass);
            background: white;
            display: flex;
            align-items: center;
            justify-content: center;
            cursor: pointer;
            font-size: 16px;
            transition: all 0.2s ease;
        }

        .icon-btn:hover { background: #F8FAFC; }

        /* Main Screen Container */
        .screen-container {
            flex: 1;
            overflow-y: auto;
            padding: 16px;
            display: flex;
            flex-direction: column;
            gap: 16px;
            scrollbar-width: none;
        }
        .screen-container::-webkit-scrollbar { display: none; }

        .tab-page { display: none; }
        .tab-page.active { display: flex; flex-direction: column; gap: 16px; }

        /* Quiet Sky Cards & Hero */
        .glass-card {
            background: var(--surface-white);
            border: 1px solid var(--border-glass);
            border-radius: 24px;
            padding: 20px;
            box-shadow: var(--card-shadow);
        }

        .weather-hero {
            background: linear-gradient(135deg, #0F2942 0%, #1E3A8A 100%);
            color: white;
            border-radius: 28px;
            padding: 24px;
            box-shadow: 0 15px 35px rgba(15, 41, 66, 0.2);
            position: relative;
        }

        .hero-top { display: flex; justify-content: space-between; align-items: flex-start; }
        .hero-chip { background: rgba(255,255,255,0.2); padding: 4px 10px; border-radius: 20px; font-size: 11px; font-weight: 700; letter-spacing: 0.5px; }
        .hero-city { font-size: 26px; font-weight: 800; margin-top: 6px; }
        .hero-temp { font-size: 56px; font-weight: 800; line-height: 1; letter-spacing: -2px; margin: 12px 0 4px; }
        .hero-cond { font-size: 15px; font-weight: 600; opacity: 0.95; }

        .hero-metrics {
            display: flex;
            justify-content: space-between;
            margin-top: 18px;
            padding-top: 14px;
            border-top: 1px solid rgba(255, 255, 255, 0.15);
        }

        .m-item { display: flex; flex-direction: column; }
        .m-item .lbl { font-size: 10px; color: rgba(255,255,255,0.7); text-transform: uppercase; }
        .m-item .val { font-size: 14px; font-weight: 700; margin-top: 2px; }

        .cyclone-banner {
            background: #FEF2F2;
            border: 1.5px solid #FCA5A5;
            border-radius: 20px;
            padding: 14px 16px;
            display: flex;
            gap: 12px;
            align-items: flex-start;
        }

        /* Weather AI Floating Orb Widget */
        .ai-orb-card {
            background: linear-gradient(135deg, #0F2942 0%, #0284C7 100%);
            border-radius: 24px;
            padding: 18px;
            color: white;
            display: flex;
            align-items: center;
            gap: 14px;
            cursor: pointer;
            box-shadow: 0 10px 25px rgba(2, 132, 199, 0.3);
        }

        .orb-pulse {
            width: 44px;
            height: 44px;
            border-radius: 50%;
            background: radial-gradient(circle, #38BDF8 0%, #0284C7 100%);
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 22px;
            box-shadow: 0 0 15px #38BDF8;
            animation: pulseOrb 2s infinite alternate;
        }

        @keyframes pulseOrb {
            0% { transform: scale(1); box-shadow: 0 0 10px #38BDF8; }
            100% { transform: scale(1.08); box-shadow: 0 0 22px #38BDF8; }
        }

        /* Bottom Navigation Bar (Matching Android Compose UI) */
        .bottom-nav {
            background: rgba(255, 255, 255, 0.95);
            backdrop-filter: blur(16px);
            border-top: 1px solid var(--border-glass);
            padding: 10px 14px 14px;
            display: flex;
            justify-content: space-around;
            align-items: center;
            z-index: 100;
        }

        .nav-item {
            display: flex;
            flex-direction: column;
            align-items: center;
            gap: 4px;
            color: var(--slate-secondary);
            font-size: 11px;
            font-weight: 600;
            cursor: pointer;
            transition: all 0.2s ease;
            padding: 6px 10px;
            border-radius: 14px;
            text-decoration: none;
        }

        .nav-item .icon { font-size: 20px; }

        .nav-item.active {
            color: var(--primary-navy);
            background: rgba(15, 41, 66, 0.08);
            font-weight: 800;
        }

        /* Chat Layout */
        .chat-wrap { display: flex; flex-direction: column; height: 100%; min-height: 580px; }
        .chat-box { flex: 1; overflow-y: auto; display: flex; flex-direction: column; gap: 12px; padding: 12px 0; }
        .bubble { max-width: 84%; padding: 12px 16px; border-radius: 18px; font-size: 13.5px; line-height: 1.45; }
        .bubble.user { align-self: flex-end; background: var(--primary-navy); color: white; border-bottom-right-radius: 4px; }
        .bubble.ai { align-self: flex-start; background: #F1F5F9; color: var(--slate-primary); border-bottom-left-radius: 4px; border: 1px solid var(--border-glass); }

        .chat-input { display: flex; gap: 8px; padding-top: 10px; border-top: 1px solid var(--border-glass); }
        .chat-input input { flex: 1; padding: 12px 16px; border-radius: 14px; border: 1px solid var(--border-glass); font-size: 13.5px; outline: none; }
        .chat-input button { padding: 0 18px; background: var(--primary-navy); color: white; border: none; border-radius: 14px; font-weight: 700; cursor: pointer; }

        /* Tables & Lists */
        .data-table { width: 100%; border-collapse: collapse; font-size: 12.5px; }
        .data-table th, .data-table td { padding: 10px 12px; text-align: left; border-bottom: 1px solid #E2E8F0; }
        .data-table th { background: #F8FAFC; color: var(--slate-secondary); font-weight: 700; }
        #map { height: 480px; width: 100%; border-radius: 20px; border: 1px solid var(--border-glass); }

        .pill-badge { display: inline-block; padding: 3px 8px; border-radius: 6px; font-size: 10.5px; font-weight: 700; }
        .pill-danger { background: #FEE2E2; color: #DC2626; }
        .pill-warning { background: #FEF3C7; color: #D97706; }
        .pill-success { background: #D1FAE5; color: #059669; }

        .search-modal { position: absolute; top: 0; left: 0; right: 0; bottom: 0; background: rgba(15,41,66,0.5); backdrop-filter: blur(8px); display: none; z-index: 1000; padding: 20px; }
        .search-card { background: white; border-radius: 24px; padding: 20px; box-shadow: 0 20px 50px rgba(0,0,0,0.2); }
    </style>
</head>
<body>

    <!-- Smartphone App Canvas Container -->
    <div class="device-wrapper">

        <!-- Top Quiet Sky Header -->
        <header class="quiet-header">
            <div class="brand-box" onclick="switchNav('home')">
                <img src="https://raw.githubusercontent.com/Ismit-06/Weather_GPT/main/assets/logo.png" alt="WeatherGPT Logo">
                <h1>WeatherGPT</h1>
            </div>
            <div class="header-actions">
                <button class="icon-btn" onclick="openSearchModal()" title="Search Location">📍</button>
                <button class="icon-btn" onclick="switchNav('alerts')" title="Alerts">🚨</button>
            </div>
        </header>

        <!-- Main Screen Body Container -->
        <div class="screen-container">

            <!-- Location Bar -->
            <div style="display: flex; justify-content: space-between; align-items: center; padding: 2px 4px;">
                <div>
                    <strong style="color: var(--ocean-blue); font-size: 14px;" id="currentLocationName">📍 Visakhapatnam, Andhra Pradesh</strong>
                    <div style="color: var(--slate-muted); font-size: 11px;" id="currentCoords">(17.6868°N, 83.2185°E)</div>
                </div>
                <button onclick="useGPS()" style="background: white; border: 1px solid var(--border-glass); padding: 5px 10px; border-radius: 10px; font-size: 11px; font-weight: 700; cursor: pointer;">🎯 GPS</button>
            </div>

            <!-- TAB 1: HOME SCREEN -->
            <div id="page-home" class="tab-page active">
                <div id="cycloneBanner" class="cyclone-banner" style="display:none;">
                    <span style="font-size: 22px;">🚨</span>
                    <div style="flex:1;">
                        <span class="pill-badge pill-danger" id="cycloneBadge">CRITICAL CYCLONE ALERT</span>
                        <strong style="display:block; color: #991B1B; font-size: 13.5px; margin-top: 2px;" id="cycloneTitle">Severe Cyclonic Warning</strong>
                        <p style="font-size: 12px; color: #7F1D1D; margin-top: 2px;" id="cycloneMessage">Deep depression approaching Odisha-Vizag coast with high wind gusts.</p>
                    </div>
                </div>

                <!-- Weather Hero Telemetry -->
                <div class="weather-hero">
                    <div class="hero-top">
                        <div>
                            <span class="hero-chip">LIVE MET TELEMETRY</span>
                            <div class="hero-city" id="heroCity">Visakhapatnam</div>
                        </div>
                        <div style="font-size: 44px;" id="heroIcon">⛈️</div>
                    </div>
                    <div class="hero-temp" id="heroTemp">28.4°C</div>
                    <div class="hero-cond" id="heroCondition">Heavy Rain & Thunderstorm</div>
                    <div class="hero-metrics">
                        <div class="m-item"><span class="lbl">Wind</span><span class="val" id="metricWind">47.5 km/h</span></div>
                        <div class="m-item"><span class="lbl">Pressure</span><span class="val" id="metricPressure">993 hPa</span></div>
                        <div class="m-item"><span class="lbl">Humidity</span><span class="val" id="metricHumidity">88%</span></div>
                        <div class="m-item"><span class="lbl">Rain</span><span class="val" id="metricRain">12.4 mm</span></div>
                    </div>
                </div>

                <!-- Weather AI Assistant Floating Widget -->
                <div class="ai-orb-card" onclick="switchNav('chat')">
                    <div class="orb-pulse">🤖</div>
                    <div style="flex:1;">
                        <strong style="font-size: 14.5px; display:block;">WeatherGPT AI Assistant</strong>
                        <p style="font-size: 11.5px; opacity: 0.9;">Ask questions, track cyclones & flood risk</p>
                    </div>
                    <span style="font-size: 18px;">➔</span>
                </div>

                <!-- Next 24-Hour Hourly Forecast -->
                <div class="glass-card">
                    <h3 style="font-size: 14.5px; font-weight: 800; margin-bottom: 12px;">📅 24-Hour Forecast</h3>
                    <div id="hourlyContainer" style="display: flex; gap: 10px; overflow-x: auto; padding-bottom: 6px;"></div>
                </div>

                <!-- Farming & Agriculture Card -->
                <div class="glass-card">
                    <h3 style="font-size: 14.5px; font-weight: 800; margin-bottom: 10px;">🌾 Agriculture Intelligence</h3>
                    <div id="agriContent"><p style="color: var(--slate-secondary); font-size: 12.5px;">Loading farm assessment...</p></div>
                </div>
            </div>

            <!-- TAB 2: CHAT SCREEN -->
            <div id="page-chat" class="tab-page">
                <div class="glass-card chat-wrap">
                    <div style="display: flex; align-items: center; justify-content: space-between; padding-bottom: 10px; border-bottom: 1px solid var(--border-glass);">
                        <strong style="font-size: 15px; color: var(--primary-navy);">🤖 WeatherGPT AI Reasoning Agent</strong>
                        <span class="pill-badge pill-success">LOCAL LLM / BACKEND ACTIVE</span>
                    </div>
                    <div class="chat-box" id="chatMessages">
                        <div class="bubble ai">Hello! I am WeatherGPT. Ask me any weather question, cyclone trajectory, or agricultural advisory.</div>
                    </div>
                    <div class="chat-input">
                        <input type="text" id="chatInput" placeholder="Ask WeatherGPT..." onkeydown="if(event.key==='Enter') sendChatMessage()">
                        <button onclick="sendChatMessage()">Ask</button>
                    </div>
                </div>
            </div>

            <!-- TAB 3: SKY AI VISION SCREEN -->
            <div id="page-camera" class="tab-page">
                <div class="glass-card">
                    <h3 style="font-size: 16px; font-weight: 800; margin-bottom: 6px;">☁️ Sky AI Computer Vision</h3>
                    <p style="color: var(--slate-secondary); font-size: 12px; margin-bottom: 16px;">Capture or upload a cloud photograph for instant vision AI weather analysis.</p>
                    <input type="file" id="skyImageInput" accept="image/*" style="margin-bottom: 12px; font-size: 12px;">
                    <button onclick="analyzeSkyImage()" style="width: 100%; padding: 12px; background: var(--primary-navy); color: white; border: none; border-radius: 14px; font-weight: 700; cursor: pointer;">Analyze Sky Photo</button>
                    <div id="skyResult" style="margin-top: 14px; background: #F8FAFC; border: 1px solid var(--border-glass); border-radius: 14px; padding: 14px;">
                        <p style="color: var(--slate-muted); font-size: 12px;">Cloud classification results will appear here.</p>
                    </div>
                </div>
            </div>

            <!-- TAB 4: HAZARDS MAP SCREEN -->
            <div id="page-map" class="tab-page">
                <div class="glass-card" style="padding: 10px;">
                    <div id="map"></div>
                </div>
            </div>

            <!-- TAB 5: ALERTS & RESERVOIRS SCREEN -->
            <div id="page-alerts" class="tab-page">
                <div class="glass-card">
                    <h3 style="font-size: 15px; font-weight: 800; margin-bottom: 12px; color: var(--danger-red);">🚨 Regional Hazard & Emergency Alerts</h3>
                    <div id="alertsFeed"><p style="color: var(--slate-secondary); font-size: 12px;">Fetching IMD alert feed...</p></div>
                </div>
                <div class="glass-card">
                    <h3 style="font-size: 15px; font-weight: 800; margin-bottom: 12px;">🌊 CWC Monitored Dam & Water Storage</h3>
                    <div style="overflow-x: auto;">
                        <table class="data-table">
                            <thead><tr><th>Reservoir</th><th>Storage (%)</th><th>Risk Level</th></tr></thead>
                            <tbody id="damsTableBody"><tr><td colspan="3">Loading dam data...</td></tr></tbody>
                        </table>
                    </div>
                </div>
            </div>

        </div>

        <!-- Bottom Android Compose Style Navigation Bar -->
        <nav class="bottom-nav">
            <div class="nav-item active" id="btn-home" onclick="switchNav('home')">
                <span class="icon">🌤️</span>
                <span>Home</span>
            </div>
            <div class="nav-item" id="btn-chat" onclick="switchNav('chat')">
                <span class="icon">🤖</span>
                <span>Chat</span>
            </div>
            <div class="nav-item" id="btn-camera" onclick="switchNav('camera')">
                <span class="icon">☁️</span>
                <span>Sky AI</span>
            </div>
            <div class="nav-item" id="btn-map" onclick="switchNav('map')">
                <span class="icon">🗺️</span>
                <span>Map</span>
            </div>
            <div class="nav-item" id="btn-alerts" onclick="switchNav('alerts')">
                <span class="icon">🚨</span>
                <span>Alerts</span>
            </div>
        </nav>

        <!-- Location Search Dialog Modal -->
        <div class="search-modal" id="searchModal">
            <div class="search-card">
                <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px;">
                    <strong style="font-size: 15px; color: var(--primary-navy);">📍 Search Location in India</strong>
                    <button onclick="closeSearchModal()" style="background: none; border: none; font-size: 18px; cursor: pointer;">✕</button>
                </div>
                <input type="text" id="searchInput" placeholder="Type city name (e.g. Visakhapatnam)..." onkeyup="handleSearch(event)" style="width: 100%; padding: 12px; border-radius: 12px; border: 1px solid var(--border-glass); outline: none; font-size: 13px;">
                <div class="search-results-dropdown" id="searchDropdown" style="margin-top: 8px; max-height: 200px; overflow-y: auto;"></div>
            </div>
        </div>

    </div>

    <script>
        let currentLat = 17.6868, currentLon = 83.2185, currentLocationStr = "Visakhapatnam, Andhra Pradesh", map = null;

        document.addEventListener("DOMContentLoaded", () => {
            fetchWeatherData(currentLat, currentLon, currentLocationStr);
            fetchAlerts(currentLat, currentLon);
            fetchAgriData(currentLat, currentLon);
            fetchDamsData();
        });

        function switchNav(tabId) {
            document.querySelectorAll('.tab-page').forEach(el => el.classList.remove('active'));
            document.querySelectorAll('.nav-item').forEach(el => el.classList.remove('active'));
            document.getElementById('page-' + tabId).classList.add('active');
            document.getElementById('btn-' + tabId).classList.add('active');
            if (tabId === 'map' && !map) setTimeout(initMap, 100);
        }

        async function fetchWeatherData(lat, lon, name) {
            currentLat = lat; currentLon = lon; currentLocationStr = name;
            document.getElementById('currentLocationName').innerText = "📍 " + name;
            document.getElementById('currentCoords').innerText = `(${lat.toFixed(4)}°N, ${lon.toFixed(4)}°E)`;
            document.getElementById('heroCity').innerText = name.split(',')[0];
            try {
                const res = await fetch(`/weather/current?latitude=${lat}&longitude=${lon}`);
                const data = await res.json();
                if (data.status === "success" && data.current) {
                    const c = data.current;
                    document.getElementById('heroTemp').innerText = `${c.temperature_c.toFixed(1)}°C`;
                    document.getElementById('heroCondition').innerText = c.symbol_code || "Clear";
                    document.getElementById('metricWind').innerText = `${(c.wind_speed_ms * 3.6).toFixed(1)} km/h`;
                    document.getElementById('metricPressure').innerText = `${c.pressure_hpa || 1013} hPa`;
                    document.getElementById('metricHumidity').innerText = `${c.relative_humidity_pct}%`;
                    document.getElementById('metricRain').innerText = `${c.precipitation_mm || 0} mm`;
                }
                if (data.forecast) renderHourlyForecast(data.forecast);
            } catch (err) { console.error(err); }
        }

        function renderHourlyForecast(forecast) {
            const container = document.getElementById('hourlyContainer'); container.innerHTML = '';
            forecast.slice(0, 24).forEach(item => {
                const timeStr = new Date(item.time).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
                const el = document.createElement('div');
                el.style.cssText = "min-width: 80px; background: #F8FAFC; border: 1px solid var(--border-glass); border-radius: 14px; padding: 10px; text-align: center;";
                el.innerHTML = `<div style="font-size: 10.5px; color: var(--slate-secondary); font-weight: 600;">${timeStr}</div><div style="font-size: 20px; margin: 4px 0;">🌧️</div><div style="font-size: 13px; font-weight: 800;">${item.temperature_c}°C</div><div style="font-size: 9.5px; color: var(--slate-muted);">💧 ${item.precipitation_mm || 0} mm</div>`;
                container.appendChild(el);
            });
        }

        async function fetchAlerts(lat, lon) {
            try {
                const res = await fetch(`/alerts?latitude=${lat}&longitude=${lon}`);
                const data = await res.json();
                const container = document.getElementById('alertsFeed'); container.innerHTML = '';
                if (data.alerts && data.alerts.length > 0) {
                    const cyclone = data.alerts.find(a => a.type === 'CYCLONE_WARNING' || a.type === 'CYCLONE_ALERT' || a.severity === 'CRITICAL');
                    if (cyclone) {
                        document.getElementById('cycloneBanner').style.display = 'flex';
                        document.getElementById('cycloneTitle').innerText = cyclone.message;
                    } else { document.getElementById('cycloneBanner').style.display = 'none'; }
                    data.alerts.forEach(alert => {
                        const div = document.createElement('div');
                        div.style.cssText = "padding: 12px; border-radius: 12px; border: 1px solid var(--border-glass); margin-bottom: 8px; background: white;";
                        div.innerHTML = `<div style="display: flex; justify-content: space-between; margin-bottom: 4px;"><strong style="color: var(--slate-primary); font-size: 13px;">${alert.type}</strong><span class="pill-badge pill-danger">${alert.severity}</span></div><p style="font-size: 12px; color: var(--slate-secondary);">${alert.message}</p>`;
                        container.appendChild(div);
                    });
                } else { container.innerHTML = '<p style="color: var(--slate-secondary); font-size: 12px;">No active hazard alerts.</p>'; }
            } catch (err) { console.error(err); }
        }

        async function fetchAgriData(lat, lon) {
            try {
                const res = await fetch(`/agriculture?latitude=${lat}&longitude=${lon}`);
                const data = await res.json();
                const container = document.getElementById('agriContent');
                if (data.status === 'success') {
                    container.innerHTML = `<div style="display: flex; justify-content: space-between; margin-bottom: 6px;"><span style="font-size: 12px; font-weight: 600;">Crop Suitability:</span><span class="pill-badge pill-success">${data.suitability_level} (${data.suitability_score}/100)</span></div><p style="font-size: 11.5px; color: var(--slate-secondary);"><strong>Irrigation:</strong> ${data.rainfall_assessment?.irrigation_impact || 'Normal'}</p>`;
                }
            } catch (err) { console.error(err); }
        }

        async function fetchDamsData() {
            try {
                const res = await fetch(`/dams?limit=10`);
                const data = await res.json();
                const tbody = document.getElementById('damsTableBody'); tbody.innerHTML = '';
                if (data.reservoirs) {
                    data.reservoirs.forEach(dam => {
                        const tr = document.createElement('tr');
                        const pct = dam.storage_percent || dam.pct || 0;
                        const badgeClass = pct >= 85 ? 'pill-danger' : (pct >= 70 ? 'pill-warning' : 'pill-success');
                        tr.innerHTML = `<td><strong>${dam.name || dam.reservoir_name}</strong></td><td><strong>${pct.toFixed(1)}%</strong></td><td><span class="pill-badge ${badgeClass}">${pct >= 85 ? 'SPILL' : 'OK'}</span></td>`;
                        tbody.appendChild(tr);
                    });
                }
            } catch (err) { console.error(err); }
        }

        async function sendChatMessage() {
            const input = document.getElementById('chatInput');
            const q = input.value.trim(); if (!q) return;
            const messagesDiv = document.getElementById('chatMessages');
            const uMsg = document.createElement('div'); uMsg.className = 'bubble user'; uMsg.innerText = q;
            messagesDiv.appendChild(uMsg); input.value = ''; messagesDiv.scrollTop = messagesDiv.scrollHeight;
            try {
                const res = await fetch(`/chat/weather`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ question: q, latitude: currentLat, longitude: currentLon }) });
                const data = await res.json();
                const aiMsg = document.createElement('div'); aiMsg.className = 'bubble ai'; aiMsg.innerText = data.answer || data.response || "No response.";
                messagesDiv.appendChild(aiMsg); messagesDiv.scrollTop = messagesDiv.scrollHeight;
            } catch (err) { console.error(err); }
        }

        async function analyzeSkyImage() {
            const input = document.getElementById('skyImageInput');
            if (!input.files || !input.files[0]) { alert("Select photo first."); return; }
            const formData = new FormData();
            formData.append("image", input.files[0]); formData.append("latitude", currentLat); formData.append("longitude", currentLon);
            const resDiv = document.getElementById('skyResult');
            resDiv.innerHTML = "<p style='color: var(--ocean-blue); font-weight:600; font-size:12px;'>Running Sky AI analysis...</p>";
            try {
                const res = await fetch(`/api/v1/weather/analyze-sky`, { method: 'POST', body: formData });
                const data = await res.json();
                if (data.status === 'success') {
                    resDiv.innerHTML = `<h4 style="color: var(--primary-navy); font-size:13px; margin-bottom: 4px;">Analysis Complete</h4><p style="font-size: 12px; margin-bottom: 4px;"><strong>Assessment:</strong> ${data.assessment}</p><p style="font-size: 11.5px; color: var(--slate-secondary);">${data.explanation}</p>`;
                } else { resDiv.innerHTML = `<p style="color: var(--danger-red); font-size:12px;">Failed: ${data.detail || 'Error'}</p>`; }
            } catch (err) { resDiv.innerHTML = `<p style="color: var(--danger-red); font-size:12px;">Request failed.</p>`; }
        }

        function initMap() {
            map = L.map('map').setView([currentLat, currentLon], 7);
            L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', { attribution: '© OpenStreetMap' }).addTo(map);
            L.marker([currentLat, currentLon]).addTo(map).bindPopup(`<b>${currentLocationStr}</b>`).openPopup();
        }

        function openSearchModal() { document.getElementById('searchModal').style.display = 'block'; }
        function closeSearchModal() { document.getElementById('searchModal').style.display = 'none'; }

        async function handleSearch(e) {
            const query = e.target.value.trim(); const dropdown = document.getElementById('searchDropdown');
            if (query.length < 2) { dropdown.innerHTML = ''; return; }
            try {
                const res = await fetch(`/location/search?query=${encodeURIComponent(query)}`);
                const data = await res.json();
                if (data.results && data.results.length > 0) {
                    dropdown.innerHTML = '';
                    data.results.forEach(item => {
                        const div = document.createElement('div');
                        div.style.cssText = "padding: 10px; border-bottom: 1px solid #f1f5f9; cursor: pointer; font-size: 12px;";
                        div.innerText = `${item.name}, ${item.admin1 || ''}`;
                        div.onclick = () => { closeSearchModal(); fetchWeatherData(item.latitude, item.longitude, `${item.name}, ${item.admin1 || ''}`); fetchAlerts(item.latitude, item.longitude); fetchAgriData(item.latitude, item.longitude); };
                        dropdown.appendChild(div);
                    });
                }
            } catch (err) { console.error(err); }
        }

        function useGPS() {
            if (navigator.geolocation) {
                navigator.geolocation.getCurrentPosition(pos => {
                    fetchWeatherData(pos.coords.latitude, pos.coords.longitude, "GPS Location");
                    fetchAlerts(pos.coords.latitude, pos.coords.longitude);
                    fetchAgriData(pos.coords.latitude, pos.coords.longitude);
                });
            }
        }
    </script>
</body>
</html>
"""
