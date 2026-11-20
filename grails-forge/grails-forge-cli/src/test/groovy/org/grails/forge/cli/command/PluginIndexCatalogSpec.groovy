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
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.apache.grails.forge.cli.pack.PackArtifacts
import org.apache.grails.forge.cli.pack.PackArtifacts.Reply
import org.apache.grails.forge.cli.pack.PackArtifacts.Transport
import org.apache.grails.forge.cli.pack.PackCatalog
import org.apache.grails.forge.core.bom.BomVersions
import org.grails.forge.application.ContextFactory
import org.grails.forge.application.WebAvailableFeatures
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.cli.CommandFixture
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.GrailsPicocliFactory
import org.grails.forge.util.VersionInfo
import picocli.CommandLine
import spock.lang.Requires
import spock.lang.Unroll

/**
 * The plugin index of Grails as a feature catalog, with the index as it is published: a part of
 * {@code grails-plugins-index.json} of {@code apache/grails-plugins-metadata} is among the resources of
 * these tests, as it is and with {@code pack} members added to some versions (see the README beside
 * it). The whole index is read when {@code -PpluginIndex=<file>} names a copy of it:
 * {@code ./gradlew :grails-forge-cli:test --tests '*PluginIndexCatalogSpec' -PpluginIndex=/path/to/grails-plugins-index.json}.
 * Nothing is asked of the network.
 */
class PluginIndexCatalogSpec extends CommandSpec implements CommandFixture {

    /** The number in the SHA-256 of a pack of the resource, the name of the pack, and the artifacts a jar with that pack is published as */
    static final Map<Integer, List> PACKS = [
            1 : ['mail', ['org.grails.plugins:grails-mail:5.0.3', 'org.grails.plugins:grails-mail:5.0.2']],
            2 : ['mail-legacy', ['org.grails.plugins:mail:4.0.2', 'org.grails.plugins:mail:3.0.0']],
            3 : ['rest-client-legacy', ['io.github.gpc:grails-datastore-rest-client-legacy:8.0.1']],
            4 : ['filterpane', ['org.grails.plugins:filterpane:2.4.3']],
            5 : ['oauth2-github', ['org.grails.plugins:spring-security-oauth2-github:1.0.2']],
            6 : ['elasticsearch', ['org.grails.plugins:elasticsearch:4.0.0']],
            7 : ['hibernate-filter', ['org.grails.plugins:grails-hibernate-filter:0.2.0']],
            8 : ['schwartz', ['com.agileorbit:schwartz:1.0.1']],
            9 : ['asset-pipeline-legacy', ['org.grails.plugins:asset-pipeline:1.9.9']],
            10: ['cache-of-a-plugin', ['org.apache.grails:grails-cache:8.0.0']],
            11: ['web-console-of-a-plugin', ['org.grails.plugins:grails-web-console:8.0.0']],
    ]

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    ByteArrayOutputStream err = new ByteArrayOutputStream()
    File repository
    File work
    String address
    List<URI> asked = []

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out))
        System.setErr(new PrintStream(err))
        repository = Files.createTempDirectory('grailsforgerepository').toFile()
        work = Files.createTempDirectory('grailsforgecatalog').toFile()
        address = repository.toPath().toAbsolutePath().normalize().toUri().toString()
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
        repository.deleteDir()
        work.deleteDir()
    }

    void "the plugin index as it is published is a catalog that records no pack"() {
        given:
        File index = resource('plugins-index.json')

        when:
        PackCatalog catalog = PackCatalog.parse(index.bytes, index.toURI())

        then: 'every plugin of it is read, and none is left out'
        catalog.plugins.isEmpty()
        catalog.withoutPacks
        catalog.withoutPack.size() == 23
        catalog.withoutPack.containsAll(['org.grails.plugins:grails-mail', 'cloud.wondrify:asset-pipeline-grails', 'org.grails.plugins:grails3-cas-client'])
        catalog.leftOut == 0
        catalog.versionsLeftOut == 0
        catalog.problems.isEmpty()

        when:
        int exit = run('--list-catalog-features', '--feature-catalog', index.toURI().toString())

        then:
        exit == 0
        out.toString().contains('  the catalog records no feature pack')
        out.toString().contains('  no feature of this catalog can be selected by its name')

        when:
        exit = run('shop', '--features', 'mail', '--feature-catalog', index.toURI().toString())

        then:
        exit == 2
        err.toString().readLines()[0] == "The requested feature does not exist: mail. mail was not found in the feature catalog ${index.toPath().toUri()} " +
                '(which records no feature pack) either'
        asked.isEmpty()
    }

    void "the plugin index with packs in some versions is read with everything else it has"() {
        given:
        File index = withPacks()

        when:
        PackCatalog catalog = PackCatalog.parse(index.bytes, index.toURI())

        then: 'the plugins with a pack, none left out, and the others by their coordinates'
        catalog.plugins*.coordinates as Set == ['org.grails.plugins:grails-mail', 'io.github.gpc:grails-datastore-rest-client-legacy', 'org.grails.plugins:filterpane',
                                               'org.grails.plugins:spring-security-oauth2-github', 'org.grails.plugins:grails-elasticsearch',
                                               'org.grails.plugins:grails-hibernate-filter', 'com.agileorbit:schwartz', 'cloud.wondrify:asset-pipeline-grails',
                                               'org.apache.grails:grails-cache', 'org.grails.plugins:grails-web-console'] as Set
        catalog.plugins.size() + catalog.withoutPack.size() == 23
        catalog.leftOut == 0
        catalog.versionsLeftOut == 0

        and: 'a version has the coordinates and the repository the index gives it, or else those of its plugin'
        PackCatalog.Plugin mail = catalog.plugins.find { it.coordinates == 'org.grails.plugins:grails-mail' }
        mail.versions*.coordinates*.toString() == ['org.grails.plugins:grails-mail:5.0.3', 'org.grails.plugins:grails-mail:5.0.2', 'org.grails.plugins:mail:4.0.2',
                                                   'org.grails.plugins:mail:3.0.0']
        mail.versions*.repository*.toString() == [PackArtifacts.MAVEN_CENTRAL, PackArtifacts.MAVEN_CENTRAL, PackArtifacts.MAVEN_CENTRAL, 'https://repo.grails.org/plugins/']
        mail.versions*.grailsVersion == ['7.0.0 > *', '7.0.0 > *', '6.0.0 > *', '3.0 > *']

        and: 'a plugin with more versions than most has its pack found among them'
        catalog.plugins.find { it.coordinates == 'cloud.wondrify:asset-pipeline-grails' }.versions*.coordinates*.toString() == ['org.grails.plugins:asset-pipeline:1.9.9']

        and: 'a version without an artifact is not there, and the reason a plugin is deprecated for is'
        catalog.plugins.find { it.coordinates == 'org.grails.plugins:grails-hibernate-filter' }.versions.isEmpty()
        catalog.plugins.find { it.coordinates == 'com.agileorbit:schwartz' }.deprecated == 'Source repository is archived.'
        catalog.plugins.find { it.coordinates == 'org.grails.plugins:grails-mail' }.deprecated == null
    }

    @Unroll
    void "the feature #name of the plugin index is taken as #coordinates"() {
        given:
        File index = withPacks()

        when:
        int exit = run('--list-features', '--features', name, '--feature-catalog', index.toURI().toString(), '--feature-pack-repository', address)

        then:
        exit == 0
        out.toString().contains("Feature pack ${name} from ${coordinates} (${address})")
        out.toString().contains("The feature ${name} is taken as ${coordinates}, ${said}which the feature catalog ${index.toPath().toUri()}")
        asked.isEmpty()

        where: 'the latest release, under the coordinates the index has for that version, with the ranges of the descriptors in every form the index has'
        name                      | coordinates                                               | said
        'mail'                    | 'org.grails.plugins:grails-mail:5.0.3'                    | ''
        'mail-legacy'             | 'org.grails.plugins:mail:4.0.2'                           | 'the coordinates the catalog gives for version 4.0.2 of the plugin org.grails.plugins:grails-mail, '
        'rest-client-legacy'      | 'io.github.gpc:grails-datastore-rest-client-legacy:8.0.1' | ''
        'filterpane'              | 'org.grails.plugins:filterpane:2.4.3'                     | ''
        'oauth2-github'           | 'org.grails.plugins:spring-security-oauth2-github:1.0.2'  | ''
        'asset-pipeline-legacy'   | 'org.grails.plugins:asset-pipeline:1.9.9'                 | 'the coordinates the catalog gives for version 1.9.9 of the plugin cloud.wondrify:asset-pipeline-grails, '
        'web-console-of-a-plugin' | 'org.grails.plugins:grails-web-console:8.0.0'             | ''
    }

    @Unroll
    void "the feature #name of the plugin index is not taken: #why"() {
        given:
        File index = withPacks()

        when:
        int exit = run('shop', '--features', name, '--feature-catalog', index.toURI().toString(), '--feature-pack-repository', address)

        then:
        exit == 2
        err.toString().readLines()[0].contains(said)
        !new File(dir, 'shop').exists()
        asked.isEmpty()

        where:
        name                | why                                                         | said
        'elasticsearch'     | 'the descriptor of the plugin has it for another Grails'    | "org.grails.plugins:grails-elasticsearch has it in no version for Grails ${VersionInfo.grailsVersion} and Forge " +
                "${VersionInfo.version} (4.0.0 is for Grails [9.0.0,10.0.0), which the descriptor of the plugin gives as 5.3.0 > 6.2.3 and Forge [9.0.0,10.0.0))"
        'schwartz'          | 'its plugin is deprecated'                                  | 'com.agileorbit:schwartz is deprecated (Source repository is archived.), and is used only by its ' +
                'coordinates, such as com.agileorbit:schwartz:1.0.1'
        'hibernate-filter'  | 'the index says its only version has no artifact'           | 'The requested feature does not exist: hibernate-filter'
        'cache-of-a-plugin' | 'the BOM of the application has the plugin in a version of its own' | 'org.apache.grails:grails-bom, which the application gets, manages ' +
                "org.apache.grails:grails-cache at ${BomVersions.of('grails-bom').versionOf('org.apache.grails:grails-cache')}, and the catalog has no pack for"
    }

    void "the list of the plugin index names what would be fetched, and the plugin where that has other coordinates"() {
        given:
        File index = withPacks()

        when:
        int exit = run('--list-catalog-features', '--feature-catalog', index.toURI().toString())

        then:
        exit == 0
        List<String> lines = out.toString().readLines()
        lines.any { it.startsWith('  mail ') && it.contains(' org.grails.plugins:grails-mail:5.0.3 ') && it.contains('Provides a convenient DSL for sending email') }
        lines.any { it.startsWith('  mail-legacy ') && it.contains(' org.grails.plugins:mail:4.0.2 of org.grails.plugins:grails-mail ') }
        lines.any { it.startsWith('  asset-pipeline-legacy ') && it.contains(' org.grails.plugins:asset-pipeline:1.9.9 of cloud.wondrify:asset-pipeline-grails ') }
        !lines.any { it.startsWith('  schwartz ') || it.startsWith('  elasticsearch ') || it.startsWith('  hibernate-filter ') || it.startsWith('  cache-of-a-plugin ') }
        lines.any { it.startsWith('  3 more are not offered: ') }
        !out.toString().contains('left out')
    }

    @Requires({ System.getProperty('forge.pluginIndex') })
    void "the whole plugin index, of the file a property names, is a catalog that is read to its end"() {
        given:
        File index = new File(System.getProperty('forge.pluginIndex'))

        when:
        PackCatalog catalog = PackCatalog.parse(index.bytes, index.toURI())

        then: 'every plugin is read: none is refused for what the index says of it'
        catalog.leftOut == 0
        catalog.versionsLeftOut == 0
        catalog.problems.isEmpty()
        catalog.plugins.size() + catalog.withoutPack.size() >= 250
        index.length() < PackCatalog.MAX_BYTES.intdiv(2)

        when:
        int exit = run('--list-catalog-features', '--feature-catalog', index.toURI().toString())

        then:
        exit == 0
        originalOut.println "the plugin index ${index}: ${index.length()} bytes, ${catalog.plugins.size()} plugins with a pack, ${catalog.withoutPack.size()} without"
    }

    private File resource(String name) {
        File copy = new File(work, name)
        copy.bytes = getClass().getResourceAsStream("/plugins-index/${name}").bytes
        return copy
    }

    /**
     * The index with packs, in which the SHA-256 of each pack is that of a jar this publishes for it:
     * the resource has the number of the pack in the place of the digest.
     */
    private File withPacks() {
        String text = new String(getClass().getResourceAsStream('/plugins-index/plugins-index-with-packs.json').bytes, 'UTF-8')
        PACKS.each { Integer number, List pack ->
            File first = null
            (pack[1] as List<String>).each { String coordinates ->
                List<String> parts = coordinates.split(':').toList()
                File directory = new File(repository, "${parts[0].replace('.', '/')}/${parts[1]}/${parts[2]}")
                directory.mkdirs()
                File jar = new File(directory, "${parts[1]}-${parts[2]}.jar")
                if (first == null) {
                    new ZipOutputStream(new FileOutputStream(jar)).withCloseable { ZipOutputStream zip ->
                        ['META-INF/grails-forge/feature.yml'          : CreateAppFeaturePackSpec.DESCRIPTOR.replace('name: acme-audit', "name: ${pack[0]}"),
                         'META-INF/grails-forge/templates/audit.txt': 'kept as it is'].each { String path, String content ->
                            zip.putNextEntry(new ZipEntry(path))
                            zip.write(content.getBytes('UTF-8'))
                            zip.closeEntry()
                        }
                    }
                    first = jar
                } else {
                    jar.bytes = first.bytes
                }
            }
            String placeholder = String.format('%064d', number)
            assert text.contains(placeholder)
            text = text.replace(placeholder, MessageDigest.getInstance('SHA-256').digest(first.bytes).encodeHex().toString())
        }
        File index = new File(work, 'plugins-index-with-packs.json')
        index.setText(text, 'UTF-8')
        return index
    }

    private int run(String... args) {
        Path home = work.toPath()
        Transport transport = { URI asking ->
            asked << asking
            return new Reply(404, null, null)
        } as Transport
        CreateAppCommand command = new CreateAppCommand(beanContext.getBean(WebAvailableFeatures), beanContext.getBean(ContextFactory),
                beanContext.getBean(ProjectGenerator)) {

            @Override
            protected String featureCatalogSetting() {
                return 'none'
            }

            @Override
            protected Path featureCatalogHome() {
                return home
            }

            @Override
            protected PackArtifacts createPackArtifacts() {
                return new PackArtifacts(transport)
            }

            @Override
            protected String grailsRepoUrl() {
                return null
            }
        }
        out.reset()
        err.reset()
        return new CommandLine(command, new GrailsPicocliFactory(beanContext)).execute(args)
    }
}
