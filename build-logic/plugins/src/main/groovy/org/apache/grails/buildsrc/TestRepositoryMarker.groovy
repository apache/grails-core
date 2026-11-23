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
package org.apache.grails.buildsrc

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.temporal.ChronoUnit

import groovy.transform.CompileStatic

/**
 * The record a build leaves in the repository of the tests ({@code build/local-maven}) when it has
 * published all of its modules there: what was published, when, and from which sources.
 *
 * <p>Two builds publish into that repository, the framework ({@link #FRAMEWORK}) and the Grails Gradle
 * plugins ({@link #GRADLE_PLUGINS}), and each leaves a record of its own under {@link #DIRECTORY}. It is
 * written by the task {@code publishAllPublicationsToTestCaseMavenRepoRepository} of the root project,
 * after every publish it depends on. Each individual Maven publication invalidates the old record first,
 * so a partial or failed publication cannot be mistaken for the last complete publication.</p>
 *
 * <p>The record is what lets a build use the repository without publishing it first, and refuse one that
 * is not what it needs: see {@link PrebuiltTestRepository}.</p>
 */
@CompileStatic
class TestRepositoryMarker {

    /** The directory of the records in the repository. It is no hidden one: a workflow artifact leaves those out. */
    static final String DIRECTORY = 'grails-test-repository'

    /** The name of the record of the framework build. */
    static final String FRAMEWORK = 'grails-core'

    /** The name of the record of the build of the Grails Gradle plugins. */
    static final String GRADLE_PLUGINS = 'grails-gradle'

    /** The call sites of the Grails plugin modules, {@link #INDY} unless the build ran with {@code -PgrailsIndy=false}. */
    static final String CALL_SITES = 'callSites'
    static final String INDY = 'indy'
    static final String CLASSIC = 'classic'

    static final String VERSION = 'version'
    static final String PUBLISHED = 'published'
    static final String PUBLICATION = 'publication'
    static final String SBOM = 'sbom'
    static final String FINGERPRINT = 'fingerprint'

    /**
     * The directory, in the repository, of the copies of {@link #FRAMEWORK_OUTPUTS}. A copy has the path the
     * file has in the checkout.
     */
    static final String FRAMEWORK_OUTPUTS_DIRECTORY = "${DIRECTORY}/framework"

    /**
     * What the build of Forge takes from the framework build that is no published module, by its path in
     * the checkout: the POMs of the BOMs as they are generated, which the generator reads the versions of
     * Grails from, and the Grails wrapper as it is installed, which every generated application gets (the
     * start scripts are in no publication). The publish of the framework build leaves a copy of each in the
     * repository, so that a build that uses the repository without the framework build has them.
     */
    static final List<String> FRAMEWORK_OUTPUTS = [
            'grails-bom/base/build/publications/maven/pom-default.xml',
            'grails-bom/default/build/publications/maven/pom-default.xml',
            'grails-bom/hibernate7/build/publications/maven/pom-default.xml',
            'grails-wrapper/build/install/apache-grails-wrapper-bin/grailsw',
            'grails-wrapper/build/install/apache-grails-wrapper-bin/grailsw.bat',
            'grails-wrapper/build/install/apache-grails-wrapper-bin/grails-wrapper.jar',
    ].asImmutable()

    /** The task of the framework build that installs the Grails wrapper of {@link #FRAMEWORK_OUTPUTS}. */
    static final String WRAPPER_INSTALL_TASK = ':grails-wrapper:installDist'

    /** The fingerprint of a publish from sources Git could say nothing about. */
    static final String UNKNOWN = 'unknown'

    private TestRepositoryMarker() {
    }

    static File file(File repository, String build) {
        return new File(repository, "${DIRECTORY}/${build}.properties")
    }

    static File sourcesFile(File repository, String build) {
        return new File(repository, "${DIRECTORY}/${build}.sources")
    }

    /**
     * Writes the record of a build.
     *
     * @param repository the repository of the tests
     * @param build {@link #FRAMEWORK} or {@link #GRADLE_PLUGINS}
     * @param values what was published: {@link #VERSION}, {@link #CALL_SITES}, {@link #PUBLICATION}, {@link #SBOM}
     * @param sources the sources it was published from, or {@code null} when they are not known
     */
    static void write(File repository, String build, Map<String, String> values, FrameworkSources sources) {
        Map<String, String> record = new TreeMap<String, String>(values)
        record.put(PUBLISHED, Instant.now().truncatedTo(ChronoUnit.SECONDS).toString())
        record.put(FINGERPRINT, sources?.fingerprint ?: UNKNOWN)
        File file = file(repository, build)
        file.parentFile.mkdirs()
        File sourcesFile = sourcesFile(repository, build)
        if (sources == null) {
            sourcesFile.delete()
        } else {
            sourcesFile.setText(sources.toText(), StandardCharsets.UTF_8.name())
        }
        // written last: the record is there only when all of it is
        file.setText(record.collect { String key, String value -> "${key}=${value}\n".toString() }.join(''), StandardCharsets.UTF_8.name())
    }

    /**
     * Copies {@link #FRAMEWORK_OUTPUTS} from a checkout into the repository. One that the checkout does not
     * have is left out, and removed from the repository.
     */
    static void copyFrameworkOutputs(File checkout, File repository) {
        for (String path : FRAMEWORK_OUTPUTS) {
            File output = new File(checkout, path)
            File copy = frameworkOutput(repository, path)
            if (output.isFile()) {
                copy.parentFile.mkdirs()
                copy.bytes = output.bytes
            } else {
                copy.delete()
            }
        }
    }

    static File frameworkOutput(File repository, String path) {
        return new File(repository, "${FRAMEWORK_OUTPUTS_DIRECTORY}/${path}")
    }

    /** @return the record of a build, or {@code null} when the repository has none */
    static Map<String, String> read(File repository, String build) {
        File file = file(repository, build)
        if (!file.isFile()) {
            return null
        }
        Map<String, String> record = [:]
        file.getText(StandardCharsets.UTF_8.name()).readLines().each { String line ->
            int equals = line.indexOf('=')
            if (equals > 0) {
                record.put(line.substring(0, equals), line.substring(equals + 1))
            }
        }
        return record
    }

    /** @return the sources a build was published from, or {@code null} when the repository does not say */
    static FrameworkSources readSources(File repository, String build) {
        File file = sourcesFile(repository, build)
        return file.isFile() ? FrameworkSources.parse(file.getText(StandardCharsets.UTF_8.name())) : null
    }
}
