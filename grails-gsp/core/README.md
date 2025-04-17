## Grails GSP

This subproject is required for all Grails applications and plugins that require GSP processing.  If your project includes GSPs you should add the following to your `build.gradle` which is provided by the [Grails Gradle Plugin](https://github.com/grails/grails-core/tree/master/grails-gradle-plugin).

``` gradle
apply plugin: "org.grails.grails-gsp"
```

It is typical of standard Grails application to use this in conjunction with `grails-web` as in the following example:

``` gradle
apply plugin: "org.grails.grails-web"
apply plugin: "org.grails.grails-gsp"
```

Dependencies
-----
To see what additional subprojects will be included with this, you can view this project's [build.gradle](https://github.com/grails/grails-core/blob/master/grails-gsp/build.gradle)
