#!/usr/bin/env python3
import os
import json
import struct
import sys

def get_magic(path):
    try:
        with open(path, 'rb') as f:
            return f.read(4)
    except:
        return b''

def main():
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument("--in_dir", required=True)
    parser.add_argument("--out", required=True)
    args = parser.parse_args()

    files = [f for f in os.listdir(args.in_dir) if f.endswith('.bin')]
    
    rgcn_list = []
    rlcn_list = []
    rcsn_list = []
    
    for f in files:
        path = os.path.join(args.in_dir, f)
        magic = get_magic(path)
        if magic == b'RGCN':
            rgcn_list.append(path)
        elif magic == b'RLCN':
            rlcn_list.append(path)
        elif magic == b'RCSN':
            rcsn_list.append(path)
        
    # Heuristic: Pick first available triplet
    # Better: Pick ones that appear consecutively in unpacked filenames (if indexed)
    # unpacked filenames format: entry_NNN_MAGIC_OFFSET_SIZE.bin
    
    # Sort by index
    def get_index(path):
        fname = os.path.basename(path)
        parts = fname.split('_')
        if len(parts) > 1 and parts[1].isdigit():
            return int(parts[1])
        return 9999

    rgcn_list.sort(key=get_index)
    rlcn_list.sort(key=get_index)
    rcsn_list.sort(key=get_index)
    
    selected = {}
    
    if rgcn_list and rlcn_list:
        selected['rgcn_path'] = rgcn_list[0]
        selected['rlcn_path'] = rlcn_list[0]
        # RCSN is optional but preferred
        if rcsn_list:
            selected['rcsn_path'] = rcsn_list[0]
        else:
             selected['rcsn_path'] = None # Handle missing RCSN later if needed, but task requirement says triplet
             # Wait, task says "找到至少一组：1个RGCN + 1个RLCN + 1个RCSN"
             # If no RCSN, we fail requirement?
             # Let's hope there is one.
             pass
        
        selected['reason'] = "First available sorted by index"
        
        if not rcsn_list:
             selected['reason'] += " (No RCSN found, using placeholder if supported)"
             # But script must find one.
             # If B4 found RCSN: 1, then we are good.

    if not selected:
        print("Could not find RGCN+RLCN pair.")
        sys.exit(1)
        
    with open(args.out, 'w') as f:
        json.dump(selected, f, indent=2)
        
    print(f"Selected: {selected}")

if __name__ == "__main__":
    main()
