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

grails-boot
===========

Grails integrations with Spring Boot

GORM Spring Boot plugins have been moved to [boot-plugins](https://github.com/grails/grails-data-mapping/tree/6.1.x/boot-plugins)

To run Spring Boot App
```shell
 sdk env
 ./gradlew :gsp-example:bootRun
```

To run Spring Boot Groovy Script
```shell
 cd sample-apps/gsp/script
 sdk env
 groovy -Dgroovy.grape.report.downloads=true app.groovy
```
