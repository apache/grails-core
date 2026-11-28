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

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

import org.gradle.testkit.runner.GradleRunner

/**
 * The jar of a plugin with a Forge feature pack, and what is published with it, are the same bytes
 * whoever builds them: in another directory, with files that were written in another order, at other
 * times and with other permissions, and by a build whose language, time zone and clock are others.
 */
class GrailsPluginForgePackReproducibilitySpec extends GradleSpecification {

    def "the jar with the feature pack and what is published with it are the same bytes from two builds that differ in everything but the sources"() {
        given: 'the plugin, with more files in its pack than one directory lists in the order of their names'
        GradleRunner runner = setupTestResourceProject('plugin-forge-pack')
        File first = runner.projectDir
        ['zeta.txt', 'Alpha.txt', 'beta/one.txt', 'beta/Two.txt', 'a/b/c/deep.txt', '10.txt', '9.txt'].each { String name ->
            File file = new File(first, "src/main/forge/templates/more/${name}")
            file.parentFile.mkdirs()
            file.setText("of ${name}\n", 'UTF-8')
        }

        and: 'the same sources in a directory of another name and depth: written in the opposite order, a year apart, and readable by other people or not'
        File second = new File(first.parentFile, 'Another Place/deeper/plugin')
        List<Path> files = Files.walk(first.toPath()).withCloseable { it.filter { Path path -> Files.isRegularFile(path) }.toList() }
        boolean posix = first.toPath().fileSystem.supportedFileAttributeViews().contains('posix')
        files.toSorted().reverse().each { Path file ->
            Path copy = second.toPath().resolve(first.toPath().relativize(file).toString())
            Files.createDirectories(copy.parent)
            Files.copy(file, copy)
            Files.setLastModifiedTime(file, FileTime.fromMillis(1_500_000_000_000L))
            Files.setLastModifiedTime(copy, FileTime.fromMillis(1_700_000_000_000L + files.indexOf(file) * 61_000L))
            if (posix) {
                Files.setPosixFilePermissions(file, PosixFilePermissions.fromString('rw-r--r--'))
                Files.setPosixFilePermissions(copy, PosixFilePermissions.fromString('rwxrwx---'))
            }
        }
        if (posix) {
            Files.walk(second.toPath()).withCloseable { it.filter { Path path -> Files.isDirectory(path) }.toList() }.each { Path directory ->
                Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString('rwx------'))
            }
        }

        when: 'the first is built as a developer builds it'
        Map<String, String> environment = new HashMap<>(System.getenv())
        environment.remove('SOURCE_DATE_EPOCH')
        runner.withProjectDir(first).withEnvironment(environment + [TZ: 'UTC', LANG: 'C', LC_ALL: 'C'])
        executeTask('publish', ['-Ppublished', '--max-workers=2', '--no-build-cache',
                                '-Dorg.gradle.jvmargs=-Xmx768m -Duser.language=en -Duser.country=US -Duser.timezone=UTC -Dfile.encoding=UTF-8'])

        and: 'the second on a computer of another language and time zone, with the clock of a release'
        runner.withProjectDir(second).withEnvironment(environment + [TZ: 'Pacific/Kiritimati', LANG: 'tr_TR.UTF-8', LC_ALL: 'tr_TR.UTF-8', SOURCE_DATE_EPOCH: '1600000000'])
        executeTask('publish', ['-Ppublished', '--max-workers=2', '--no-build-cache',
                                '-Dorg.gradle.jvmargs=-Xmx768m -Duser.language=tr -Duser.country=TR -Duser.timezone=Pacific/Kiritimati -Dfile.encoding=ISO-8859-9'])

        then: 'the jar is the same bytes'
        File jar = new File(first, 'build/libs/test-plugin-forge-pack-1.0.0.jar')
        File other = new File(second, 'build/libs/test-plugin-forge-pack-1.0.0.jar')
        jar.file
        sha(jar) == sha(other)

        and: 'its entries are in one order, in a directory the files and then the directories and each by name, with one time and the modes of a jar and not those of the files'
        List<ZipEntry> entries = new ZipFile(jar).withCloseable { ZipFile zip -> zip.entries().toList() }
        List<String> pack = entries*.name.findAll { String name -> name.startsWith('META-INF/grails-forge/') && !name.endsWith('/') }
        pack == ['META-INF/grails-forge/feature.yml',
                 'META-INF/grails-forge/templates/Audit.groovy.gsp',
                 'META-INF/grails-forge/templates/conf/audit.txt',
                 'META-INF/grails-forge/templates/more/10.txt',
                 'META-INF/grails-forge/templates/more/9.txt',
                 'META-INF/grails-forge/templates/more/Alpha.txt',
                 'META-INF/grails-forge/templates/more/zeta.txt',
                 'META-INF/grails-forge/templates/more/a/b/c/deep.txt',
                 'META-INF/grails-forge/templates/more/beta/Two.txt',
                 'META-INF/grails-forge/templates/more/beta/one.txt']
        entries*.time.toUnique().size() == 1
        entries.findAll { ZipEntry entry -> !entry.directory }.every { ZipEntry entry -> unixMode(jar, entry.name) == 0644 }
        entries.findAll { ZipEntry entry -> entry.directory }.every { ZipEntry entry -> unixMode(jar, entry.name) == 0755 }

        and: 'what names the pack to a tool that reads no descriptor is there, and nothing of the computer or the time of the build'
        String manifest = new ZipFile(jar).withCloseable { ZipFile zip -> zip.getInputStream(zip.getEntry('META-INF/MANIFEST.MF')).getText('UTF-8') }
        manifest.contains('Grails-Forge-Pack: acme-audit')
        !manifest.contains(first.name)
        !manifest.contains(System.getProperty('user.name')) || System.getProperty('user.name').length() < 4

        and: 'the files of the pack are the bytes of the sources'
        new ZipFile(jar).withCloseable { ZipFile zip ->
            pack.every { String name ->
                zip.getInputStream(zip.getEntry(name)).bytes == new File(first, 'src/main/forge/' + name.substring('META-INF/grails-forge/'.length())).bytes
            }
        }

        and: 'what is published beside the jar is the same too: the pom and the module file, which has the digests of the jar'
        Map<String, String> firstPublication = published(new File(first, 'build/published'))
        firstPublication.keySet().any { String name -> name.endsWith('.pom') }
        firstPublication.keySet().any { String name -> name.endsWith('.module') }
        firstPublication == published(new File(second, 'build/published'))
    }

    // the files of a published version by name with their digests; not the listing of the versions of the repository, which says when it was written
    private static Map<String, String> published(File repository) {
        Map<String, String> files = new TreeMap<>()
        repository.eachFileRecurse { File file ->
            if (file.file && !file.name.startsWith('maven-metadata.xml')) {
                files.put(repository.toPath().relativize(file.toPath()).join('/'), sha(file))
            }
        }
        files
    }

    private static String sha(File file) {
        MessageDigest.getInstance('SHA-256').digest(file.bytes).encodeHex().toString()
    }

    // the permissions an entry has in the central directory of the jar, where a tool of Unix reads them
    private static int unixMode(File jar, String name) {
        byte[] bytes = jar.bytes
        byte[] wanted = name.getBytes('UTF-8')
        for (int at = bytes.length - 46; at >= 0; at--) {
            if (bytes[at] == 0x50 && bytes[at + 1] == 0x4b && bytes[at + 2] == 0x01 && bytes[at + 3] == 0x02) {
                int length = (bytes[at + 28] & 0xff) | ((bytes[at + 29] & 0xff) << 8)
                if (length == wanted.length && Arrays.equals(Arrays.copyOfRange(bytes, at + 46, at + 46 + length), wanted)) {
                    int high = (bytes[at + 40] & 0xff) | ((bytes[at + 41] & 0xff) << 8)
                    return high & 0777
                }
            }
        }
        throw new IllegalArgumentException("${jar} has no entry ${name}")
    }
}
