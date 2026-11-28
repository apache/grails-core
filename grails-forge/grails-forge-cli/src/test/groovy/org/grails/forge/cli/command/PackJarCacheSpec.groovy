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
package org.grails.forge.cli.command

import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.apache.grails.forge.cli.pack.PackArtifacts
import org.apache.grails.forge.cli.pack.PackArtifacts.Reply
import org.apache.grails.forge.cli.pack.PackArtifacts.Transport
import org.grails.forge.application.ContextFactory
import org.grails.forge.application.WebAvailableFeatures
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.cli.CommandFixture
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.GrailsPicocliFactory
import picocli.CommandLine
import spock.lang.IgnoreIf
import spock.lang.Unroll

/**
 * The jars of plugins that are kept on this computer once they were fetched for their feature packs:
 * when a kept jar is used in the place of a request, when it is not, and what is said of it. The
 * repository is an answer this specification gives itself and counts the requests of, and the home the
 * jars are kept under is a directory of each feature.
 */
class PackJarCacheSpec extends CommandSpec implements CommandFixture {

    static final String PLUGIN = 'com.acme:acme-audit-grails'
    static final String COORDINATES = PLUGIN + ':2.3.0'
    static final String SNAPSHOT = PLUGIN + ':2.4.0-SNAPSHOT'
    static final String REPOSITORY = 'https://repository.invalid/maven/'
    static final String JAR = REPOSITORY + 'com/acme/acme-audit-grails/2.3.0/acme-audit-grails-2.3.0.jar'
    static final String SNAPSHOT_DIRECTORY = REPOSITORY + 'com/acme/acme-audit-grails/2.4.0-SNAPSHOT/'
    static final String SNAPSHOT_JAR = SNAPSHOT_DIRECTORY + 'acme-audit-grails-2.4.0-SNAPSHOT.jar'
    static final String CENTRAL = 'https://repo.maven.apache.org/maven2/com/acme/acme-audit-grails/2.3.0/acme-audit-grails-2.3.0.jar'
    static final boolean POSIX = FileSystems.default.supportedFileAttributeViews().contains('posix')

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    ByteArrayOutputStream err = new ByteArrayOutputStream()
    File work
    File home
    Path packs
    String previousHome
    List<URI> asked = []
    Map<String, Closure<Reply>> answers = [:]
    Transport transport = { URI address ->
        asked << address
        Closure<Reply> answer = answers[address.toString()]
        return answer == null ? new Reply(404, null, null) : answer.call()
    } as Transport

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out))
        System.setErr(new PrintStream(err))
        work = Files.createTempDirectory('grailsforgepackjars').toFile().canonicalFile
        // the home of each feature: the jars are kept under it, and so is the journal of a change
        home = new File(work, 'home')
        home.mkdirs()
        packs = home.toPath().resolve('.grails').resolve('forge').resolve('packs')
        previousHome = System.getProperty('user.home')
        System.setProperty('user.home', home.absolutePath)
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
        System.setProperty('user.home', previousHome)
        work.deleteDir()
    }

    void "a jar that is asked for with its SHA-256 is fetched once, and the second application is created without a request"() {
        given:
        byte[] jar = published(JAR)
        String sha256 = sha256(jar)

        when: 'the first application'
        int exit = create('first', '--features', COORDINATES, '--feature-pack-repository', REPOSITORY, '--feature-pack-sha256', "${COORDINATES}=${sha256}")

        then: 'the jar is fetched, checked against what the repository publishes, and kept by its digest for the user alone'
        exit == 0
        asked*.toString() == [JAR, JAR + '.sha256']
        out.toString().contains('    the jar matches the sha256 checksum the repository publishes for it')
        !out.toString().contains('is the copy that is kept')
        Files.readAllBytes(packs.resolve(sha256 + '.jar')) == jar
        kept() == [sha256 + '.jar', '*.last']
        !POSIX || PosixFilePermissions.toString(Files.getPosixFilePermissions(packs.resolve(sha256 + '.jar'))) == 'rw-------'
        !POSIX || PosixFilePermissions.toString(Files.getPosixFilePermissions(packs)) == 'rwx------'

        when: 'the second'
        asked.clear()
        exit = create('second', '--features', COORDINATES, '--feature-pack-repository', REPOSITORY, '--feature-pack-sha256', "${COORDINATES}=${sha256}")

        then: 'no repository is asked, and the command says which jar it used'
        exit == 0
        asked.isEmpty()
        out.toString().contains("Feature pack acme-audit from ${COORDINATES} (${REPOSITORY}), sha256 ${sha256}")
        out.toString().contains('    the jar has the SHA-256 that was asked for with --feature-pack-sha256')
        out.toString().contains("    the jar is the copy that is kept on this computer, ${packs.resolve(sha256 + '.jar')}, which has that SHA-256")
        !out.toString().contains('checksum the repository publishes')

        and: 'the application is the one that was created with the jar of the repository'
        new File(work, 'second/.grails/forge.lock.yml').text == new File(work, 'first/.grails/forge.lock.yml').text
        new File(work, 'second/.grails/forge.lock.yml').text.contains("sha256: ${sha256}\n")
        new File(work, 'second/build.gradle').text == new File(work, 'first/build.gradle').text
    }

    @Unroll
    void "without a SHA-256, #kind is fetched every time while the network is asked"() {
        given:
        byte[] jar = published(address)

        expect: 'two applications, and the jar is asked for both'
        create('first', '--features', coordinates, '--feature-pack-repository', REPOSITORY) == 0
        asked*.toString().count(address) == 1
        create('second', '--features', coordinates, '--feature-pack-repository', REPOSITORY) == 0
        asked*.toString().count(address) == 2
        !out.toString().contains('is the copy that is kept')

        and: 'it is kept all the same, for when it is asked for by its digest or without the network'
        Files.readAllBytes(packs.resolve(sha256(jar) + '.jar')) == jar

        where:
        kind         | coordinates | address
        'a release'  | COORDINATES | JAR
        'a snapshot' | SNAPSHOT    | SNAPSHOT_JAR
    }

    void "a jar that was given another SHA-256 than the one that is asked for is not taken from its copy"() {
        given: 'the jar of a first application'
        byte[] jar = published(JAR)
        create('first', '--features', COORDINATES, '--feature-pack-repository', REPOSITORY) == 0
        String other = 'ab' * 32

        when: 'another digest is asked for'
        asked.clear()
        int exit = create('second', '--features', COORDINATES, '--feature-pack-repository', REPOSITORY, '--feature-pack-sha256', "${COORDINATES}=${other}")

        then: 'the repository is asked, and its jar is refused as it was before jars were kept'
        exit != 0
        asked*.toString() == [JAR]
        err.toString().contains("checksum mismatch: the sha256 of acme-audit-grails-2.3.0.jar is ${sha256(jar)}, and ${other} was asked for")
    }

    void "the application of a record is generated again without the network from the jar that is kept"() {
        given: 'an application whose pack was fetched'
        byte[] jar = published(JAR)
        String sha256 = sha256(jar)
        create('app', '--features', COORDINATES, '--feature-pack-repository', REPOSITORY) == 0
        File app = new File(work, 'app')
        asked.clear()

        when:
        int exit = regenerate(app, '--offline', '--output', new File(work, 'again').absolutePath)

        then: 'nothing is asked, the command says which jar it used, and the application is the same'
        exit == 0
        asked.isEmpty()
        out.toString().contains("The feature pack acme-audit of the application is fetched again as ${COORDINATES}, with the SHA-256 ${sha256} its record has.")
        out.toString().replaceAll(/\s+/, ' ').contains("It was not fetched: the jar is the copy that is kept on this computer, ${packs.resolve(sha256 + '.jar')}, which has that SHA-256")
        new File(work, 'again/.grails/forge.lock.yml').text == new File(app, '.grails/forge.lock.yml').text
        new File(work, 'again/build.gradle').text == new File(app, 'build.gradle').text

        when: 'with the network, the jar of the record is not fetched either: its digest is known'
        exit = regenerate(app, '--diff')

        then:
        exit == 0
        asked.isEmpty()
        out.toString().replaceAll(/\s+/, ' ').contains('It was not fetched: the jar is the copy that is kept on this computer')

        when: 'a feature is added without the network'
        exit = add(app, 'grails-quartz', '--offline')

        then:
        exit == 0
        asked.isEmpty()
        new File(app, 'build.gradle').text.contains('quartz')
    }

    void "a jar with the SHA-256 of the record is used without the network wherever it was fetched from"() {
        given: 'an application, and its jar kept as one that was fetched from another repository'
        byte[] jar = published(JAR)
        String sha256 = sha256(jar)
        create('app', '--features', COORDINATES, '--feature-pack-repository', REPOSITORY) == 0
        Files.newDirectoryStream(packs, '*.last').withCloseable { files -> files.each { Path file -> Files.delete(file) } }
        asked.clear()

        when:
        int exit = regenerate(new File(work, 'app'), '--offline', '--diff')

        then:
        exit == 0
        asked.isEmpty()
        out.toString().replaceAll(/\s+/, ' ').contains("It was not fetched: the jar is the copy that is kept on this computer, ${packs.resolve(sha256 + '.jar')}, which has that SHA-256")

        when: 'with the network such a jar is not used: where it was found is asked'
        exit = regenerate(new File(work, 'app'), '--diff')

        then:
        exit == 0
        asked*.toString() == [CENTRAL, JAR, JAR + '.sha256']
        !out.toString().contains('It was not fetched')
    }

    void "a kept jar whose bytes are not those of its name is removed and fetched again"() {
        given: 'a jar that is kept, and whose file was changed'
        byte[] jar = published(JAR)
        String sha256 = sha256(jar)
        create('first', '--features', COORDINATES, '--feature-pack-repository', REPOSITORY) == 0
        Path copy = packs.resolve(sha256 + '.jar')
        Files.write(copy, differentJar())
        asked.clear()

        when: 'without the network it is not used, and is removed'
        int exit = create('offline', '--offline', '--features', COORDINATES, '--feature-pack-repository', REPOSITORY, '--feature-pack-sha256', "${COORDINATES}=${sha256}")

        then:
        exit != 0
        asked.isEmpty()
        !Files.exists(copy)
        out.toString().contains("The copy of a jar that is kept as ${copy} did not have the SHA-256 it is named by, and was removed")
        err.toString().contains("No copy of the jar of ${COORDINATES} with the SHA-256 ${sha256} is kept on this computer, in ${packs}")
        !new File(work, 'offline/build.gradle').exists()

        when: 'the file is changed again, and the network is asked'
        Files.write(copy, differentJar())
        exit = create('second', '--features', COORDINATES, '--feature-pack-repository', REPOSITORY, '--feature-pack-sha256', "${COORDINATES}=${sha256}")

        then: 'the jar is fetched, and kept as it is'
        exit == 0
        asked*.toString() == [JAR, JAR + '.sha256']
        out.toString().contains("The copy of a jar that is kept as ${copy} did not have the SHA-256 it is named by, and was removed")
        out.toString().contains('    the jar matches the sha256 checksum the repository publishes for it')
        Files.readAllBytes(copy) == jar
        new File(work, 'second/.grails/forge.lock.yml').text == new File(work, 'first/.grails/forge.lock.yml').text
    }

    @Unroll
    void "without the network a jar that is not kept is refused, and the refusal names it #how"() {
        when:
        List<String> pinned = digest == null ? [] : ['--feature-pack-sha256', "${COORDINATES}=${digest}".toString()]
        int exit = create('app', (['--offline', '--features', COORDINATES, '--feature-pack-repository', REPOSITORY] + pinned) as String[])

        then:
        exit != 0
        asked.isEmpty()
        err.toString().contains("The feature pack of ${COORDINATES} was not taken from a repository. ${REPOSITORY}: not asked, since --offline makes no " +
                "request to the network. ${refusal.replace('PACKS', packs.toString())}")
        !new File(work, 'app/build.gradle').exists()
        !Files.exists(packs)

        where:
        how                                 | digest    || refusal
        'by its coordinates and its digest' | 'cd' * 32 || "No copy of the jar of ${COORDINATES} with the SHA-256 ${'cd' * 32} is kept on this computer, in PACKS"
        'by its coordinates'                | null      || "No copy of a jar that was fetched for ${COORDINATES} from that repository is kept on this computer, in PACKS"
    }

    void "without the network and without a SHA-256, the jar that was last fetched from that repository for those coordinates is used, and said to be"() {
        given:
        byte[] jar = published(SNAPSHOT_JAR)
        String sha256 = sha256(jar)
        create('first', '--features', SNAPSHOT, '--feature-pack-repository', REPOSITORY) == 0
        asked.clear()

        when:
        int exit = create('second', '--offline', '--features', SNAPSHOT, '--feature-pack-repository', REPOSITORY)

        then:
        exit == 0
        asked.isEmpty()
        out.toString().contains("    the jar is the copy that is kept on this computer, ${packs.resolve(sha256 + '.jar')}, of the jar that was last fetched from " +
                "${REPOSITORY} for ${SNAPSHOT}: --offline asks nothing of the network, so whether the repository still has that jar is not known")
        !out.toString().contains('the jar has the SHA-256 that was asked for')
        new File(work, 'second/.grails/forge.lock.yml').text == new File(work, 'first/.grails/forge.lock.yml').text

        when: 'another repository is named, which never gave a jar for the coordinates'
        exit = create('third', '--offline', '--features', SNAPSHOT, '--feature-pack-repository', 'https://other.invalid/maven/')

        then: 'its jar is not known, and that of the first repository is not taken for it'
        exit != 0
        asked.isEmpty()
        err.toString().contains("No copy of a jar that was fetched for ${SNAPSHOT} from that repository is kept on this computer, in ${packs}")
    }

    @Unroll
    void "a jar that #failure is not kept, whole or in part"() {
        given:
        byte[] jar = jar()
        answers[JAR] = answer.curry(jar)
        answers[JAR + '.sha256'] = { new Reply(200, null, new ByteArrayInputStream(checksum.call(jar).bytes)) }

        when:
        int exit = create('app', '--features', COORDINATES, '--feature-pack-repository', REPOSITORY)

        then:
        exit != 0
        err.toString().contains(said)
        !Files.exists(packs) || kept().isEmpty()

        where:
        failure                                | answer                                                                           | checksum                    || said
        'ends before its last byte'            | { byte[] bytes -> new Reply(200, null, new Broken(bytes)) }                      | { byte[] bytes -> sha256(bytes) } || 'cannot be read (IOException)'
        'is not the one the repository names'  | { byte[] bytes -> new Reply(200, null, new ByteArrayInputStream(bytes)) }        | { byte[] bytes -> 'ef' * 32 }     || 'checksum mismatch'
        'holds no feature pack'                | { byte[] bytes -> new Reply(200, null, new ByteArrayInputStream(differentJar(false))) } | { byte[] bytes -> sha256(differentJar(false)) } || 'cannot be used'
    }

    void "--refresh fetches a jar whose copy is kept, and keeps what it fetched"() {
        given:
        byte[] jar = published(JAR)
        String sha256 = sha256(jar)
        create('first', '--features', COORDINATES, '--feature-pack-repository', REPOSITORY) == 0
        asked.clear()

        when:
        int exit = create('second', '--refresh', '--features', COORDINATES, '--feature-pack-repository', REPOSITORY, '--feature-pack-sha256', "${COORDINATES}=${sha256}")

        then:
        exit == 0
        asked*.toString() == [JAR, JAR + '.sha256']
        !out.toString().contains('is the copy that is kept')
        Files.readAllBytes(packs.resolve(sha256 + '.jar')) == jar

        when: 'with --offline it is refused, as it is for a catalog'
        exit = create('third', '--refresh', '--offline', '--features', COORDINATES, '--feature-pack-repository', REPOSITORY)

        then:
        exit == 2
        err.toString().contains('--refresh reads a feature catalog anew and --offline asks nothing of the network: give one of them')
    }

    @IgnoreIf({ !PackJarCacheSpec.POSIX })
    void "no jar is read from or kept in a directory that others can write to"() {
        given: 'a jar that is kept, in a directory that is then opened to others'
        byte[] jar = published(JAR)
        String sha256 = sha256(jar)
        create('first', '--features', COORDINATES, '--feature-pack-repository', REPOSITORY) == 0
        Files.setPosixFilePermissions(packs, PosixFilePermissions.fromString('rwxrwxrwx'))
        Files.delete(packs.resolve(sha256 + '.jar'))
        asked.clear()

        when: 'the jar is asked for by its digest'
        int exit = create('second', '--features', COORDINATES, '--feature-pack-repository', REPOSITORY, '--feature-pack-sha256', "${COORDINATES}=${sha256}")

        then: 'it is fetched, the command says why no copy is used, and none is written'
        exit == 0
        asked*.toString() == [JAR, JAR + '.sha256']
        out.toString().contains("No copy of the jar of a plugin is read or kept: ${packs} is not a directory of yours that nobody else can write to: it can be " +
                'written to by its group or by others')
        !Files.exists(packs.resolve(sha256 + '.jar'))

        when: 'without the network'
        asked.clear()
        exit = create('third', '--offline', '--features', COORDINATES, '--feature-pack-repository', REPOSITORY, '--feature-pack-sha256', "${COORDINATES}=${sha256}")

        then:
        exit != 0
        asked.isEmpty()
        err.toString().contains("No copy of a jar is read on this computer, whether there is one or not: ${packs} is not a directory of yours")
    }

    void "the jar of a repository that is a directory is read from the directory, and is not kept"() {
        given:
        File repository = new File(work, 'repository')
        File directory = new File(repository, 'com/acme/acme-audit-grails/2.3.0')
        directory.mkdirs()
        byte[] jar = jar()
        new File(directory, 'acme-audit-grails-2.3.0.jar').bytes = jar
        String address = repository.toPath().toUri().toString()

        expect:
        create('app', '--features', COORDINATES, '--feature-pack-repository', address, '--feature-pack-sha256', "${COORDINATES}=${sha256(jar)}") == 0
        create('again', '--offline', '--features', COORDINATES, '--feature-pack-repository', address, '--feature-pack-sha256', "${COORDINATES}=${sha256(jar)}") == 0
        asked.isEmpty()
        !Files.exists(packs)
        !out.toString().contains('is the copy that is kept')
    }

    // the names of what is kept, with those of the records of a fetch, which are digests of what they are of, as one pattern
    private List<String> kept() {
        List<String> names = []
        Files.newDirectoryStream(packs).withCloseable { files -> files.each { Path file -> names << file.fileName.toString() } }
        return names.collect { String name -> name.endsWith('.last') ? '*.last' : name }.sort { String name -> name.startsWith('*') ? 1 : 0 }
    }

    // a jar the repository of this specification answers with, and whose SHA-256 it publishes
    private byte[] published(String address) {
        byte[] jar = jar()
        answers[address] = { new Reply(200, null, new ByteArrayInputStream(jar)) }
        answers[address + '.sha256'] = { new Reply(200, null, new ByteArrayInputStream(sha256(jar).bytes)) }
        return jar
    }

    static byte[] jar() {
        return zip(['META-INF/grails-forge/feature.yml'        : CreateAppFeaturePackSpec.DESCRIPTOR,
                    'META-INF/grails-forge/templates/audit.txt': 'kept as it is'])
    }

    // a jar with other bytes: of another pack, or of none
    static byte[] differentJar(boolean withPack = true) {
        return zip(withPack ? ['META-INF/grails-forge/feature.yml'        : CreateAppFeaturePackSpec.DESCRIPTOR,
                               'META-INF/grails-forge/templates/audit.txt': 'not what was fetched'] : ['META-INF/MANIFEST.MF': 'Manifest-Version: 1.0\n'])
    }

    static byte[] zip(Map<String, String> entries) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream()
        new ZipOutputStream(bytes).withCloseable { ZipOutputStream zip ->
            entries.each { String path, String text ->
                ZipEntry entry = new ZipEntry(path)
                entry.time = 0
                zip.putNextEntry(entry)
                zip.write(text.getBytes('UTF-8'))
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    static String sha256(byte[] bytes) {
        return MessageDigest.getInstance('SHA-256').digest(bytes).encodeHex().toString()
    }

    private PackArtifacts artifacts() {
        return new PackArtifacts(transport).keepingJarsUnder(home.toPath())
    }

    private int create(String name, String... arguments) {
        File directory = new File(work, name)
        directory.mkdirs()
        CreateAppCommand command = new CreateAppCommand(beanContext.getBean(WebAvailableFeatures), beanContext.getBean(ContextFactory),
                beanContext.getBean(ProjectGenerator)) {

            @Override
            protected PackArtifacts createPackArtifacts() {
                return artifacts()
            }

            @Override
            protected String grailsRepoUrl() {
                return null
            }
        }
        return inDirectory(directory) {
            new CommandLine(command, new GrailsPicocliFactory(beanContext)).execute((['com.example.shop', '--inplace'] + arguments.toList()) as String[])
        }
    }

    private int regenerate(File directory, String... arguments) {
        ProjectRegenerateCommand command = new ProjectRegenerateCommand(beanContext.getBean(ProjectGenerator)) {

            @Override
            protected PackArtifacts createPackArtifacts() {
                return artifacts()
            }

            @Override
            protected String grailsRepoUrl() {
                return null
            }
        }
        return inDirectory(directory) { new CommandLine(command, new GrailsPicocliFactory(beanContext)).execute(arguments) }
    }

    private int add(File directory, String... arguments) {
        FeatureAddCommand command = new FeatureAddCommand(beanContext.getBean(ProjectGenerator)) {

            @Override
            protected PackArtifacts createPackArtifacts() {
                return artifacts()
            }

            @Override
            protected String grailsRepoUrl() {
                return null
            }
        }
        return inDirectory(directory) { new CommandLine(command, new GrailsPicocliFactory(beanContext)).execute(arguments) }
    }

    private int inDirectory(File directory, Closure<Integer> run) {
        String current = System.getProperty('user.dir')
        System.setProperty('user.dir', directory.absolutePath)
        try {
            out.reset()
            err.reset()
            return run.call()
        } finally {
            System.setProperty('user.dir', current)
        }
    }

    // an answer that ends with a failure before its last byte
    static final class Broken extends InputStream {

        private final byte[] bytes
        private int at

        Broken(byte[] bytes) {
            this.bytes = bytes
        }

        @Override
        int read() throws IOException {
            if (at >= bytes.length.intdiv(2)) {
                throw new IOException('the connection was reset')
            }
            return bytes[at++] & 0xff
        }
    }
}
