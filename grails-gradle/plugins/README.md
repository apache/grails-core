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

grails-profile
---------
_Todo_: Add the docs

grails-profile-publish
---------
_Todo_: Add the docs

grails-web
---------
* Adds web specific extensions


Typical Project Type Gradle Plugin Includes
========
Below are typical Gradle plugin applies that certain types of projects should expect.  These should be automatically added of you when using `grails create-app` and `grails create-plugin` commands.  However, if you wish to enhance or change the scope of your plugin or project you may have to change (add or remove) a grails gradle plugin.

Create App
----

<h4>Grails Web Project</h4>
-----
A project created with a typical `grails create-app --profile=web`

```
apply plugin: "org.apache.grails.gradle.grails-web"
apply plugin: "org.apache.grails.gradle.grails-gsp"
```

<h4>Grails Web API Project</h4>
----
A project created with a typical `grails create-app --profile=web-api`

```
apply plugin: "org.apache.grails.gradle.grails-web"
```

<h4>Grails Web Micro Project</h4>

A project created with a typical `grails create-app --profile=web-micro`

There is no plugins used here as this project type creates a stand alone runnable groovy application and no `build.gradle` file.


Create Plugin
---

<h4>Grails Plugin Web Project</h4>
A project created with a typical `grails create-plugin --profile=web-plugin`

```
apply plugin: "org.apache.grails.gradle.grails-plugin"
apply plugin: "org.apache.grails.gradle.grails-gsp"
```

<h4>Grails Plugin Web API Project</h4>
A project created with a typical `grails create-plugin --profile=web-api`. _Note: No org.apache.grails.gradle.grails-plugin include_

```
apply plugin: "org.apache.grails.gradle.grails-web"
```


<h4>Grails Plugin Web Plugin Project</h4>
A project created with a typical `grails create-plugin --profile=plugin`.

```
apply plugin: "org.apache.grails.gradle.grails-plugin"
```

<h4>Grails Plugin Web Micro Project</h4>

A project created with a typical `grails create-plugin --profile=web-micro`

There is no plugins used here as this project type creates a stand alone runnable groovy application and no `build.gradle`` file.
