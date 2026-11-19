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

import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.apache.grails.forge.cli.pack.PackArtifacts
import org.grails.forge.cli.CliRunner
import org.grails.forge.cli.CommandFixture
import org.grails.forge.cli.CommandSpec
import org.yaml.snakeyaml.Yaml
import spock.lang.Unroll

/**
 * The feature pack of a plugin that is given by the coordinates of the plugin. Every repository here is
 * a directory: nothing is asked of the network.
 */
class CreateAppPackCoordinatesSpec extends CommandSpec implements CommandFixture {

    static final String COORDINATES = 'com.acme:acme-audit-grails:2.3.0'

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    ByteArrayOutputStream err = new ByteArrayOutputStream()
    File repository
    String address

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out))
        System.setErr(new PrintStream(err))
        repository = Files.createTempDirectory('grailsforgerepository').toFile()
        address = repository.toPath().toAbsolutePath().normalize().toUri().toString()
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
        repository.deleteDir()
    }

    void "an application is created with the feature pack of a plugin that is given by its coordinates"() {
        given:
        File jar = publish(COORDINATES)

        when: 'the plugin is selected next to a feature of the framework'
        int exit = CliRunner.run(CreateAppCommand, beanContext, 'com.example.shop', '--features', "h2,${COORDINATES}",
                '--feature-pack-repository', address)

        then:
        exit == 0
        out.toString().contains("Feature pack acme-audit from ${COORDINATES} (${address}), sha256 ${sha256(jar)}")
        out.toString().contains('    the repository publishes no checksum the jar could be checked against')
        out.toString().contains('    dependencies: implementation com.acme:acme-audit-grails:2.3.0')
        !out.toString().contains('Warning')
        out.toString().contains('Application created')

        and: 'the feature of the pack is the one that is selected, under its own name'
        new File(dir, 'shop/build.gradle').text.contains('implementation "com.acme:acme-audit-grails:2.3.0"')
        new File(dir, 'shop/grails-app/conf/com/example/audit.txt').text == 'kept as it is'

        and: 'what the application was made with is written next to its features, where the commands of the application still read them'
        Map config = new Yaml().load(new File(dir, 'shop/grails-forge-cli.yml').text) as Map
        (config.features as List).containsAll(['acme-audit', 'h2'])
        config.featurePacks == [[name: 'acme-audit', coordinates: COORDINATES, repository: address,
                                 artifact: 'acme-audit-grails-2.3.0.jar', sha256: sha256(jar)]]
    }

    void "the feature of the plugin is listed with the others"() {
        given:
        publish(COORDINATES)

        when:
        int exit = CliRunner.run(CreateAppCommand, beanContext, '--list-features', '--features', COORDINATES,
                '--feature-pack-repository', address)

        then:
        exit == 0
        out.toString() =~ /acme-audit\s+Adds the Acme audit plugin/
    }

    void "a plugin that no repository has is said to be found in none, and nothing is created"() {
        given:
        File other = Files.createTempDirectory('grailsforgerepository').toFile()
        String otherAddress = other.toPath().toAbsolutePath().normalize().toUri().toString()

        when:
        int exit = CliRunner.run(CreateAppCommand, beanContext, 'shop', '--features', COORDINATES,
                '--feature-pack-repository', address, '--feature-pack-repository', otherAddress)

        then:
        exit == 1
        err.toString().contains("The feature pack of ${COORDINATES} was not taken from a repository. ${address}: not found; ${otherAddress}: not found")
        !err.toString().contains('Exception')
        !new File(dir, 'shop').exists()

        cleanup:
        other.deleteDir()
    }

    void "the second repository is asked when the first does not have the plugin"() {
        given:
        File empty = Files.createTempDirectory('grailsforgerepository').toFile()
        publish(COORDINATES)

        when:
        int exit = CliRunner.run(CreateAppCommand, beanContext, 'shop', '--features', COORDINATES,
                '--feature-pack-repository', empty.toURI().toString(), '--feature-pack-repository', address)

        then:
        exit == 0
        out.toString().contains("Feature pack acme-audit from ${COORDINATES} (${address})")

        cleanup:
        empty.deleteDir()
    }

    @Unroll
    void "a jar is checked against the #kind the repository publishes for it"() {
        given:
        File jar = publish(COORDINATES)
        new File(jar.path + ".${kind}").setText(digest(jar, algorithm) + suffix, 'UTF-8')

        when:
        int exit = CliRunner.run(CreateAppCommand, beanContext, 'shop', '--features', COORDINATES, '--feature-pack-repository', address)

        then:
        exit == 0
        out.toString().contains("    the jar matches the ${kind} checksum the repository publishes for it")

        where:
        kind     | algorithm | suffix
        'sha256' | 'SHA-256' | '\n'
        'sha1'   | 'SHA-1'   | '  acme-audit-grails-2.3.0.jar\n'
    }

    @Unroll
    void "a jar that is not the one the repository publishes a #kind for is refused, and nothing is created"() {
        given:
        File jar = publish(COORDINATES)
        new File(jar.path + ".${kind}").setText(published, 'UTF-8')

        when:
        int exit = CliRunner.run(CreateAppCommand, beanContext, 'shop', '--features', COORDINATES, '--feature-pack-repository', address)

        then:
        exit == 1
        err.toString().contains("The feature pack of ${COORDINATES} was not taken from a repository. ${address}: ${reason}")
        !out.toString().contains('Feature pack')
        !new File(dir, 'shop').exists()

        where:
        kind     | published  || reason
        'sha256' | '0' * 64   || 'checksum mismatch: the sha256 of acme-audit-grails-2.3.0.jar is'
        'sha1'   | 'f' * 40   || 'checksum mismatch: the sha1 of acme-audit-grails-2.3.0.jar is'
        'sha256' | 'not one'  || 'acme-audit-grails-2.3.0.jar.sha256 of the repository is not a sha256'
        'sha256' | 'a' * 2000 || 'acme-audit-grails-2.3.0.jar.sha256 has more than the 1024 bytes it can have'
    }

    void "a repository that gives a wrong jar ends the search: the next one is not asked"() {
        given:
        File jar = publish(COORDINATES)
        new File(jar.path + '.sha256').setText('0' * 64, 'UTF-8')
        File other = Files.createTempDirectory('grailsforgerepository').toFile()

        when:
        int exit = CliRunner.run(CreateAppCommand, beanContext, 'shop', '--features', COORDINATES,
                '--feature-pack-repository', address, '--feature-pack-repository', other.toURI().toString())

        then:
        exit == 1
        err.toString().contains('checksum mismatch')
        err.toString().contains('(1 more repository not asked)')

        cleanup:
        other.deleteDir()
    }

    void "a jar that has the SHA-256 that was asked for is taken"() {
        given:
        File jar = publish(COORDINATES)

        when:
        int exit = CliRunner.run(CreateAppCommand, beanContext, 'shop', '--features', COORDINATES, '--feature-pack-repository', address,
                '--feature-pack-sha256', "${COORDINATES}=${sha256(jar).toUpperCase()}")

        then:
        exit == 0
        out.toString().contains("Feature pack acme-audit from ${COORDINATES} (${address}), sha256 ${sha256(jar)}")
        out.toString().contains('    the jar has the SHA-256 that was asked for with --feature-pack-sha256')
    }

    void "a jar that does not have the SHA-256 that was asked for is refused before it is opened"() {
        given: 'a file that is no jar, which would be said if it were opened'
        File jar = publish(COORDINATES)
        jar.setText('not a jar', 'UTF-8')

        when:
        int exit = CliRunner.run(CreateAppCommand, beanContext, 'shop', '--features', COORDINATES, '--feature-pack-repository', address,
                '--feature-pack-sha256', "${COORDINATES}=${'1' * 64}")

        then:
        exit == 1
        err.toString().contains("${address}: checksum mismatch: the sha256 of acme-audit-grails-2.3.0.jar is ${sha256(jar)}, and ${'1' * 64} was asked for")
        !err.toString().contains('cannot be read as a jar')
        !new File(dir, 'shop').exists()
    }

    @Unroll
    void "the option #options is a mistake in the command line"() {
        given:
        publish(COORDINATES)

        when:
        int exit = CliRunner.run(CreateAppCommand, beanContext, (['shop', '--features', COORDINATES] + options) as String[])

        then:
        exit == 2
        err.toString().contains(message)
        !new File(dir, 'shop').exists()

        where:
        options                                                                          || message
        ['--feature-pack-sha256', 'com.acme:acme-audit-grails:2.3.0']                    || 'is not group:artifact:version=sha256'
        ['--feature-pack-sha256', 'com.acme:acme-audit-grails:2.3.0=abc']                || 'does not end with a SHA-256'
        ['--feature-pack-sha256', "com.acme:other:1.0=${'1' * 64}".toString()]           || 'which is not among the features asked for with --features'
        ['--feature-pack-sha256', "${COORDINATES}=${'1' * 64}".toString(),
         '--feature-pack-sha256', "${COORDINATES}=${'2' * 64}".toString()]               || 'gives two different SHA-256'
        ['--feature-pack-repository', 'http://repository.invalid/maven']                 || 'the address has to be https, or file: for a directory'
        ['--feature-pack-repository', 'ftp://repository.invalid/maven']                  || 'the address has to be https, or file: for a directory'
        ['--feature-pack-repository', 'https://user:secret@repository.invalid/maven']    || 'the address cannot have a user, a query or a fragment'
        ['--feature-pack-repository', 'https://repository.invalid/maven?token=1']        || 'the address cannot have a user, a query or a fragment'
        ['--feature-pack-repository', 'repository']                                      || 'the address has to be https, or file: for a directory'
    }

    @Unroll
    void "#feature is not the coordinates of one version of a plugin"() {
        when: 'the repository is one that cannot be asked, and it is not'
        int exit = CliRunner.run(CreateAppCommand, beanContext, 'shop', '--features', feature)

        then:
        exit == 2
        err.toString().contains(message)
        !new File(dir, 'shop').exists()

        where:
        feature                               || message
        'com.acme:acme-audit-grails'          || 'they are group:artifact:version'
        'com.acme:acme-audit-grails:2.3.0:x'  || 'they are group:artifact:version'
        'com.acme:acme-audit-grails:'         || 'the version is one version'
        ':acme-audit-grails:2.3.0'            || 'starts with a letter or digit'
        'com.acme:acme-audit-grails:2.+'      || 'not a range and not one with a +'
        'com.acme:acme-audit-grails:[2.0'     || 'not a range and not one with a +'
        'com.acme:acme-audit-grails:(,3.0]'   || 'not a range and not one with a +'
        'com.acme:acme-audit-grails:latest.release' || 'does not name one version'
        'com.acme:acme-audit-grails:LATEST'   || 'does not name one version'
        'com..acme:acme-audit-grails:2.3.0'   || 'the group has an empty part'
        'com.acme:../../etc:2.3.0'            || 'starts with a letter or digit'
        'com.acme:acme audit:2.3.0'           || 'starts with a letter or digit'
    }

    void "a jar of more than 64 MiB is not read to its end"() {
        given:
        File jar = publish(COORDINATES)
        new RandomAccessFile(jar, 'rw').withCloseable { it.setLength(PackArtifacts.MAX_JAR_BYTES + 1) }

        when:
        int exit = CliRunner.run(CreateAppCommand, beanContext, 'shop', '--features', COORDINATES, '--feature-pack-repository', address)

        then:
        exit == 1
        err.toString().contains("${address}: acme-audit-grails-2.3.0.jar has more than the 67108864 bytes it can have")
        !new File(dir, 'shop').exists()
    }

    void "a jar that holds no feature pack is said to hold none, and nothing is created"() {
        given:
        publish(COORDINATES, ['META-INF/MANIFEST.MF': 'Manifest-Version: 1.0\n'])

        when:
        int exit = CliRunner.run(CreateAppCommand, beanContext, 'shop', '--features', COORDINATES, '--feature-pack-repository', address)

        then:
        exit == 2
        err.toString().contains("The feature pack descriptor ${COORDINATES} (${address}) is not valid")
        err.toString().contains('There is no META-INF/grails-forge/feature.yml in the jar')
        !new File(dir, 'shop').exists()
    }

    void "a file that is no jar is said not to be one"() {
        given:
        publish(COORDINATES).setText('not a jar', 'UTF-8')

        when:
        int exit = CliRunner.run(CreateAppCommand, beanContext, 'shop', '--features', COORDINATES, '--feature-pack-repository', address)

        then:
        exit == 2
        err.toString().contains('It cannot be read as a jar')
        !new File(dir, 'shop').exists()
    }

    void "a pack that does not add the plugin it came from is used, with a warning"() {
        given:
        publish('com.acme:acme-audit-starter:1.0')

        when:
        int exit = CliRunner.run(CreateAppCommand, beanContext, 'shop', '--features', 'com.acme:acme-audit-starter:1.0',
                '--feature-pack-repository', address)

        then:
        exit == 0
        out.toString().contains('Warning')
        out.toString().contains('The feature pack acme-audit declares no dependency on com.acme:acme-audit-starter, the plugin it was taken from: ' +
                'the application is generated without that plugin')
        !new File(dir, 'shop/build.gradle').text.contains('acme-audit-starter')
    }

    void "two plugins whose packs have one name cannot be used together"() {
        given:
        publish(COORDINATES)
        publish('com.acme:acme-audit-grails:2.4.0')

        when:
        int exit = CliRunner.run(CreateAppCommand, beanContext, 'shop', '--features', "${COORDINATES},com.acme:acme-audit-grails:2.4.0",
                '--feature-pack-repository', address)

        then:
        exit == 2
        err.toString().contains('both have the name acme-audit')
        !new File(dir, 'shop').exists()
    }

    void "a pack whose name is that of a feature of the framework is refused"() {
        given:
        publish(COORDINATES, pack(CreateAppFeaturePackSpec.DESCRIPTOR.replace('name: acme-audit', 'name: h2')))

        when:
        int exit = CliRunner.run(CreateAppCommand, beanContext, 'shop', '--features', COORDINATES, '--feature-pack-repository', address)

        then:
        exit == 2
        err.toString().contains('h2')
        !new File(dir, 'shop').exists()
    }

    void "the same coordinates given twice are one selection"() {
        given:
        publish(COORDINATES)

        when:
        int exit = CliRunner.run(CreateAppCommand, beanContext, 'shop', '--features', "${COORDINATES},${COORDINATES}",
                '--feature-pack-repository', address)

        then:
        exit == 0
        out.toString().count('Feature pack acme-audit from') == 1
    }

    void "a snapshot that was asked for is taken from its latest build, which is named"() {
        given:
        File jar = publish('com.acme:acme-audit-grails:2.4.0-SNAPSHOT', pack(CreateAppFeaturePackSpec.DESCRIPTOR), 'acme-audit-grails-2.4.0-20261119.101112-7.jar')
        new File(jar.parentFile, 'acme-audit-grails-2.4.0-20261118.090807-6.jar').setText('an older build', 'UTF-8')
        new File(jar.parentFile, 'maven-metadata.xml').setText(metadata, 'UTF-8')

        when:
        int exit = CliRunner.run(CreateAppCommand, beanContext, 'shop', '--features', 'com.acme:acme-audit-grails:2.4.0-SNAPSHOT',
                '--feature-pack-repository', address)

        then:
        exit == 0
        out.toString().contains("Feature pack acme-audit from com.acme:acme-audit-grails:2.4.0-SNAPSHOT (${address}), sha256 ${sha256(jar)}")
        Map config = new Yaml().load(new File(dir, 'shop/grails-forge-cli.yml').text) as Map
        (config.featurePacks as List<Map>)[0].artifact == 'acme-audit-grails-2.4.0-20261119.101112-7.jar'
        (config.featurePacks as List<Map>)[0].coordinates == 'com.acme:acme-audit-grails:2.4.0-SNAPSHOT'

        where:
        metadata << [
                '<?xml version="1.0" encoding="UTF-8"?>\n<metadata><groupId>com.acme</groupId><artifactId>acme-audit-grails</artifactId>' +
                        '<version>2.4.0-SNAPSHOT</version><versioning><snapshot><timestamp>20261119.101112</timestamp><buildNumber>7</buildNumber></snapshot>' +
                        '<snapshotVersions>' +
                        '<snapshotVersion><classifier>sources</classifier><extension>jar</extension><value>2.4.0-20261119.101112-9</value></snapshotVersion>' +
                        '<snapshotVersion><extension>pom</extension><value>2.4.0-20261119.101112-8</value></snapshotVersion>' +
                        '<snapshotVersion><extension>jar</extension><value>2.4.0-20261119.101112-7</value></snapshotVersion>' +
                        '</snapshotVersions></versioning></metadata>',
                '<metadata><version>2.4.0-SNAPSHOT</version><versioning><snapshot><timestamp>20261119.101112</timestamp>' +
                        '<buildNumber>7</buildNumber></snapshot></versioning></metadata>'
        ]
    }

    void "a snapshot in a repository that names no builds, as the local one of Maven, is the jar of the version"() {
        given:
        File jar = publish('com.acme:acme-audit-grails:2.4.0-SNAPSHOT')
        new File(jar.parentFile, 'maven-metadata-local.xml').setText('<metadata/>', 'UTF-8')

        when:
        int exit = CliRunner.run(CreateAppCommand, beanContext, 'shop', '--features', 'com.acme:acme-audit-grails:2.4.0-SNAPSHOT',
                '--feature-pack-repository', address)

        then:
        exit == 0
        out.toString().contains("Feature pack acme-audit from com.acme:acme-audit-grails:2.4.0-SNAPSHOT (${address})")
    }

    @Unroll
    void "the metadata of a snapshot that #what is refused"() {
        given:
        File jar = publish('com.acme:acme-audit-grails:2.4.0-SNAPSHOT')
        new File(jar.parentFile, 'maven-metadata.xml').setText(metadata, 'UTF-8')
        new File(repository, 'secret.txt').setText('a file outside the metadata', 'UTF-8')

        when:
        int exit = CliRunner.run(CreateAppCommand, beanContext, 'shop', '--features', 'com.acme:acme-audit-grails:2.4.0-SNAPSHOT',
                '--feature-pack-repository', address)

        then:
        exit == 1
        err.toString().contains("${address}: ${reason}")
        !err.toString().contains('a file outside the metadata')
        !new File(dir, 'shop').exists()

        where:
        what                             | metadata                                                                             || reason
        'is not XML'                     | 'not xml'                                                                            || 'maven-metadata.xml cannot be read'
        'has a document type'            | "<!DOCTYPE metadata [<!ENTITY x SYSTEM 'file:../../../../secret.txt'>]><metadata><versioning><snapshotVersions><snapshotVersion><extension>jar</extension><value>&x;</value></snapshotVersion></snapshotVersions></versioning></metadata>" || 'maven-metadata.xml cannot be read'
        'names a build of another version' | '<metadata><versioning><snapshotVersions><snapshotVersion><extension>jar</extension><value>9.9.9-1</value></snapshotVersion></snapshotVersions></versioning></metadata>' || 'maven-metadata.xml names a build that is not one of this snapshot'
        'names a path'                   | '<metadata><versioning><snapshotVersions><snapshotVersion><extension>jar</extension><value>2.4.0-1/../../x</value></snapshotVersion></snapshotVersions></versioning></metadata>' || 'maven-metadata.xml names a build that is not one of this snapshot'
        'is larger than it can be'       | '<metadata>' + ' ' * PackArtifacts.MAX_METADATA_BYTES + '</metadata>'                || 'maven-metadata.xml has more than the 262144 bytes it can have'
    }

    void "the options for the repository and the SHA-256 are in the help of the command"() {
        when:
        CliRunner.run(CreateAppCommand, beanContext, '--help')

        then:
        out.toString().contains('--feature-pack-repository')
        out.toString().contains('--feature-pack-sha256')
    }

    private static Map<String, String> pack(String descriptor) {
        return ['META-INF/grails-forge/feature.yml': descriptor, 'META-INF/grails-forge/templates/audit.txt': 'kept as it is']
    }

    private File publish(String coordinates, Map<String, String> entries = pack(CreateAppFeaturePackSpec.DESCRIPTOR), String fileName = null) {
        List<String> parts = coordinates.split(':').toList()
        File directory = new File(repository, "${parts[0].replace('.', '/')}/${parts[1]}/${parts[2]}")
        directory.mkdirs()
        File jar = new File(directory, fileName ?: "${parts[1]}-${parts[2]}.jar")
        new ZipOutputStream(new FileOutputStream(jar)).withCloseable { ZipOutputStream zip ->
            entries.each { String path, String text ->
                zip.putNextEntry(new ZipEntry(path))
                zip.write(text.getBytes('UTF-8'))
                zip.closeEntry()
            }
        }
        return jar
    }

    private static String sha256(File file) {
        return digest(file, 'SHA-256')
    }

    private static String digest(File file, String algorithm) {
        return MessageDigest.getInstance(algorithm).digest(file.bytes).encodeHex().toString()
    }
}
