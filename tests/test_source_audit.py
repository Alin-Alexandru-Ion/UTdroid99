import contextlib
import importlib.util
import io
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch


SCRIPT = Path(__file__).resolve().parents[1] / 'scripts' / 'audit_source_tree.py'
spec = importlib.util.spec_from_file_location('audit_source_tree', SCRIPT)
audit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)


class SourceAuditTest(unittest.TestCase):
    def run_audit(self, root):
        output = io.StringIO()
        with patch.object(audit, 'ROOT', root), patch.object(sys, 'argv', ['audit']), contextlib.redirect_stdout(output):
            failed = audit.main()
        return failed, output.getvalue()

    def test_private_material_is_rejected_even_without_matching_secret_text(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for name in ('.env.production', 'upload.jks', 'identity.pem'):
                (root / name).write_text('placeholder')
            failed, output = self.run_audit(root)
            self.assertTrue(failed)
            for name in ('.env.production', 'upload.jks', 'identity.pem'):
                self.assertIn('FAIL ' + name, output)

    def test_ordinary_source_is_accepted(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / 'main.c').write_text('int main(void) { return 0; }')
            failed, output = self.run_audit(root)
            self.assertFalse(failed)
            self.assertIn('0 findings', output)

    def test_rejects_patch_archive_even_with_a_misleading_extension(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / 'patch.dat').write_bytes(b'BZh9example')
            failed, output = self.run_audit(root)
            self.assertTrue(failed)
            self.assertIn('FAIL patch.dat: binary or generated artifact', output)


if __name__ == '__main__':
    unittest.main()
