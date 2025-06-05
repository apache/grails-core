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

This is a CAS-enabled test application.  To run it successfully, a CAS
server is required.  The URL for the CAS server is configured in the
[application.groovy](spring-security-cas-test1/grails-app/conf/application.groovy)
file.  Setting up a CAS server is out of the scope of this document, but
good places to start are [Apereo CAS GitHub](https://github.com/apereo/cas)
and the [CAS Initializr](https://getcas.apereo.org/ui) service.

The test application can be run with:

`./gradlew :testapp-spring-security-cas-test1:bootRun`

The test application URLs are:
* [http://localhost:8081/secure/admins](http://localhost:8081/secure/admins)
* [http://localhost:8081/secure/users](http://localhost:8081/secure/users)

The test app creates the `admin` and `user` users in
[BootStrap.groovy](spring-security-cas-test1/grails-app/init/spring/security/cas/test/BootStrap.groovy). 
The password is the same as the username.
