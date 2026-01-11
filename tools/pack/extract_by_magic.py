#!/usr/bin/env python3
import sys
import struct
import os
import json
import argparse

def ensure_dir(path):
    if not os.path.exists(path):
        os.makedirs(path)

def read_u32(f):
    data = f.read(4)
    if len(data) < 4: return None
    return struct.unpack('<I', data)[0]

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--in', dest='input', required=True)
    parser.add_argument('--out_dir', required=True)
    args = parser.parse_args()

    in_path = args.input
    out_dir = args.out_dir
    ensure_dir(out_dir)

    scan_file = os.path.join(os.path.dirname(in_path), '..', 'pack_scan.json')
    if not os.path.exists(scan_file):
        # Fallback relative to script location or assume standard path
        scan_file = os.path.join(out_dir, '..', 'pack_scan.json')
    
    # Hardcoded scan results if file read fails or logic complex
    # Based on previous step output:
    scan_results = {
        "NFTR": [52],
        "RGCN": [4424],
        "RLCN": [20856, 217456, 218188],
        "RCSN": [218008],
        "NSCR": [] # Not in previous grep output but user mentioned it?
        # Re-checking grep output: only showed first few lines. 
        # User prompt said NSCR was found in B2 summary.
        # Let's trust the file content if possible, else use provided knowledge.
    }
    
    # Try reading the actual json
    try:
        with open(scan_file, 'r') as f:
            data = json.load(f)
            scan_results = data.get('locations', scan_results)
    except:
        print(f"警告：无法读取 {scan_file}，使用默认已知偏移。")

    target_magics = ["NFTR", "NSCR", "RGCN", "RLCN", "RCSN"]
    
    # Flatten all offsets to find "next offset" easily
    all_offsets = []
    for m, offsets in scan_results.items():
        if m in target_magics:
            for o in offsets:
                all_offsets.append(o)
    all_offsets.sort()
    
    file_size = os.path.getsize(in_path)
    slices_index = []

    with open(in_path, 'rb') as f:
        for magic in target_magics:
            offsets = scan_results.get(magic, [])
            for off in offsets:
                f.seek(off)
                check_magic = f.read(4)
                if check_magic != magic.encode('utf-8'):
                    print(f"警告: offset {off} 处魔数 {check_magic} 不匹配 {magic}")
                    continue
                
                # Method A: Read size from offset+4
                f.seek(off + 4)
                # Usually chunk size is offset 4 (u32 LE) in NDS generic formats
                # except sometimes it's header size? Usually full file size.
                size_candidate_bytes = f.read(4)
                size_candidate = struct.unpack('<I', size_candidate_bytes)[0]
                
                method = "u32"
                final_size = 0
                
                # Validation
                if 16 < size_candidate <= (file_size - off):
                    final_size = size_candidate
                else:
                    # Method B: Scan for next known magic
                    method = "next_magic"
                    # Find smallest offset in all_offsets that is > off
                    next_off = file_size
                    for cand_off in all_offsets:
                        if cand_off > off:
                            next_off = cand_off
                            break
                    final_size = next_off - off
                
                # Extract
                out_name = f"{magic}_{off}_{final_size}.bin"
                out_path = os.path.join(out_dir, out_name)
                
                f.seek(off)
                data = f.read(final_size)
                
                with open(out_path, 'wb') as out_f:
                    out_f.write(data)
                
                slices_index.append({
                    "magic": magic,
                    "offset": off,
                    "size": final_size,
                    "path": out_path,
                    "method": method
                })
                print(f"已提取 {magic}: offset={off}, size={final_size} ({method})")

    # Write index
    index_path = os.path.join(out_dir, "index.json")
    with open(index_path, 'w') as jf:
        json.dump(slices_index, jf, indent=2)
    
    print(f"提取完成，索引已写入 {index_path}")

if __name__ == '__main__':
    main()
