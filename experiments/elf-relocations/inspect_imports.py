#!/usr/bin/env python3
"""Inspect AArch64 ELF64 dynamic call relocations without modifying the input.

Research prototype: reads section headers and does not handle stripped section
tables, dynamic-only ELF files, other machines or non-JUMP_SLOT relocations.
"""
import argparse
import json
from pathlib import Path
import struct

ELF_HEADER = struct.Struct('<16sHHIQQQIHHHHHH')
SECTION = struct.Struct('<IIQQQQIIQQ')
SYMBOL = struct.Struct('<IBBHQQ')
RELA = struct.Struct('<QQq')
SHT_DYNSYM, SHT_STRTAB, SHT_RELA = 11, 3, 4
R_AARCH64_JUMP_SLOT = 1026


def inspect(data):
    def checked_slice(offset, size):
        if offset < 0 or size < 0 or offset > len(data) or size > len(data) - offset:
            raise ValueError('ELF data exceeds file bounds')
        return memoryview(data)[offset:offset + size]

    if len(data) < ELF_HEADER.size:
        raise ValueError('ELF header is truncated')
    header = ELF_HEADER.unpack_from(data)
    ident, machine, shoff, shsize, shnum = header[0], header[2], header[6], header[11], header[12]
    if ident[:6] != b'\x7fELF\x02\x01' or machine != 183:
        raise ValueError('Expected little-endian AArch64 ELF64')
    if shsize != SECTION.size or not shnum:
        raise ValueError('Unsupported or missing section table')
    checked_slice(shoff, shnum * shsize)
    sections = [SECTION.unpack_from(data, shoff + i * shsize) for i in range(shnum)]
    result = []
    for section in sections:
        _, kind, _, _, offset, size, link, _, _, stride = section
        if kind != SHT_RELA:
            continue
        if stride != RELA.size or size % stride or link >= shnum:
            raise ValueError('Invalid RELA section')
        symbols = sections[link]
        if symbols[1] != SHT_DYNSYM or symbols[9] != SYMBOL.size or symbols[5] % SYMBOL.size:
            continue  # Regular debug/static symbol relocations are not dynamic imports.
        if symbols[6] >= shnum or sections[symbols[6]][1] != SHT_STRTAB:
            raise ValueError('Invalid dynamic string table')
        symbol_data = checked_slice(symbols[4], symbols[5])
        strings = bytes(checked_slice(sections[symbols[6]][4], sections[symbols[6]][5]))
        relocations = checked_slice(offset, size)
        for pos in range(0, size, stride):
            target, info, addend = RELA.unpack_from(relocations, pos)
            if info & 0xffffffff != R_AARCH64_JUMP_SLOT:
                continue
            symbol_index = info >> 32
            if symbol_index >= len(symbol_data) // SYMBOL.size:
                raise ValueError('Dynamic symbol index out of range')
            name_offset = SYMBOL.unpack_from(symbol_data, symbol_index * SYMBOL.size)[0]
            if name_offset >= len(strings):
                raise ValueError('Dynamic symbol name out of range')
            end = strings.find(b'\0', name_offset)
            if end < 0:
                raise ValueError('Unterminated dynamic symbol name')
            try:
                name = strings[name_offset:end].decode('utf-8')
            except UnicodeError as exc:
                raise ValueError('Invalid dynamic symbol name') from exc
            result.append({'symbol': name, 'offset': target, 'addend': addend})
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('elf', type=Path)
    parser.add_argument('--symbol', action='append', required=True,
                        help='Dynamic symbol to locate; repeat for multiple symbols')
    args = parser.parse_args()
    try:
        matches = inspect(args.elf.read_bytes())
        selected = {name: [r for r in matches if r['symbol'] == name] for name in args.symbol}
        print(json.dumps(selected, indent=2))
        if any(len(rows) != 1 for rows in selected.values()):
            parser.exit(1, 'Every requested symbol must have exactly one call relocation.\n')
    except (OSError, ValueError, struct.error) as exc:
        parser.exit(1, f'ELF inspection failed: {exc}\n')


if __name__ == '__main__':
    main()
