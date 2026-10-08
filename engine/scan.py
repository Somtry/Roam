#!/usr/bin/env python3
"""
Roam Travel Memory Engine — 检测原型 (Python)
未来将移植为纯 Kotlin 模块(不依赖 android.*)。

管道: 扫描 → 元数据 → 正片过滤 → 日级聚合 → Burst 聚类 → Trip 候选 → 置信度
降级: 优先 EXIF DateTimeOriginal; 缺失则解析文件名时间戳 (IMG_/MVIMG_YYYYMMDD_HHMMSS)
原则: 保守优先 —— 拿不准就折叠, 不抢首页 C 位
"""

import json
import re
import subprocess
import sys
from datetime import datetime, timedelta
from pathlib import Path

# ---------------- 参数 (V0.1 规格, 与产品二稿 §7.2 一致) ----------------
MIN_WIDTH = 1000          # 正片最小宽度: 过滤截图/表情包
BURST_GAP_MIN = 20        # 间隔 <= 20min 的连拍归入同一 burst
TRIP_MIN_DAYS = 2         # Trip 需要连续 >= 2 天活动
SUSPECT_DAYS_GAP = 1      # 候选段内允许的最大日间隔
GPS_HOME_MIN_KM = 150     # 距常住地 > 150km 才算异地
FOLDOUT_MIN_SHOTS = 5     # 单日连拍 >= 5 张才够格进"可能是旅行"折叠区

# 常住地 (单机主语料: 由机主申报或近12月密度簇推断; 冒烟测试先硬编码)
HOME = None  # e.g. (30.27, 120.15) 杭州; None = 未知, 距离判定退化为有GPS即异地证据

EARTH_R_KM = 6371.0


def haversine_km(a, b):
    """两坐标球面距离(km)。a/b = (lat, lon)。"""
    from math import radians, sin, cos, asin, sqrt
    lat1, lon1, lat2, lon2 = map(radians, (a[0], a[1], b[0], b[1]))
    dlat, dlon = lat2 - lat1, lon2 - lon1
    h = sin(dlat / 2) ** 2 + cos(lat1) * cos(lat2) * sin(dlon / 2) ** 2
    return 2 * EARTH_R_KM * asin(min(1.0, sqrt(h)))


def stay_points(day_items):
    """把一天内的照片聚成停留点(stay point): 距上一锚点 <= 200m 归同点。"""
    anchor, pts = None, []
    for p in day_items:
        if not p['gps']:
            continue
        if anchor and haversine_km(anchor, p['gps']) <= 0.2:
            pts[-1]['photos'] += 1
        else:
            anchor = p['gps']
            pts.append({'gps': p['gps'], 'photos': 1, 't': p['dt']})
    return pts

FILENAME_TS = re.compile(r'(?:MV)?IMG[_-](\d{8})[_-](\d{6})', re.I)


def sh(*args):
    return subprocess.run(args, capture_output=True, text=True, check=True).stdout


def parse_shoot_time(meta):
    """拍摄时间: EXIF 优先, 文件名兜底, SubSecTimeOriginal 补毫秒。"""
    dt = None
    source = None
    exif_dt = meta.get('DateTimeOriginal')
    if exif_dt:
        try:
            dt = datetime.strptime(exif_dt, '%Y:%m:%d %H:%M:%S')
            source = 'EXIF'
        except ValueError:
            pass
    if dt is None:
        m = FILENAME_TS.search(meta.get('FileName') or '')
        if m:
            try:
                dt = datetime.strptime(m.group(1) + m.group(2), '%Y%m%d%H%M%S')
                source = 'Filename'
            except ValueError:
                pass
    if dt and meta.get('SubSecTimeOriginal'):
        try:  # SubSec 是字符串, 补上微秒, 用于秒内排序
            dt = dt.replace(microsecond=int(str(meta['SubSecTimeOriginal'])[:6].ljust(6, '0')))
        except (ValueError, TypeError):
            pass
    return dt, source


def parse_gps(meta):
    lat, lon = meta.get('GPSLatitude'), meta.get('GPSLongitude')
    if isinstance(lat, (int, float)) and isinstance(lon, (int, float)) and (lat or lon):
        return float(lat), float(lon)
    return None


def scan(folder):
    out = sh('exiftool', '-json', '-r', '-n', '-DateTimeOriginal', '-SubSecTimeOriginal',
             '-GPSLatitude', '-GPSLongitude', '-Make', '-Model', '-ImageWidth', '-ImageHeight',
             '-FileName', str(folder))
    photos = []
    for m in json.loads(out or '[]'):
        dt, src = parse_shoot_time(m)
        photos.append({
            'file': m.get('FileName', '?'),
            'dt': dt, 't_source': src,
            'gps': parse_gps(m),
            'camera': (m.get('Make'), m.get('Model')),
            'w': m.get('ImageWidth'), 'h': m.get('ImageHeight'),
            'valid': bool(m.get('Make') and m.get('Model')
                          and (m.get('ImageWidth') or 0) >= MIN_WIDTH),
        })
    return photos


# ---------------- 聚类 ----------------

def cluster_bursts(items):
    """同日内间隔 <= BURST_GAP 的归入同 burst。"""
    bursts, cur = [], None
    for p in items:
        if cur and (p['dt'] - cur[-1]['dt']) <= timedelta(minutes=BURST_GAP_MIN):
            cur.append(p)
        else:
            cur = [p]
            bursts.append(cur)
    return bursts


def detect(photos):
    valid = [p for p in photos if p['valid'] and p['dt']]
    dropped = len(photos) - len(valid)
    by_day = {}
    for p in valid:
        by_day.setdefault(p['dt'].date(), []).append(p)
    days = sorted(by_day)

    # 日级聚合
    day_stats = []
    for d in days:
        items = sorted(by_day[d], key=lambda x: x['dt'])
        bursts = cluster_bursts(items)
        day_stats.append({'day': d, 'n': len(items), 'bursts': bursts,
                          'span': (items[0]['dt'], items[-1]['dt']),
                          't_sources': {x['t_source'] for x in items},
                          'has_gps': sum(1 for x in items if x['gps'])})

    # Trip 候选: 连续 >= TRIP_MIN_DAYS 天的活动段
    candidates, i = [], 0
    while i < len(day_stats):
        j = i
        while j + 1 < len(day_stats) and \
                (day_stats[j + 1]['day'] - day_stats[j]['day']).days <= SUSPECT_DAYS_GAP + 1:
            j += 1
        seg = day_stats[i:j + 1]
        n_days = (seg[-1]['day'] - seg[0]['day']).days + 1
        active = len(seg)
        shots = sum(s['n'] for s in seg)
        gps_hits = sum(s['has_gps'] for s in seg)
        gps_ratio = gps_hits / shots if shots else 0
        # GPS 距常住地: 该段照片距 HOME 的最大距离
        max_home_km, why = 0.0, ''
        if HOME and gps_hits:
            km = max(haversine_km(HOME, p['gps']) for s in seg for p in
                     [x for x in by_day[s['day']] if x['gps']] for _ in [0])
            max_home_km = km
        is_away = (HOME is None and gps_hits > 0) or \
                  (HOME is not None and max_home_km > GPS_HOME_MIN_KM)
        if active >= TRIP_MIN_DAYS and is_away:
            conf = min(1.0, 0.4 * min(active / TRIP_MIN_DAYS, 2.5) + 0.4 * gps_ratio + 0.2)
            why = f"距常住地 {max_home_km:.0f} km" if HOME else "有GPS但常住地未知,以异地为前提"
            candidates.append({'type': 'TRIP', 'days': seg, 'n_days': n_days,
                               'active': active, 'shots': shots,
                               'conf': conf, 'gps_ratio': gps_ratio,
                               'locatable': gps_hits > 0, 'why': why})
        else:
            # 单日活动 → 折叠区候选 (Possible Trips)
            for s in seg:
                top = max(len(b) for b in s['bursts'])
                if s['n'] >= FOLDOUT_MIN_SHOTS or top >= FOLDOUT_MIN_SHOTS:
                    reason = '单日活动无跨天证据' if active < TRIP_MIN_DAYS else '有跨天但GPS不足以判异地'
                    candidates.append({'type': 'POSSIBLE', 'days': [s], 'n_days': 1,
                                       'active': 1, 'shots': s['n'],
                                       'conf': 0.3, 'gps_ratio': 0,
                                       'locatable': gps_hits > 0, 'why': reason + (
                                           f" · {max_home_km:.0f}km" if HOME else '')})
        i = j + 1
    return valid, dropped, day_stats, candidates


# ---------------- 报告 ----------------

def report(photos, valid, dropped, day_stats, candidates):
    L = []
    add = L.append
    add('Roam Engine v0.1-proto · 扫描报告')
    add('=' * 58)
    add(f"扫描照片      : {len(photos)} 张")
    add(f"正片(通过过滤) : {len(valid)} 张   [截图/表情包/无相机字段 剔除 {dropped} 张]")
    t_exif = sum(1 for p in valid if p['t_source'] == 'EXIF')
    t_fn = sum(1 for p in valid if p['t_source'] == 'Filename')
    g = sum(1 for p in valid if p['gps'])
    add(f"时间来源      : EXIF {t_exif} 张 | 文件名兜底 {t_fn} 张")
    add(f"GPS 信号      : {g} 张 (无 GPS → 降级路径: 时间聚类)")
    add('')
    add('— 日级聚合 —')
    for s in day_stats:
        b = ' '.join(str(len(x)) for x in s['bursts'])
        pts = stay_points(sorted(by_day[s['day']], key=lambda x: x['dt'])) if False else None
        add(f"  {s['day']}  {s['n']:>3} 张  {s['span'][0].strftime('%H:%M')}—{s['span'][1].strftime('%H:%M')}"
            f"  bursts[{b}]  含GPS {s['has_gps']}")
    add('')
    trips = [c for c in candidates if c['type'] == 'TRIP']
    poss = [c for c in candidates if c['type'] == 'POSSIBLE']
    add('=' * 58)
    add(f"We found {len(trips)} trips." if trips else
        "We found 0 trips.  (证据不足, 引擎选择保守)")
    for c in trips:
        d0, d1 = c['days'][0]['day'], c['days'][-1]['day']
        loc = '可定位' if c['locatable'] else '不可定位(无GPS)'
        add(f"  TRIP  {d0} → {d1}  {c['active']}个活动日 / 跨度{c['n_days']}天"
            f"  {c['shots']}张  置信度 {c['conf']:.0%}  {loc}  {c.get('why','')}")
    if poss:
        add('')
        add(f"可能是旅行 ({len(poss)}) → 折叠区, 不上首页 C 位")
        for c in poss:
            s = c['days'][0]
            span = s['span']
            add(f"  POSSIBLE  {s['day']}  {span[0].strftime('%H:%M')}—{span[1].strftime('%H:%M')}"
                f"  {c['shots']}张  {c.get('why','')}")
    add('=' * 58)
    return '\n'.join(L)


def main(folder):
    photos = scan(folder)
    valid, dropped, day_stats, candidates = detect(photos)
    print(report(photos, valid, dropped, day_stats, candidates))


if __name__ == '__main__':
    folder = sys.argv[1] if len(sys.argv) > 1 else 'data/沉水'
    main(folder)
