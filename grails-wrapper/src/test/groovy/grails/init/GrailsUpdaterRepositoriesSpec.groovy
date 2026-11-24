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

import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

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
        CliJars.marker(installed('9.0.0')) == 'release of the second'
        result.updater.selectedVersion == new GrailsVersion('9.0.0')
        result.updater.executedJarFile.canonicalFile == installed('9.0.0').canonicalFile
        proxy.requestedHosts.empty

        and: 'the repository that was named and did not have it is said in the line a snapshot has, without a stack trace'
        result.out.contains("...The Grails CLI was not found in the overridden repository [${cliDirectory(first).path}]: " +
                "${new File(cliDirectory(first), '9.0.0/grails-cli-9.0.0-all.jar').absolutePath} does not exist")
        result.out.contains('...Trying the next repository.')
        !result.out.contains("overridden repository [${cliDirectory(second).path}]")
        !result.err.contains('\tat ')
        !result.err.contains(cliDirectory(first).path)
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
        CliJars.marker(installed('9.0.1')) == 'the newer release'
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
        CliJars.marker(installed('9.0.0')) == 'the release'
        result.out.contains("overridden repository [${cliDirectory(first).path}]: No Grails Releases were found for the allowed types: RELEASE")
    }

    def "a snapshot that no repository has is an error that names every repository that was tried"() {
        given:
        File named = repository('named')

        when:
        update([named], [GrailsReleaseType.SNAPSHOT], '9.0.0-SNAPSHOT')

        then:
        GrailsWrapperException e = thrown()
        List<String> lines = e.message.readLines()
        lines.size() == 4
        lines[0] == 'Could not find the Grails CLI in any repository:'
        lines[1].startsWith("  [${cliDirectory(named).path}]: Could not determine local metadata file from local maven repository: neither ")
        lines[2].startsWith('  [https://repo1.maven.org/maven2/org/apache/grails/grails-cli]: its maven-metadata.xml could not be read: ')
        lines[3].startsWith('  [https://repository.apache.org/content/groups/public/org/apache/grails/grails-cli]: its maven-metadata.xml could not be read: ')

        and: 'both remote repositories were tried after the named one, in their order, and nothing was installed'
        proxy.requestedHosts == REMOTE_HOSTS
        new File(home, 'wrapper').list().length == 0
    }

    def "a release that no repository has is not installed, and every repository is named"() {
        given:
        File named = repository('named')

        when:
        update([named], [GrailsReleaseType.RELEASE], '9.0.0')

        then: 'the error names every repository and why it did not give the jar'
        GrailsWrapperException e = thrown()
        List<String> lines = e.message.readLines()
        lines.size() == 4
        lines[0] == 'Could not find the Grails CLI in any repository:'
        lines[1] == "  [${cliDirectory(named).path}]: ${new File(cliDirectory(named), '9.0.0/grails-cli-9.0.0-all.jar').absolutePath} does not exist"
        lines[2].startsWith('  [https://repo1.maven.org/maven2/org/apache/grails/grails-cli]: the download failed: ')
        lines[3].startsWith('  [https://repository.apache.org/content/groups/public/org/apache/grails/grails-cli]: the download failed: ')

        and: 'nothing is left in the home'
        proxy.requestedHosts == REMOTE_HOSTS
        new File(home, 'wrapper').list().length == 0
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
        String firstBuild = CliJars.marker(installed('9.0.1-SNAPSHOT'))
        publishSnapshot(named, '9.0.1-SNAPSHOT', 'the second build')
        boolean snapshotNeedsUpdating = needsUpdating([GrailsReleaseType.SNAPSHOT], '9.0.1-SNAPSHOT')
        Map again = update([named], [GrailsReleaseType.SNAPSHOT], '9.0.1-SNAPSHOT')

        then:
        snapshot.updated
        firstBuild == 'the first build'
        snapshotNeedsUpdating
        again.updated
        CliJars.marker(installed('9.0.1-SNAPSHOT')) == 'the second build'
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
        result.out.contains('Unable to execute Grails Wrapper: Could not find the Grails CLI in any repository:')
        result.out.contains(message)
        proxy.requestedHosts == REMOTE_HOSTS
        !installed(version).exists()

        and: 'the message is all that is said: there is no stack trace'
        !result.out.contains('\tat ')
        !result.err.contains('\tat ')

        where:
        kind       | version          | message
        'snapshot' | '9.0.0-SNAPSHOT' | 'Could not determine local metadata file from local maven repository: neither '
        'release'  | '9.0.0'          | 'grails-cli-9.0.0-all.jar does not exist'
    }

    @Unroll
    def "the next repository is tried, and the first is named in one line, when #situation"() {
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
        Map result = runWrapper(environment, ['help'])

        then: 'the command line of the second repository is installed and started'
        result.status == 0
        result.out.contains("${CliJars.STARTED}help")
        CliJars.marker(installed('9.0.0-SNAPSHOT')) == 'the command line'
        proxy.requestedHosts.empty

        and: 'one line names the repository and what is wrong with it, with nothing of the file that a version does not have'
        result.err.readLines() == ["The Grails CLI could not be taken from the repository [${cliDirectory(broken).path}]: ${message}".toString()]

        where:
        situation                                        | version          | path                                      | content                                         | message
        'the metadata of a snapshot is cut off'          | '9.0.0-SNAPSHOT' | '9.0.0-SNAPSHOT/maven-metadata-local.xml' | '<metadata><versioning><snapshotVersions><snap' | 'its maven-metadata-local.xml is not well-formed XML (line 1)'
        'the metadata of a snapshot is empty'            | '9.0.0-SNAPSHOT' | '9.0.0-SNAPSHOT/maven-metadata-local.xml' | ''                                              | 'its maven-metadata-local.xml is not well-formed XML (line 1)'
        'the metadata of a snapshot names no jar'        | '9.0.0-SNAPSHOT' | '9.0.0-SNAPSHOT/maven-metadata-local.xml' | '<metadata/>'                                   | 'its metadata of the snapshot names no jar'
        'the metadata of a snapshot names a path'        | '9.0.0-SNAPSHOT' | '9.0.0-SNAPSHOT/maven-metadata-local.xml' | snapshotMetadata('../../../other/9.0.0')        | 'its metadata of the snapshot names what is no version'
        'the list of versions is cut off'                | null             | 'maven-metadata-local.xml'                | '<metadata><versioning><versions><version>9.0.' | 'its maven-metadata-local.xml is not well-formed XML (line 1)'
        'the list of versions is empty'                  | null             | 'maven-metadata-local.xml'                | ''                                              | 'its maven-metadata-local.xml is not well-formed XML (line 1)'
        'the list of versions has what is not a version' | null             | 'maven-metadata-local.xml'                | rootMetadata(['not-a-version'])                 | 'its maven-metadata-local.xml could not be read: it lists [not-a-version], which is no version'
        'the list of versions has a command'             | null             | 'maven-metadata-local.xml'                | rootMetadata(['9.0.0 $(rm) `x` | y'])           | 'its maven-metadata-local.xml could not be read: it lists [9.0.0???rm???x????y], which is no version'
    }

    def "a jar that is cut off is not installed, and the wrapper ends with status 1 and why"() {
        given:
        File named = repository('named')
        File jar = publishRelease(named, '9.0.0', 'the command line')
        jar.text = 'PK the start of a jar and no more'

        when:
        Map result = runWrapper(GRAILS_REPO_URL: named.path, PREFERRED_GRAILS_VERSION: '9.0.0')

        then: 'the wrapper looks into what it downloads, and ends there: the other repositories are not asked'
        result.status == 1
        result.out.contains("Unable to execute Grails Wrapper: The Grails CLI that the repository [${cliDirectory(named).path}] gave is not installed: " +
                'the download is not a Grails command line that can be started: it does not open as a jar: ')
        result.out.contains('No other repository was asked: the command line of another repository does not take the place of a download that is not whole. ' +
                'Run the command again; if the repository gives the same download, correct the repository or remove it from GRAILS_REPO_URL.')
        !result.out.contains('Could not find the Grails CLI in any repository')
        result.err.empty
        !result.out.contains('Updated wrapper to version')
        proxy.requestedHosts.empty

        and: 'neither the jar nor the file it was downloaded to is in the home'
        new File(home, 'wrapper').list().length == 0
    }

    @IgnoreIf({ os.windows || System.getProperty('user.name') == 'root' })
    def "a home that cannot be written to is said with the way to name another, and no repository is asked"() {
        given:
        File named = repository('named')
        publishRelease(named, '9.0.0', 'the command line')
        assert home.setWritable(false)

        when:
        update([named], [GrailsReleaseType.RELEASE], '9.0.0')

        then:
        GrailsWrapperException e = thrown()
        e.message == "The Grails wrapper cannot write to [${new File(home.canonicalFile, 'wrapper').absolutePath}], where it keeps the command line it downloads. " +
                'Make the directory writable, or name another home for the wrapper with the environment variable GRAILS_WRAPPER_HOME.'
        home.list().length == 0
        proxy.requestedHosts.empty

        when: 'the wrapper is run'
        Map result = runWrapper(GRAILS_REPO_URL: named.path, PREFERRED_GRAILS_VERSION: '9.0.0')

        then: 'it ends with status 1 and the message, without a stack trace'
        result.status == 1
        result.out.contains("Unable to execute Grails Wrapper: The Grails wrapper cannot write to [${new File(home.canonicalFile, 'wrapper').absolutePath}]")
        !result.out.contains('\tat ')
        !result.err.contains('\tat ')
        proxy.requestedHosts.empty

        cleanup:
        home.setWritable(true)
    }

    @IgnoreIf({ os.windows || System.getProperty('user.name') == 'root' })
    def "a directory of a version that cannot be written to is said, and no other repository is asked"() {
        given:
        File named = repository('named')
        publishRelease(named, '9.0.0', 'the command line')
        File versionDirectory = installed('9.0.0').parentFile
        versionDirectory.mkdirs()
        assert versionDirectory.setWritable(false)

        when:
        update([named], [GrailsReleaseType.RELEASE], '9.0.0')

        then:
        GrailsWrapperException e = thrown()
        e.message.startsWith("The Grails wrapper cannot write to [${versionDirectory.canonicalPath}], where it keeps the command line it downloads (")
        e.message.endsWith('name another home for the wrapper with the environment variable GRAILS_WRAPPER_HOME.')
        proxy.requestedHosts.empty

        cleanup:
        versionDirectory.setWritable(true)
    }

    @Unroll
    def "a jar that is cut off in the first repository, which has #checksums, ends the update, and the whole one of the second is not taken"() {
        given:
        File first = repository('first')
        File second = repository('second')
        File cutOff = publishRelease(first, '9.0.0', 'of the first')
        CliJars.writeChecksums(cutOff, kinds)
        byte[] whole = cutOff.bytes
        cutOff.bytes = Arrays.copyOf(whole, whole.length.intdiv(2))
        CliJars.writeChecksums(publishRelease(second, '9.0.0', 'of the second'), kinds)

        when:
        Map result = attempt([first, second], [GrailsReleaseType.RELEASE], '9.0.0')

        then:
        result.thrown instanceof GrailsWrapperException
        result.thrown.message == notInstalled(first, message)
        result.err.empty
        !result.out.contains(cliDirectory(second).path)
        proxy.requestedHosts.empty

        and: 'nothing is installed, and nothing of the download is left'
        new File(home, 'wrapper').list().length == 0

        where:
        checksums                | kinds                             | message
        'no checksum files'      | []                                | 'the download is not a Grails command line that can be started: it does not open as a jar: ZipException'
        'an MD5 checksum'        | ['md5']                           | 'the download does not match the MD5 checksum of the repository: it was cut off or is corrupt'
        'a SHA-1 checksum'       | ['sha1', 'md5']                   | 'the download does not match the SHA-1 checksum of the repository: it was cut off or is corrupt'
        'a SHA-256 checksum'     | ['sha256', 'sha1', 'md5']         | 'the download does not match the SHA-256 checksum of the repository: it was cut off or is corrupt'
        'every kind of checksum' | ['sha512', 'sha256', 'sha1', 'md5'] | 'the download does not match the SHA-512 checksum of the repository: it was cut off or is corrupt'
    }

    @Unroll
    def "a whole jar is #outcome when the repository has #situation"() {
        given:
        File named = repository('named')
        File jar = publishRelease(named, '9.0.0', 'the command line')
        checksums.each { String kind, Closure<String> content ->
            new File(jar.parentFile, "${jar.name}.${kind}").text = content.is(WRONG) ? CliJars.checksum('another file'.bytes, kind) : content(jar)
        }

        when:
        Map result = attempt([named], [GrailsReleaseType.RELEASE], '9.0.0')

        then: 'a download that is refused ends the update, and the remote repositories are not asked'
        (result.updated as boolean) == (message == null)
        installed('9.0.0').exists() == (message == null)
        result.thrown?.message == (message == null ? null : notInstalled(named, message))
        proxy.requestedHosts.empty

        where:
        outcome     | situation                                              | checksums                                                               | message
        'installed' | 'a checksum in upper case'                             | [sha512: { File f -> CliJars.checksum(f.bytes, 'sha512').toUpperCase() }] | null
        'installed' | 'a checksum followed by the name of the file'          | [sha1: { File f -> "${CliJars.checksum(f.bytes, 'sha1')}  ${f.name}\n" }] | null
        'installed' | 'a right strong checksum and a wrong weak one'         | [sha512: { File f -> CliJars.checksum(f.bytes, 'sha512') }, md5: WRONG]   | null
        'refused'   | 'a wrong strong checksum and a right weak one'         | [sha512: WRONG, md5: { File f -> CliJars.checksum(f.bytes, 'md5') }]      | 'the download does not match the SHA-512 checksum of the repository: it was cut off or is corrupt'
        'refused'   | 'the checksum of another file'                         | [sha256: WRONG]                                                         | 'the download does not match the SHA-256 checksum of the repository: it was cut off or is corrupt'
        'refused'   | 'a checksum file that holds a page and no checksum'    | [sha1: { File f -> '<html><body>Not here</body></html>' }]              | 'its .sha1 checksum file does not hold a checksum'
        'refused'   | 'an empty checksum file'                               | [sha512: { File f -> '' }]                                              | 'its .sha512 checksum file does not hold a checksum'
    }

    def "without a checksum file a jar with a damaged entry is refused, since every entry is read"() {
        given: 'a jar of the right length that opens as a jar, with one byte of an entry changed'
        File named = repository('named')
        File jar = publishRelease(named, '9.0.0', 'what the entry held')
        byte[] bytes = jar.bytes
        int at = new String(bytes, 'ISO-8859-1').indexOf('what the entry held')
        assert at > 0
        bytes[at] = (byte) 'W'
        jar.bytes = bytes
        assert StartClass.defect(jar) == null

        when:
        update([named], [GrailsReleaseType.RELEASE], '9.0.0')

        then:
        GrailsWrapperException e = thrown()
        e.message == notInstalled(named, 'the download is corrupt: IOException: an entry of the jar does not match its CRC')
        new File(home, 'wrapper').list().length == 0
        proxy.requestedHosts.empty
    }

    def "an installed file that is no jar is said in one line, downloaded again and started"() {
        given: 'what an interrupted download left with a wrapper that did not look into it'
        File named = repository('named')
        CliJars.writeChecksums(publishRelease(named, '9.0.0', 'the command line'), ['sha512'])
        installed('9.0.0').parentFile.mkdirs()
        installed('9.0.0').text = 'PK the start of a jar and no more'

        when:
        Map result = runWrapper([GRAILS_REPO_URL: named.path, PREFERRED_GRAILS_VERSION: '9.0.0'], ['help'])

        then:
        result.status == 0
        result.out.readLines().count { String line ->
            line.startsWith("The installed Grails command line at [${installed('9.0.0').canonicalPath}] cannot be started (it does not open as a jar: ") &&
                    line.endsWith('). Downloading it again.')
        } == 1
        result.out.contains("${CliJars.STARTED}help")
        CliJars.marker(installed('9.0.0')) == 'the command line'
        proxy.requestedHosts.empty
    }

    def "an installed file that is no jar and that no repository replaces ends the wrapper after one download"() {
        given:
        File named = repository('named')
        installed('9.0.0').parentFile.mkdirs()
        installed('9.0.0').text = 'PK the start of a jar and no more'

        when:
        Map result = runWrapper(GRAILS_REPO_URL: named.path, PREFERRED_GRAILS_VERSION: '9.0.0')

        then: 'every repository was asked once'
        result.status == 1
        result.out.contains('). Downloading it again.')
        result.out.contains('Unable to execute Grails Wrapper: Could not find the Grails CLI in any repository:')
        proxy.requestedHosts == REMOTE_HOSTS

        and: 'the file is as it was, and nothing is beside it'
        installed('9.0.0').text == 'PK the start of a jar and no more'
        installed('9.0.0').parentFile.list() == ['grails-cli-9.0.0-all.jar']
    }

    def "a directory of a version without its jar, which a failed update of an earlier wrapper left, needs updating"() {
        given:
        installed('9.0.0').parentFile.mkdirs()

        expect:
        needsUpdating([GrailsReleaseType.RELEASE], '9.0.0')
        needsUpdating([GrailsReleaseType.RELEASE], null)
    }

    def "wrappers that download one version into one home at the same time all leave a whole jar, and none finds part of one"() {
        given: 'a jar that takes a while to copy'
        File named = repository('named')
        byte[] padding = new byte[16 * 1024 * 1024]
        new Random(16348).nextBytes(padding)
        File published = new File(cliDirectory(named), '9.0.0/grails-cli-9.0.0-all.jar')
        published.parentFile.mkdirs()
        published.bytes = CliJars.jar('the command line', padding)
        CliJars.writeChecksums(published, ['sha512'])
        ExecutorService pool = Executors.newFixedThreadPool(5)
        CountDownLatch start = new CountDownLatch(1)
        AtomicBoolean done = new AtomicBoolean()
        System.setProperty('grails.repo.url', named.path)

        when: 'four wrappers update, and a fifth looks at the place of the jar all the while, as one that is about to start it'
        List<Future<Boolean>> updates = (1..4).collect {
            pool.submit({
                start.await()
                new GrailsUpdater(new LinkedHashSet<>([GrailsReleaseType.RELEASE]), new GrailsVersion('9.0.0'), home.path).update()
            } as Callable<Boolean>)
        }
        Future<List<String>> looks = pool.submit({
            List<String> defects = []
            start.await()
            while (!done.get()) {
                if (installed('9.0.0').exists()) {
                    // as a wrapper looks: on Windows it waits through the moment in which the move of another
                    // wrapper keeps the file from being opened, and finds the jar as it is after the move
                    String defect = StartClass.defect(installed('9.0.0'))
                    if (defect != null) {
                        defects.add(defect)
                    }
                }
            }
            defects
        } as Callable<List<String>>)
        start.countDown()
        List<Boolean> updated = updates.collect { Future<Boolean> update -> update.get(5, TimeUnit.MINUTES) }
        done.set(true)

        then: 'no update failed; on Windows one may have left the whole jar of another in place, which the look had open'
        updated.every() || System.getProperty('os.name').toLowerCase().contains('windows')
        looks.get(1, TimeUnit.MINUTES).empty
        installed('9.0.0').bytes == published.bytes

        and: 'no file of a download is left'
        installed('9.0.0').parentFile.list() == ['grails-cli-9.0.0-all.jar']

        cleanup:
        done.set(true)
        pool.shutdownNow()
        System.clearProperty('grails.repo.url')
    }

    def "the file of a download that a killed wrapper left a day ago is removed, one of today is not"() {
        given:
        File named = repository('named')
        publishRelease(named, '9.0.0', 'the command line')
        File versionDirectory = installed('9.0.0').parentFile
        versionDirectory.mkdirs()
        File old = new File(versionDirectory, 'grails-cli-9.0.0-all.jar.1.part')
        old.text = 'part of a jar'
        assert old.setLastModified(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(2))
        File inProgress = new File(versionDirectory, 'grails-cli-9.0.0-all.jar.2.part')
        inProgress.text = 'part of a jar'

        when:
        Map result = update([named], [GrailsReleaseType.RELEASE], '9.0.0')

        then:
        result.updated
        !old.exists()
        inProgress.exists()
    }

    def "the user and the password of a repository URL are in no message"() {
        given:
        String withCredentials = 'https://deployer:s3cret@repository.example.invalid/releases'
        File named = repository('named')
        publishRelease(named, '9.0.0', 'the command line')

        when: 'the repository cannot be reached, and the next one has the command line'
        Map result = update([withCredentials, named], [GrailsReleaseType.RELEASE], '9.0.0')

        then:
        result.updated
        proxy.requestedHosts == ['repository.example.invalid']
        result.out.contains('...Update Repository is overridden to prefer [https://***@repository.example.invalid/releases].')
        result.err.contains('The Grails CLI could not be taken from the repository [https://***@repository.example.invalid/releases/org/apache/grails/grails-cli]: ')
        !result.out.contains('s3cret')
        !result.err.contains('s3cret')
        !result.out.contains('deployer')
        !result.err.contains('deployer')

        when: 'no repository has it'
        update([withCredentials], [GrailsReleaseType.RELEASE], '9.0.1')

        then:
        GrailsWrapperException e = thrown()
        e.message.contains('  [https://***@repository.example.invalid/releases/org/apache/grails/grails-cli]: ')
        !e.message.contains('s3cret')
        !e.message.contains('deployer')
    }

    @Unroll
    def "no part of the user or the password of a repository URL that #what is in any message"() {
        given: 'a repository named after one whose URL cannot be read with confidence, and which has the command line'
        File named = repository('named')
        publishRelease(named, '9.0.0', 'the command line')

        when:
        Map result = updateWhatever([url, named], '9.0.0')
        String said = "${result.out}\n${result.err}\n${result.thrown ?: ''}"

        then: 'the URL is shown as far as it is no user and no password'
        said.contains("...Update Repository is overridden to prefer [${shown}].")

        and: 'nothing of the user and the password is in what is written or thrown'
        ['deployer', 'Xq7', 'Zk9'].every { String secret -> !said.contains(secret) }

        where:
        what                                          | url                                                                       || shown
        'has an @ in the password'                    | 'https://deployer:Xq7@Zk9@repository.example.invalid/releases'            || 'https://***@repository.example.invalid/releases'
        'has several @'                               | 'https://deployer@Xq7:Zk9@repository.example.invalid/releases'            || 'https://***@repository.example.invalid/releases'
        'has a / in the password'                     | 'https://deployer:Xq7/Zk9@repository.example.invalid/releases'            || 'https://***@repository.example.invalid/releases'
        'has a # in the password'                     | 'https://deployer:Xq7#Zk9@repository.example.invalid/releases'            || 'https://***@repository.example.invalid/releases'
        'has a ? in the password'                     | 'https://deployer:Xq7?Zk9@repository.example.invalid/releases'            || 'https://***@repository.example.invalid/releases'
        'has no scheme'                               | 'deployer:Xq7Zk9@repository.example.invalid/releases'                     || '***@repository.example.invalid/releases'
        'has no scheme but its slashes'               | '//deployer:Xq7Zk9@repository.example.invalid/releases'                   || '***@repository.example.invalid/releases'
        'has an opening bracket in the password'      | 'https://deployer:Xq7[Zk9@repository.example.invalid/releases'            || 'https://***@repository.example.invalid/releases'
        'has an opening bracket before the user'      | 'https://[deployer:Xq7Zk9@repository.example.invalid/releases'            || 'https://***@repository.example.invalid/releases'
        'has a closing bracket in the password'       | 'https://deployer:Xq7]Zk9@repository.example.invalid/releases'            || 'https://***@repository.example.invalid/releases'
        'has a percent-encoded @ in the password'     | 'https://deployer:Xq7%40Zk9@repository.example.invalid/releases'          || 'https://***@repository.example.invalid/releases'
        'has only percent-encoded @'                  | 'https://deployer:Xq7%40Zk9%40repository.example.invalid/releases'        || 'https://***/releases'
        'has a user and no password'                  | 'https://deployer@repository.example.invalid/releases'                    || 'https://***@repository.example.invalid/releases'
        'has a space in the password'                 | 'https://deployer:Xq7 Zk9@repository.example.invalid/releases'            || 'https://***@repository.example.invalid/releases'
        'has a user and a password but no @'          | 'https://deployer:Xq7Zk9/releases'                                        || 'https://***/releases'
    }

    @Unroll
    def "a #kind that does not match its checksum in the first named repository ends the wrapper with status 1, and the second repository is not asked"() {
        given: 'the first repository has another jar in the place of the one its checksum file is of, and the second has a whole one'
        File first = repository('first')
        File second = repository('second')
        File tampered = publish(first, version, 'the jar of the first')
        CliJars.writeChecksums(tampered, ['sha1'])
        tamper(tampered)
        publish(second, version, 'the jar of the second')

        when:
        Map result = runWrapper([GRAILS_REPO_URL: "${first.path};${second.path}".toString(), PREFERRED_GRAILS_VERSION: version], ['help'])

        then: 'the message names the repository and why, without a stack trace'
        result.status == 1
        result.out.contains("Unable to execute Grails Wrapper: ${notInstalled(first, 'the download does not match the SHA-1 checksum of the repository: it was cut off or is corrupt')}")
        !result.out.contains('\tat ')
        !result.err.contains('\tat ')

        and: 'nothing is installed or started, and neither the second repository nor a remote one is asked'
        !result.out.contains(CliJars.STARTED)
        !result.out.contains('Updated wrapper to version')
        new File(home, 'wrapper').list().length == 0
        !result.out.contains('...Trying the next repository.')
        !result.out.contains(cliDirectory(second).path)
        !result.err.contains(cliDirectory(second).path)
        proxy.requestedHosts.empty

        where:
        kind       | version
        'release'  | '9.0.0'
        'snapshot' | '9.0.0-SNAPSHOT'
    }

    @Unroll
    def "the update of a #kind ends at a jar of the first named repository that does not match its checksum"() {
        given:
        File first = repository('first')
        File second = repository('second')
        File tampered = publish(first, version, 'the jar of the first')
        CliJars.writeChecksums(tampered, ['sha512', 'sha1'])
        tamper(tampered)
        publish(second, version, 'the jar of the second')

        when:
        Map result = attempt([first, second], [type], version)

        then:
        result.thrown instanceof GrailsWrapperException
        result.thrown.message == notInstalled(first, 'the download does not match the SHA-512 checksum of the repository: it was cut off or is corrupt')
        !result.updated
        new File(home, 'wrapper').list().length == 0
        proxy.requestedHosts.empty

        and: 'no line says that a repository was passed over'
        result.err.empty
        !result.out.contains('...Trying the next repository.')
        !result.out.contains(cliDirectory(second).path)

        where:
        kind       | version          | type
        'release'  | '9.0.0'          | GrailsReleaseType.RELEASE
        'snapshot' | '9.0.0-SNAPSHOT' | GrailsReleaseType.SNAPSHOT
    }

    @Unroll
    def "a #kind that the first named repository does not have is taken from the second, which the wrapper starts"() {
        given:
        File first = repository('first')
        File second = repository('second')
        CliJars.writeChecksums(publish(second, version, 'the jar of the second'), ['sha1'])

        when:
        Map result = runWrapper([GRAILS_REPO_URL: "${first.path};${second.path}".toString(), PREFERRED_GRAILS_VERSION: version], ['help'])

        then:
        result.status == 0
        result.out.contains("${CliJars.STARTED}help")
        CliJars.marker(installed(version)) == 'the jar of the second'
        result.out.contains("...The Grails CLI was not found in the overridden repository [${cliDirectory(first).path}]: ")
        result.out.contains('...Trying the next repository.')
        proxy.requestedHosts.empty

        where:
        kind       | version
        'release'  | '9.0.0'
        'snapshot' | '9.0.0-SNAPSHOT'
    }

    def "an installed snapshot is kept when the next download of it does not match its checksum, and the wrapper ends"() {
        given:
        File named = repository('named')
        publishSnapshot(named, '9.0.0-SNAPSHOT', 'the first build')
        update([named], [GrailsReleaseType.SNAPSHOT], '9.0.0-SNAPSHOT')
        File next = publishSnapshot(named, '9.0.0-SNAPSHOT', 'the second build')
        CliJars.writeChecksums(next, ['sha256'])
        tamper(next)

        when:
        Map result = runWrapper([GRAILS_REPO_URL: named.path, PREFERRED_GRAILS_VERSION: '9.0.0-SNAPSHOT'], ['help'])

        then:
        result.status == 1
        result.out.contains("Unable to execute Grails Wrapper: ${notInstalled(named, 'the download does not match the SHA-256 checksum of the repository: it was cut off or is corrupt')}")
        !result.out.contains(CliJars.STARTED)
        CliJars.marker(installed('9.0.0-SNAPSHOT')) == 'the first build'
        installed('9.0.0-SNAPSHOT').parentFile.list() == ['grails-cli-9.0.0-SNAPSHOT-all.jar']
        proxy.requestedHosts.empty
    }

    // the update as update does it, which keeps what was written when it throws, and what it threw as a stack trace shows it
    private Map updateWhatever(List repositories, String version) {
        Map result = [:]
        result.err = SystemStubs.tapSystemErr {
            result.out = SystemStubs.tapSystemOut {
                SystemStubs.restoreSystemProperties {
                    proxy.systemProperties.each { String name, String value -> System.setProperty(name, value) }
                    System.setProperty('grails.repo.url', repositories.collect { it instanceof File ? it.path : it }.join(';'))
                    SystemStubs.withEnvironmentVariable('GRAILS_WRAPPER_HOME', home.path).execute {
                        try {
                            result.updated = new GrailsUpdater(new LinkedHashSet<>([GrailsReleaseType.RELEASE]), new GrailsVersion(version)).update()
                        } catch (Exception e) {
                            StringWriter trace = new StringWriter()
                            e.printStackTrace(new PrintWriter(trace))
                            result.thrown = trace.toString()
                        }
                    }
                }
            }
        }
        result
    }

    // stands for the checksum of another file, of the kind of the checksum file
    private static final Closure<String> WRONG = { File jar -> null }

    private File repository(String name) {
        File repository = new File(dir, name)
        cliDirectory(repository).mkdirs()
        repository
    }

    private static File cliDirectory(File repository) {
        new File(repository, 'org/apache/grails/grails-cli')
    }

    /**
     * @param marker what tells the jar from the one of another repository, see {@link CliJars#marker}
     * @return the jar
     */
    private static File publishRelease(File repository, String version, String marker) {
        File versionDirectory = new File(cliDirectory(repository), version)
        versionDirectory.mkdirs()
        File jar = new File(versionDirectory, "grails-cli-${version}-all.jar")
        jar.bytes = CliJars.jar(marker)
        jar
    }

    /**
     * A snapshot as {@code publishToMavenLocal} writes it: the jar has the name of the version.
     */
    private static File publishSnapshot(File repository, String version, String content) {
        File jar = publishRelease(repository, version, content)
        new File(cliDirectory(repository), "${version}/maven-metadata-local.xml").text = snapshotMetadata(version)
        jar
    }

    private static String snapshotMetadata(String value) {
        """\
            <metadata>
              <versioning>
                <snapshotVersions>
                  <snapshotVersion>
                    <classifier>all</classifier>
                    <extension>jar</extension>
                    <value>${value}</value>
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
     * Updates in this JVM, with the given directories, or URLs, as the repositories the user named and the remote
     * repositories behind the proxy.
     *
     * @return updated, updater, and what was written to out and err
     */
    private Map update(List repositories, List<GrailsReleaseType> allowed, String version) {
        Map result = attempt(repositories, allowed, version)
        if (result.thrown) {
            throw result.thrown
        }
        result
    }

    /**
     * {@link #update}, which keeps what was written when the update throws.
     *
     * @return updated or thrown, updater, and what was written to out and err
     */
    private Map attempt(List repositories, List<GrailsReleaseType> allowed, String version) {
        Map result = [:]
        result.err = SystemStubs.tapSystemErr {
            result.out = SystemStubs.tapSystemOut {
                SystemStubs.restoreSystemProperties {
                    proxy.systemProperties.each { String name, String value -> System.setProperty(name, value) }
                    System.setProperty('grails.repo.url', repositories.collect { it instanceof File ? it.path : it }.join(';'))
                    SystemStubs.withEnvironmentVariable('GRAILS_WRAPPER_HOME', home.path).execute {
                        GrailsUpdater updater = new GrailsUpdater(new LinkedHashSet<>(allowed), version ? new GrailsVersion(version) : null)
                        result.updater = updater
                        try {
                            result.updated = updater.update()
                        } catch (GrailsWrapperException e) {
                            result.thrown = e
                        }
                    }
                }
            }
        }
        result
    }

    /**
     * What ends the wrapper when a repository that was named gave a jar that is not whole.
     */
    private static String notInstalled(File repository, String reason) {
        "The Grails CLI that the repository [${cliDirectory(repository).path}] gave is not installed: ${reason}. " +
                'No other repository was asked: the command line of another repository does not take the place of a download that is not whole. ' +
                'Run the command again; if the repository gives the same download, correct the repository or remove it from GRAILS_REPO_URL.'
    }

    /**
     * Replaces a jar by another one that is whole, as one that was tampered with is: the checksum files beside it
     * are those of the jar that was there.
     */
    private static void tamper(File jar) {
        jar.bytes = CliJars.jar('a jar in the place of the published one')
    }

    private static File publish(File repository, String version, String marker) {
        version.endsWith('-SNAPSHOT') ? publishSnapshot(repository, version, marker) : publishRelease(repository, version, marker)
    }

    /**
     * Runs the wrapper in a JVM of its own, as {@code grailsw} does, in a directory that is no project.
     *
     * @return status, and what was written to out and err
     */
    private Map runWrapper(Map<String, String> environment, List<String> arguments = []) {
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
