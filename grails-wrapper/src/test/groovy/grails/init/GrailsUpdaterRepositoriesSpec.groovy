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
import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll
import uk.org.webcompere.systemstubs.SystemStubs

/**
 * The repositories the wrapper goes through for the command line: the ones the user named first, then the
 * remote ones. The repositories of these tests are directories, and the remote ones are behind a proxy
 * that refuses them, so nothing is asked of the network.
 */
class GrailsUpdaterRepositoriesSpec extends Specification {

    private static final List<String> REMOTE_HOSTS = ['repo1.maven.org', 'repository.apache.org']

    @TempDir
    File dir

    @AutoCleanup
    RefusingProxy proxy = new RefusingProxy()

    File home
    Process process

    void setup() {
        home = new File(dir, 'home')
        home.mkdirs()
    }

    void cleanup() {
        process?.destroyForcibly()?.waitFor()
    }

    def "a release the first named repository lacks is taken from the second, and the first is named"() {
        given:
        File first = repository('first')
        File second = repository('second')
        publishRelease(second, '9.0.0', 'release of the second')

        when:
        Map result = update([first, second], [GrailsReleaseType.RELEASE], '9.0.0')

        then: 'the second repository gave the jar, and no remote repository was asked'
        result.updated
        installed('9.0.0').text == 'release of the second'
        result.updater.selectedVersion == new GrailsVersion('9.0.0')
        result.updater.executedJarFile.canonicalFile == installed('9.0.0').canonicalFile
        proxy.requestedHosts.empty

        and: 'the error names the repository that did not have it'
        result.err.contains("There was an error downloading the wrapper jar from [${cliDirectory(first).path}]")
        !result.err.contains("[${cliDirectory(second).path}]")
    }

    def "without a version the newest allowed one of the first repository that lists versions is taken"() {
        given: 'a repository without the command line, and one that lists releases, a milestone and a snapshot'
        File first = repository('first')
        File second = repository('second')
        new File(cliDirectory(second), 'maven-metadata-local.xml').text = rootMetadata(['9.0.0', '9.0.1', '9.1.0-M1', '9.1.0-SNAPSHOT'])
        publishRelease(second, '9.0.0', 'the older release')
        publishRelease(second, '9.0.1', 'the newer release')

        when:
        Map result = update([first, second], [GrailsReleaseType.RELEASE], null)

        then:
        result.updated
        result.updater.selectedVersion == new GrailsVersion('9.0.1')
        installed('9.0.1').text == 'the newer release'
        !installed('9.0.0').exists()
        proxy.requestedHosts.empty

        and: 'the repository that was named and did not have it is said, the one that had it is not'
        result.out.contains("...The Grails CLI was not found in the overridden repository [${cliDirectory(first).path}]: ")
        result.out.contains('...Trying the next repository.')
        !result.out.contains("overridden repository [${cliDirectory(second).path}]")
    }

    def "a repository that lists no version of an allowed type is passed over"() {
        given:
        File first = repository('first')
        new File(cliDirectory(first), 'maven-metadata-local.xml').text = rootMetadata(['9.1.0-M1', '9.1.0-SNAPSHOT'])
        File second = repository('second')
        new File(cliDirectory(second), 'maven-metadata.xml').text = rootMetadata(['9.0.0'])
        publishRelease(second, '9.0.0', 'the release')

        when:
        Map result = update([first, second], [GrailsReleaseType.RELEASE], null)

        then:
        result.updated
        installed('9.0.0').text == 'the release'
        result.out.contains("overridden repository [${cliDirectory(first).path}]: No Grails Releases were found for the allowed types: RELEASE")
    }

    def "a snapshot that no repository has is an error that names every repository that was tried"() {
        given:
        File named = repository('named')

        when:
        update([named], [GrailsReleaseType.SNAPSHOT], '9.0.0-SNAPSHOT')

        then:
        IllegalStateException e = thrown()
        e.message == "Could not find the Grails Repo in any repository: ${cliDirectory(named).path}," +
                'https://repo1.maven.org/maven2/org/apache/grails/grails-cli,' +
                'https://repository.apache.org/content/groups/public/org/apache/grails/grails-cli'

        and: 'both remote repositories were tried after the named one, in their order, and nothing was installed'
        proxy.requestedHosts == REMOTE_HOSTS
        new File(home, 'wrapper').list().length == 0
    }

    def "a release that no repository has is not installed, and every repository is named"() {
        given:
        File named = repository('named')

        when:
        Map result = update([named], [GrailsReleaseType.RELEASE], '9.0.0')

        then:
        !result.updated
        proxy.requestedHosts == REMOTE_HOSTS
        !installed('9.0.0').exists()
        result.err.contains("There was an error downloading the wrapper jar from [${cliDirectory(named).path}]")
        result.err.contains('There was an error downloading the wrapper jar from [https://repo1.maven.org/maven2/org/apache/grails/grails-cli]')
        result.err.contains('There was an error downloading the wrapper jar from [https://repository.apache.org/content/groups/public/org/apache/grails/grails-cli]')
    }

    def "a release that is installed is kept, and a snapshot that is installed is replaced"() {
        given:
        File named = repository('named')
        publishRelease(named, '9.0.0', 'the release')
        publishSnapshot(named, '9.0.1-SNAPSHOT', 'the first build')

        when: 'the release is installed'
        Map release = update([named], [GrailsReleaseType.RELEASE], '9.0.0')

        then:
        release.updated
        !needsUpdating([GrailsReleaseType.RELEASE], '9.0.0')

        when: 'the snapshot is installed, and the repository gets a new build of it'
        Map snapshot = update([named], [GrailsReleaseType.SNAPSHOT], '9.0.1-SNAPSHOT')
        String firstBuild = installed('9.0.1-SNAPSHOT').text
        publishSnapshot(named, '9.0.1-SNAPSHOT', 'the second build')
        boolean snapshotNeedsUpdating = needsUpdating([GrailsReleaseType.SNAPSHOT], '9.0.1-SNAPSHOT')
        Map again = update([named], [GrailsReleaseType.SNAPSHOT], '9.0.1-SNAPSHOT')

        then:
        snapshot.updated
        firstBuild == 'the first build'
        snapshotNeedsUpdating
        again.updated
        installed('9.0.1-SNAPSHOT').text == 'the second build'
        proxy.requestedHosts.empty
    }

    def "a version that is not installed needs updating"() {
        expect:
        needsUpdating([GrailsReleaseType.RELEASE], '9.0.0')
        needsUpdating([GrailsReleaseType.RELEASE], null)
    }

    @Unroll
    def "the wrapper ends with status 1 when no repository has the #kind"() {
        given:
        File named = repository('named')

        when:
        Map result = runWrapper(GRAILS_REPO_URL: named.path, PREFERRED_GRAILS_VERSION: version)

        then:
        result.status == 1
        result.out.contains('Unable to execute Grails Wrapper: ')
        result.out.contains(message)
        proxy.requestedHosts == REMOTE_HOSTS
        !installed(version).exists()

        where:
        kind       | version          | message
        'snapshot' | '9.0.0-SNAPSHOT' | 'Could not find the Grails Repo in any repository: '
        'release'  | '9.0.0'          | 'grails-cli-9.0.0-all.jar'
    }

    @Unroll
    def "the wrapper ends with status 1 and tries no other repository when #situation"() {
        given: 'a first repository with a file that cannot be read, and a second one that has the snapshot'
        File broken = repository('broken')
        File whole = repository('whole')
        publishSnapshot(whole, '9.0.0-SNAPSHOT', 'the command line')
        new File(cliDirectory(whole), 'maven-metadata-local.xml').text = rootMetadata(['9.0.0-SNAPSHOT'])
        File file = new File(cliDirectory(broken), path)
        file.parentFile.mkdirs()
        file.text = content

        when:
        Map<String, String> environment = [GRAILS_REPO_URL: "${broken.path};${whole.path}".toString(), GRAILS_WRAPPER_ALLOWED_TYPES: 'SNAPSHOT']
        if (version) {
            environment.PREFERRED_GRAILS_VERSION = version
        }
        Map result = runWrapper(environment)

        then:
        result.status == 1
        result.err.contains(message)
        proxy.requestedHosts.empty
        !installed('9.0.0-SNAPSHOT').exists()

        where:
        situation                                        | version          | path                                      | content                                         | message
        'the metadata of a snapshot is cut off'          | '9.0.0-SNAPSHOT' | '9.0.0-SNAPSHOT/maven-metadata-local.xml' | '<metadata><versioning><snapshotVersions><snap' | 'Could not parse snapshot version from maven metadata.'
        'the metadata of a snapshot is empty'            | '9.0.0-SNAPSHOT' | '9.0.0-SNAPSHOT/maven-metadata-local.xml' | ''                                              | 'Could not parse snapshot version from maven metadata.'
        'the list of versions is cut off'                | null             | 'maven-metadata-local.xml'                | '<metadata><versioning><versions><version>9.0.' | 'Unable to fetch latest Grails CLI from ['
        'the list of versions has what is not a version' | null             | 'maven-metadata-local.xml'                | rootMetadata(['not-a-version'])                 | 'Unable to fetch latest Grails CLI from ['
    }

    def "a jar that is cut off is installed as it is, and the wrapper ends with status 1 when it starts it"() {
        given:
        File named = repository('named')
        publishRelease(named, '9.0.0', 'PK the start of a jar and no more')

        when:
        Map result = runWrapper(GRAILS_REPO_URL: named.path, PREFERRED_GRAILS_VERSION: '9.0.0')

        then: 'the wrapper does not look into what it downloads'
        result.status == 1
        result.out.contains('Updated wrapper to version: 9.0.0')
        result.out.contains('Unable to execute Grails Wrapper: ')
        installed('9.0.0').text == 'PK the start of a jar and no more'
        proxy.requestedHosts.empty
    }

    @IgnoreIf({ os.windows || System.getProperty('user.name') == 'root' })
    def "a home that cannot be written to gets nothing, and the error names the file that could not be written"() {
        given:
        File named = repository('named')
        publishRelease(named, '9.0.0', 'the command line')
        assert home.setWritable(false)

        when:
        Map result = update([named], [GrailsReleaseType.RELEASE], '9.0.0')

        then:
        !result.updated
        home.list().length == 0
        result.err.contains(installed('9.0.0').canonicalPath)

        cleanup:
        home.setWritable(true)
    }

    private File repository(String name) {
        File repository = new File(dir, name)
        cliDirectory(repository).mkdirs()
        repository
    }

    private static File cliDirectory(File repository) {
        new File(repository, 'org/apache/grails/grails-cli')
    }

    private static void publishRelease(File repository, String version, String content) {
        File versionDirectory = new File(cliDirectory(repository), version)
        versionDirectory.mkdirs()
        new File(versionDirectory, "grails-cli-${version}-all.jar").text = content
    }

    /**
     * A snapshot as {@code publishToMavenLocal} writes it: the jar has the name of the version.
     */
    private static void publishSnapshot(File repository, String version, String content) {
        publishRelease(repository, version, content)
        new File(cliDirectory(repository), "${version}/maven-metadata-local.xml").text = """\
            <metadata>
              <versioning>
                <snapshotVersions>
                  <snapshotVersion>
                    <classifier>all</classifier>
                    <extension>jar</extension>
                    <value>${version}</value>
                  </snapshotVersion>
                </snapshotVersions>
              </versioning>
            </metadata>
            """.stripIndent()
    }

    private static String rootMetadata(List<String> versions) {
        '<metadata><versioning><versions>' +
                versions.collect { String version -> "<version>${version}</version>" }.join('') +
                '</versions></versioning></metadata>'
    }

    private File installed(String version) {
        new File(home, "wrapper/${version}/grails-cli-${version}-all.jar")
    }

    private boolean needsUpdating(List<GrailsReleaseType> allowed, String version) {
        boolean needsUpdating = false
        SystemStubs.withEnvironmentVariable('GRAILS_WRAPPER_HOME', home.path).execute {
            needsUpdating = new GrailsUpdater(new LinkedHashSet<>(allowed), version ? new GrailsVersion(version) : null).needsUpdating()
        }
        needsUpdating
    }

    /**
     * Updates in this JVM, with the given directories as the repositories the user named and the remote
     * repositories behind the proxy.
     *
     * @return updated, updater, and what was written to out and err
     */
    private Map update(List<File> repositories, List<GrailsReleaseType> allowed, String version) {
        Map result = [:]
        result.err = SystemStubs.tapSystemErr {
            result.out = SystemStubs.tapSystemOut {
                SystemStubs.restoreSystemProperties {
                    proxy.systemProperties.each { String name, String value -> System.setProperty(name, value) }
                    System.setProperty('grails.repo.url', repositories*.path.join(';'))
                    SystemStubs.withEnvironmentVariable('GRAILS_WRAPPER_HOME', home.path).execute {
                        GrailsUpdater updater = new GrailsUpdater(new LinkedHashSet<>(allowed), version ? new GrailsVersion(version) : null)
                        result.updater = updater
                        result.updated = updater.update()
                    }
                }
            }
        }
        result
    }

    /**
     * Runs the wrapper in a JVM of its own, as {@code grailsw} does, in a directory that is no project.
     *
     * @return status, and what was written to out and err
     */
    private Map runWrapper(Map<String, String> environment) {
        File workingDirectory = new File(dir, 'working')
        workingDirectory.mkdirs()
        File out = new File(dir, 'out.txt')
        File err = new File(dir, 'err.txt')
        List<String> command = [new File(System.getProperty('java.home'), 'bin/java').path]
        command.addAll(proxy.systemProperties.collect { String name, String value -> "-D${name}=${value}".toString() })
        command.add("-Duser.home=${new File(dir, 'user').path}".toString())
        command.addAll(['-cp', new File(GrailsUpdater.protectionDomain.codeSource.location.toURI()).path, 'grails.init.Start'])
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
