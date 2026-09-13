import io, os, json
from PIL import Image, ImageDraw
from fastapi.testclient import TestClient
from service.main import app
from service.model_loader import model_manager

model_manager.load()
client = TestClient(app)

categories = {
    'INDOOR': [
        ('white_ceiling', (240, 240, 242)),
        ('blue_ceiling', (180, 190, 210)),
        ('textured_ceiling', (220, 220, 225)),
        ('painted_wall', (210, 210, 210)),
        ('blue_wall', (140, 160, 200)),
        ('white_wall', (245, 245, 245)),
        ('grey_wall', (160, 160, 165)),
        ('curtain', (170, 180, 205)),
        ('blanket', (150, 170, 210)),
        ('carpet', (140, 140, 130)),
        ('floor_wood', (160, 110, 70))
    ],
    'WINDOW_AND_SCREEN': [
        ('window_reflection', (160, 175, 195)),
        ('screen_displaying_sky', (100, 160, 230)),
        ('tv_showing_clouds', (190, 200, 220)),
        ('laptop_screen', (120, 170, 235))
    ],
    'OUTDOOR_NON_SKY': [
        ('ocean_water', (30, 80, 140)),
        ('lake_water', (40, 90, 120)),
        ('snow_ground', (240, 245, 255)),
        ('concrete_road', (120, 120, 125)),
        ('building_facade', (180, 170, 160)),
        ('roof_tiles', (150, 70, 60)),
        ('vegetation_trees', (40, 110, 45))
    ]
}

results = {}
for cat_name, items in categories.items():
    results[cat_name] = []
    print(f'=== Testing {cat_name} ===')
    for label, rgb in items:
        img = Image.new('RGB', (256, 256), color=rgb)
        draw = ImageDraw.Draw(img)
        for y in range(0, 256, 16):
            draw.line([(0, y), (256, y)], fill=tuple(max(0, c - 10) for c in rgb), width=1)
            
        buf = io.BytesIO()
        img.save(buf, format='JPEG', quality=85)
        
        res = client.post('/api/sky/analyze', files={'image': (f'{label}.jpg', io.BytesIO(buf.getvalue()), 'image/jpeg')})
        data = res.json()
        sky_det = data.get('sky_detected', False)
        conf = data.get('sky_confidence', 0.0)
        scene = data.get('scene_type', 'unknown')
        
        correct_reject = (sky_det is False)
        results[cat_name].append({
            'label': label,
            'sky_detected': sky_det,
            'confidence': conf,
            'scene_type': scene,
            'correct_rejection': correct_reject
        })
        status_text = 'REJECTED_SUCCESS' if correct_reject else 'FALSE_POSITIVE'
        print(f'  {label}: sky_det={sky_det} (conf={conf:.2f}, scene={scene}) -> {status_text}')

with open('evaluation/reports/hard_negative_test_results.json', 'w') as f:
    json.dump(results, f, indent=2)
print('Hard negative test suite complete.')
