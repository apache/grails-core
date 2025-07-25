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

[![Java CI](https://github.com/apache/grails-spring-security/actions/workflows/gradle.yml/badge.svg)](https://github.com/apache/grails-spring-security/actions/workflows/gradle.yml)

Grails Spring Security
======================

See [documentation](https://apache.github.io/grails-spring-security/latest/ghpages.html) for detailed information.

- [CORE](https://apache.github.io/grails-spring-security/latest/core-plugin/guide/)
  - [ACL](https://apache.github.io/grails-spring-security/latest/acl-plugin/guide/)
  - [CAS](https://apache.github.io/grails-spring-security/latest/cas-plugin/guide/)
  - [LDAP](https://apache.github.io/grails-spring-security/latest/ldap-plugin/guide/)
  - [OAuth2](https://apache.github.io/grails-spring-security/latest/oauth2-plugin/guide/)
  - [REST](https://apache.github.io/grails-spring-security/latest/rest-plugin/guide/)
  - [UI](https://apache.github.io/grails-spring-security/latest/ui-plugin/guide/)

### Building

To build this project from source, first bootstrap gradle:

     cd gradle-bootstrap
     gradle
     cd -

After bootstrap the project, you can build it with the command:

     ./gradlew build

To run the build only, and skip the tests, run:

     ./gradlew build -PskipTests

Then publish the jar files to mavenLocal for usage:

    ./gradlew publishToMavenLocal

### Branch structure 

- `7.0.x` compatible with Grails 7
- `6.0.x` compatible with Grails 6
- `5.0.x` compatible with Grails 5
- `4.0.x` compatible with Grails 4
- `3.3.x` compatible with Grails 3.3.x
- `3.2.x` compatible with Grails 3.2.x

Grails 7 requires disabling any Spring Security Auto Configurations you may have in your classpath.  This can be done via annotation or `application.yml`
e.g.
```yml
spring:
  autoconfigure:
    exclude:
      - org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration
      - org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration
      - org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
      - org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration
      - org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientAutoConfiguration
      - org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration
      - org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
```
