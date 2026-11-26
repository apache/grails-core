/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.forge.features.publish

import org.gradle.testkit.runner.BuildResult
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.OperatingSystem
import org.grails.forge.utils.CommandSpec
import spock.lang.Unroll

/**
 * A plugin that is generated with the feature {@code grails-publish} is built and published by its own
 * Gradle build, as it is generated: with the values of the {@code grailsPublish} block still to fill in
 * and without credentials. The snapshot is published to a directory of the test, by the Gradle property
 * the Grails Publish plugin reads the address of a Maven repository from, so nothing is written to the
 * local Maven repository of whoever runs the spec.
 *
 * <p>A REST API plugin is not built here: as it is generated it has no {@code grails-app/views}, which the
 * Gradle plugin of the JSON views asks for when it is applied, with the feature and without it. What the
 * feature gives that type is held by {@code GrailsPublishSpec} of the core module.</p>
 */
class GrailsPublishBuildSpec extends CommandSpec {

    @Unroll
    void 'a generated #type.name with grails-publish assembles as it is, and publishes a snapshot to a directory'() {
        given:
        generateProject(OperatingSystem.LINUX, ['grails-publish'], type)
        File repository = new File(dir, 'build/published-by-the-spec')

        when: 'the build as it is generated: its version is a release, and there is no key and no repository'
        BuildResult assembled = executeGradle('assemble')

        then:
        assembled.output.contains('BUILD SUCCESSFUL')

        when: 'a snapshot is published to the Maven repository the property names'
        BuildResult published = executeGradle('publish', '-Pversion=0.1-SNAPSHOT', "-PmavenPublishUrl=${repository.toURI()}".toString())
        // the artifact is named after the project, which is named after its directory
        File version = new File(repository, "example/grails/${dir.name}/0.1-SNAPSHOT")
        List<String> names = (version.list() ?: []) as List<String>
        String pom = version.listFiles()?.find { it.name.endsWith('.pom') }?.text

        then:
        published.output.contains('BUILD SUCCESSFUL')
        names.any { it.endsWith('.jar') && !it.endsWith('-sources.jar') && !it.endsWith('-javadoc.jar') }
        names.any { it.endsWith('-sources.jar') }
        names.any { it.endsWith('-javadoc.jar') }
        names.any { it.endsWith('-plugin.xml') }

        and: 'the POM has what the grailsPublish block says, which is still to fill in'
        pom.contains('<description>TODO: describe the plugin</description>')
        pom.contains('<url>https://github.com/TODO-owner/TODO-repository</url>')
        pom.contains('<name>The Apache Software License, Version 2.0</name>')
        pom.contains('<id>todo</id>')

        where:
        type << [ApplicationType.PLUGIN, ApplicationType.WEB_PLUGIN]
    }

    @Override
    String getTempDirectoryPrefix() {
        'testgrailspublish'
    }
}
