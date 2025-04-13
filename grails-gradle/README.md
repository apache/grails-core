Grails Gradle Plugins
========

Latest API Docs: https://grails.github.io/grails-gradle-plugin/latest/api/

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

grails-publish
---------
A Gradle plugin to ease publishing with the maven publish plugin or the nexus publish plugin.

Artifacts published by this plugin include sources, the jar file, and a javadoc jar that contains both the groovydoc & javadoc.

Example Usage:

    grailsPublish {
        websiteUrl = 'http://foo.com/myplugin'
        license {
            name = 'Apache-2.0'
        }
        issueTrackerUrl = 'https://github.com/myname/myplugin/issues'
        vcsUrl = 'https://github.com/myname/myplugin'
        title = 'My plugin title'
        desc = 'My plugin description'
        developers = [johndoe: 'John Doe']
    }

or

    grailsPublish {
        githubSlug = 'foo/bar'
        license {
            name = 'Apache-2.0'
        }
        title = 'My plugin title'
        desc = 'My plugin description'
        developers = [johndoe: 'John Doe']
    }

By default, this plugin will publish to the specified `MAVEN_PUBLISH` instance for snapshots, and `NEXUS_PUBLISH` for releases.  To change the snapshot publish behavior, set `snapshotRepoType` to `PublishType.NEXUS_PUBLISH`. To change the release publish behavior, set `releaseRepoType` to `PublishType.MAVEN_PUBLISH`.

The credentials and connection url must be specified as a project property or an environment variable.

`MAVEN_PUBLISH` Environment Variables are:

    MAVEN_PUBLISH_USERNAME
    MAVEN_PUBLISH_PASSWORD
    MAVEN_PUBLISH_URL

`NEXUS_PUBLISH` Environment Variables are:

    NEXUS_PUBLISH_USERNAME
    NEXUS_PUBLISH_PASSWORD
    NEXUS_PUBLISH_URL
    NEXUS_PUBLISH_SNAPSHOT_URL
    NEXUS_PUBLISH_STAGING_PROFILE_ID

By default, the release or snapshot state is determined by the project.version or projectVersion gradle property.  To override this behavior, use the environment variable `GRAILS_PUBLISH_RELEASE` to decide if it's a release or snapshot.

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
apply plugin: "org.grails.grails-web"
apply plugin: "org.grails.grails-gsp"
```

<h4>Grails Web API Project</h4>
----
A project created with a typical `grails create-app --profile=web-api`

```
apply plugin: "org.grails.grails-web"
```

<h4>Grails Web Micro Project</h4>

A project created with a typical `grails create-app --profile=web-micro`

There is no plugins used here as this project type creates a stand alone runnable groovy application and no `build.gradle` file.


Create Plugin
---

<h4>Grails Plugin Web Project</h4>
A project created with a typical `grails create-plugin --profile=web-plugin`

```
apply plugin: "org.grails.grails-plugin"
apply plugin: "org.grails.grails-gsp"
```

<h4>Grails Plugin Web API Project</h4>
A project created with a typical `grails create-plugin --profile=web-api`. _Note: No org.grails.grails-plugin include_

```
apply plugin: "org.grails.grails-web"
```


<h4>Grails Plugin Web Plugin Project</h4>
A project created with a typical `grails create-plugin --profile=plugin`.

```
apply plugin: "org.grails.grails-plugin"
```

<h4>Grails Plugin Web Micro Project</h4>

A project created with a typical `grails create-plugin --profile=web-micro`

There is no plugins used here as this project type creates a stand alone runnable groovy application and no `build.gradle`` file.
