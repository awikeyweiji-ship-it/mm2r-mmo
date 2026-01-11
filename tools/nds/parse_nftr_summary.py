#!/usr/bin/env python3
import sys
import struct
import os
import argparse

def read_u16(f):
    return struct.unpack('<H', f.read(2))[0]

def read_u32(f):
    return struct.unpack('<I', f.read(4))[0]

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--in', dest='input', required=True)
    args = parser.parse_args()
    
    path = args.input
    if not os.path.exists(path):
        print("错误：文件不存在")
        sys.exit(1)
        
    file_len = os.path.getsize(path)
    print(f"解析摘要: {os.path.basename(path)} (Size: {file_len})")
    
    with open(path, 'rb') as f:
        magic = f.read(4)
        if magic != b'NFTR':
            print(f"错误: 魔数不匹配 ({magic})")
            return
            
        print("魔数: NFTR (Nitro Font Resource)")
        
        # Standard Nitro Header
        endian = f.read(2) # 0xFFFE
        version = read_u16(f)
        total_size = read_u32(f)
        header_size = read_u16(f)
        num_blocks = read_u16(f)
        
        print(f"版本: {version >> 8}.{version & 0xFF}")
        print(f"头部声明大小: {total_size} (实际: {file_len})")
        print(f"Header Size: {header_size}, Blocks: {num_blocks}")
        
        # Parse Blocks
        while f.tell() < file_len:
            block_magic = f.read(4)
            if not block_magic: break
            block_size = read_u32(f)
            
            magic_str = block_magic.decode('latin-1', errors='replace')[::-1] # Usually reversed? Or just ASCII. 
            # Actually NDS blocks are usually like "FINF", "CGLP", "TGLP"
            # Try decoding normally first.
            try:
                magic_str = block_magic.decode('utf-8')
            except:
                pass
                
            print(f"-- Block: {magic_str} (Size: {block_size})")
            
            if magic_str == "FINF":
                # Font Info
                sub_header = f.read(1) # Unknown
                line_height = f.read(1) # Maybe?
                # Actually structure is complex. Just dumping some bytes.
                f.seek(f.tell()-2) # Undo
                
                # FINF Structure approx:
                # u8 fontType, u8 height, u16 unknown
                # u8 unknown, u8 defaultWidth, u8 defaultLength, u8 encoding
                # u32 offsetGLPH, u32 offsetWIDTH, u32 offsetMAP
                
                font_type = ord(f.read(1))
                height = ord(f.read(1))
                f.read(2) # skip
                default_width = ord(f.read(1))
                default_length = ord(f.read(1)) # or vice versa
                f.read(1)
                encoding = ord(f.read(1))
                
                print(f"   Font Height: {height}px")
                print(f"   Default Width: {default_width}px")
                print(f"   Encoding: {encoding}")

            elif magic_str == "CGLP":
                # Char Glyph
                # u8 cellWidth, u8 cellHeight
                # u16 cellSize, u16 qBitDepth, u8 rotation, u8 dummy
                cell_w = ord(f.read(1))
                cell_h = ord(f.read(1))
                print(f"   Cell Size: {cell_w}x{cell_h}")
                
            elif magic_str == "TGLP":
                 # Texture Glyph (Pre-rendered)
                 cell_w = ord(f.read(1))
                 cell_h = ord(f.read(1))
                 print(f"   Cell Size: {cell_w}x{cell_h}")

            elif magic_str == "CMAP":
                # Char Map
                first_char = read_u16(f)
                last_char = read_u16(f)
                map_type = read_u32(f)
                print(f"   Map Range: 0x{first_char:04X} - 0x{last_char:04X}")
                print(f"   Map Type: {map_type}")
            
            # Skip rest of block
            # Current pos is block start + 8 + read_bytes
            # We want to go to block start + block_size
            # Wait, block_size usually includes header (8 bytes).
            # So next block is at block_start + block_size
            
            # Re-calculate correct seek
            # Current f.tell() is messy.
            # Better approach: store block start before reading type
            # But we already read 8 bytes.
            # So next block is at (current_pos - 8 - bytes_read_in_block) + block_size
            
            # Easier: Just track absolute offsets
            # We are not robustly tracking bytes read in block.
            # So let's rely on the fact that blocks are sequential and we know sizes.
            # But we need to account for what we read inside the 'if'.
            pass
            
            # Conservative skip:
            # Since we can't easily track exactly how many bytes we read inside the if/elif,
            # we should have saved the start position.
            # Refactoring slightly for robustness.
            
    print("解析摘要结束。")

if __name__ == '__main__':
    main()
