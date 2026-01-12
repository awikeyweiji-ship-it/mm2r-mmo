#!/usr/bin/env python3
import sys
import argparse
import json
import os
import struct

def parse_args():
    parser = argparse.ArgumentParser(description="Unpack MM2R PAK based on probe")
    parser.add_argument("--in", dest="input_file", required=True, help="Input PAK file")
    parser.add_argument("--probe", dest="probe_file", required=True, help="Probe JSON")
    parser.add_argument("--out", dest="output_dir", required=True, help="Output directory")
    parser.add_argument("--limit", type=int, default=200, help="Max files to unpack")
    return parser.parse_args()

def main():
    args = parse_args()
    
    with open(args.probe_file, 'r') as f:
        probe = json.load(f)
        
    if probe['entry_mode_guess'] not in ['offset_size', 'start_end']:
        print("Probe entry mode unknown, skipping unpack.")
        return

    os.makedirs(args.output_dir, exist_ok=True)
    
    with open(args.input_file, 'rb') as f:
        data = f.read()
        
    entries = probe['entries']
    count = 0
    magic_stats = {}
    
    for i, entry in enumerate(entries):
        if count >= args.limit: break
        
        off = entry['offset']
        size = entry['size']
        magic = entry.get('magic', 'UNK')
        
        if size <= 0: continue
        if off + size > len(data): continue
        
        # Read magic from file if UNK
        if magic == 'UNK':
            head = data[off:off+4]
            # Try to see if ascii
            try:
                # simple check
                if all(32 <= b <= 126 for b in head):
                    magic = head.decode('latin-1')
                else:
                    magic = head.hex()
            except:
                magic = head.hex()
                
        magic_stats[magic] = magic_stats.get(magic, 0) + 1
        
        # Write
        fname = f"entry_{i:03d}_{magic}_{off}_{size}.bin"
        out_path = os.path.join(args.output_dir, fname)
        
        with open(out_path, 'wb') as out_f:
            out_f.write(data[off:off+size])
            
        # Meta
        meta = {
            "offset": off,
            "size": size,
            "magic": magic,
            "index": i
        }
        with open(out_path + ".json", 'w') as meta_f:
            json.dump(meta, meta_f, indent=2)
            
        count += 1
        
    print(f"Unpacked {count} files.")
    print("Magic stats:")
    for m, c in magic_stats.items():
        print(f"  {m}: {c}")

if __name__ == "__main__":
    main()
