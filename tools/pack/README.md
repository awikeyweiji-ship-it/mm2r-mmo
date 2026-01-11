# NDS Pack Unpacker Tool

This tool attempts to unpack generic container formats often found in NDS games, specifically targeting files like `pack_data.pak`.

## Usage

```bash
python3 unpack_pack.py --in <input_file> --out <output_directory> [--limit <max_files>]
```

## Logic

1. **NARC Check**: Checks if the file starts with "NARC". If so, treats it as a standard Nintendo Archive.
2. **Table Guess**: If not NARC, it guesses a simple file allocation table structure at the beginning of the file.
   - It assumes the first 4 bytes might be a file count.
   - It checks for two common entry formats: `(offset, size)` or `(start, end)`.
   - It validates these guesses by checking for monotonicity and boundary validity.
3. **Signature Scan**: If structural unpacking fails, it scans the file for common NDS resource signatures (e.g., "NCLR", "NCGR", "NSCR", "SDAT") to identify potential embedded assets.

## Output

- Extracted files are placed in the output directory, organized by the detected method (e.g., `narc/`, `table_guess/`).
- A scan report `pack_scan.json` is generated if signatures are scanned.
