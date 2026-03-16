<!--
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

## grails-testing-support-dbcleanup-h2

Provides the H2 database cleanup implementation for the `DatabaseCleaner` SPI, enabling automatic table truncation in H2-backed integration tests.

### Required Libraries

To use H2 database cleanup in your integration tests, add the following dependencies:

```gradle
// H2 JDBC Driver
testImplementation 'com.h2database:h2'
```
