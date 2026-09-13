import io, json
from PIL import Image, ImageDraw, ImageFilter
from fastapi.testclient import TestClient
from service.main import app
from service.model_loader import model_manager

model_manager.load()
client = TestClient(app)

def create_bedsheet_variant(name, base_rgb, pattern=None):
    img = Image.new('RGB', (512, 512), color=base_rgb)
    draw = ImageDraw.Draw(img)
    
    # Draw wrinkles
    for i in range(10, 500, 25):
        w_color = tuple(max(0, min(255, c + (20 if i % 2 == 0 else -20))) for c in base_rgb)
        draw.line([(0, i), (512, i + (i % 40) - 20)], fill=w_color, width=5)
        draw.line([(i, 0), (i + (i % 30) - 15, 512)], fill=w_color, width=4)
        
    if pattern == 'striped':
        for x in range(0, 512, 40):
            draw.rectangle([x, 0, x + 20, 512], fill=(200, 200, 220))
    elif pattern == 'sunlight_patch':
        draw.ellipse([100, 100, 400, 400], fill=tuple(min(255, c + 60) for c in base_rgb))
    elif pattern == 'partial_frame':
        draw.rectangle([0, 256, 512, 512], fill=(120, 120, 120))
        
    img = img.filter(ImageFilter.GaussianBlur(1.0))
    return img

variants = [
    ('1_original_bedsheet', None, None, 'dataset/nightmare/bedsheet/known_bedsheet_failure_001.png'),
    ('2_similar_bedsheet', (190, 205, 220), None, None),
    ('3_white_bedsheet', (235, 235, 240), None, None),
    ('4_blue_bedsheet', (130, 160, 210), None, None),
    ('5_grey_bedsheet', (170, 175, 180), None, None),
    ('6_patterned_bedsheet', (180, 195, 215), 'striped', None),
    ('7_wrinkled_bedsheet', (185, 200, 218), None, None),
    ('8_bedsheet_under_sunlight', (210, 215, 220), 'sunlight_patch', None),
    ('9_bedsheet_under_indoor_light', (220, 205, 180), None, None),
    ('10_bedsheet_partial_frame', (185, 200, 218), 'partial_frame', None)
]

results = []
all_passed = True

print('=== EXECUTING 10 BEDSHEET REGRESSION TESTS ===')

for idx, var in enumerate(variants, 1):
    label, rgb, pattern, path = var
    if path:
        with open(path, 'rb') as f:
            img_bytes = f.read()
    else:
        img = create_bedsheet_variant(label, rgb, pattern=pattern)
        buf = io.BytesIO()
        img.save(buf, format='JPEG', quality=85)
        img_bytes = buf.getvalue()

    filename = f'{label}.jpg'
    res = client.post(
        '/api/sky/analyze',
        files={'image': (filename, io.BytesIO(img_bytes), 'image/jpeg')}
    )
    assert res.status_code == 200
    data = res.json()

    sky_det = data.get('sky_detected', False)
    conf = data.get('sky_confidence', 0.0)
    scene = data.get('scene_type', 'unknown')
    cloud_cond = data.get('cloud_condition')

    passed = (sky_det is False) and (cloud_cond is None)
    if not passed:
        all_passed = False

    results.append({
        'test_id': idx,
        'label': label,
        'sky_detected': sky_det,
        'sky_confidence': conf,
        'scene_type': scene,
        'cloud_condition': cloud_cond,
        'passed': passed
    })
    status_str = 'PASS' if passed else 'FAIL'
    print(f'[{idx}/10] {label}: sky_detected={sky_det} (conf={conf:.2f}, scene={scene}, cloud={cloud_cond}) -> {status_str}')

with open('evaluation/reports/bedsheet_regression_results.json', 'w') as f:
    json.dump({'all_passed': all_passed, 'tests': results}, f, indent=2)

final_str = 'ALL 10 PASSED' if all_passed else 'REGRESSION DETECTED'
print(f'=== BEDSHEET REGRESSION SUMMARY: {final_str} ===')
