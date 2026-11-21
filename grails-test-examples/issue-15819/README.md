<!--
SPDX-License-Identifier: Apache-2.0

Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements. See the NOTICE file distributed with
this work for additional information regarding copyright ownership.
The ASF licenses this file to You under the Apache License, Version 2.0
(the "License"); you may not use this file except in compliance with
the License. You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# Response rendering regression (#15819)

Functional coverage based on the
[end-application reproducer](https://github.com/ruthst00/15819-response-renderer-overloads).
The application registers a Spring `RequestContextFilter` at order `-100`, after
Grails' request filter, and serves a small GSP fragment from `/search/results`.
Keep the fragment small and do not flush the response: committing it can mask
the unwanted conventional view resolution that caused the original failure.

There is deliberately no `results.gsp` or `upload.gsp`. The tests cover template
rendering with AJAX and normal GET requests and multipart uploads behind a late
request-context filter. `/search/upload` reads a `title` form field and a `file`
upload, and renders the title, original filename and uploaded content in the fragment.

`GrailsDispatcherServletRequestBindingSpec` provides the dispatcher regression
coverage on `7.0.x`, including multipart resolution, request publication, nested
dispatches and asynchronous processing. It has a distinct name so merge-up can
retain the existing dispatcher and hidden-method specs on `8.0.x`.

The tests use real HTTP requests against the embedded application; no browser,
database, or Docker container is needed. From the repository root, run:

```bash
./gradlew :grails-test-examples-issue-15819:integrationTest
```
