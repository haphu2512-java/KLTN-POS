import sys
import urllib.request
import json
import time
import re
import pymysql

sys.stdout.reconfigure(encoding='utf-8')

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Content-Type": "application/json",
    "platform": "webnew",
    "xapikey": "bhx-api-core-2022"
}

# Target categories to crawl (id, display_category_name)
CATEGORIES = [
    # Sữa & Sản phẩm từ sữa
    (2386, "Sữa tươi"),
    (2943, "Sữa hạt, sữa đậu"),
    (7558, "Sữa chua"),
    (2944, "Sữa chua uống"),
    (2382, "Sữa bột & Sữa dinh dưỡng"),
    
    # Bánh kẹo & Đồ ăn vặt
    (3364, "Bánh snack"),
    (3357, "Bánh quy"),
    (3358, "Bánh bông lan"),
    (7622, "Bánh Chocopie"),
    (6597, "Socola"),
    (2687, "Kẹo các loại"),
    (3487, "Hạt khô & Hạt dinh dưỡng"),
    
    # Mì & Đồ ăn liền
    (2565, "Mì ăn liền"),
    (2566, "Phở, bún ăn liền"),
    (2965, "Hủ tiếu, miến"),
    (2564, "Cháo gói, cháo tươi"),
    (2513, "Gạo, nếp"),
    
    # Gia vị & Dầu ăn
    (2286, "Dầu ăn"),
    (2289, "Nước mắm"),
    (2683, "Nước tương"),
    (2806, "Hạt nêm, gia vị"),
    (2804, "Đường & Muối"),
    
    # Đồ uống & Giải khát
    (2443, "Nước ngọt"),
    (2282, "Bia & Nước có cồn"),
    (2563, "Nước khoáng, nước suối"),
    (3226, "Nước tăng lực"),
    (3265, "Nước ép trái cây"),
    (2524, "Cà phê hoà tan"),
    (2385, "Trà khô, trà túi lọc"),
    
    # Thịt, Cá, Hải sản & Trứng
    (8781, "Thịt heo"),
    (8139, "Thịt bò"),
    (8790, "Thịt gà, vịt"),
    (8782, "Cá, hải sản tươi"),
    (8783, "Trứng gà, vịt, cút"),
    
    # Rau củ & Trái cây
    (8820, "Rau lá tươi"),
    (8785, "Củ, quả tươi"),
    (8788, "Trái cây tươi"),
    (3235, "Đậu, nấm & Đồ khô"),
    
    # Hóa mỹ phẩm & Đồ dùng gia đình
    (2463, "Bột giặt & Nước giặt"),
    (2465, "Nước xả vải"),
    (2387, "Nước rửa chén"),
    (2483, "Dầu gội & Xả tóc"),
    (2444, "Sữa tắm"),
    (2446, "Kem đánh răng"),
    (9081, "Giấy vệ sinh & Khăn giấy"),
    (4326, "Màng bọc & Túi rác")
]

def make_request(url, body):
    data = json.dumps(body).encode('utf-8')
    req = urllib.request.Request(url, data=data, headers=HEADERS, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            return json.loads(resp.read().decode('utf-8'))
    except Exception as e:
        # print(f"Err {url}: {e}")
        return None

def clean_unit(unit_str):
    if not unit_str or not isinstance(unit_str, str):
        return "Cái"
    unit_str = unit_str.strip()
    if not unit_str:
        return "Cái"
    # Take first word or common units
    lower = unit_str.lower()
    for u in ["hộp", "gói", "chai", "lon", "túi", "vỉ", "lốc", "kg", "gam", "g", "bịch", "thùng", "khay", "trái", "củ", "quả", "bó", "cuộn"]:
        if lower.startswith(u) or lower == u:
            return u.capitalize()
    return unit_str[:25]

def generate_ean13(idx):
    # Vietnamese prefix 893
    base = f"893{idx:09d}"
    # Calculate checksum
    odd_sum = sum(int(base[i]) for i in range(0, 12, 2))
    even_sum = sum(int(base[i]) for i in range(1, 12, 2))
    total = odd_sum + even_sum * 3
    checksum = (10 - (total % 10)) % 10
    return f"{base}{checksum}"

def fetch_all_bhx_products():
    collected = {}
    used_barcodes = set()
    barcode_counter = 100000000

    print("=== 1. FETCHING FLASH SALE PRODUCTS ===")
    fs_res = make_request("https://api.bachhoaxanh.com/gw/FlashSale/GetAllProductFlashSaleInDay", {})
    if fs_res and 'data' in fs_res and isinstance(fs_res['data'], dict):
        for group_id, group in fs_res['data'].items():
            if isinstance(group, dict) and 'lstProduct' in group:
                for p in group['lstProduct']:
                    pid = p.get('id')
                    name = p.get('fullName') or p.get('name')
                    avatar = p.get('avatar')
                    if pid and name and avatar and pid not in collected:
                        # Extract price
                        price = 0
                        if p.get('productPrices') and len(p['productPrices']) > 0:
                            price = p['productPrices'][0].get('sysPrice') or p['productPrices'][0].get('price') or 0
                        cate_name = (p.get('category') or {}).get('name') or "Hàng tiêu dùng"
                        unit = clean_unit(p.get('unit'))
                        barcode = str(p.get('productCode') or "").strip()
                        collected[pid] = {
                            "bhx_id": pid,
                            "name": name.strip(),
                            "category": cate_name,
                            "unit": unit,
                            "sale_price": float(price) if price else 25000.0,
                            "image_url": avatar,
                            "barcode": barcode
                        }
    print(f"Total after Flash Sale: {len(collected)}")

    print("\n=== 2. FETCHING DAILY SHOPPING FRESH PRODUCTS ===")
    for gid, gname in [(1741, 'Thịt & Món mặn'), (1742, 'Rau tươi'), (1743, 'Món canh & Lẩu'), 
                       (1744, 'Rau sống & Salad'), (1745, 'Trái cây tươi'), (1746, 'Tiệc tại gia'), (1747, 'Tráng miệng')]:
        daily_res = make_request("https://api.bachhoaxanh.com/gw/Home/DailyShoppingV2", {
            "ProvinceId": 3, "StoreId": 3391, "GroupId": gid, "Paging": {"PageIndex": 0, "PageSize": 100}
        })
        if daily_res and 'data' in daily_res and isinstance(daily_res['data'], dict):
            for p in daily_res['data'].get('products', []):
                pid = p.get('id')
                name = p.get('fullName') or p.get('name')
                avatar = p.get('avatar')
                if pid and name and avatar and pid not in collected:
                    price = 0
                    if p.get('productPrices') and len(p['productPrices']) > 0:
                        price = p['productPrices'][0].get('price') or p['productPrices'][0].get('sysPrice') or 0
                    unit = clean_unit(p.get('unit'))
                    barcode = str(p.get('productCode') or "").strip()
                    collected[pid] = {
                        "bhx_id": pid,
                        "name": name.strip(),
                        "category": gname,
                        "unit": unit,
                        "sale_price": float(price) if price else 30000.0,
                        "image_url": avatar,
                        "barcode": barcode
                    }
        time.sleep(0.05)
    print(f"Total after Daily Shopping: {len(collected)}")

    print("\n=== 3. FETCHING BY CATEGORIES ===")
    for cid, cname in CATEGORIES:
        # Fetch page 0 and page 1
        for page in [0, 1]:
            cat_res = make_request("https://api.bachhoaxanh.com/gw/Category/AjaxProduct", {
                "CategoryId": cid, "ProvinceId": 3, "StoreId": 3391, "PageSize": 50, "PageIndex": page
            })
            if cat_res and 'data' in cat_res and isinstance(cat_res['data'], dict):
                prods = cat_res['data'].get('products', [])
                if not prods:
                    break
                for p in prods:
                    pid = p.get('id')
                    name = p.get('fullName') or p.get('name')
                    avatar = p.get('avatar')
                    if pid and name and avatar and pid not in collected:
                        price = 0
                        if p.get('productPrices') and len(p['productPrices']) > 0:
                            price = p['productPrices'][0].get('sysPrice') or p['productPrices'][0].get('price') or 0
                        unit = clean_unit(p.get('unit'))
                        barcode = str(p.get('productCode') or "").strip()
                        collected[pid] = {
                            "bhx_id": pid,
                            "name": name.strip(),
                            "category": cname,
                            "unit": unit,
                            "sale_price": float(price) if price else 35000.0,
                            "image_url": avatar,
                            "barcode": barcode
                        }
            time.sleep(0.08)
        print(f"Category '{cname}' -> Total collected so far: {len(collected)}")

    # Post process: fix prices, cost prices, unique barcodes
    cleaned_products = []
    for pid, p in collected.items():
        name = p['name'][:250]
        # Clean sale price
        sale_price = p['sale_price']
        if sale_price <= 1000:
            sale_price = 15000.0
        # Round to nearest 1,000 VND
        sale_price = round(sale_price / 1000.0) * 1000.0
        
        # Calculate realistic cost price (~75%-80% of sale price, margin 20-25%)
        cost_price = round((sale_price * 0.78) / 1000.0) * 1000.0
        if cost_price >= sale_price:
            cost_price = sale_price - 2000.0
        if cost_price <= 0:
            cost_price = max(1000.0, sale_price - 3000.0)
            
        # Ensure unique valid barcode
        barcode = p['barcode']
        if not barcode or len(barcode) < 6 or barcode in used_barcodes or not barcode.isdigit():
            barcode = generate_ean13(barcode_counter)
            barcode_counter += 1
            
        used_barcodes.add(barcode)
        
        cleaned_products.append({
            "name": name,
            "barcode": barcode,
            "category": p['category'],
            "unit": p['unit'],
            "cost_price": cost_price,
            "sale_price": sale_price,
            "image_url": p['image_url']
        })

    print(f"\n==========================================")
    print(f"TOTAL CLEANED PRODUCTS READY: {len(cleaned_products)}")
    print(f"==========================================")
    return cleaned_products

if __name__ == '__main__':
    prods = fetch_all_bhx_products()
    with open('bhx_products_scraped.json', 'w', encoding='utf-8') as f:
        json.dump(prods, f, ensure_ascii=False, indent=2)
    print("Saved to bhx_products_scraped.json")
