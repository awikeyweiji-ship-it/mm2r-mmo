#!/usr/bin/env python3
import argparse
import struct
import os
import sys
import math
import logging
from datetime import datetime

# Setup logging
timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
log_file = f"logs/b5_render_{timestamp}.log"
logging.basicConfig(filename=log_file, level=logging.INFO, 
                    format='%(asctime)s - %(levelname)s - %(message)s')
console = logging.StreamHandler()
console.setLevel(logging.WARNING) # Only warnings to console to keep output clean
logging.getLogger('').addHandler(console)

def write_png(width, height, pixels, out_path):
    # Minimal PNG writer (RGB888)
    # Using zlib if available, else uncompressed (not recommended but simple)
    # But python usually has zlib.
    import zlib
    
    # 8-bit depth, RGB (color type 2)
    # Header
    png_signature = b'\x89PNG\r\n\x1a\n'
    ihdr_data = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    ihdr_crc = zlib.crc32(b'IHDR' + ihdr_data) & 0xffffffff
    ihdr = struct.pack(">I", len(ihdr_data)) + b'IHDR' + ihdr_data + struct.pack(">I", ihdr_crc)
    
    # IDAT
    # Scanlines: Filter byte (0) + R, G, B, R, G, B...
    raw_data = bytearray()
    for y in range(height):
        raw_data.append(0) # No filter
        for x in range(width):
            if y < len(pixels) and x < len(pixels[y]):
                r, g, b = pixels[y][x]
            else:
                r, g, b = 0, 0, 0
            raw_data.extend((r, g, b))
            
    compressed = zlib.compress(raw_data)
    idat_crc = zlib.crc32(b'IDAT' + compressed) & 0xffffffff
    idat = struct.pack(">I", len(compressed)) + b'IDAT' + compressed + struct.pack(">I", idat_crc)
    
    # IEND
    iend_data = b''
    iend_crc = zlib.crc32(b'IEND' + iend_data) & 0xffffffff
    iend = struct.pack(">I", len(iend_data)) + b'IEND' + iend_data + struct.pack(">I", iend_crc)
    
    with open(out_path, 'wb') as f:
        f.write(png_signature)
        f.write(ihdr)
        f.write(idat)
        f.write(iend)

def bgr555_to_rgb888(val):
    b = (val >> 10) & 0x1F
    g = (val >> 5) & 0x1F
    r = val & 0x1F
    
    # Expand 5-bit to 8-bit (x * 8 + x / 4 approx, or x << 3 | x >> 2)
    r = (r << 3) | (r >> 2)
    g = (g << 3) | (g >> 2)
    b = (b << 3) | (b >> 2)
    return (r, g, b)

def parse_rlcn(data):
    # Try to find PLTT chunk
    logging.info(f"Parsing RLCN, size {len(data)}")
    palette = [(0,0,0)] * 256 # Default black
    
    # Scan for 'TTLP' (PLTT backwards in LE?) or 'PLTT'
    # NDS chunks usually have magic
    
    offset = 0
    while offset < len(data) - 4:
        chunk_magic = data[offset:offset+4]
        if chunk_magic == b'TTLP' or chunk_magic == b'PLTT':
            logging.info(f"Found PLTT at {offset}")
            # Chunk size at +4
            chunk_size = struct.unpack('<I', data[offset+4:offset+8])[0]
            # Data starts at +8? Or header size?
            # Standard NDS: Magic(4), Size(4), ... data
            # PLTT usually has header: unknown(4), data_size(4)?
            # Let's assume data starts at offset+16 or try to heuristic
            
            # Read colors
            # Try to read 256 colors (512 bytes)
            p_start = offset + 16 # Skip chunk header
            p_len = min(512, len(data) - p_start)
            
            for i in range(p_len // 2):
                val = struct.unpack('<H', data[p_start + i*2 : p_start + i*2 + 2])[0]
                palette[i] = bgr555_to_rgb888(val)
            return palette
        offset += 4
        
    # Fallback: Read last 512 bytes
    logging.warning("PLTT not found, using fallback")
    p_start = max(0, len(data) - 512)
    for i in range((len(data) - p_start) // 2):
        val = struct.unpack('<H', data[p_start + i*2 : p_start + i*2 + 2])[0]
        palette[i] = bgr555_to_rgb888(val)
        
    return palette

def parse_rgcn(data):
    logging.info(f"Parsing RGCN, size {len(data)}")
    tiles = [] # List of 8x8 tiles (each is 64 bytes for 8bpp, or 32 bytes for 4bpp)
    
    # Scan for 'RAHC' (CHAR)
    offset = 0
    tile_data_start = -1
    
    while offset < len(data) - 4:
        if data[offset:offset+4] == b'RAHC' or data[offset:offset+4] == b'CHAR':
            logging.info(f"Found CHAR at {offset}")
            # Chunk size at +4
            # Header usually 0x18 bytes or so?
            # Tile data follows
            tile_data_start = offset + 32 # Skip header roughly
            break
        offset += 4
        
    if tile_data_start == -1:
        tile_data_start = 64 # Guess
        
    # Assume 4bpp (32 bytes per tile)
    # Read as many tiles as possible
    bpp = 4
    bytes_per_tile = 32
    
    curr = tile_data_start
    while curr + bytes_per_tile <= len(data):
        tile_bytes = data[curr:curr+bytes_per_tile]
        # Decode 4bpp to index map (8x8)
        # 4bpp: each byte is 2 pixels. Low nibble = p1? Or high? 
        # GBA/NDS: usually low nibble = left pixel (p0), high = right (p1)?
        # Actually it's p0 = byte & 0xF, p1 = byte >> 4.
        
        t_pixels = []
        for b in tile_bytes:
            p0 = b & 0xF
            p1 = (b >> 4) & 0xF
            t_pixels.append(p0)
            t_pixels.append(p1)
            
        tiles.append(t_pixels)
        curr += bytes_per_tile
        
    logging.info(f"Parsed {len(tiles)} tiles (4bpp assumption)")
    return tiles

def parse_rcsn(data):
    logging.info(f"Parsing RCSN, size {len(data)}")
    width = 32
    height = 24
    map_data = []
    
    # Scan for 'RCSN' (SCRN)
    offset = 0
    scrn_found = False
    
    while offset < len(data) - 4:
        if data[offset:offset+4] == b'NRCS' or data[offset:offset+4] == b'SCRN':
            logging.info(f"Found SCRN at {offset}")
            # Header might contain width/height?
            # Usually width/height is fixed for BG unless specified in display control
            # But SCRN chunk has size.
            # Let's assume data follows header.
            map_offset = offset + 16 # Skip
            
            # Read u16 entries
            # Each entry: Tile Index (10 bits), Flip X (1), Flip Y (1), Palette (4)
            
            remaining = len(data) - map_offset
            count = remaining // 2
            
            # Try to deduce width
            # If count == 32*24 (768), then 32x24
            # If count == 32*32 (1024), then 32x32
            
            if count == 1024:
                height = 32
            elif count == 2048:
                width = 64 # or 32x64
                
            for i in range(count):
                val = struct.unpack('<H', data[map_offset + i*2 : map_offset + i*2 + 2])[0]
                tile_idx = val & 0x3FF
                pal_idx = (val >> 12) & 0xF
                flip_h = (val >> 10) & 1
                flip_v = (val >> 11) & 1
                map_data.append({
                    'tile': tile_idx,
                    'pal': pal_idx,
                    'fh': flip_h,
                    'fv': flip_v
                })
            scrn_found = True
            break
        offset += 4
        
    if not scrn_found:
        logging.warning("SCRN chunk not found, generating dummy map")
        # Dummy linear map
        for i in range(width * height):
            map_data.append({'tile': i, 'pal': 0, 'fh': 0, 'fv': 0})
            
    return width, height, map_data

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--rgcn", required=True)
    parser.add_argument("--rlcn", required=True)
    parser.add_argument("--rcsn", required=False) # Optional
    parser.add_argument("--out", required=True)
    args = parser.parse_args()
    
    try:
        with open(args.rgcn, 'rb') as f: rgcn_data = f.read()
        with open(args.rlcn, 'rb') as f: rlcn_data = f.read()
        rcsn_data = None
        if args.rcsn and os.path.exists(args.rcsn):
            with open(args.rcsn, 'rb') as f: rcsn_data = f.read()
            
        palette = parse_rlcn(rlcn_data)
        tiles = parse_rgcn(rgcn_data)
        
        if rcsn_data:
            map_w, map_h, tile_map = parse_rcsn(rcsn_data)
        else:
            # Default map if no RCSN
            map_w, map_h = 32, 24
            tile_map = [{'tile': i % len(tiles), 'pal': 0, 'fh': 0, 'fv': 0} for i in range(map_w*map_h)]
            
        # Render
        # Output image dimensions
        out_w = map_w * 8
        out_h = map_h * 8
        pixels = [[(0,0,0) for _ in range(out_w)] for _ in range(out_h)]
        
        for i, entry in enumerate(tile_map):
            if i >= len(tile_map): break
            
            tx = (i % map_w) * 8
            ty = (i // map_w) * 8
            
            t_idx = entry['tile']
            # pal_bank = entry['pal'] # Not implementing palette banking for 4bpp yet, assume 0
            
            if t_idx < len(tiles):
                tile_pixels = tiles[t_idx] # 64 indices
                for py in range(8):
                    for px in range(8):
                        c_idx = tile_pixels[py * 8 + px]
                        # 0 is transparent usually, but we render black or bg color
                        # Palette lookup
                        color = palette[c_idx] if c_idx < len(palette) else (255, 0, 255)
                        
                        # Handle flips (simple)
                        dest_x = tx + (7 - px if entry['fh'] else px)
                        dest_y = ty + (7 - py if entry['fv'] else py)
                        
                        if dest_y < out_h and dest_x < out_w:
                            pixels[dest_y][dest_x] = color
            else:
                # Missing tile, red placeholder
                pass
                
        write_png(out_w, out_h, pixels, args.out)
        logging.info(f"Rendered to {args.out}")
        print(f"Rendered: {args.out} ({out_w}x{out_h})")
        
    except Exception as e:
        logging.error(f"Render failed: {e}", exc_info=True)
        # Create a dummy failure image
        write_png(32, 32, [[(255,0,0)]*32]*32, args.out)
        print(f"Render failed but created fallback: {args.out}")

if __name__ == "__main__":
    main()
