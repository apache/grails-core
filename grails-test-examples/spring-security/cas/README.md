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

This is a CAS-enabled test application. It no longer needs a hand-run CAS server: an
[Apereo CAS](https://github.com/apereo/cas) server is started in a container by
[CasContainerHolder](test1/src/main/groovy/grails/plugin/springsecurity/cas/test/CasContainerHolder.groovy),
and [CasTestEnvironmentPostProcessor](test1/src/main/groovy/grails/plugin/springsecurity/cas/test/CasTestEnvironmentPostProcessor.groovy)
points the CAS plugin at it before the application context is built. Docker (or a compatible
container runtime) is therefore required to run or test this application.

## Running the tests

The application is exercised under three configurations, selected with the `TESTCONFIG` system
property. Each has to be its own run, because the configuration is applied at application startup.

The specs only run when one of these is selected, so a build that covers every Spring Security
example does not repeat a configuration that the dedicated per-configuration CI job already runs.
Running `check` without `-DTESTCONFIG` therefore reports no CAS tests.

| `TESTCONFIG` | Configuration | Covered by |
|---|---|---|
| `cas` (default) | `proxyCallbackUrl` and `proxyReceptorUrl` unset, single signout enabled | `CasLoginSpec`, `CasNoProxyReceptorSpec`, `CasSingleSignOutSpec` |
| `casProxy` | both proxy settings configured, single signout enabled | `CasLoginSpec`, `CasProxyTicketSpec`, `CasSingleSignOutSpec` |
| `casNoSingleSignout` | `cas.useSingleSignout` left at its default | `CasLoginSpec`, `CasNoProxyReceptorSpec`, `CasNoSingleSignOutSpec` |

```
./gradlew :grails-test-examples-spring-security-cas-test1:check -DTESTCONFIG=cas
./gradlew :grails-test-examples-spring-security-cas-test1:check -DTESTCONFIG=casProxy
./gradlew :grails-test-examples-spring-security-cas-test1:check -DTESTCONFIG=casNoSingleSignout
```

`cas.useSingleSignout` is opt-in as of Grails 8. The app enables it for the first two configurations
so the single signout filter is exercised; enabling it disables session fixation prevention, and the
plugin warns about that at startup.

The CAS image is pinned to a known-good version and can be overridden:

```
./gradlew :grails-test-examples-spring-security-cas-test1:check -PcasContainerVersion=7.3.6
```

The specs skip themselves when no Docker daemon is available.

## Running the application

```
./gradlew :grails-test-examples-spring-security-cas-test1:bootRun
```

The test application URLs are:
* [http://localhost:8081/secure/admins](http://localhost:8081/secure/admins)
* [http://localhost:8081/secure/users](http://localhost:8081/secure/users)
* [http://localhost:8081/secure/proxyStatus](http://localhost:8081/secure/proxyStatus) — asks CAS for a proxy ticket

`/secure/proxyStatus` needs the proxy configuration, which is only applied when a configuration that
enables it is selected. Without one it reports `NO_PROXY_TICKET`, because no proxy callback URL was
configured and CAS was never asked for a proxy-granting ticket. To exercise it, pass the
configuration to `bootRun` as well:

```
./gradlew :grails-test-examples-spring-security-cas-test1:bootRun -DTESTCONFIG=casProxy
```

The test app creates the `admin` and `user` users in
[BootStrap.groovy](test1/grails-app/init/grails/plugin/springsecurity/cas/test/BootStrap.groovy).
The password is the same as the username, and the containerised CAS server is configured to accept
the same two accounts.

## How the container is reached

The CAS server runs in a container while the application runs on the host, so the two see each other
at different addresses:

| Leg | Address used |
|---|---|
| application and browser → CAS | `http://localhost:<mapped port>/cas` |
| CAS → application (single logout, proxy callback) | `http://host.testcontainers.internal:<server port>/...` |

The service URL and the proxy callback URL depend on the port the embedded server binds, which is
random under integration tests. They are set by
[CasServiceUrlConfigurer](test1/src/main/groovy/grails/plugin/springsecurity/cas/test/CasServiceUrlConfigurer.groovy)
once the server has started but before it serves a request.

CAS only authorises `https` services out of the box, so a service definition permitting `http` — and
carrying the proxy policy that lets CAS issue proxy-granting tickets — is copied into the container
from [grailsTest-10000001.json](test1/src/main/resources/cas/services/grailsTest-10000001.json).
