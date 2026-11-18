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
package org.grails.gradle.plugin.core

import org.gradle.testkit.runner.BuildResult

/**
 * Covers what an application gets for building a native image, and what it does not get for not
 * building one.
 *
 * <p>These are reactions to the plugin the application applied, not a setting it has to find.
 * Applying GraalVM's plugin is already how an application says it wants an image; nothing here is
 * reached without it, so an application that has no use for any of it pays nothing and sees no
 * change.</p>
 *
 * @since 8.0
 * @see GrailsGradlePlugin#configureNativeImage
 */
class GrailsNativeImageDefaultsSpec extends GradleSpecification {

    void 'an application that builds no image is left as it was'() {
        given:
            setupTestResourceProject('native-defaults-off')

        when:
            BuildResult result = executeTask('inspectDefaults')

        then: 'invokedynamic stays off, as it is for every application that did not ask for it'
            result.output.contains('INDY=false')

        and: 'and nothing an image would want has been added'
            result.output.contains('HAS_NATIVE_EXTENSION=false')
    }

    void 'definitions are generated for the environment the application will run in'() {
        given: 'the plugin that asks for generated definitions is what this reacts to'
            setupTestResourceProject('native-defaults-aot')

        when:
            BuildResult result = executeTask('inspectAot')

        then: 'development declares reloadable beans, which cannot be written out as code'
            result.output.contains('GRAILS_ENV=production')
    }

    void 'an application that builds an image gets what an image needs'() {
        given:
            setupTestResourceProject('native-defaults-on')

        when:
            BuildResult result = executeTask('inspectDefaults')

        then: 'a classic call site defines a class as it runs, which an image has no way to do'
            result.output.contains('INDY=true')
    }

    void 'the pages are recorded after they are compiled, not before'() {
        given: 'an application with pages, which are half of what an image has to be told about'
            setupTestResourceProject('native-metadata-ordering')

        when: 'the archive is built'
            BuildResult result = executeTask('bootJar', ['--dry-run'])

        then: 'the task that reads the compiled pages runs after the task that compiles them'
            int compiled = result.output.indexOf(':compileGroovyPages')
            int recorded = result.output.indexOf(':generateNativeMetadata')
            compiled >= 0
            recorded >= 0
            compiled < recorded
    }

    void 'the recorded metadata is not reached through processResources'() {
        given: 'which would put it ahead of the pages, since compiling those runs the classes task'
            setupTestResourceProject('native-metadata-ordering')

        when:
            BuildResult result = executeTask('inspectMetadataWiring')

        then: 'so the resource task no longer consumes it'
            result.output.contains('PROCESS_RESOURCES_DEPENDS_ON_METADATA=false')

        and: 'and the archive does, which is built once the pages exist'
            result.output.contains('BOOTJAR_DEPENDS_ON_METADATA=true')
    }
}
