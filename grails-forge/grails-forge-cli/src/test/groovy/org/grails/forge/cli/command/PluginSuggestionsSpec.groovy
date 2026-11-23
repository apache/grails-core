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
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.apache.grails.forge.cli.pack.PackArtifacts
import org.apache.grails.forge.cli.pack.PackArtifacts.Reply
import org.apache.grails.forge.cli.pack.PackArtifacts.Transport
import org.apache.grails.forge.cli.plugin.ListPluginsCommand
import org.apache.grails.forge.cli.plugin.PluginInfoCommand
import org.grails.forge.cli.CommandFixture
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeContext
import org.grails.forge.cli.GrailsPicocliFactory
import org.grails.forge.util.VersionInfo
import picocli.CommandLine
import spock.lang.Unroll

/**
 * Every command line {@code plugin-info} suggests for the feature pack of a plugin creates, typed as it
 * is printed in the same environment, an application with that plugin in the version it names: from
 * the same feature catalog, from the repository that has the plugin, and with the create command of a
 * type the pack is for. Each suggestion is run here as the command line would run it.
 *
 * <p>The plugin index is a file, or an https address this specification answers. The repository
 * {@code GRAILS_REPO_URL} names is a directory, and the repositories a plugin index names are https
 * addresses that this specification answers from directories: nothing is asked of the network.</p>
 */
class PluginSuggestionsSpec extends CommandSpec implements CommandFixture {

    static final String PLUGIN = 'com.acme:acme-audit-grails'
    static final String OTHER = 'com.other:other-audit-grails'
    static final String RANGE = '[9.0.0,10.0.0)'
    static final String OF_THE_INDEX = 'https://repo.example.org/maven/'
    static final String OF_THE_VERSION = 'https://releases.example.org/grails/'
    static final String INDEX_SITE = 'https://plugins.example.org/'
    /** An address a shell would act on, were it not quoted */
    static final String OF_A_SHELL = 'https://repo.example.org/maven;$(id)/'
    /** A feature of Grails, which a feature pack cannot be called */
    static final String OF_GRAILS = 'micronaut-http-client'

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    ByteArrayOutputStream err = new ByteArrayOutputStream()
    File work
    File local
    Map<String, File> served = [:]
    List<URI> asked = []

    Transport transport = { URI address ->
        asked << address
        String text = address.toString()
        String root = served.keySet().find { String each -> text.startsWith(each) }
        File file = root == null ? null : new File(served.get(root), text.substring(root.length()))
        return file != null && file.isFile() ? new Reply(200, null, new ByteArrayInputStream(file.bytes)) : new Reply(404, null, null)
    } as Transport

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out))
        System.setErr(new PrintStream(err))
        work = Files.createTempDirectory('grailsforgesuggestions').toFile()
        local = new File(work, 'local')
        served.put(OF_THE_INDEX, new File(work, 'of-the-index'))
        served.put(OF_THE_VERSION, new File(work, 'of-the-version'))
        served.put(OF_A_SHELL, new File(work, 'of-a-shell'))
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
        work.deleteDir()
    }

    @Unroll
    void "for #what, with #environment, every command line plugin-info suggests creates an application with the plugin in the version it names"() {
        given:
        File index = fixture(what)
        String address = index.toPath().toUri().toString()
        Map<String, String> settings = [GRAILS_REPO_URL: local.absolutePath]
        List<String> options = []
        if (environment.contains('--plugin-index')) {
            options << '--plugin-index' << address
        } else {
            settings.GRAILS_PLUGIN_INDEX = address
        }
        if (environment.contains('GRAILS_FORGE_CATALOG=none')) {
            settings.GRAILS_FORGE_CATALOG = 'none'
        } else if (environment.contains('GRAILS_FORGE_CATALOG=another')) {
            settings.GRAILS_FORGE_CATALOG = another().toPath().toUri().toString()
        }

        when:
        int exit = info(settings, options + [PLUGIN])
        String said = out.toString()
        List<String> suggested = suggested(said)

        and: 'each is typed as it is printed'
        List<String> created = suggested.collect { String line -> typed(settings, line, version) }

        then: 'each creates an application with the plugin in that version'
        exit == 0
        created == suggested.collect { '0 with the plugin' }

        and: 'they are those of the pack'
        said.contains(pack)
        suggested == commands.collect { String line -> line.replace('INDEX', address) }

        when: 'the plugins are listed'
        exit = list(settings, options + ['--json', 'acme'])

        then: 'a feature of a pack is listed for the plugin where plugin-info suggests how an application is created with it'
        exit == 0
        (new JsonSlurper().parseText(out.toString()) as Map).plugins.find { it.coordinates == PLUGIN }.featurePacks == (commands ? ['acme-audit'] : [])

        where:
        what                                      | environment                                       || version | pack                                                                       | commands
        'a plugin of a feature no other has'      | '--plugin-index'                                  || '2.3.0' | '* Feature pack: acme-audit, of version 2.3.0. '                          | ["create-app NAME --features ${PLUGIN}:2.3.0", 'create-app NAME --features acme-audit --feature-catalog INDEX']
        'a plugin of a feature no other has'      | 'GRAILS_PLUGIN_INDEX'                             || '2.3.0' | '* Feature pack: acme-audit, of version 2.3.0. '                          | ["create-app NAME --features ${PLUGIN}:2.3.0", 'create-app NAME --features acme-audit']
        'a plugin of a feature no other has'      | 'GRAILS_PLUGIN_INDEX and GRAILS_FORGE_CATALOG=none' || '2.3.0' | '* Feature pack: acme-audit, of version 2.3.0. '                        | ["create-app NAME --features ${PLUGIN}:2.3.0", 'create-app NAME --features acme-audit --feature-catalog INDEX']
        'a plugin of a feature no other has'      | 'GRAILS_PLUGIN_INDEX and GRAILS_FORGE_CATALOG=another' || '2.3.0' | '* Feature pack: acme-audit, of version 2.3.0. '                     | ["create-app NAME --features ${PLUGIN}:2.3.0", 'create-app NAME --features acme-audit --feature-catalog INDEX']
        'two plugins of one feature'              | 'GRAILS_PLUGIN_INDEX'                             || '2.3.0' | '* Feature pack: acme-audit, of version 2.3.0. '                          | ["create-app NAME --features ${PLUGIN}:2.3.0", "create-app NAME --features '${PLUGIN}#acme-audit'"]
        'two plugins of one feature'              | '--plugin-index'                                  || '2.3.0' | '* Feature pack: acme-audit, of version 2.3.0. '                          | ["create-app NAME --features ${PLUGIN}:2.3.0", "create-app NAME --features '${PLUGIN}#acme-audit' --feature-catalog INDEX"]
        'a pack for REST APIs only'               | 'GRAILS_PLUGIN_INDEX'                             || '2.3.0' | '* Feature pack: acme-audit, of version 2.3.0, for applications of type rest-api. ' | ["create-restapi NAME --features ${PLUGIN}:2.3.0", 'create-restapi NAME --features acme-audit']
        'a later version for REST APIs only'      | 'GRAILS_PLUGIN_INDEX'                             || '2.3.0' | '* Feature pack: acme-audit, of version 2.3.0. '                          | ["create-app NAME --features ${PLUGIN}:2.3.0", 'create-app NAME --features acme-audit']
        'a plugin of the repository of the index' | 'GRAILS_PLUGIN_INDEX'                             || '2.3.0' | '* Feature pack: acme-audit, of version 2.3.0. '                          | ["create-app NAME --features ${PLUGIN}:2.3.0 --feature-pack-repository ${OF_THE_INDEX}", 'create-app NAME --features acme-audit']
        'a version of a repository of its own'    | 'GRAILS_PLUGIN_INDEX'                             || '2.3.0' | '* Feature pack: acme-audit, of version 2.3.0. '                          | ["create-app NAME --features ${PLUGIN}:2.3.0 --feature-pack-repository ${OF_THE_VERSION}", 'create-app NAME --features acme-audit']
        'a repository a shell would act on'       | 'GRAILS_PLUGIN_INDEX'                             || '2.3.0' | '* Feature pack: acme-audit, of version 2.3.0. '                          | ["create-app NAME --features ${PLUGIN}:2.3.0 --feature-pack-repository '${OF_A_SHELL}'", 'create-app NAME --features acme-audit']
        'a pack with the name of a feature of Grails' | 'GRAILS_PLUGIN_INDEX'                         || '2.3.0' | "* Feature pack: ${OF_GRAILS}, which an application of Grails ${VersionInfo.grailsVersion} is not created with: " | []
    }

    void "an index of the network is read by the command line plugin-info suggests as plugin-info read it, and not from the copy of a day before"() {
        given: 'the index has the plugin in 2.2.0, and a create command keeps a copy of it'
        File site = new File(work, 'plugins')
        site.mkdirs()
        served.put(INDEX_SITE, site)
        String address = "${INDEX_SITE}index.json"
        Map<String, Object> older = pack(publish(local, "${PLUGIN}:2.2.0"))
        new File(site, 'index.json').text = JsonOutput.toJson([plugin(PLUGIN, ['2.2.0': older])])
        Map<String, String> settings = [GRAILS_REPO_URL: local.absolutePath, GRAILS_PLUGIN_INDEX: address]
        assert typed(settings, 'create-app NAME --features acme-audit', '2.2.0') == '0 with the plugin'

        and: 'then 2.3.0 is published, which a create command does not see in its copy'
        new File(site, 'index.json').text = JsonOutput.toJson([plugin(PLUGIN, ['2.2.0': older, '2.3.0': pack(publish(local, "${PLUGIN}:2.3.0"))])])
        assert typed(settings, 'create-app NAME --features acme-audit', '2.2.0') == '0 with the plugin'

        when:
        int exit = info(settings, [PLUGIN])
        List<String> suggested = suggested(out.toString())

        then: 'the command line that selects the feature by its name reads the index anew'
        exit == 0
        out.toString().contains('* Feature pack: acme-audit, of version 2.3.0. ')
        suggested == ["create-app NAME --features ${PLUGIN}:2.3.0", 'create-app NAME --features acme-audit --refresh']
        suggested.collect { String line -> typed(settings, line, '2.3.0') } == ['0 with the plugin', '0 with the plugin']
    }

    void "a pack with the name of a feature of Grails creates no application, by its coordinates, its full name or its name"() {
        given:
        Map<String, String> settings = [GRAILS_REPO_URL: local.absolutePath, GRAILS_PLUGIN_INDEX: fixture('a pack with the name of a feature of Grails').toPath().toUri().toString()]

        expect: 'the pack is refused, and the name selects the feature of Grails'
        ["create-app NAME --features ${PLUGIN}:2.3.0", "create-app NAME --features '${PLUGIN}#${OF_GRAILS}'", "create-app NAME --features ${OF_GRAILS}"].every { String line ->
            !typed(settings, line, '2.3.0').startsWith('0 with the plugin')
        }
    }

    private File fixture(String what) {
        switch (what) {
            case 'a plugin of a feature no other has':
                return index([plugin(PLUGIN, ['2.3.0': pack(publish(local, "${PLUGIN}:2.3.0"))])])
            case 'two plugins of one feature':
                return index([plugin(PLUGIN, ['2.3.0': pack(publish(local, "${PLUGIN}:2.3.0"))]),
                              plugin(OTHER, ['1.0.0': pack(publish(local, "${OTHER}:1.0.0"))])])
            case 'a pack for REST APIs only':
                return index([plugin(PLUGIN, ['2.3.0': pack(publish(local, "${PLUGIN}:2.3.0", 'acme-audit', ['rest-api']), [applicationTypes: ['rest-api']])])])
            case 'a later version for REST APIs only':
                return index([plugin(PLUGIN, ['2.3.0': pack(publish(local, "${PLUGIN}:2.3.0")),
                                              '2.4.0': pack(publish(local, "${PLUGIN}:2.4.0", 'acme-audit', ['rest-api']), [applicationTypes: ['rest-api']])])])
            case 'a plugin of the repository of the index':
                return index([plugin(PLUGIN, ['2.3.0': pack(publish(served.get(OF_THE_INDEX), "${PLUGIN}:2.3.0"))]) + ['maven-repo': OF_THE_INDEX.substring(0, OF_THE_INDEX.length() - 1)]])
            case 'a version of a repository of its own':
                Map<String, Object> listed = plugin(PLUGIN, ['2.3.0': pack(publish(served.get(OF_THE_VERSION), "${PLUGIN}:2.3.0"))]) + ['maven-repo': OF_THE_INDEX]
                ((List<Map>) listed.versions)[0].put('maven-repo', OF_THE_VERSION)
                return index([listed])
            case 'a repository a shell would act on':
                return index([plugin(PLUGIN, ['2.3.0': pack(publish(served.get(OF_A_SHELL), "${PLUGIN}:2.3.0"))]) + ['maven-repo': OF_A_SHELL]])
            case 'a pack with the name of a feature of Grails':
                return index([plugin(PLUGIN, ['2.3.0': pack(publish(local, "${PLUGIN}:2.3.0", OF_GRAILS), [name: OF_GRAILS])])])
            default:
                throw new IllegalArgumentException(what)
        }
    }

    // a catalog that lists the plugin too, with another version, for the variable of the catalogs to name
    private File another() {
        return index([plugin(PLUGIN, ['2.2.0': pack(publish(local, "${PLUGIN}:2.2.0"))])], 'another.json')
    }

    // the command lines a plugin-info of this specification suggests, without the name of the program
    private static List<String> suggested(String said) {
        return said.readLines().findAll { String line -> line.startsWith('    plugin-info create-') }.collect { String line -> line.substring('    plugin-info '.length()) }
    }

    /**
     * Runs a command line as a shell reads it, with the name of the application for {@code NAME}.
     *
     * @return the exit code, whether the application has the plugin in the version, and what the
     * command said first on its error stream when it failed
     */
    private String typed(Map<String, String> settings, String line, String version) {
        List<String> words = words(line)
        int code = create(settings, words[0], words.drop(1).collect { String word -> word == 'NAME' ? 'com.example.shop' : word })
        File lock = new File(dir, 'shop/.grails/forge.lock.yml')
        String result = "${code} ${lock.isFile() && lock.text.contains("coordinates: ${PLUGIN}\n  version: ${version}\n") ? 'with the plugin' : 'without the plugin'}" +
                (code == 0 ? '' : ": ${err.toString().readLines().find { String each -> each.trim() }}")
        new File(dir, 'shop').deleteDir()
        return result
    }

    private int list(Map<String, String> settings, List<String> args) {
        ListPluginsCommand command = new ListPluginsCommand(ForgeContext.of(beanContext)) {

            @Override
            protected PackArtifacts createPackArtifacts() {
                return new PackArtifacts(transport)
            }

            @Override
            protected String pluginIndexSetting() {
                return settings.GRAILS_PLUGIN_INDEX
            }
        }
        return run(command, args)
    }

    private int info(Map<String, String> settings, List<String> args) {
        PluginInfoCommand command = new PluginInfoCommand(ForgeContext.of(beanContext)) {

            @Override
            protected PackArtifacts createPackArtifacts() {
                return new PackArtifacts(transport)
            }

            @Override
            protected String pluginIndexSetting() {
                return settings.GRAILS_PLUGIN_INDEX
            }

            @Override
            protected String featureCatalogSetting() {
                return settings.GRAILS_FORGE_CATALOG
            }

            @Override
            protected String grailsRepoUrl() {
                return settings.GRAILS_REPO_URL
            }
        }
        return run(command, args)
    }

    private int run(Object command, List<String> args) {
        out.reset()
        err.reset()
        CommandLine commandLine = new CommandLine(command)
        commandLine.setOut(new PrintWriter(new OutputStreamWriter(out, 'UTF-8'), true))
        commandLine.setErr(new PrintWriter(new OutputStreamWriter(err, 'UTF-8'), true))
        return commandLine.execute(args as String[])
    }

    private int create(Map<String, String> settings, String name, List<String> args) {
        ForgeContext context = ForgeContext.of(beanContext)
        Path home = work.toPath()
        CreateCommand command
        switch (name) {
            case CreateAppCommand.NAME:
                command = new CreateAppCommand(context) {

                    @Override
                    protected String featureCatalogSetting() {
                        return settings.GRAILS_FORGE_CATALOG
                    }

                    @Override
                    protected String pluginIndexSetting() {
                        return settings.GRAILS_PLUGIN_INDEX
                    }

                    @Override
                    protected String grailsRepoUrl() {
                        return settings.GRAILS_REPO_URL
                    }

                    @Override
                    protected PackArtifacts createPackArtifacts() {
                        return new PackArtifacts(transport)
                    }

                    @Override
                    protected Path featureCatalogHome() {
                        return home
                    }
                }
                break
            case CreateRestApiCommand.NAME:
                command = new CreateRestApiCommand(context) {

                    @Override
                    protected String featureCatalogSetting() {
                        return settings.GRAILS_FORGE_CATALOG
                    }

                    @Override
                    protected String pluginIndexSetting() {
                        return settings.GRAILS_PLUGIN_INDEX
                    }

                    @Override
                    protected String grailsRepoUrl() {
                        return settings.GRAILS_REPO_URL
                    }

                    @Override
                    protected PackArtifacts createPackArtifacts() {
                        return new PackArtifacts(transport)
                    }

                    @Override
                    protected Path featureCatalogHome() {
                        return home
                    }
                }
                break
            default:
                throw new IllegalArgumentException("plugin-info suggested ${name}, which this specification does not run")
        }
        out.reset()
        err.reset()
        return new CommandLine(command, new GrailsPicocliFactory(beanContext)).execute(args as String[])
    }

    /**
     * @return the words a POSIX shell reads a line as, where the line quotes with single quotes only
     */
    private static List<String> words(String line) {
        List<String> words = []
        StringBuilder word = null
        boolean quoted = false
        for (char each : line.toCharArray()) {
            if (quoted) {
                if (each == '\'' as char) {
                    quoted = false
                } else {
                    word.append(each)
                }
            } else if (each == ' ' as char) {
                if (word != null) {
                    words << word.toString()
                    word = null
                }
            } else {
                word = word ?: new StringBuilder()
                if (each == '\'' as char) {
                    quoted = true
                } else {
                    word.append(each)
                }
            }
        }
        if (word != null) {
            words << word.toString()
        }
        return words
    }

    private static Map<String, Object> plugin(String coordinates, Map<String, Map> packs) {
        return [coords  : coordinates, name: 'Acme Audit', desc: 'Audits everything', licenses: ['Apache-2.0'],
                versions: packs.collect { String version, Map pack -> [version: version, grailsVersion: '9.0.0 > *', pack: pack] as Map<String, Object> }] as Map<String, Object>
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

    private static File publish(File repository, String coordinates, String name = 'acme-audit', List<String> applicationTypes = null) {
        List<String> parts = coordinates.split(':').toList()
        File directory = new File(repository, "${parts[0].replace('.', '/')}/${parts[1]}/${parts[2]}")
        directory.mkdirs()
        File jar = new File(directory, "${parts[1]}-${parts[2]}.jar")
        String descriptor = "format: 1\nkind: feature\ntemplateApi: 1\nname: ${name}\n" +
                "compatibility:\n  grails: '${RANGE}'\n  forge: '${RANGE}'\n" +
                (applicationTypes == null ? '' : "applicationTypes: [${applicationTypes.join(', ')}]\n") +
                "title: Acme Audit Trail\ndescription: Adds the Acme audit plugin\n" +
                "dependencies:\n  - scope: implementation\n    coordinates: ${parts[0]}:${parts[1]}\n    version: ${parts[2]}\n"
        new ZipOutputStream(new FileOutputStream(jar)).withCloseable { ZipOutputStream zip ->
            ['META-INF/coordinates.txt': coordinates, 'META-INF/grails-forge/feature.yml': descriptor].each { String path, String text ->
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
