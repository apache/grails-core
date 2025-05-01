/*
 * Copyright 2017-2024 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.forge.feature.migration;

import jakarta.inject.Singleton;
import org.grails.forge.application.generator.GeneratorContext;
import org.grails.forge.build.dependencies.Dependency;
import org.grails.forge.feature.migration.templates.dbMigrationGradle;
import org.grails.forge.template.RockerWritable;

@Singleton
public class DatabaseMigrationPlugin implements MigrationFeature {

    @Override
    public String getName() {
        return "database-migration";
    }

    @Override
    public String getTitle() {
        return "Grails Database Migration Plugin";
    }

    @Override
    public String getDescription() {
        return "Adds support for Liquibase database migrations. The Database Migration plugin helps you manage database changes while developing Grails applications. ";
    }

    public String getThirdPartyDocumentation() {
        return "https://www.liquibase.org/";
    }

    @Override
    public String getDocumentation() {
        return "https://grails.github.io/grails-database-migration/latest/";
    }

    @Override
    public void apply(GeneratorContext generatorContext) {
        final String srcDirPath = getSrcDirPath();
        generatorContext.addBuildscriptDependency(Dependency.builder()
                .groupId("org.apache.grails")
                .artifactId("grails-data-hibernate5-dbmigration")
                .buildSrc()
                .extension(new RockerWritable(dbMigrationGradle.template(srcDirPath))));
        generatorContext.addDependency(Dependency.builder()
                .groupId("org.apache.grails")
                .artifactId("grails-data-hibernate5-dbmigration")
                .implementation());
    }

    private String getSrcDirPath() {
        return "grails-app/migrations";
    }
}
