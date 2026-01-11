import argparse
import os
import struct

def main():
    parser = argparse.ArgumentParser(description='Parse summary of NFTR file.')
    parser.add_argument('--in', dest='input_file', required=True, help='Input NFTR bin file')
    args = parser.parse_args()

    file_path = args.input_file
    if not os.path.exists(file_path):
        print(f"Error: File {file_path} not found.")
        return

    with open(file_path, 'rb') as f:
        data = f.read()

    file_size = len(data)
    print(f"解析文件: {file_path}")
    print(f"文件总大小: {file_size} bytes")

    # Magic check
    if len(data) < 4:
        print("文件过小，无法读取 Magic。")
        return
        
    magic = data[0:4].decode('ascii', errors='ignore')
    print(f"Magic: {magic} ({'匹配' if magic == 'NFTR' else '不匹配'})")
    
    if magic != 'NFTR':
        print("非 NFTR 文件，停止解析。")
        return

    # Basic Header (Nitro Header common format)
    # 0x00: Magic (4)
    # 0x04: Endian (2) - usually 0xFFFE
    # 0x06: Version (2)
    # 0x08: File Size (4)
    # 0x0C: Header Size (2)
    # 0x0E: Num Blocks (2)
    
    if len(data) < 16:
        print("头部数据不足 16 字节。")
        return

    try:
        endian = struct.unpack('<H', data[4:6])[0]
        version = struct.unpack('<H', data[6:8])[0]
        header_size = struct.unpack('<I', data[8:12])[0] # Careful, spec says u32 size at 0x8?
        # Actually standard NDS header:
        # 0x04: u16 endian
        # 0x06: u16 version
        # 0x08: u32 file_size
        # 0x0C: u16 header_size
        # 0x0E: u16 num_blocks
        
        reported_size = struct.unpack('<I', data[8:12])[0]
        header_len = struct.unpack('<H', data[12:14])[0]
        num_blocks = struct.unpack('<H', data[14:16])[0]
        
        print(f"Endian: 0x{endian:04X}")
        print(f"Version: 0x{version:04X}")
        print(f"Header Reported Size: {reported_size}")
        print(f"Header Length: {header_len}")
        print(f"Number of Blocks: {num_blocks}")

        # Try to find FINF (Font Info) block
        # Blocks usually follow the header.
        current_offset = header_len
        
        # Limit search loop to prevent hang
        for _ in range(num_blocks):
            if current_offset + 8 > file_size:
                break
            
            block_magic = data[current_offset:current_offset+4].decode('ascii', errors='ignore')
            block_size = struct.unpack('<I', data[current_offset+4:current_offset+8])[0]
            
            print(f"-- Block Found: {block_magic} at {current_offset}, size {block_size}")
            
            if block_magic == 'FINF':
                # Parse FINF summary
                # FINF structure (approx):
                # 0x00: Magic
                # 0x04: Size
                # 0x08: Unknown/Encoding?
                # 0x0C: Height?
                # ...
                # Let's try to extract some bytes that might be meaningful
                if block_size >= 0x20:
                    finf_data = data[current_offset:current_offset+block_size]
                    # Common fields in FINF:
                    # +0x08: u8 fontType?
                    # +0x09: u8 height?
                    # +0x0A: u16 unknown
                    # +0x0C: u8 defaultWidth?
                    # +0x0D: u8 defaultHeight?
                    
                    # Just dumping some raw values for analysis
                    try:
                        u8_vals = struct.unpack('BBBBBB', finf_data[8:14])
                        print(f"   FINF Raw Bytes [0x8:0xE]: {u8_vals}")
                        print(f"   Possible Height: {u8_vals[1]}")
                    except:
                        print("   Unable to parse FINF details.")
            
            elif block_magic == 'CGLP':
                # Character Glyph (Bitmaps)
                # Usually contains width, height, bpp
                if block_size >= 16:
                    cglp_data = data[current_offset:current_offset+block_size]
                    try:
                        # +0x08: u8 cellWidth
                        # +0x09: u8 cellHeight
                        # +0x0A: u16 cellLen?
                        cw, ch = struct.unpack('BB', cglp_data[8:10])
                        print(f"   CGLP Cell Size: {cw}x{ch}")
                    except:
                        pass
                        
            elif block_magic == 'CMAP':
                # Character Map
                print("   Found Character Map info.")

            current_offset += block_size

    except Exception as e:
        print(f"解析过程中遇到错误: {e}")

if __name__ == "__main__":
    main()
