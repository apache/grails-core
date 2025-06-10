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

## Grails GSP

This subproject is required for all Grails applications and plugins that require GSP processing.  If your project includes GSPs you should add the following to your `build.gradle` which is provided by the [Grails Gradle Plugin](https://github.com/grails/grails-core/tree/master/grails-gradle-plugin).

``` gradle
apply plugin: "org.apache.grails.gradle.grails-gsp"
```

It is typical of standard Grails application to use this in conjunction with `grails-web` as in the following example:

``` gradle
apply plugin: "org.apache.grails.gradle.grails-web"
apply plugin: "org.apache.grails.gradle.grails-gsp"
```

Dependencies
-----
To see what additional subprojects will be included with this, you can view this project's [build.gradle](https://github.com/grails/grails-core/blob/master/grails-gsp/build.gradle)
