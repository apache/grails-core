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
package grails.init

import java.util.concurrent.TimeUnit

import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.lang.TempDir

/**
 * What the Grails wrapper writes, started by its script in a project as a user or an agent starts it: every message
 * of the wrapper goes to standard error, and standard output is the command line's alone. Under {@code ./grailsw mcp}
 * standard output carries the Model Context Protocol, so a line of the wrapper there, about a download or an update,
 * would be a line the client cannot read. The command line of these tests is a jar that writes one line, with its
 * arguments, to standard output; the scripts and the jar of the wrapper are the ones the build generates.
 */
class WrapperOutputSpec extends Specification {

    private static final boolean WINDOWS = System.getProperty('os.name').toLowerCase(Locale.ROOT).contains('windows')

    @TempDir
    File dir

    // nothing is asked of the network: a request for a remote repository goes to this proxy, which refuses it
    @AutoCleanup
    RefusingProxy proxy = new RefusingProxy()

    File project
    File home
    File repository

    void setup() {
        project = new File(dir, 'shop')
        project.mkdirs()
        File scripts = new File(System.getProperty('grails.wrapper.scripts'))
        ['grailsw', 'grailsw.bat'].each { String name ->
            new File(project, name).bytes = new File(scripts, name).bytes
        }
        new File(project, 'grailsw').setExecutable(true)
        new File(project, 'grails-wrapper.jar').bytes = new File(System.getProperty('grails.wrapper.jar')).bytes
        home = new File(dir, 'home')
        home.mkdirs()
        repository = new File(dir, 'repository')
        cliDirectory().mkdirs()
    }

    void "a download writes nothing to standard output: what the command line writes is all it has"() {
        given: 'a project of a release that is not installed'
        inProjectOf('9.0.0')
        publish('9.0.0')

        when:
        Map result = grailsw('mcp')

        then: 'standard output is the line of the command line, with the arguments it was given'
        result.status == 0
        result.out.readLines() == ["${CliJars.STARTED}mcp".toString()]

        and: 'what the wrapper says of the download is on standard error'
        result.err.contains("...Update Repository is overridden to prefer [${repository.path}].")
        result.err.contains('Updating Grails wrapper, allowed versions to update to are [RELEASE]...')
        result.err.contains("...Moved local downloaded jar to: ${installed('9.0.0').canonicalFile.parentFile.path}")
        result.err.contains('Updated wrapper to version: 9.0.0')
        proxy.requestedHosts.empty
    }

    void "an update writes nothing to standard output, and neither does a start that downloads nothing"() {
        given: 'a project of a release that is installed'
        inProjectOf('9.0.0')
        publish('9.0.0')
        assert grailsw('mcp').status == 0

        when: 'the command line is downloaded again for update-wrapper, which the command line of Grails 9 is given'
        Map updated = grailsw('update-wrapper')

        then:
        updated.status == 0
        updated.out.readLines() == ["${CliJars.STARTED}update-wrapper".toString()]
        updated.err.contains('Updated wrapper to version: 9.0.0')

        when: 'the wrapper starts the command line it has'
        Map started = grailsw('mcp', '--read-only')

        then:
        started.status == 0
        started.out.readLines() == ["${CliJars.STARTED}mcp,--read-only".toString()]
        !started.err.contains('Updated wrapper')
    }

    void "a snapshot, which is downloaded on every start, writes nothing to standard output"() {
        given: 'a project of a snapshot'
        inProjectOf('9.0.0-SNAPSHOT')
        publish('9.0.0-SNAPSHOT')
        new File(cliDirectory(), '9.0.0-SNAPSHOT/maven-metadata-local.xml').text = '<metadata><versioning><snapshotVersions><snapshotVersion>' +
                '<classifier>all</classifier><extension>jar</extension><value>9.0.0-SNAPSHOT</value>' +
                '</snapshotVersion></snapshotVersions></versioning></metadata>'

        when:
        List<Map> runs = [grailsw('mcp'), grailsw('mcp')]

        then:
        runs.every { Map result ->
            result.status == 0 &&
                    result.out.readLines() == ["${CliJars.STARTED}mcp".toString()] &&
                    result.err.contains('...A Grails snapshot version has been detected. Downloading latest snapshot.') &&
                    result.err.contains('... Using Snapshot URL: ') &&
                    result.err.contains('Updated wrapper to version: 9.0.0-SNAPSHOT')
        }
    }

    void "a command line that is installed and cannot be started is downloaded again, and only standard error says so"() {
        given: 'what an interrupted download left with a wrapper that did not look into it'
        inProjectOf('9.0.0')
        publish('9.0.0')
        installed('9.0.0').parentFile.mkdirs()
        installed('9.0.0').text = 'PK the start of a jar and no more'

        when:
        Map result = grailsw('mcp')

        then:
        result.status == 0
        result.out.readLines() == ["${CliJars.STARTED}mcp".toString()]
        result.err.contains('). Downloading it again.')
    }

    void "a wrapper that cannot start the command line says why on standard error, and writes nothing to standard output"() {
        given: 'a project of a release that no repository has'
        inProjectOf('9.0.0')

        when:
        Map result = grailsw('mcp')

        then:
        result.status == 1
        result.out.empty
        result.err.contains('Unable to execute Grails Wrapper: Could not find the Grails CLI in any repository:')
    }

    private void inProjectOf(String version) {
        new File(project, 'gradle.properties').text = "grailsVersion=${version}\n"
    }

    private File cliDirectory() {
        new File(repository, 'org/apache/grails/grails-cli')
    }

    private void publish(String version) {
        File jar = new File(cliDirectory(), "${version}/grails-cli-${version}-all.jar")
        jar.parentFile.mkdirs()
        jar.bytes = CliJars.jar("the command line of ${version}")
    }

    private File installed(String version) {
        new File(home, "wrapper/${version}/grails-cli-${version}-all.jar")
    }

    // runs grailsw of the project, or grailsw.bat on Windows, as a user who types it does
    private Map grailsw(String... arguments) {
        List<String> command = WINDOWS ? [System.getenv('ComSpec') ?: 'cmd.exe', '/d', '/c', 'grailsw.bat'] : [new File(project, 'grailsw').path]
        command.addAll(arguments)
        File out = File.createTempFile('out', '.txt', dir)
        File err = File.createTempFile('err', '.txt', dir)
        ProcessBuilder builder = new ProcessBuilder(command).directory(project).redirectOutput(out).redirectError(err)
        ['GRAILS_REPO_URL', 'PREFERRED_GRAILS_VERSION', 'GRAILS_WRAPPER_ALLOWED_TYPES', 'GRAILS_WRAPPER_HOME', 'JAVA_TOOL_OPTIONS', 'JAVA_OPTS',
         'GRAILSW_OPTS', 'DEBUG'].each { String name ->
            builder.environment().remove(name)
        }
        builder.environment().put('JAVA_HOME', System.getProperty('java.home'))
        builder.environment().put('GRAILS_WRAPPER_HOME', home.path)
        builder.environment().put('GRAILS_REPO_URL', repository.path)
        builder.environment().put('JAVA_OPTS', proxy.systemProperties.collect { String name, String value -> "-D${name}=${value}" }.join(' '))
        Process process = builder.start()
        try {
            assert process.waitFor(3, TimeUnit.MINUTES): "the wrapper did not end:\n${out.text}\n${err.text}"
            return [status: process.exitValue(), out: out.text, err: err.text]
        } finally {
            process.destroyForcibly()
        }
    }
}
