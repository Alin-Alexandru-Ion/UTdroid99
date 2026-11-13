import importlib.util
from pathlib import Path
import struct
import unittest


SCRIPT = Path(__file__).resolve().parents[1] / 'experiments/elf-relocations/inspect_imports.py'
spec = importlib.util.spec_from_file_location('inspect_imports', SCRIPT)
elf = importlib.util.module_from_spec(spec)
spec.loader.exec_module(elf)


def fixture():
    strings = b'\0sample_function\0'
    symbols = bytes(elf.SYMBOL.size) + elf.SYMBOL.pack(1, 0x12, 0, 0, 0, 0)
    relocations = elf.RELA.pack(0x123456, (1 << 32) | elf.R_AARCH64_JUMP_SLOT, 0)
    str_off = elf.ELF_HEADER.size
    sym_off = str_off + len(strings)
    rela_off = sym_off + len(symbols)
    shoff = rela_off + len(relocations)
    ident = b'\x7fELF\x02\x01\x01' + bytes(9)
    header = elf.ELF_HEADER.pack(ident, 3, 183, 1, 0, 0, shoff, 0, 64, 0, 0, 64, 4, 0)
    sections = b''.join([
        bytes(elf.SECTION.size),
        elf.SECTION.pack(0, elf.SHT_STRTAB, 0, 0, str_off, len(strings), 0, 0, 1, 0),
        elf.SECTION.pack(0, elf.SHT_DYNSYM, 0, 0, sym_off, len(symbols), 1, 0, 8, elf.SYMBOL.size),
        elf.SECTION.pack(0, elf.SHT_RELA, 0, 0, rela_off, len(relocations), 2, 0, 8, elf.RELA.size),
    ])
    return header + strings + symbols + relocations + sections


class ElfRelocationTest(unittest.TestCase):
    def test_discovers_jump_slot_by_name(self):
        self.assertEqual(elf.inspect(fixture()), [
            {'symbol': 'sample_function', 'offset': 0x123456, 'addend': 0}])

    def test_rejects_truncated_section_table(self):
        with self.assertRaises(ValueError):
            elf.inspect(fixture()[:-1])

    def test_rejects_out_of_range_symbol(self):
        data = bytearray(fixture())
        reloc_start = elf.ELF_HEADER.size + len(b'\0sample_function\0') + 2 * elf.SYMBOL.size
        struct.pack_into('<Q', data, reloc_start + 8, (9 << 32) | elf.R_AARCH64_JUMP_SLOT)
        with self.assertRaises(ValueError):
            elf.inspect(data)


if __name__ == '__main__':
    unittest.main()
