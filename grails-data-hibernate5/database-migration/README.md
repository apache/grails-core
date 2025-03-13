[![Java CI](https://github.com/grails/grails-database-migration/actions/workflows/gradle.yml/badge.svg)](https://github.com/grails/grails-database-migration/actions/workflows/gradle.yml)

# Grails Database Migration Plugin

## Branches

**9.0.x** Version of the plugin compatible with Grails 7 and Liquibase 4.27

**5.0.x** Version of the plugin compatible with Grails 6 and Liquibase 4.19

**4.0.x** Version of the plugin compatible with Grails 5 and Liquibase 4.6

**3.x** Version of the plugin compatible with Grails 3 / 4 and Hibernate 5.

**2.x**. Version of the plugin compatible with Grails 3 and Hibernate 4.

**1.x** There is a 1.x branch for on-going maintenance of 1.x versions of the plugin compatible with Grails 2. 

Please submit any pull requests to the appropriate branch.  

Changes to the 1.x branch or 2.x branch will be merged into the master branch if appropriate.

## Overview

The Database Migration plugin helps you manage database changes while developing Grails applications. The plugin uses the Liquibase library. Using this plugin (and Liquibase in general) adds some structure and process to managing database changes. It will help avoid inconsistencies, communication issues, and other problems with ad-hoc approaches.

Database migrations are represented in text form, either using a Groovy DSL or native Liquibase XML, in one or more changelog files. This approach makes it natural to maintain the changelog files in source control and also works well with branches. Changelog files can include other changelog files, so often developers create hierarchical files organized with various schemes.
One popular approach is to have a root changelog named changlog.groovy (or changelog.xml) and to include a changelog per feature/branch that includes multiple smaller changelogs. Once the feature is finished and merged into the main development tree/trunk the changelog files can either stay as they are or be merged into one large file. Use whatever approach makes sense for your applications, but keep in mind that there are many options available for changelog management.

## Versions
* 1.x: Grails 2
* 2.x: Grails 3 with Hibernate 4
* 3.x: Grails 3 with Hibernate 5
* 4.0.x Grails 5
* 5.0.x Grails 6
* 9.0.x Grails 7

## Documentation
* Latest https://gorm.grails.org/latest/hibernate/manual/#_grails_database_migration_plugin
* Grails 2: https://grails.github.io/grails-database-migration/docs/manual/index.html
* Grails 3 (Hibernate 4): https://grails.github.io/grails-database-migration/2.0.x/index.html
* Grails 3/4 (Hibernate 5): https://grails.github.io/grails-database-migration/3.0.x/index.html
* Grails 5 (Hibernate 5): https://grails.github.io/grails-database-migration/4.0.x/index.html
* Grails 6 (Hibernate 5): https://grails.github.io/grails-database-migration/5.0.x/index.html
* Grails 7 (Hibernate 5): https://gorm.grails.org/9.0.x/hibernate/manual/#_grails_database_migration_plugin
* Snapshot: https://gorm.grails.org/snapshot/hibernate/manual/#_grails_database_migration_plugin


## Package distribution

Software is distributed on [Maven Central](https://mvnrepository.com/artifact/org.grails.plugins/database-migration)
