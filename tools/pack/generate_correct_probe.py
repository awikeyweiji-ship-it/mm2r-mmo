#!/usr/bin/env python3
import json
import struct
import os

def main():
    pak_path = "contentpacks/poc/raw/pack_data.pak"
    out_path = "contentpacks/poc/pak_probe.json"
    
    with open(pak_path, 'rb') as f:
        data = f.read()
        
    table_start = 288
    entry_size = 8
    entries = []
    
    # Format: Size, Offset
    
    for i in range(16):
        pos = table_start + i * entry_size
        if pos + 8 > len(data): break
        
        # Swapped: first 4 bytes size, next 4 bytes offset
        size = struct.unpack('<I', data[pos:pos+4])[0]
        off = struct.unpack('<I', data[pos+4:pos+8])[0]
        
        magic = "UNK"
        if off < len(data) and size > 0:
            head = data[off:off+4]
            try:
                # Try to decode as ascii if possible
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
            "mode": "size_offset",
            "magic": magic
        })

    result = {
        "file_size": len(data),
        "name_count": 13,
        "name_end_offset": 184,
        "table_start_guess": table_start,
        "entry_mode_guess": "offset_size", 
        "entries": entries,
        "note": "Manually corrected table start & swapped (Size, Offset)"
    }
    
    with open(out_path, 'w') as f:
        json.dump(result, f, indent=2)
    
    print(f"Generated probe json with {len(entries)} entries.")

if __name__ == "__main__":
    main()
