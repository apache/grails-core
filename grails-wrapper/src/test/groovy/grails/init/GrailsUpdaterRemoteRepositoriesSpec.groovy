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

import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

/**
 * The repositories the wrapper asks when no repository is named: Maven Central, then the ASF repository. Both are
 * served by a proxy on the loopback address, so nothing is asked of the network.
 */
class GrailsUpdaterRemoteRepositoriesSpec extends Specification {

    private static final String CENTRAL = 'https://repo1.maven.org/maven2/org/apache/grails/grails-cli'
    private static final String ASF = 'https://repository.apache.org/content/groups/public/org/apache/grails/grails-cli'
    private static final String RELEASE_JAR = 'org/apache/grails/grails-cli/9.0.0/grails-cli-9.0.0-all.jar'
    private static final String SNAPSHOT_VALUE = '9.0.0-20261120.101112-3'
    private static final String NOT_WHOLE = 'No other repository was asked: the command line of another repository does not take the place of a ' +
            'download that is not whole. Run the command again.'

    @TempDir
    File dir

    RepositoryProxy proxy
    File home
    File central
    File asf
    Process process

    void setup() {
        home = new File(dir, 'home')
        home.mkdirs()
        central = new File(dir, 'central')
        asf = new File(dir, 'asf')
        proxy = new RepositoryProxy(dir)
    }

    void cleanup() {
        process?.destroyForcibly()?.waitFor()
        proxy?.close()
    }

    def "a release of Maven Central that does not match its checksum ends the wrapper with status 1, and the ASF repository is not asked"() {
        given:
        File tampered = jar(central, RELEASE_JAR, 'the jar of Maven Central')
        CliJars.writeChecksums(tampered, ['sha1'])
        tampered.bytes = CliJars.jar('a jar in the place of the published one')
        jar(asf, RELEASE_JAR, 'the jar of the ASF repository')
        proxy.serve(RepositoryProxy.CENTRAL, central).serve(RepositoryProxy.ASF, asf)

        when:
        Map result = runWrapper([PREFERRED_GRAILS_VERSION: '9.0.0'], ['help'])

        then: 'the message names Maven Central and why'
        result.status == 1
        result.out.contains("Unable to execute Grails Wrapper: The Grails CLI that the repository [${CENTRAL}] gave is not installed: " +
                "the download does not match the SHA-1 checksum of the repository: it was cut off or is corrupt. ${NOT_WHOLE}")
        !result.out.contains('\tat ')
        !result.err.contains('\tat ')

        and: 'the jar and its checksum files were asked of Maven Central, and nothing of the ASF repository'
        proxy.requests == ['', '.sha512', '.sha256', '.sha1'].collect { String suffix -> "${RepositoryProxy.CENTRAL} ${RELEASE_JAR}${suffix}".toString() }
        proxy.requestedHosts.every { String host -> host == RepositoryProxy.CENTRAL }

        and: 'nothing is installed or started'
        !result.out.contains(CliJars.STARTED)
        new File(home, 'wrapper').list().length == 0
    }

    def "a snapshot of the ASF repository that does not match its checksum ends the wrapper with status 1 and that repository"() {
        given: 'Maven Central has no snapshots, and the ASF repository has the snapshot in the layout of a remote repository'
        new File(asf, 'org/apache/grails/grails-cli/9.0.0-SNAPSHOT').mkdirs()
        new File(asf, 'org/apache/grails/grails-cli/9.0.0-SNAPSHOT/maven-metadata.xml').text = snapshotMetadata()
        File tampered = jar(asf, "org/apache/grails/grails-cli/9.0.0-SNAPSHOT/grails-cli-${SNAPSHOT_VALUE}-all.jar", 'the jar of the ASF repository')
        CliJars.writeChecksums(tampered, ['sha256'])
        tampered.bytes = CliJars.jar('a jar in the place of the published one')
        central.mkdirs()
        proxy.serve(RepositoryProxy.CENTRAL, central).serve(RepositoryProxy.ASF, asf)

        when:
        Map result = runWrapper([PREFERRED_GRAILS_VERSION: '9.0.0-SNAPSHOT'], ['help'])

        then: 'the message names the ASF repository and why, and is not that no repository has the snapshot'
        result.status == 1
        result.out.contains("Unable to execute Grails Wrapper: The Grails CLI that the repository [${ASF}] gave is not installed: " +
                "the download does not match the SHA-256 checksum of the repository: it was cut off or is corrupt. ${NOT_WHOLE}")
        !result.out.contains('Could not find the Grails CLI in any repository')
        !result.out.contains(CliJars.STARTED)
        new File(home, 'wrapper').list().length == 0

        and: 'Maven Central was asked for the metadata of the snapshot, which it does not have'
        proxy.requests.first() == "${RepositoryProxy.CENTRAL} org/apache/grails/grails-cli/9.0.0-SNAPSHOT/maven-metadata.xml".toString()
    }

    def "a download of Maven Central that breaks off ends the wrapper with status 1, and the ASF repository is not asked"() {
        given: 'Maven Central sends the length of the jar and half of it'
        long length = jar(central, RELEASE_JAR, 'the jar of Maven Central').length()
        jar(asf, RELEASE_JAR, 'the jar of the ASF repository')
        proxy.serve(RepositoryProxy.CENTRAL, central).serve(RepositoryProxy.ASF, asf).cutOff(RepositoryProxy.CENTRAL, RELEASE_JAR)

        when:
        Map result = runWrapper([PREFERRED_GRAILS_VERSION: '9.0.0'], ['help'])

        then:
        result.status == 1
        result.out.contains("Unable to execute Grails Wrapper: The Grails CLI that the repository [${CENTRAL}] gave is not installed: " +
                "the download failed: IOException: it was cut off after ${length.intdiv(2)} of ${length} bytes. ${NOT_WHOLE}")
        !result.out.contains(CliJars.STARTED)
        new File(home, 'wrapper').list().length == 0
        proxy.requests == ["${RepositoryProxy.CENTRAL} ${RELEASE_JAR}".toString()]
        proxy.requestedHosts == [RepositoryProxy.CENTRAL]
    }

    @Unroll
    def "a release is taken from the ASF repository when Maven Central #situation"() {
        given:
        central.mkdirs()
        CliJars.writeChecksums(jar(asf, RELEASE_JAR, 'the jar of the ASF repository'), ['sha1'])
        proxy.serve(RepositoryProxy.ASF, asf)
        if (served) {
            proxy.serve(RepositoryProxy.CENTRAL, central)
        }
        if (status) {
            proxy.answer(RepositoryProxy.CENTRAL, RELEASE_JAR, status)
        }

        when:
        Map result = runWrapper([PREFERRED_GRAILS_VERSION: '9.0.0'], ['help'])

        then:
        result.status == 0
        result.out.contains("${CliJars.STARTED}help")
        CliJars.marker(new File(home, 'wrapper/9.0.0/grails-cli-9.0.0-all.jar')) == 'the jar of the ASF repository'
        proxy.requestedHosts.first() == RepositoryProxy.CENTRAL
        proxy.requests.contains("${RepositoryProxy.ASF} ${RELEASE_JAR}".toString())

        and: 'Maven Central is named on standard error where it failed, and not where it does not have the jar'
        result.err.readLines() == (line == null ? [] : [line])

        where:
        situation               | served | status || line
        'does not have it'      | true   | null   || null
        'cannot be reached'     | false  | null   || "The Grails CLI could not be taken from the repository [${CENTRAL}]: the download failed: IOException: Unable to tunnel through proxy. Proxy returns \"HTTP/1.1 404 Not Found\"".toString()
        'answers with an error' | true   | 503    || "The Grails CLI could not be taken from the repository [${CENTRAL}]: the download failed: IOException: HTTP 503 for ${CENTRAL}/9.0.0/grails-cli-9.0.0-all.jar".toString()
    }

    private static File jar(File repository, String path, String marker) {
        File jar = new File(repository, path)
        jar.parentFile.mkdirs()
        jar.bytes = CliJars.jar(marker)
        jar
    }

    private static String snapshotMetadata() {
        """\
            <metadata>
              <groupId>org.apache.grails</groupId>
              <artifactId>grails-cli</artifactId>
              <version>9.0.0-SNAPSHOT</version>
              <versioning>
                <snapshot><timestamp>20261120.101112</timestamp><buildNumber>3</buildNumber></snapshot>
                <snapshotVersions>
                  <snapshotVersion>
                    <classifier>all</classifier>
                    <extension>jar</extension>
                    <value>${SNAPSHOT_VALUE}</value>
                  </snapshotVersion>
                </snapshotVersions>
              </versioning>
            </metadata>
            """.stripIndent()
    }

    /**
     * Runs the wrapper in a JVM of its own, as {@code grailsw} does, in a directory that is no project, without a
     * repository named, and with the proxy for the remote repositories.
     *
     * @return status, and what was written to out and err
     */
    private Map runWrapper(Map<String, String> environment, List<String> arguments) {
        File workingDirectory = new File(dir, 'working')
        workingDirectory.mkdirs()
        File out = new File(dir, 'out.txt')
        File err = new File(dir, 'err.txt')
        List<String> command = [new File(System.getProperty('java.home'), 'bin/java').path]
        command.addAll(proxy.systemProperties.collect { String name, String value -> "-D${name}=${value}".toString() })
        command.add("-Duser.home=${new File(dir, 'user').path}".toString())
        command.addAll(['-cp', new File(GrailsUpdater.protectionDomain.codeSource.location.toURI()).path, 'grails.init.Start'])
        command.addAll(arguments)
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(workingDirectory)
                .redirectOutput(out)
                .redirectError(err)
        ['GRAILS_REPO_URL', 'PREFERRED_GRAILS_VERSION', 'GRAILS_WRAPPER_ALLOWED_TYPES', 'JAVA_TOOL_OPTIONS'].each { String name ->
            builder.environment().remove(name)
        }
        builder.environment().put('GRAILS_WRAPPER_HOME', home.path)
        builder.environment().putAll(environment)
        process = builder.start()
        assert process.waitFor(5, TimeUnit.MINUTES)
        [status: process.exitValue(), out: out.text, err: err.text]
    }
}
