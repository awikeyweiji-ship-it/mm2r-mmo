#!/usr/bin/env python3
import sys
import struct
import os
import json
import hashlib
import argparse

def read_u8(f):
    return struct.unpack('<B', f.read(1))[0]

def read_u16(f):
    return struct.unpack('<H', f.read(2))[0]

def read_u32(f):
    return struct.unpack('<I', f.read(4))[0]

def ensure_dir(path):
    if not os.path.exists(path):
        os.makedirs(path)

def get_string_decoded(bytes_data):
    try:
        return bytes_data.decode('utf-8')
    except UnicodeDecodeError:
        return bytes_data.decode('latin-1', errors='replace')

def parse_fnt(f, fnt_offset, file_allocation_table):
    # Directory Table Entry: 8 bytes
    # u32 sub_table_offset (relative to fnt_offset)
    # u16 first_file_id
    # u16 parent_id (or total directories for root)
    
    # Read Root Directory Entry
    f.seek(fnt_offset)
    root_sub_table_offset = read_u32(f)
    root_first_file_id = read_u16(f)
    total_dirs = read_u16(f) # For root, this is total directories
    
    dirs_to_process = [(0, "")] # (dir_id, path_prefix)
    # We need to read all directory table entries first to map IDs to their sub-tables
    dir_entries = []
    f.seek(fnt_offset)
    for i in range(total_dirs):
        off = read_u32(f)
        first_id = read_u16(f)
        parent_id = read_u16(f) # parent_id unused for now, useful for tree walking
        dir_entries.append({
            'sub_table_offset': off,
            'first_file_id': first_id,
            'parent_id': parent_id
        })

    file_tree = []
    
    # Process directories
    # We can't just iterate dir_entries because we need the path context.
    # But FNT structure is hierarchical. The sub-tables contain the names.
    
    # Re-approach: Iterate dir_entries to get sub-tables, but we need to know "who is my child" to build path?
    # Actually, the Sub-Table logic:
    # A sub-table contains a sequence of length-byte + name.
    # if length-byte == 0x00: end of sub-table.
    # if length-byte & 0x80: it's a sub-directory.
    #   id = read_u16()
    #   name_len = length-byte & 0x7F
    # else: it's a file.
    #   name_len = length-byte
    #   file_id = current_file_id++
    
    # To build full paths, we need to traverse from Root (0).
    
    queue = [(0, "")] # dir_id, current_path (starts empty, root is typically treated as root)
    
    # Keep track of processed dirs to avoid cycles if any (unlikely in valid ROM)
    visited_dirs = set()
    
    while queue:
        curr_dir_id, curr_path = queue.pop(0)
        if curr_dir_id >= len(dir_entries):
            continue
            
        if curr_dir_id in visited_dirs:
            continue
        visited_dirs.add(curr_dir_id)
        
        entry = dir_entries[curr_dir_id]
        sub_table_abs_offset = fnt_offset + entry['sub_table_offset']
        
        f.seek(sub_table_abs_offset)
        
        current_file_id = entry['first_file_id']
        
        while True:
            len_byte = read_u8(f)
            if len_byte == 0x00:
                break
            
            is_subdir = (len_byte & 0x80) != 0
            name_len = len_byte & 0x7F
            
            name_bytes = f.read(name_len)
            name = get_string_decoded(name_bytes)
            
            if is_subdir:
                sub_dir_id = read_u16(f)
                new_path = os.path.join(curr_path, name)
                queue.append((sub_dir_id, new_path))
            else:
                # It is a file
                file_path = os.path.join(curr_path, name)
                
                # Get FAT info
                if current_file_id < len(file_allocation_table):
                    start, end = file_allocation_table[current_file_id]
                    size = end - start
                    file_tree.append({
                        'path': file_path,
                        'file_id': current_file_id,
                        'start': start,
                        'end': end,
                        'size': size
                    })
                else:
                    print(f"Warning: File ID {current_file_id} out of FAT range.")
                
                current_file_id += 1
                
    return file_tree

def main():
    parser = argparse.ArgumentParser(description='Extract NDS ROM contents POC')
    parser.add_argument('--rom', required=True, help='Path to NDS ROM')
    parser.add_argument('--out', required=True, help='Output directory')
    parser.add_argument('--limit', type=int, default=50, help='Max files to extract')
    args = parser.parse_args()

    rom_path = args.rom
    out_dir = args.out
    
    if not os.path.exists(rom_path):
        print(f"Error: ROM not found at {rom_path}")
        sys.exit(1)
        
    ensure_dir(out_dir)
    ensure_dir(os.path.join(out_dir, "raw"))
    
    # SHA256 of ROM
    sha256 = hashlib.sha256()
    with open(rom_path, 'rb') as f:
        while chunk := f.read(8192):
            sha256.update(chunk)
    rom_hash = sha256.hexdigest()
    
    with open(rom_path, 'rb') as f:
        # Read Header
        f.seek(0x40)
        fnt_offset = read_u32(f)
        fnt_size = read_u32(f)
        
        f.seek(0x48)
        fat_offset = read_u32(f)
        fat_size = read_u32(f)
        
        # Parse FAT
        f.seek(fat_offset)
        file_count = fat_size // 8
        fat_entries = []
        for _ in range(file_count):
            start = read_u32(f)
            end = read_u32(f)
            fat_entries.append((start, end))
            
        # Parse FNT
        file_tree = parse_fnt(f, fnt_offset, fat_entries)
        
        # Sort by file_id for consistency
        file_tree.sort(key=lambda x: x['file_id'])
        
        # Write file_tree.json
        with open(os.path.join(out_dir, 'file_tree.json'), 'w') as jf:
            json.dump(file_tree, jf, indent=2)
            
        # Write manifest.json
        manifest = {
            'rom_sha256': rom_hash,
            'file_count': len(file_tree),
            'extracted_at': os.path.basename(rom_path), # Using filename as placeholder/timestamp ref
            'sample_files': [x['path'] for x in file_tree[:10]]
        }
        with open(os.path.join(out_dir, 'manifest.json'), 'w') as jf:
            json.dump(manifest, jf, indent=2)
            
        # Extract files
        print(f"Total files found: {len(file_tree)}")
        extract_count = 0
        for entry in file_tree:
            if extract_count >= args.limit:
                break
                
            path = entry['path']
            # Remove leading slashes if any to ensure it joins correctly
            clean_path = path.lstrip('/\\')
            out_path = os.path.join(out_dir, 'raw', clean_path)
            ensure_dir(os.path.dirname(out_path))
            
            f.seek(entry['start'])
            data = f.read(entry['size'])
            
            with open(out_path, 'wb') as out_f:
                out_f.write(data)
                
            extract_count += 1
            
        print(f"Extracted {extract_count} files.")

if __name__ == '__main__':
    main()
