#!/usr/bin/env python3
"""生成 App 启动图标（无需 PIL，纯 zlib/struct 手写 PNG）。
图标：深蓝圆角方块 + 白色镜头外圈 + 深色镜头 + 红色录制点
"""
import os, struct, zlib

SIZES = {"mipmap-mdpi": 48, "mipmap-hdpi": 72, "mipmap-xhdpi": 96,
         "mipmap-xxhdpi": 144, "mipmap-xxxhdpi": 192}

def png_chunk(tag, data):
    c = struct.pack(">I", len(data)) + tag + data
    return c + struct.pack(">I", zlib.crc32(tag + data) & 0xffffffff)

def write_png(path, size, px):
    raw = b""
    for y in range(size):
        raw += b"\x00"  # filter type 0
        for x in range(size):
            raw += bytes(px[y][x])
    ihdr = struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0)
    png = b"\x89PNG\r\n\x1a\n"
    png += png_chunk(b"IHDR", ihdr)
    png += png_chunk(b"IDAT", zlib.compress(raw, 9))
    png += png_chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(png)

def make_icon(size):
    # 渐变背景
    def bg(x, y):
        t = y / max(size - 1, 1)
        r = int(30 + 15 * t)
        g = int(58 + 40 * t)
        b = int(138 + 60 * t)
        return (r, g, b, 255)
    def in_round(x, y, rad):
        if x < rad and y < rad:
            return (x - rad) ** 2 + (y - rad) ** 2 <= rad * rad
        if x >= size - rad and y < rad:
            return (x - (size - rad)) ** 2 + (y - rad) ** 2 <= rad * rad
        if x < rad and y >= size - rad:
            return (x - rad) ** 2 + (y - (size - rad)) ** 2 <= rad * rad
        if x >= size - rad and y >= size - rad:
            return (x - (size - rad)) ** 2 + (y - (size - rad)) ** 2 <= rad * rad
        return True
    rad = size * 0.20
    cx, cy = size * 0.50, size * 0.50
    r_outer = size * 0.32
    r_inner = size * 0.22
    r_dot = size * 0.055
    dx_dot, dy_dot = size * 0.16, size * 0.16
    px = []
    for y in range(size):
        row = []
        for x in range(size):
            if not in_round(x + 0.5, y + 0.5, rad):
                row.append((0, 0, 0, 0))
                continue
            d = ((x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2) ** 0.5
            ddot = ((x + 0.5 - (cx - dx_dot)) ** 2 + (y + 0.5 - (cy - dy_dot)) ** 2) ** 0.5
            if ddot <= r_dot:
                row.append((239, 68, 68, 255))          # 红点
            elif d <= r_inner:
                row.append((15, 23, 42, 255))           # 镜头内圈深色
            elif d <= r_outer:
                row.append((226, 232, 240, 255))        # 镜头白圈
            else:
                row.append(bg(x, y))
        px.append(row)
    return px

def main():
    out_root = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "res")
    for d, s in SIZES.items():
        path = os.path.join(out_root, d, "ic_launcher.png")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        write_png(path, s, make_icon(s))
        print("wrote", path)

if __name__ == "__main__":
    main()
