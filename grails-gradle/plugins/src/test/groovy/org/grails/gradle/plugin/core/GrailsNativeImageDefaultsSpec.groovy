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
import org.gradle.testkit.runner.GradleRunner

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

        then: 'the compiler keeps its own default, invokedynamic, as it does for every application that set no other'
            result.output.contains('INDY_OPTION=unset')

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
            result.output.contains('INDY_OPTION=true')
    }

    void 'an application that builds an image and turns invokedynamic off fails the build with the reason'() {
        given:
            GradleRunner runner = setupTestResourceProject('native-defaults-on')

        when:
            BuildResult result = runner.withArguments(['inspectDefaults', '-PfixtureIndy=false', '--stacktrace']).forwardOutput().buildAndFail()

        then: 'rather than building an image that fails on its first dynamic call'
            result.output.contains('A native image needs every Groovy class compiled with invokedynamic')
            result.output.contains('Remove grails { indy = false }')
    }

    void 'an application that builds an image and sets invokedynamic on explicitly builds as before'() {
        given:
            setupTestResourceProject('native-defaults-on')

        when:
            BuildResult result = executeTask('inspectDefaults', ['-PfixtureIndy=true'])

        then:
            result.output.contains('INDY_OPTION=true')
    }

    void 'a compile task a build script set to classic call sites is compiled with invokedynamic when the application builds an image'() {
        given:
            setupTestResourceProject('native-defaults-on')

        when:
            BuildResult result = executeTask('inspectDefaults', ['-PfixtureTaskIndy=false'])

        then: 'the plugin applies the invokedynamic convention to every Groovy compile task after the build script'
            result.output.contains('INDY_OPTION=true')
    }

    void 'a compile task set to classic call sites after the plugin has configured it fails as it starts'() {
        given: 'a build script afterEvaluate runs after the plugin\'s, so the task keeps the classic setting'
            GradleRunner runner = setupTestResourceProject('native-defaults-on')

        when:
            BuildResult result = runner.withArguments(['compileGroovy', '-PfixtureLateTaskIndy=false', '--stacktrace']).forwardOutput().buildAndFail()

        then: 'the task refuses to compile rather than feed classic call sites to the image'
            result.output.contains('A native image needs every Groovy class compiled with invokedynamic')
            result.output.contains(':compileGroovy is configured to compile classic call sites')
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
