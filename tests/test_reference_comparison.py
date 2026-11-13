import contextlib
import importlib.util
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch


SCRIPTS = Path(__file__).resolve().parents[1] / 'scripts'
sys.path.insert(0, str(SCRIPTS))
spec = importlib.util.spec_from_file_location('compare_reference_sources', SCRIPTS / 'compare_reference_sources.py')
comparison = importlib.util.module_from_spec(spec)
spec.loader.exec_module(comparison)


class ReferenceComparisonTest(unittest.TestCase):
    def test_reports_matching_code_and_ignores_short_windows(self):
        code = '\n'.join('distinctive_variable_%d = calculate_some_long_value(%d, 123456789);' % (i, i) for i in range(4))
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            ref = root / 'upstream'
            ref.mkdir()
            (ref / 'sample.c').write_text(code, encoding='utf-8')
            output = root / 'result.json'
            candidates = [(Path('native/example.c'), 'file', code.encode('utf-8'))]
            with patch.object(comparison, 'files', return_value=candidates), \
                 patch.object(sys, 'argv', ['compare', '--reference', str(ref), '--output', str(output)]), \
                 contextlib.redirect_stdout(io.StringIO()):
                comparison.main()
            report = json.loads(output.read_text(encoding='utf-8'))
            self.assertEqual(report['reference_files'], 1)
            self.assertEqual(report['matches'][0]['file'], 'native/example.c')
            self.assertEqual(list(comparison.windows(['x', 'y', 'z', 'q'])), [])


if __name__ == '__main__':
    unittest.main()
