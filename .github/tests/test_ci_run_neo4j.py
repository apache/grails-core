#!/usr/bin/env python3
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / 'ci-run-neo4j-tests.sh'


class Neo4jExitTests(unittest.TestCase):
    def test_exit_status_requires_success_from_this_invocation(self):
        cases = [
            (0, 'SUCCESS', None, 0),
            (0, None, None, 0),
            (1, 'FAILURE', None, 1),
            (1, 'SUCCESS', None, 1),
            (124, 'SUCCESS', None, 0),
            (124, 'FAILURE', None, 124),
            (124, None, None, 124),
            (124, None, 'SUCCESS', 124),
            (124, 'OTHER', None, 124),
            (137, 'SUCCESS', None, 137),
        ]
        for code, marker, stale, expected in cases:
            with self.subTest(code=code, marker=marker, stale=stale):
                with tempfile.TemporaryDirectory() as directory:
                    root = Path(directory)
                    (root / 'build').mkdir()
                    if stale:
                        (root / 'build/ci-build-finished.txt').write_text(stale)
                    timeout = root / 'timeout'
                    timeout.write_text(
                        '#!/usr/bin/env python3\n'
                        'import os\n'
                        'import subprocess\n'
                        'import sys\n'
                        'subprocess.run(sys.argv[3:], check=True)\n'
                        'sys.exit(int(os.environ["GRAILS_CI_TEST_TIMEOUT_EXIT"]))\n'
                    )
                    timeout.chmod(0o755)
                    gradle = root / 'gradlew'
                    gradle.write_text(
                        '#!/usr/bin/env python3\n'
                        'import os\n'
                        'import sys\n'
                        'from pathlib import Path\n'
                        'Path("args.txt").write_text("\\n".join(sys.argv[1:]))\n'
                        'if "GRAILS_CI_TEST_MARKER" in os.environ:\n'
                        '    Path("build/ci-build-finished.txt").write_text('
                        'os.environ["GRAILS_CI_TEST_MARKER"])\n'
                    )
                    gradle.chmod(0o755)
                    env = dict(
                        os.environ,
                        PATH=f'{root}:{os.environ["PATH"]}',
                        GRAILS_CI_TEST_TIMEOUT_EXIT=str(code),
                    )
                    env.pop('GRAILS_CI_TEST_MARKER', None)
                    if marker is not None:
                        env['GRAILS_CI_TEST_MARKER'] = marker
                    result = subprocess.run(
                        ['bash', str(SCRIPT), '-PgrailsIndy=false'],
                        cwd=root, env=env, capture_output=True, text=True,
                    )
                    self.assertEqual(result.returncode, expected, result.stderr)
                    args = (root / 'args.txt').read_text().splitlines()
                    self.assertIn('--init-script', args)
                    self.assertIn('-PonlyNeo4jTests', args)
                    self.assertIn('-PgrailsIndy=false', args)


if __name__ == '__main__':
    unittest.main()
