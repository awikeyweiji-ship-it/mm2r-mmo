import argparse
import os
import struct
import json

def main():
    parser = argparse.ArgumentParser(description='Extract slices from pack based on magic offsets.')
    parser.add_argument('--in', dest='input_file', required=True, help='Input pack file')
    parser.add_argument('--out_dir', required=True, help='Output directory for slices')
    args = parser.parse_args()

    input_path = args.input_file
    out_dir = args.out_dir
    
    if not os.path.exists(out_dir):
        os.makedirs(out_dir)

    # Magic list we care about
    target_magics = ["NFTR", "NSCR", "RGCN", "RLCN", "RCSN"]
    
    # Read pack_scan.json to get offsets
    # Assumption: pack_scan.json is in the same dir as the pack or a known location. 
    # For this task, we can try to infer it or re-scan if needed.
    # But based on instructions, we should rely on the previous step's output if possible?
    # Actually, the instructions say "Step 1... 3) 对每个 offset". It implies we have a list of offsets.
    # We will read contentpacks/poc/pack_scan.json as generated in Step 0.
    
    scan_file = 'contentpacks/poc/pack_scan.json'
    if not os.path.exists(scan_file):
        print(f"Error: {scan_file} not found. Please run Step 0 first.")
        return

    with open(scan_file, 'r') as f:
        scan_data = json.load(f)

    with open(input_path, 'rb') as f:
        file_content = f.read()
        file_len = len(file_content)

    slices_index = []
    
    # Group offsets by magic to help finding next magic
    # Actually scan_data is sorted by offset already (from Step 0 code)
    sorted_offsets = sorted(scan_data, key=lambda x: x['offset'])
    
    for i, item in enumerate(sorted_offsets):
        magic = item['magic']
        offset = item['offset']
        
        if magic not in target_magics:
            continue
            
        # Verify magic
        f_magic = file_content[offset:offset+4].decode('ascii', errors='ignore')
        if f_magic != magic:
            print(f"Warning: Magic mismatch at {offset}, expected {magic}, got {f_magic}. Skipping.")
            continue
            
        # Determine size
        # Method A: Read u32 at offset+4
        size = -1
        method = "unknown"
        
        if offset + 8 <= file_len:
            size_candidate = struct.unpack('<I', file_content[offset+4:offset+8])[0]
            # Validate candidate
            if size_candidate > 0 and offset + size_candidate <= file_len:
                # Basic sanity check: size shouldn't be too small for these formats (usually header > 16)
                if size_candidate > 16:
                    size = size_candidate
                    method = "u32"
        
        # Method B: Search for next magic
        if size == -1: # Method A failed or invalid
            next_offset = file_len
            # Find the next known magic in our sorted list
            if i + 1 < len(sorted_offsets):
                next_offset = sorted_offsets[i+1]['offset']
            
            # If the calculated size is valid
            size_candidate = next_offset - offset
            if size_candidate > 16:
                size = size_candidate
                method = "next_magic"
            else:
                 # Fallback: maybe just take till end if it's the last one?
                 if i + 1 == len(sorted_offsets) and file_len - offset > 16:
                     size = file_len - offset
                     method = "to_end"
                 else:
                     print(f"Warning: Could not determine valid size for {magic} at {offset}. Skipping.")
                     continue

        # Final write
        out_filename = f"{magic}_{offset}_{size}.bin"
        out_path = os.path.join(out_dir, out_filename)
        
        with open(out_path, 'wb') as out_f:
            out_f.write(file_content[offset:offset+size])
            
        slices_index.append({
            "magic": magic,
            "offset": offset,
            "size": size,
            "path": out_path,
            "method": method
        })
        print(f"Extracted {magic} at {offset}, size={size} ({method}) -> {out_filename}")

    # Write index
    index_path = os.path.join(out_dir, 'index.json')
    with open(index_path, 'w') as f:
        json.dump(slices_index, f, indent=2)
    print(f"Index written to {index_path}")

if __name__ == "__main__":
    main()
