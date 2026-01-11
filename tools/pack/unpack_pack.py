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

def read_u32_at(f, offset):
    pos = f.tell()
    f.seek(offset)
    val = read_u32(f)
    f.seek(pos)
    return val

def try_unpack_narc(f, out_dir, limit):
    # Minimal NARC implementation placeholder
    # In a full impl, we would parse FATB, FNTB, FIMG
    # For this phase, we just check magic.
    f.seek(0)
    magic = f.read(4)
    if magic != b'NARC':
        return False
    
    print("Detected NARC format. (Full parser not implemented in this minimal script, skipping to scan/guess for safety)")
    return False 

def try_table_guess(f, file_size, out_dir, limit):
    f.seek(0)
    count_candidate = read_u32(f)
    if count_candidate is None: return False
    
    # Heuristic: File count reasonable?
    if not (1 <= count_candidate <= 20000):
        return False
        
    entries = []
    
    # Try Format A: Offset (u32), Size (u32) -> 8 bytes per entry
    # Try Format B: Start (u32), End (u32) -> 8 bytes per entry
    
    # Let's check Format A first
    # Validation: Offsets must be increasing? Not necessarily, but usually data follows the table.
    # Stronger check: 
    #   offset[0] >= header_size + count * 8
    #   offset[i] + size[i] <= file_size
    
    # Actually, many simple archives are just count + entries.
    table_size = count_candidate * 8
    min_data_start = 4 + table_size
    
    # Read first few entries to validate
    # We'll read all to be safe for the check, but only extract limit.
    
    valid_a = True
    entries_a = []
    
    f.seek(4)
    prev_offset = min_data_start # Expect data to start after table
    
    try:
        for i in range(count_candidate):
            off = read_u32(f)
            sz = read_u32(f)
            if off is None or sz is None:
                valid_a = False; break
            
            # Check basic bounds
            if off < 4: # Offset can't point to count itself usually
                valid_a = False; break
            if off + sz > file_size:
                valid_a = False; break
            
            entries_a.append({'offset': off, 'size': sz, 'id': i})
            
    except:
        valid_a = False
        
    if valid_a:
        # Check overlaps or crazy gaps?
        # Let's just assume valid if bounds were okay.
        # But wait, common format is also Start, End.
        pass

    # If Format A looked suspicious, we could try B.
    # For now, let's proceed with A if it passed basic bounds.
    # BUT, the provided header dump shows "10 00 20 01" -> 0x01200010? 
    # Or "10 00" -> u16?
    # Wait, the hexdump was: 10 00 20 01 00 00 b8 00 44 55 ...
    # 0x00 : 10 00 20 01 -> LE: 0x01200010 (18874384) -> Too big for file count.
    # 0x00 : 10 00 -> u16: 0x0010 (16)?
    
    # Let's re-read the hex provided in step 0.
    # 10 00 20 01 ...
    # Maybe it's not a standard count-first format.
    # If the first u32 is huge, table guess failed.
    
    if count_candidate > 20000:
        return False

    if valid_a and entries_a:
        print(f"Table Guess (Offset/Size) seems valid. Count: {count_candidate}")
        target_dir = os.path.join(out_dir, "table_guess")
        ensure_dir(target_dir)
        
        extracted_count = 0
        for e in entries_a:
            if extracted_count >= limit: break
            
            f.seek(e['offset'])
            data = f.read(e['size'])
            name = f"file_{e['id']:06d}.bin"
            with open(os.path.join(target_dir, name), 'wb') as out_f:
                out_f.write(data)
            extracted_count += 1
        return True

    return False

def scan_signatures(f, file_size, out_dir):
    sigs = [b"NARC", b"BMG", b"BTX0", b"RGCN", b"RLCN", b"RCSN", b"SDAT", b"NFTR", b"NCLR", b"NCGR", b"NSCR"]
    stats = {s.decode(): 0 for s in sigs}
    locations = {s.decode(): [] for s in sigs}
    
    # Reading in chunks
    chunk_size = 65536
    f.seek(0)
    offset = 0
    overlap = 8 # Max sig len is small, 8 is plenty
    
    while True:
        data = f.read(chunk_size)
        if not data: break
        
        for s in sigs:
            s_str = s.decode()
            start = 0
            while True:
                idx = data.find(s, start)
                if idx == -1: break
                
                abs_offset = offset + idx
                # Basic alignment check? Usually 4-byte aligned
                if abs_offset % 4 == 0:
                    stats[s_str] += 1
                    if len(locations[s_str]) < 200:
                        locations[s_str].append(abs_offset)
                
                start = idx + 1
        
        # Move back for overlap
        if len(data) == chunk_size:
            f.seek(f.tell() - overlap)
            offset = f.tell()
        else:
            break
            
    # Filter stats > 0
    final_stats = {k: v for k, v in stats.items() if v > 0}
    
    print("Signature Scan Results:")
    print(json.dumps(final_stats, indent=2))
    
    report = {
        'stats': final_stats,
        'locations': locations
    }
    
    with open(os.path.join(out_dir, '..', 'pack_scan.json'), 'w') as jf:
        json.dump(report, jf, indent=2)
        
    return True

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--in', dest='input', required=True)
    parser.add_argument('--out', required=True)
    parser.add_argument('--limit', type=int, default=200)
    args = parser.parse_args()

    in_path = args.input
    out_dir = args.out
    
    if not os.path.exists(in_path):
        print(f"Error: Input {in_path} missing")
        sys.exit(1)
        
    ensure_dir(out_dir)
    file_size = os.path.getsize(in_path)
    
    with open(in_path, 'rb') as f:
        # 1. Try NARC
        if try_unpack_narc(f, out_dir, args.limit):
            print("Unpacked as NARC")
            sys.exit(0)
            
        # 2. Try Table Guess
        if try_table_guess(f, file_size, out_dir, args.limit):
            print("Unpacked using Table Guess")
            sys.exit(0)
            
        # 3. Signature Scan
        print("Structure unknown. Running signature scan...")
        scan_signatures(f, file_size, out_dir)
        print("Scan complete. Check pack_scan.json")

if __name__ == '__main__':
    main()
