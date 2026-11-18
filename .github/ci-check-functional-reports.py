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

"""Fail unless parsed JUnit XML shows a positive green suite count."""

from __future__ import annotations

import os
import sys
import xml.etree.ElementTree as ET


def main() -> int:
    suites = 0
    tests = 0
    failures = 0
    errors = 0
    for root, _dirs, files in os.walk('.'):
        norm = root.replace('\\', '/')
        if '/build/test-results' not in norm:
            continue
        for name in files:
            if not name.endswith('.xml'):
                continue
            path = os.path.join(root, name)
            try:
                tree = ET.parse(path)
            except ET.ParseError as exc:
                print(f'unparsable {path}: {exc}')
                return 1
            node = tree.getroot()
            candidates = [node] if node.tag.endswith('testsuite') else list(node)
            for suite in candidates:
                if not suite.tag.endswith('testsuite'):
                    continue
                if not all(key in suite.attrib for key in ('tests', 'failures', 'errors')):
                    print(f'missing attrs {path}')
                    return 1
                suites += 1
                tests += int(suite.attrib['tests'])
                failures += int(suite.attrib['failures'])
                errors += int(suite.attrib['errors'])
    print(f'suites={suites} tests={tests} failures={failures} errors={errors}')
    if suites > 0 and tests > 0 and failures == 0 and errors == 0:
        return 0
    return 1


if __name__ == '__main__':
    sys.exit(main())
