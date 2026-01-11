# NDS ROM Extraction POC

## Overview
This directory contains tools for extracting content from NDS ROMs (NitroFS).
The primary goal is to parse the FNT (File Name Table) and FAT (File Allocation Table) to generate a file tree and manifest, and extract raw files.

## Tools
*   `extract_nds.py`: A pure Python script to parse NDS ROMs and extract files.

## Usage
```bash
python3 extract_nds.py --rom <path_to_rom> --out <output_directory> --limit <number_of_files_to_extract>
```

## Output
The script generates the following in the output directory:
*   `file_tree.json`: A JSON representation of the file system structure (paths, file IDs, offsets, sizes).
*   `manifest.json`: Metadata about the extraction (ROM SHA256, extraction timestamp, file count, sample files).
*   `raw/`: A directory containing the extracted files (preserving directory structure).
