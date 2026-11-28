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

Grails Gradle Plugins
========

Latest API Docs: https://grails.apache.org/docs/latest/api/

Below are the plugins that are provided by the grails-gradle-plugin dependency.

```
buildscript {
    dependencies {
        classpath "org.apache.grails:grails-gradle-plugins:$grailsVersion"
    }
}
```

grails-core
---------
_Todo_: Add the docs

grails-doc
---------
_Todo_: Add the docs

grails-gsp
---------
* Configure GSP Compiling Task

grails-plugin
---------
* Configure Ast Sources
* Configure Project Name And Version AST Metadata
* Configure Plugin Resources
* Configure Plugin Jar Task
* Configure Sources Jar Task

grails-web
---------
* Adds web specific extensions


Typical Project Type Gradle Plugin Includes
========
Below are the Gradle plugins that certain types of projects apply. The `grails create-app`, `grails create-restapi`, `grails create-plugin`, `grails create-web-plugin` and `grails create-rest-api-plugin` commands add them for you. If you change the scope of your plugin or project you may have to add or remove a Grails Gradle plugin.

Create App
----

<h4>Grails Web Project</h4>
-----
A project created with `grails create-app`

```
apply plugin: "org.apache.grails.gradle.grails-web"
apply plugin: "org.apache.grails.gradle.grails-gsp"
```

<h4>Grails REST API Project</h4>
----
A project created with `grails create-restapi`

```
apply plugin: "org.apache.grails.gradle.grails-web"
```


Create Plugin
---

<h4>Grails Web Plugin Project</h4>
A project created with `grails create-web-plugin`

```
apply plugin: "org.apache.grails.gradle.grails-plugin"
apply plugin: "org.apache.grails.gradle.grails-gsp"
```

<h4>Grails REST API Plugin Project</h4>
A project created with `grails create-rest-api-plugin`

```
apply plugin: "org.apache.grails.gradle.grails-plugin"
apply plugin: "org.apache.grails.gradle.grails-gson"
```

<h4>Grails Plugin Project</h4>
A project created with `grails create-plugin`

```
apply plugin: "org.apache.grails.gradle.grails-plugin"
```
