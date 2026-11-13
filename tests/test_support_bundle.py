import contextlib
import importlib.util
import io
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
from zipfile import ZipFile


SCRIPT = Path(__file__).resolve().parents[1] / 'scripts' / 'prepare_support_bundle.py'
spec = importlib.util.spec_from_file_location('prepare_support_bundle', SCRIPT)
bundle = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bundle)


class SupportBundleTest(unittest.TestCase):
    def test_only_explicit_support_files_are_packaged(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for relative in bundle.FILES.values():
                file = root / relative
                file.parent.mkdir(parents=True, exist_ok=True)
                file.write_bytes(b'\x7fELFsynthetic-support')
            (root / 'Core.so').write_bytes(b'\x7fELFnot-support')
            output = root / 'support.zip'
            with patch.object(sys, 'argv', ['bundle', '--source-root', str(root), '--output', str(output)]), \
                 contextlib.redirect_stdout(io.StringIO()):
                bundle.main()
            with ZipFile(output) as archive:
                self.assertEqual(set(archive.namelist()), set(bundle.FILES))
                self.assertNotIn('Core.so', archive.namelist())


if __name__ == '__main__':
    unittest.main()
