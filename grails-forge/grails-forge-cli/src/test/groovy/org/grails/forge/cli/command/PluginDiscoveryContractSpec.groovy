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
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.apache.grails.forge.cli.pack.PackArtifacts
import org.apache.grails.forge.cli.pack.PackArtifacts.Reply
import org.apache.grails.forge.cli.pack.PackArtifacts.Transport
import org.apache.grails.forge.cli.plugin.ListPluginsCommand
import org.apache.grails.forge.cli.plugin.PluginInfoCommand
import org.grails.forge.application.ContextFactory
import org.grails.forge.application.WebAvailableFeatures
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.cli.CommandFixture
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeContext
import org.grails.forge.cli.ForkedCli
import org.grails.forge.cli.GrailsPicocliFactory
import org.grails.forge.util.VersionInfo
import picocli.CommandLine
import spock.lang.IgnoreIf
import spock.lang.Requires
import spock.lang.Unroll

/**
 * Every command that finds the feature of a plugin in the plugin index reads the packs of the index
 * in one way: {@code create-app} that selects a feature by its name, {@code plugin-info} that says
 * which feature a plugin has, and {@code list-plugins} that lists the plugins that have one. The
 * format of a pack is read first, and a pack of a format this Forge does not read is passed over
 * without anything else of it being read; what is wrong with one version, or what this Forge does
 * not know of it, leaves out that version and not the older releases of the plugin.
 *
 * <p>The index is a file, and the repository a directory: nothing is asked of the network. The
 * environment variable {@code GRAILS_PLUGIN_INDEX} names the index for all of the commands, which
 * the last features show with the command line started as a program of its own.</p>
 */
class PluginDiscoveryContractSpec extends CommandSpec implements CommandFixture {

    static final String PLUGIN = 'com.acme:acme-audit-grails'
    static final String RANGE = '[9.0.0,10.0.0)'

    /** A pack as a later Forge could record it: nothing of it is what this Forge reads */
    static final Map<String, Object> OF_A_LATER_FORMAT = [format: 2, kind: 42, name: ['acme-audit'], features: [[name: 'acme-audit', templateApi: 'two']],
                                                         compatibility: '9+', sha256: 'not a digest', applicationTypes: 'all']

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    ByteArrayOutputStream err = new ByteArrayOutputStream()
    File repository
    File work
    String address
    List<URI> asked = []
    String indexVariable

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out))
        System.setErr(new PrintStream(err))
        repository = Files.createTempDirectory('grailsforgerepository').toFile()
        work = Files.createTempDirectory('grailsforgediscovery').toFile()
        address = repository.toPath().toAbsolutePath().normalize().toUri().toString()
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
        repository.deleteDir()
        work.deleteDir()
    }

    void "an older release this Forge reads is chosen beside a later release whose pack has another format, by every command"() {
        given: 'the latest release has a pack of a format to come, and an older one a pack of this format'
        File jar = publish("${PLUGIN}:2.3.0")
        File index = index([plugin(PLUGIN, ['2.3.0': pack(jar), '3.0.0': OF_A_LATER_FORMAT])])

        when: 'the feature is selected by its name'
        int exit = create('com.example.shop', '--features', 'acme-audit', '--feature-catalog', index.toURI().toString(), '--feature-pack-repository', address)

        then: 'the older release is taken, and the later one is not read'
        exit == 0
        out.toString().contains("The feature acme-audit is taken as ${PLUGIN}:2.3.0, which the feature catalog ${index.toPath().toUri()}")
        new File(dir, 'shop/.grails/forge.lock.yml').text.contains("coordinates: ${PLUGIN}\n  version: 2.3.0\n")

        when: 'the features of the catalog are listed'
        exit = create('--list-catalog-features', '--feature-catalog', index.toURI().toString())

        then: 'the version of the later format is counted, and nothing of it is a problem'
        exit == 0
        out.toString().readLines().any { it.startsWith('  acme-audit ') && it.contains(" ${PLUGIN}:2.3.0") }
        out.toString().contains('  1 version has a feature pack of a format this Forge does not read, which a later Forge can read; what it has is not looked at')
        !out.toString().contains('left out')

        when: 'plugin-info is asked about the plugin, with the same file as the plugin index'
        exit = info('--plugin-index', index.toURI().toString(), PLUGIN)

        then: 'it names the feature and the version an application is created with'
        exit == 0
        out.toString().contains("* Feature pack: acme-audit, of version 2.3.0. An application is created with it by:")
        out.toString().contains("    plugin-info create-app NAME --features ${PLUGIN}:2.3.0")
        out.toString().contains("    plugin-info create-app NAME --features acme-audit --feature-catalog ${index.toPath().toUri()}")

        when: 'the same as a document'
        exit = info('--plugin-index', index.toURI().toString(), '--json', PLUGIN)
        Map document = new JsonSlurper().parseText(out.toString()) as Map

        then:
        exit == 0
        document.plugin.featurePacks == [[name: 'acme-audit', offered: true, version: '2.3.0', coordinates: "${PLUGIN}:2.3.0".toString(), applicationTypes: null,
                                          applicationType: 'web']]

        when: 'the plugins are listed'
        exit = list('--plugin-index', index.toURI().toString(), '--json', 'acme')
        document = new JsonSlurper().parseText(out.toString()) as Map

        then:
        exit == 0
        document.plugins.find { it.coordinates == PLUGIN }.featurePacks == ['acme-audit']
    }

    void "a plugin whose only pack has a format to come is listed, and its feature is not known"() {
        given:
        File index = index([plugin(PLUGIN, ['3.0.0': OF_A_LATER_FORMAT])])

        when:
        int exit = create('shop', '--features', 'acme-audit', '--feature-catalog', index.toURI().toString(), '--feature-pack-repository', address)

        then: 'the name is not known, and why it may be is said'
        exit == 2
        err.toString().readLines()[0].contains('The requested feature does not exist: acme-audit. acme-audit was not found in the feature catalog ' +
                "${index.toPath().toUri()} (1 version has a feature pack of a format this Forge does not read")
        !new File(dir, 'shop').exists()

        when:
        exit = info('--plugin-index', index.toURI().toString(), PLUGIN)

        then:
        exit == 0
        !out.toString().contains('Feature pack')
    }

    @Unroll
    void "a pack with #what is no fault of the plugin: #outcome"() {
        given:
        File jar = publish("${PLUGIN}:2.3.0")
        File later = publish("${PLUGIN}:2.4.0")
        File index = index([plugin(PLUGIN, ['2.3.0': pack(jar), '2.4.0': pack(later, overrides)])])

        when:
        int exit = create('com.example.shop', '--features', 'acme-audit', '--feature-catalog', index.toURI().toString(), '--feature-pack-repository', address)

        then:
        exit == 0
        out.toString().contains("The feature acme-audit is taken as ${PLUGIN}:${taken}, which the feature catalog")

        when:
        exit = info('--plugin-index', index.toURI().toString(), PLUGIN)

        then: 'plugin-info names the same version'
        exit == 0
        out.toString().contains("* Feature pack: acme-audit, of version ${pluginInfo}")

        where:
        what                                          | overrides                              || taken   | pluginInfo                                               | outcome
        'a kind this Forge does not know'             | [kind: 'theme']                        || '2.3.0' | '2.3.0.'                                                 | 'the older release is taken'
        'a type of application it does not know'      | [applicationTypes: ['web', 'desktop']] || '2.4.0' | '2.4.0, for applications of type web, desktop.'          | 'the later release is taken for a web application'
        'only types of application it does not know'  | [applicationTypes: ['desktop']]        || '2.3.0' | '2.3.0.'                                                 | 'the older release is taken for a web application'
        'a template API to come'                      | [templateApi: 2]                       || '2.3.0' | '2.3.0.'                                                 | 'the older release is taken'
    }

    @Unroll
    void "a later version with #what is left out, and the older release is still taken"() {
        given:
        File jar = publish("${PLUGIN}:2.3.0")
        File index = index([plugin(PLUGIN, ['2.3.0': pack(jar), '2.4.0': pack(jar) + change])])

        when:
        int exit = create('com.example.shop', '--features', 'acme-audit', '--feature-catalog', index.toURI().toString(), '--feature-pack-repository', address)

        then:
        exit == 0
        out.toString().contains("The feature acme-audit is taken as ${PLUGIN}:2.3.0, which the feature catalog")

        when:
        exit = create('--list-catalog-features', '--feature-catalog', index.toURI().toString())

        then:
        exit == 0
        out.toString().contains('  1 version is left out for what the catalog says of it:')
        out.toString().contains("    plugin 1: ${PLUGIN}: the pack of version 2.4.0: ${problem}")

        where:
        what                    | change                         || problem
        'a digest that is none' | [sha256: 'z' * 64]             || 'sha256 is not a SHA-256, which is 64 hexadecimal digits'
        'a kind that is a list' | [kind: ['feature']]            || 'kind is not a text'
        'no compatibility'      | [compatibility: 'any']         || 'compatibility is not an object'
        'a format that is none' | [format: 'one']                || 'format is not a whole number from 1'
    }

    void "a plugin whose pack is for another Grails is described with the reason no application is created with it"() {
        given:
        File jar = publish("${PLUGIN}:2.3.0")
        File index = index([plugin(PLUGIN, ['2.3.0': pack(jar, [compatibility: [grails: '[10.0.0,11.0.0)', forge: '[10.0.0,11.0.0)']])])])

        when:
        int exit = info('--plugin-index', index.toURI().toString(), PLUGIN)

        then:
        exit == 0
        out.toString().contains("* Feature pack: acme-audit, which an application of Grails ${VersionInfo.grailsVersion} is not created with: ${PLUGIN} has it in no " +
                "version for Grails ${VersionInfo.grailsVersion}")

        when:
        exit = list('--plugin-index', index.toURI().toString(), '--json', 'acme')

        then:
        exit == 0
        (new JsonSlurper().parseText(out.toString()) as Map).plugins.find { it.coordinates == PLUGIN }.featurePacks == []
    }

    void "the plugin index the environment names is the catalog of the create commands, after the option and the variable of the catalogs"() {
        given:
        File jar = publish("${PLUGIN}:2.3.0")
        File named = index([plugin(PLUGIN, ['2.3.0': pack(jar)])])
        File other = index([plugin('com.other:other-grails', ['1.0.0': pack(jar, [name: 'other-audit'])])], 'other.json')

        when: 'only GRAILS_PLUGIN_INDEX names one'
        indexVariable = named.toURI().toString()
        int exit = create('com.example.shop', '--features', 'acme-audit', '--feature-pack-repository', address)

        then:
        exit == 0
        out.toString().contains("The feature acme-audit is taken as ${PLUGIN}:2.3.0, which the feature catalog ${named.toPath().toUri()}")

        when: 'and is listed'
        exit = create('--list-catalog-features')

        then:
        exit == 0
        out.toString().contains("Feature catalog ${named.toPath().toUri()}, sha256 ")

        when: 'the option names another'
        exit = create('--list-catalog-features', '--feature-catalog', other.toURI().toString())

        then:
        exit == 0
        out.toString().contains("Feature catalog ${other.toPath().toUri()}, sha256 ")
        !out.toString().contains("Feature catalog ${named.toPath().toUri()}")

        when: 'none'
        indexVariable = 'none'
        exit = create('--list-catalog-features')

        then:
        exit == 0
        out.toString().contains('No feature catalog is read: --feature-catalog, GRAILS_FORGE_CATALOG or GRAILS_PLUGIN_INDEX names one')

        when: 'an address that is none'
        indexVariable = 'http://plugins.example.org/index.json'
        exit = create('--list-catalog-features')

        then:
        exit == 2
        err.toString().contains('http://plugins.example.org/index.json')
    }

    @IgnoreIf({ !ForkedCli.available() })
    void "GRAILS_PLUGIN_INDEX names the plugin index for plugin-info and the catalog for create-app, in the environment of the program"() {
        given:
        File jar = publish("${PLUGIN}:2.3.0")
        File index = index([plugin(PLUGIN, ['2.3.0': pack(jar), '3.0.0': OF_A_LATER_FORMAT])])
        File home = new File(work, 'home')
        home.mkdirs()

        when:
        Map<String, String> said = [
                info  : ['plugin-info', '--offline', PLUGIN],
                create: ['create-app', 'com.example.shop', '--features', 'acme-audit', '--feature-pack-repository', address],
        ].collectEntries { String name, List<String> line ->
            ProcessBuilder builder = ForkedCli.builder(dir, ["-Duser.home=${home.path}".toString()], line as String[]).redirectErrorStream(true)
            builder.environment().remove('GRAILS_FORGE_CATALOG')
            builder.environment().remove('GRAILS_REPO_URL')
            builder.environment().put('GRAILS_PLUGIN_INDEX', index.toURI().toString())
            Process program = builder.start()
            String text = program.inputStream.getText('UTF-8')
            assert program.waitFor(300, TimeUnit.SECONDS)
            [name, "${program.exitValue()} ${text}".toString()]
        }

        then: 'plugin-info names the feature, and how it is selected by its name with the environment as it is'
        said.info.startsWith('0 ')
        said.info.contains('* Feature pack: acme-audit, of version 2.3.0. An application is created with it by:')
        said.info.contains('create-app NAME --features acme-audit\n')

        and: 'create-app selects it by that name'
        said.create.startsWith('0 ')
        said.create.contains("The feature acme-audit is taken as ${PLUGIN}:2.3.0")
        new File(dir, 'shop/.grails/forge.lock.yml').text.contains("coordinates: ${PLUGIN}\n  version: 2.3.0\n")
    }

    /**
     * The index the plugins metadata repository writes, of the file {@code -PpluginIndex} names: every
     * command reads the packs it records, and none of them is a problem.
     * {@code ./gradlew :grails-forge-cli:test --tests '*PluginDiscoveryContractSpec' -PpluginIndex=/path/to/grails-plugins-index.json}
     */
    @Requires({ System.getProperty('forge.pluginIndex') })
    void "the plugin index of the file -PpluginIndex names is read by every command, with each pack it records"() {
        given:
        File index = new File(System.getProperty('forge.pluginIndex'))
        List<Map> plugins = new JsonSlurper().parse(index) as List<Map>
        List<String> withPacks = plugins.findAll { Map plugin -> plugin.versions.any { Map version -> version.pack } }*.coords

        when:
        indexVariable = index.toURI().toString()
        int exit = create('--list-catalog-features')

        then:
        exit == 0
        !out.toString().contains('left out')

        when:
        List<String> described = withPacks.collect { String coordinates ->
            assert info('--plugin-index', index.toURI().toString(), '--json', coordinates) == 0
            List packs = (new JsonSlurper().parseText(out.toString()) as Map).plugin.featurePacks as List
            "${coordinates}: ${packs.collect { Map pack -> pack.offered ? "${pack.name} ${pack.version}" : "${pack.name} (${pack.reason})" }.join(', ')}".toString()
        }

        then:
        described.size() == withPacks.size()
        originalOut.println "the plugin index ${index}: ${plugins.size()} plugins, ${withPacks.size()} with a pack"
        described.each { originalOut.println "  ${it}" }
    }

    private int create(String... args) {
        String index = indexVariable
        Path home = work.toPath()
        Transport transport = { URI asking ->
            asked << asking
            return new Reply(404, null, null)
        } as Transport
        CreateAppCommand command = new CreateAppCommand(beanContext.getBean(WebAvailableFeatures), beanContext.getBean(ContextFactory),
                beanContext.getBean(ProjectGenerator)) {

            @Override
            protected String featureCatalogSetting() {
                return null
            }

            @Override
            protected String pluginIndexSetting() {
                return index
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

    private int info(String... args) {
        return plugins(new PluginInfoCommand(ForgeContext.of(beanContext)) {

            @Override
            protected PackArtifacts createPackArtifacts() {
                return new PackArtifacts({ URI asking -> asked << asking; new Reply(404, null, null) } as Transport)
            }

            @Override
            protected String pluginIndexSetting() {
                return null
            }

            @Override
            protected String grailsRepoUrl() {
                return null
            }
        }, args)
    }

    private int list(String... args) {
        return plugins(new ListPluginsCommand(ForgeContext.of(beanContext)) {

            @Override
            protected PackArtifacts createPackArtifacts() {
                return new PackArtifacts({ URI asking -> asked << asking; new Reply(404, null, null) } as Transport)
            }

            @Override
            protected String pluginIndexSetting() {
                return null
            }
        }, args)
    }

    private int plugins(Object command, String... args) {
        out.reset()
        err.reset()
        CommandLine commandLine = new CommandLine(command)
        commandLine.setOut(new PrintWriter(new OutputStreamWriter(out, 'UTF-8'), true))
        commandLine.setErr(new PrintWriter(new OutputStreamWriter(err, 'UTF-8'), true))
        return commandLine.execute(args)
    }

    private static Map<String, Object> plugin(String coordinates, Map<String, Map> packs) {
        return [coords  : coordinates, name: 'Acme Audit', desc: 'Audits everything', licenses: ['Apache-2.0'],
                versions: packs.collect { String version, Map pack -> [version: version, grailsVersion: '9.0.0 > *', pack: pack] }] as Map<String, Object>
    }

    private static Map<String, Object> pack(File jar, Map overrides = [:]) {
        return ([format: 1, kind: 'feature', name: 'acme-audit', templateApi: 1, compatibility: [grails: RANGE, forge: RANGE], sha256: sha256(jar)] +
                overrides) as Map<String, Object>
    }

    private File index(List plugins, String name = 'index.json') {
        File index = new File(work, name)
        index.setText(JsonOutput.toJson(plugins), 'UTF-8')
        return index
    }

    private File publish(String coordinates) {
        List<String> parts = coordinates.split(':').toList()
        File directory = new File(repository, "${parts[0].replace('.', '/')}/${parts[1]}/${parts[2]}")
        directory.mkdirs()
        File jar = new File(directory, "${parts[1]}-${parts[2]}.jar")
        new ZipOutputStream(new FileOutputStream(jar)).withCloseable { ZipOutputStream zip ->
            ['META-INF/coordinates.txt'                 : coordinates,
             'META-INF/grails-forge/feature.yml'        : CreateAppFeaturePackSpec.DESCRIPTOR,
             'META-INF/grails-forge/templates/audit.txt': 'kept as it is'].each { String path, String text ->
                zip.putNextEntry(new ZipEntry(path))
                zip.write(text.getBytes('UTF-8'))
                zip.closeEntry()
            }
        }
        return jar
    }

    private static String sha256(File file) {
        return MessageDigest.getInstance('SHA-256').digest(file.bytes).encodeHex().toString()
    }
}
