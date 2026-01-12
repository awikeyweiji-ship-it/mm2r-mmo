#!/usr/bin/env python3
import sys
import argparse
import json
import struct
import re

def parse_args():
    parser = argparse.ArgumentParser(description="Probe MM2R PAK structure")
    parser.add_argument("--in", dest="input_file", required=True, help="Input PAK file")
    parser.add_argument("--scan", dest="scan_file", required=True, help="Existing pack_scan.json for reference")
    parser.add_argument("--out", dest="output_file", required=True, help="Output probe JSON")
    return parser.parse_args()

def is_valid_filename(b_str):
    try:
        s = b_str.decode('latin-1')
        if not s: return False
        if not re.match(r'^[A-Za-z0-9]', s): return False
        if not re.match(r'^[A-Za-z0-9._-]+$', s): return False
        if len(s) < 3 or len(s) > 80: return False
        return True
    except:
        return False

def probe_filenames(data, limit=500):
    entries = []
    offset = 0
    max_scan = 4400 # 限制在 4424 之前
    
    current_str_start = -1
    
    i = 0
    # skip header 8 bytes
    i = 8
    
    while i < max_scan and i < len(data):
        if data[i] == 0:
            if current_str_start != -1:
                s_bytes = data[current_str_start:i]
                if is_valid_filename(s_bytes):
                    entries.append({
                        "str": s_bytes.decode('latin-1'),
                        "start": current_str_start,
                        "end": i 
                    })
                current_str_start = -1
        else:
            if current_str_start == -1:
                current_str_start = i
        i += 1
        
    name_end_offset = 0
    if entries:
        name_end_offset = entries[-1]['end'] + 1
        
    return entries, name_end_offset

def find_table_by_target(data, target_offset):
    # Search for target_offset as u32
    target_bytes = struct.pack('<I', target_offset)
    hits = []
    start = 0
    while True:
        idx = data.find(target_bytes, start)
        if idx == -1: break
        if idx < target_offset: # table usually before data
            hits.append(idx)
        start = idx + 1
    return hits

def probe_entries_around_hit(data, hit_offset, file_size):
    # Assume hit_offset is where 'offset' is stored.
    # Try to determine pattern.
    # Pattern A: offset, size (8 bytes)
    # Pattern B: offset, end (8 bytes)
    # Pattern C: offset only (4 bytes, implied size) - contiguous?
    
    # Check +4
    if hit_offset + 8 > len(data): return None
    
    val1 = struct.unpack('<I', data[hit_offset:hit_offset+4])[0] # should be target_offset
    val2 = struct.unpack('<I', data[hit_offset+4:hit_offset+8])[0]
    
    mode = "unknown"
    size = 0
    
    # Try offset, size
    if val2 > 0 and val2 < (file_size - val1):
        # looks like size
        mode = "offset_size"
        size = val2
        
    # Try offset, end
    if val2 > val1 and val2 <= file_size:
        # looks like end
        # But wait, if files are contiguous, end of this = start of next?
        # Or start, end pair.
        mode = "start_end"
        size = val2 - val1
        
    return mode

def extract_entries(data, table_start, mode, count, file_size):
    entries = []
    stride = 8 if mode in ["offset_size", "start_end"] else 4
    
    for i in range(count):
        pos = table_start + i * stride
        if pos + stride > len(data): break
        
        off = struct.unpack('<I', data[pos:pos+4])[0]
        size = 0
        
        if mode == "offset_size":
            size = struct.unpack('<I', data[pos+4:pos+8])[0]
        elif mode == "start_end":
            end = struct.unpack('<I', data[pos+4:pos+8])[0]
            size = end - off
        
        # Validation
        magic = "UNK"
        if off < file_size and size > 0 and off + size <= file_size:
            head = data[off:off+4]
            try:
                 if all(32 <= b <= 126 for b in head):
                    magic = head.decode('latin-1')
                 else:
                    magic = head.hex()
            except:
                pass
                
        entries.append({
            "index": i,
            "offset": off,
            "size": size,
            "mode": mode,
            "magic": magic
        })
        
    return entries

def main():
    args = parse_args()
    
    with open(args.input_file, 'rb') as f:
        data = f.read()
        
    # 1. Probe names
    names, name_end = probe_filenames(data)
    
    # 2. Find anchor: RGCN at 4424
    anchor_offset = 4424
    # Verify anchor
    if data[anchor_offset:anchor_offset+4] != b'RGCN':
        # Search for first RGCN
        idx = data.find(b'RGCN')
        if idx != -1:
            anchor_offset = idx
        else:
            print("RGCN anchor not found")
            anchor_offset = -1
            
    table_hits = []
    if anchor_offset != -1:
        table_hits = find_table_by_target(data, anchor_offset)
        
    mode = "unknown"
    table_start_guess = -1
    final_entries = []
    
    if table_hits:
        # 假设命中点是某个 entry 的 offset 字段
        # 我们可以尝试向前推导 table start
        # 假设 table 紧接在 name_end 之后，并对齐
        
        # 既然我们有 names 数量，假设 entry 数量 = name 数量
        # 如果 hit 是第 k 个文件的 offset
        # 我们可以尝试匹配。
        
        # 简单起见，我们取最后一个 hit（通常如果只有一个）或者最像在 table 区域的 hit
        # The hit should be after name_end
        valid_hits = [h for h in table_hits if h >= name_end]
        
        if valid_hits:
            hit = valid_hits[0]
            # Detect mode at hit
            mode = probe_entries_around_hit(data, hit, len(data))
            
            if mode != "unknown":
                stride = 8
                # Backtrack to find table start based on name count
                # Assuming the RGCN file corresponds to one of the names.
                # Which one?
                # This is tricky. 
                # Let's assume table starts at name_end aligned to 4
                table_start_guess = (name_end + 3) & ~3
                
                # Verify if table_start_guess leads to a valid structure
                # We can just read from table_start_guess
                final_entries = extract_entries(data, table_start_guess, mode, len(names), len(data))

    result = {
        "file_size": len(data),
        "name_count": len(names),
        "name_end_offset": name_end,
        "table_start_guess": table_start_guess,
        "entry_mode_guess": mode,
        "entries": final_entries,
        "debug_hits": table_hits
    }
    
    with open(args.output_file, 'w') as f:
        json.dump(result, f, indent=2)

    print(f"Names: {len(names)}, End: {name_end}, Table: {table_start_guess}, Mode: {mode}, Entries: {len(final_entries)}")

if __name__ == "__main__":
    main()
