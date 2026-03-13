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

## grails-testing-support-http-client

Provides fluent HTTP client testing support for Grails integration and functional tests.

### Required Libraries

To use HTTP client testing support in your integration tests, add the following dependency:

```gradle
dependencies {
    integrationTestImplementation 'org.apache.grails:grails-testing-support-http-client'
}
```

### Basic Usage

In Grails integration tests, implement the `HttpClientSupport` trait:

```groovy
import grails.testing.mixin.integration.Integration
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Specification

import org.apache.grails.testing.http.client.HttpClientSupport

@Integration
class HealthSpec extends Specification implements HttpClientSupport {

    void 'health endpoint responds OK'() {
        expect:
        http('/health')
            .expectStatus(200)
            .expectContains('UP')
    }
}
```
