import argparse
import struct
import os
import json
import hashlib
from datetime import datetime

def parse_args():
    parser = argparse.ArgumentParser(description='Extract NitroFS from NDS ROM.')
    parser.add_argument('--rom', required=True, help='Path to NDS ROM file')
    parser.add_argument('--out', required=True, help='Output directory')
    parser.add_argument('--limit', type=int, default=50, help='Max files to extract')
    return parser.parse_args()

def read_uint32(f, offset):
    f.seek(offset)
    return struct.unpack('<I', f.read(4))[0]

def read_uint16(f, offset):
    f.seek(offset)
    return struct.unpack('<H', f.read(2))[0]

def parse_fnt(f, fnt_offset, fnt_size):
    # Read Root Directory Entry
    # The first entry in the Main Table is the Root Directory
    f.seek(fnt_offset)
    sub_table_offset = read_uint32(f, fnt_offset)
    first_file_id = read_uint16(f, fnt_offset + 4)
    total_dirs = read_uint16(f, fnt_offset + 6)

    dirs = []
    # Read all directory table entries
    for i in range(total_dirs):
        entry_offset = fnt_offset + (i * 8)
        sub_off = read_uint32(f, entry_offset)
        first_id = read_uint16(f, entry_offset + 4)
        parent_id = read_uint16(f, entry_offset + 6)
        dirs.append({
            'id': 0xF000 + i, # Root is F000
            'sub_table_offset': sub_off,
            'first_file_id': first_id,
            'parent_id': parent_id if i > 0 else None
        })
    
    # Traverse directory structure to build paths
    # We need to process directories.
    # The FNT structure is a bit complex. 
    # Let's just walk the tree starting from root.
    
    file_map = {} # path -> file_id
    dir_map = {} # dir_id -> path
    
    # Initialize root
    dir_map[0xF000] = ""

    # We need to process directories in order or BFS/DFS.
    # The directory table is indexed by ID (0xF000 + index).
    
    # Let's iterate through the directory table entries we parsed.
    # However, to get names, we must read the sub-tables.
    
    # Queue for processing: (dir_index, current_path)
    # But actually, the sub-tables contain the names of children.
    # So we iterate through each directory's sub-table to find its children.
    
    # Since we have `total_dirs` entries in the table, we can iterate 0 to total_dirs-1.
    # But the names of these directories (except root) are found in their PARENT's sub-table.
    # So we need to process from Root.
    
    # Let's build a hierarchy first.
    # We can read all sub-tables.
    
    files = [] # List of {path, id}
    
    # To resolve paths, we need to know the name of each directory ID.
    # Root is ""
    # We can process directory 0 (Root, 0xF000).
    # It lists files and sub-directories.
    # If we find a sub-directory, we map its ID to (parent_path + name).
    
    queue = [(0, "")] # (index, path)
    processed_dirs = set()
    
    while queue:
        dir_idx, current_path = queue.pop(0)
        if dir_idx in processed_dirs:
            continue
        processed_dirs.add(dir_idx)
        
        dir_entry = dirs[dir_idx]
        sub_table_abs_offset = fnt_offset + dir_entry['sub_table_offset']
        
        f.seek(sub_table_abs_offset)
        
        current_file_id = dir_entry['first_file_id']
        
        while True:
            type_len = ord(f.read(1))
            if type_len == 0x00:
                break
            
            length = type_len & 0x7F
            name = f.read(length).decode('utf-8', errors='replace')
            
            if type_len & 0x80:
                # Directory
                sub_dir_id = struct.unpack('<H', f.read(2))[0]
                sub_dir_idx = sub_dir_id & 0x0FFF
                
                new_path = f"{current_path}/{name}" if current_path else name
                dir_map[sub_dir_id] = new_path
                queue.append((sub_dir_idx, new_path))
            else:
                # File
                file_path = f"{current_path}/{name}" if current_path else name
                files.append({
                    'path': file_path,
                    'id': current_file_id
                })
                current_file_id += 1
                
    return files

def parse_fat(f, fat_offset, fat_size, file_count):
    entries = []
    f.seek(fat_offset)
    # We don't know exact file count from FAT size alone reliably if there's padding,
    # but we can read until end or max known file ID.
    # Actually, we should use the max file ID found in FNT or just read fat_size / 8.
    
    # Better approach: We have the list of file IDs from FNT.
    # We can just look up those IDs.
    pass

def extract(rom_path, out_dir, limit):
    print(f"Processing ROM: {rom_path}")
    
    with open(rom_path, 'rb') as f:
        # Calculate SHA256
        f.seek(0)
        rom_sha256 = hashlib.sha256(f.read()).hexdigest()
        
        # Parse Header
        fat_offset = read_uint32(f, 0x40)
        fat_size = read_uint32(f, 0x44)
        fnt_offset = read_uint32(f, 0x48)
        fnt_size = read_uint32(f, 0x4C)
        
        print(f"FAT: {fat_offset:X} (Size: {fat_size})")
        print(f"FNT: {fnt_offset:X} (Size: {fnt_size})")
        
        # Parse FNT
        files = parse_fnt(f, fnt_offset, fnt_size)
        file_count = len(files)
        print(f"Found {file_count} files in FNT.")
        
        # Parse FAT for found files
        file_tree = []
        for file_entry in files:
            fid = file_entry['id']
            # FAT entry is 8 bytes: start(4), end(4)
            entry_offset = fat_offset + (fid * 8)
            start = read_uint32(f, entry_offset)
            end = read_uint32(f, entry_offset + 4)
            size = end - start
            
            file_tree.append({
                'path': file_entry['path'],
                'id': fid,
                'offset': start,
                'end': end,
                'size': size
            })
            
        # Sort by ID just in case
        file_tree.sort(key=lambda x: x['id'])
        
        # Output setup
        raw_dir = os.path.join(out_dir, 'raw')
        os.makedirs(raw_dir, exist_ok=True)
        
        # Extract files
        extracted_count = 0
        for item in file_tree:
            if extracted_count >= limit:
                break
                
            out_path = os.path.join(raw_dir, item['path'])
            out_parent = os.path.dirname(out_path)
            os.makedirs(out_parent, exist_ok=True)
            
            f.seek(item['offset'])
            data = f.read(item['size'])
            
            with open(out_path, 'wb') as out_f:
                out_f.write(data)
            
            extracted_count += 1
            
        # Manifest
        manifest = {
            'rom_sha256': rom_sha256,
            'extracted_at': datetime.utcnow().isoformat(),
            'file_count': file_count,
            'sample_files': [f['path'] for f in file_tree[:5]],
            'extraction_limit': limit
        }
        
        # Write JSONs
        with open(os.path.join(out_dir, 'file_tree.json'), 'w') as jf:
            json.dump(file_tree, jf, indent=2)
            
        with open(os.path.join(out_dir, 'manifest.json'), 'w') as jf:
            json.dump(manifest, jf, indent=2)
            
        print(f"Extraction complete. Extracted {extracted_count} files.")
        print(f"ROM SHA256: {rom_sha256}")
        print(f"Manifest sample: {json.dumps(manifest, indent=2)}")

if __name__ == '__main__':
    args = parse_args()
    extract(args.rom, args.out, args.limit)
