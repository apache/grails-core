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
package org.apache.grails.forge.cli.plugin

import java.nio.file.Files
import java.util.function.Supplier
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.apache.grails.forge.cli.pack.PackArtifacts
import org.apache.grails.forge.cli.pack.PackArtifacts.Reply
import org.apache.grails.forge.cli.pack.PackArtifacts.Transport
import org.apache.grails.forge.core.bom.BomVersions
import org.grails.forge.cli.ForgeContext
import org.springframework.context.ApplicationContext
import picocli.CommandLine
import spock.lang.Specification
import spock.lang.Unroll

/**
 * {@code list-plugins} and {@code plugin-info}, each run as the command line runs it, with a plugin
 * index and Maven repositories that are answers of this specification: nothing is asked of a network,
 * and every address a command asks is recorded.
 */
class PluginCommandsSpec extends Specification {

    static final String INDEX = 'https://plugins.example.org/grails-plugins-index.json'
    static final String REPOSITORY = 'https://repo.example.org/maven/'
    static final String OF_THE_INDEX = 'https://repo.other.example.org/plugins/'
    static final String GRAILS = '9.0.0'
    static final String CACHE = 'org.apache.grails:grails-cache'
    static final String ESCAPE = '\u001b'

    /** What is answered for an address: bytes, a status, or what the request fails with */
    Map<String, Object> served = [:]
    List<String> asked = []
    String setting
    String repositoriesOfEnvironment
    StringWriter out = new StringWriter()
    StringWriter err = new StringWriter()

    /** The Forge application of the command line, which these indexes, without a feature pack, never start */
    ForgeContext generator = new ForgeContext({ throw new IllegalStateException('no project generator is started by this specification') } as Supplier<ApplicationContext>)
    Transport transport = { URI address ->
        asked << address.toString()
        Object answer = served[address.toString()]
        if (answer instanceof Throwable) {
            throw (Throwable) answer
        }
        return answer instanceof byte[] ? new Reply(200, null, new ByteArrayInputStream((byte[]) answer)) : new Reply((answer ?: 404) as int, null, null)
    } as Transport

    void setup() {
        served[INDEX] = index()
    }

    void "list-plugins lists the plugins of this release of Grails and of the plugin index, sorted, each with its coordinates, version and description"() {
        when:
        int exit = list('--plugin-index', INDEX)
        List<String> lines = out.toString().readLines()

        then:
        exit == 0
        err.toString().isEmpty()
        asked == [INDEX]
        lines[0] == "Grails plugins for Grails ${GRAILS}"
        lines[1] ==~ /  Name +Coordinates +Version +Description/

        and: 'a plugin of this release with the version the Grails BOM manages, whatever the index says of it'
        lines.find { it.contains(" ${CACHE} ") } ==~ "  Cache +${CACHE} +${java.util.regex.Pattern.quote(managed(CACHE))} +Caches the results of service methods.*"

        and: 'a plugin of the index with its latest release for this Grails, and without a version where none says it is for it'
        lines.find { it.contains('org.example.plugins:audit-logging') } ==~ /  Audit Logging +org\.example\.plugins:audit-logging +5\.10\.0 +Tracks changes to domain objects/
        lines.find { it.contains('com.acme:zebra-grails') } ==~ /  Zebra +com\.acme:zebra-grails +- +For Grails 3 to 5/

        and: 'in the order of their names'
        List<String> names = lines.subList(2, lines.size() - 3).collect { it.substring(2).split(/ {2,}/)[0].toLowerCase() }
        names == names.sort(false)
        names.containsAll(['audit logging', 'cache', 'zebra', 'spring security core'])

        and: 'a deprecated plugin is not listed, and it is said that one is not'
        !out.toString().contains('old-thing')
        lines[-3] == "${BundledPlugins.all().size() + 2} plugins: ${BundledPlugins.all().size()} of this release of Grails, with the version the Grails BOM manages, " +
                'and 2 of the plugin index, with the latest version that says it is for this Grails (- where none does)'
        lines[-2] == '1 deprecated plugin is not listed: --deprecated lists it'
        lines[-1] == 'More of one plugin: list-plugins plugin-info NAME'

        when:
        exit = list('--plugin-index', INDEX, '--deprecated')

        then:
        exit == 0
        out.toString().readLines().find { it.contains('old-thing') } ==~ /  Old Thing \[DEPRECATED\] +org\.example\.plugins:old-thing +1\.2\.0 +Did things/
        !out.toString().contains('deprecated plugin')
    }

    void "list-plugins lists only the plugins that have the text that is given"() {
        when:
        int exit = list('--plugin-index', INDEX, 'AUDIT')

        then: 'in whichever case, in the name, the coordinates or the description'
        exit == 0
        out.toString().readLines().findAll { it.startsWith('  ') }.drop(1)*.trim()*.split(/ {2,}/)*.getAt(1) == ['org.example.plugins:audit-logging']
        out.toString().contains('1 plugin: 0 of this release of Grails')

        when:
        exit = list('--plugin-index', INDEX, 'liquibase')

        then:
        exit == 0
        out.toString().readLines().findAll { it.contains('org.apache.grails:') }.size() == 2
        out.toString().readLines().findAll { it.contains('org.apache.grails:') }.every { it.contains('dbmigration') }

        when:
        exit = list('--plugin-index', INDEX, 'acme')

        then:
        exit == 0
        out.toString().contains('com.acme:zebra-grails')

        when:
        exit = list('--plugin-index', INDEX, 'no-such-thing')

        then: 'nothing found is no failure'
        exit == 0
        out.toString().readLines() == ['No plugin has no-such-thing in its name, its coordinates or its description',
                                       '0 plugins: 0 of this release of Grails, with the version the Grails BOM manages, and 0 of the plugin index, ' +
                                               'with the latest version that says it is for this Grails (- where none does)']

        when: 'a text that is not one line of text'
        exit = list('--plugin-index', INDEX, "audit${ESCAPE}[31m")

        then:
        exit == 2
        err.toString().readLines()[0] == 'The text to list plugins by is one line of at most 100 characters'
        !err.toString().contains(ESCAPE)
    }

    void "list-plugins --json writes the list as one document"() {
        when:
        int exit = list('--plugin-index', INDEX, '--json', 'audit')
        Map document = (Map) new JsonSlurper().parseText(out.toString())

        then:
        exit == 0
        err.toString().isEmpty()
        document.format == 1
        document.grailsVersion == GRAILS
        document.pluginIndex == [address: INDEX, read: true]
        document.notes == []
        document.deprecatedNotListed == 0
        document.plugins == [[name: 'Audit Logging', coordinates: 'org.example.plugins:audit-logging', version: '5.10.0',
                              description: 'Tracks changes to domain objects', ofGrails: false, deprecated: false, featurePacks: []]]

        when: 'the index is not there'
        served[INDEX] = new ConnectException('refused')
        exit = list('--plugin-index', INDEX, '--json', 'cache')
        document = (Map) new JsonSlurper().parseText(out.toString())

        then: 'the document says so, and nothing else is written'
        exit == 0
        err.toString().isEmpty()
        document.pluginIndex == [address: INDEX, read: false, problem: 'no connection (ConnectException)']
        document.notes.size() == 1
        document.plugins.find { it.coordinates == CACHE } == [name: 'Cache', coordinates: CACHE, version: managed(CACHE),
                                                               description: BundledPlugins.all().find { it.coordinates == CACHE }.description,
                                                               ofGrails: true, deprecated: false, featurePacks: []]
    }

    @Unroll
    void "when the plugin index #why, list-plugins says so and lists the plugins of this release of Grails"() {
        given:
        served[INDEX] = answer

        when:
        int exit = list('--plugin-index', INDEX)
        List<String> said = err.toString().readLines()

        then: 'no failure of the command: what it has in itself is the answer'
        exit == 0
        said == ["| Warning The plugin index ${INDEX} was not read (${problem}), so the plugins of the community are not known to this command: " +
                         'only the plugins of this release of Grails are']
        out.toString().contains(" ${CACHE} ")
        !out.toString().contains('audit-logging')
        out.toString().readLines()[-2] == "${BundledPlugins.all().size()} plugins: ${BundledPlugins.all().size()} of this release of Grails, with the version the Grails BOM manages"
        asked == [INDEX]

        where:
        why                              | answer                                                    | problem
        'cannot be reached'              | new ConnectException('Connection refused')                | 'no connection (ConnectException)'
        'is at a host nobody knows'      | new UnknownHostException('plugins.example.org')           | 'no connection (UnknownHostException)'
        'does not answer in time'        | new java.net.http.HttpTimeoutException('request timed out') | 'timeout'
        'is not there'                   | 404                                                       | 'not found'
        'is refused'                     | 403                                                       | 'HTTP status 403'
        'fails at its server'            | 503                                                       | 'HTTP status 503'
        'is larger than an index is'     | new byte[PluginIndex.MAX_BYTES + 1]                       | "the plugin index has more than the ${PluginIndex.MAX_BYTES} bytes it can have"
        'is a page and no index'         | '<html><body>Sign in</body></html>'.bytes                 | 'it is not JSON'
        'is JSON and no index'           | '{"message":"Not Found"}'.bytes                           | 'it is not an array of plugins, as the plugin index is'
        'is cut off'                     | '[{"coords":"org.example:cut-off","name":"Cu'.bytes      | 'it ends before the JSON document does'
        'says something a terminal acts on' | "[\"${ESCAPE}[2J\u0007\"".bytes                        | 'it has a control character in a text'
    }

    void "what the plugin index says is written as text, whatever it has in it"() {
        given:
        served[INDEX] = JsonOutput.toJson([[coords  : 'org.example.plugins:evil', name: "Evil${ESCAPE}[31m @|red loud|@\u0007", desc: "first\nsecond${ESCAPE}]0;title\u0007 %n %s",
                                            docs    : "https://example.org/${ESCAPE}[2J", vcs: 'javascript:alert(1)', licenses: ["MIT${ESCAPE}[0m"],
                                            versions: [[version: '1.0.0', grailsVersion: '7.0.0 > *'], [version: "2.0.0${ESCAPE}[31m", grailsVersion: '7.0.0 > *']]]]).getBytes('UTF-8')
        served["${REPOSITORY}org/example/plugins/evil/maven-metadata.xml".toString()] = ("<metadata><groupId>org.example.plugins</groupId><artifactId>evil</artifactId><versioning>" +
                "<release>1.0.0</release><versions><version>1.0.0</version><version>9.9&#x9b;31m</version></versions></versioning></metadata>").bytes
        served["${REPOSITORY}org/example/plugins/evil/1.0.0/evil-1.0.0-plugin.xml".toString()] = ("<plugin name='evil' version='1.0.0'><title>T&#x9b;1m @|bold t|@</title>" +
                "<description>line one\nline two&#x202e;&#x9b;2J</description><license>L&#x9b;c</license><documentation>https://example.org/&#x9b;2J</documentation>" +
                "<scm url='https://example.org/src&#x202e;'/><issueManagement url='https://example.org/issues'/></plugin>").bytes

        when:
        int exit = list('--plugin-index', INDEX, 'evil')
        String listed = out.toString()

        then:
        exit == 0
        listed.readLines()[2] ==~ /  Evil \[31m @ \|red loud\|@ +org\.example\.plugins:evil +1\.0\.0 +first second \]0;title %n %s/
        clean(listed)

        when:
        exit = info('--plugin-index', INDEX, 'org.example.plugins:evil')
        String described = out.toString()

        then: 'and so is what a repository says of the plugin: an address that is none is not shown'
        exit == 0
        clean(described)
        clean(err.toString())
        described.contains('Title: T 1m @ |bold t|@')
        described.contains('line one line two 2J')
        described.contains('* License: L c')
        described.contains('* All Versions (https://repo.example.org/maven/): 1.0.0')
        !described.contains('9.9')
        !described.contains('Documentation:')
        !described.contains('* Source:')
        described.contains('* Issue Tracker: https://example.org/issues')
        described.contains("    implementation 'org.example.plugins:evil:1.0.0'")
    }

    @Unroll
    void "#command refuses a plugin index at #address"() {
        when:
        int exit = command == 'list-plugins' ? list('--plugin-index', address) : info('--plugin-index', address, 'cache')

        then: 'a mistake in the command line, and nothing is asked'
        exit == 2
        err.toString().readLines()[0].contains(said)
        out.toString().isEmpty()
        asked.isEmpty()

        where:
        command        | address                                        | said
        'list-plugins' | 'http://plugins.example.org/index.json'        | 'is not a plugin index that is read: the address has to be https, or file: for a file'
        'plugin-info'  | 'http://plugins.example.org/index.json'        | 'is not a plugin index that is read: the address has to be https, or file: for a file'
        'list-plugins' | 'ftp://plugins.example.org/index.json'         | 'is not a plugin index that is read: the address has to be https, or file: for a file'
        'list-plugins' | 'https://user:secret@plugins.example.org/i.json' | 'the address has a user or password, and credentials are not sent'
        'list-plugins' | 'https://plugins.example.org/i.json?token=1'   | 'the address cannot have a query or a fragment'
        'list-plugins' | 'plugins.example.org/index.json'               | 'is not a plugin index that is read: the address has to be https, or file: for a file'
    }

    void "the environment names the plugin index, the option names it over the environment, and none is for no index"() {
        given:
        String other = 'https://mirror.example.org/index.json'
        served[other] = JsonOutput.toJson([[coords: 'org.mirror:only-here', name: 'Only Here', versions: [[version: '1.0.0', grailsVersion: '9.0.0 > *']]]]).bytes

        when:
        setting = other
        int exit = list()

        then:
        exit == 0
        asked == [other]
        out.toString().contains('org.mirror:only-here')

        when:
        asked.clear()
        exit = list('--plugin-index', INDEX)

        then:
        exit == 0
        asked == [INDEX]
        !out.toString().contains('org.mirror:only-here')

        when: 'an address of the environment that is none'
        asked.clear()
        setting = 'http://mirror.example.org/index.json'
        exit = list()

        then:
        exit == 2
        asked.isEmpty()
        err.toString().readLines()[0].contains('the address has to be https, or file: for a file')

        when:
        setting = 'none'
        exit = list()

        then: 'nothing is asked, and nothing is said to be missing'
        exit == 0
        asked.isEmpty()
        err.toString().isEmpty()
        out.toString().contains(" ${CACHE} ")
        out.toString().readLines()[-2] == "${BundledPlugins.all().size()} plugins: ${BundledPlugins.all().size()} of this release of Grails, with the version the Grails BOM manages"

        when: 'the option, over an environment that names one'
        setting = other
        exit = list('--plugin-index', 'none')

        then:
        exit == 0
        asked.isEmpty()
    }

    void "a plugin index can be a file, and --offline asks nothing of the network"() {
        given:
        File file = Files.createTempFile('grailsforgeindex', '.json').toFile()
        file.bytes = index()

        when:
        int exit = list('--plugin-index', file.toURI().toString(), '--offline', 'audit')

        then:
        exit == 0
        asked.isEmpty()
        out.toString().contains('org.example.plugins:audit-logging')
        err.toString().isEmpty()

        when: 'the index of the network'
        exit = list('--plugin-index', INDEX, '--offline')

        then:
        exit == 0
        asked.isEmpty()
        err.toString().readLines() == ["| Warning The plugin index ${INDEX} was not read (not asked, since --offline makes no request to the network), so the plugins of the " +
                                               'community are not known to this command: only the plugins of this release of Grails are']
        out.toString().contains(" ${CACHE} ")

        when:
        exit = info('--plugin-index', INDEX, '--offline', 'cache')

        then: 'what the command line has in itself is said, and no repository is asked'
        exit == 0
        asked.isEmpty()
        out.toString().contains("Plugin: Cache (${CACHE})")
        out.toString().contains("* Version for Grails ${GRAILS}: ${managed(CACHE)}, which org.apache.grails:grails-bom manages")
        out.toString().contains('* All Versions: not known, snapshots left out')
        out.toString().contains("    implementation '${CACHE}'")

        cleanup:
        file?.delete()
    }

    @Unroll
    void "plugin-info #given describes the Cache plugin of this release of Grails"() {
        given:
        cacheInRepository()

        when:
        int exit = info('--plugin-index', INDEX, given)
        List<String> lines = out.toString().readLines()

        then:
        exit == 0
        err.toString().isEmpty()
        lines == ["Plugin: Cache (${CACHE})",
                  'Title: Cache Plugin',
                  '',
                  'Adds caching of method results and of parts of views. With more than one line.',
                  '',
                  "* Version for Grails ${GRAILS}: ${managed(CACHE)}, which org.apache.grails:grails-bom manages",
                  '* Latest Version: 8.0.1',
                  '* All Versions (https://repo.example.org/maven/): 7.0.0, 8.0.0-RC1, 8.0.0, 8.0.1',
                  '* Grails Version: 8.0.0 > *',
                  '* License: Apache 2.0 License',
                  '* Documentation: https://docs.example.org/cache',
                  '* Issue Tracker: https://github.com/example/cache/issues',
                  '* Source: https://github.com/example/cache',
                  '* Feature: cache. In the directory of an application: plugin-info feature add cache',
                  '* Definition:',
                  '',
                  'dependencies {',
                  "    implementation '${CACHE}'",
                  '}']

        and: 'the index, the versions, and what the plugin says of itself in its latest release: the version of the BOM may not be published yet'
        asked[0] == INDEX
        asked[1] == "${REPOSITORY}org/apache/grails/grails-cache/maven-metadata.xml"
        asked.findAll { it.endsWith('-plugin.xml') }.size() <= 2
        asked.every { it.startsWith(REPOSITORY) || it == INDEX || it.startsWith(PackArtifacts.MAVEN_CENTRAL) }

        where:
        given << ['cache', 'grails-cache', 'Cache', 'CACHE', CACHE]
    }

    void "plugin-info lists the snapshots among the versions when it is asked to"() {
        given:
        cacheInRepository()

        when:
        int exit = info('--plugin-index', INDEX, 'cache', '--snapshots')

        then:
        exit == 0
        out.toString().contains('* Latest Version: 8.1.0-SNAPSHOT')
        out.toString().contains('* All Versions (https://repo.example.org/maven/): 7.0.0, 8.0.0-RC1, 8.0.0, 8.0.1, 8.1.0-SNAPSHOT')
    }

    void "plugin-info describes a plugin of the plugin index with the version an application needs in its build"() {
        when: 'no repository has anything of it'
        int exit = info('--plugin-index', INDEX, 'audit-logging')
        List<String> lines = out.toString().readLines()

        then: 'what the index says, with the repository the index names asked after the others'
        exit == 0
        err.toString().isEmpty()
        lines == ['Plugin: Audit Logging (org.example.plugins:audit-logging)',
                  '',
                  'Tracks changes to domain objects',
                  '',
                  "* Version for Grails ${GRAILS}: 5.10.0, the latest the plugin index has for this Grails",
                  '* Latest Version: 6.0.0',
                  '* All Versions (the plugin index): 5.9.0, 5.10.0, 6.0.0-RC1, 6.0.0, 4.0.0',
                  '* Grails Version: 7.0.0 > *',
                  '* License: Apache-2.0, MIT',
                  '* Documentation: https://docs.example.org/audit',
                  '* Source: https://github.com/example/audit',
                  '* Definition:',
                  '',
                  'dependencies {',
                  "    implementation 'org.example.plugins:audit-logging:5.10.0'",
                  '}']
        asked == [INDEX] + ['maven-metadata.xml', '5.10.0/audit-logging-5.10.0-plugin.xml'].collectMany { String file ->
            [REPOSITORY, PackArtifacts.MAVEN_CENTRAL, OF_THE_INDEX].collect { String repository -> "${repository}org/example/plugins/audit-logging/${file}".toString() }
        }

        when: 'with the snapshots'
        exit = info('--plugin-index', INDEX, 'Audit Logging', '--snapshots')

        then:
        exit == 0
        out.toString().contains('* All Versions (the plugin index): 5.9.0, 6.1.0-SNAPSHOT, 5.10.0, 6.0.0-RC1, 6.0.0, 4.0.0')

        when: 'a deprecated plugin, and one no version of which is for this Grails'
        exit = info('--plugin-index', INDEX, 'old-thing')

        then:
        exit == 0
        out.toString().contains('* Deprecated: Use audit-logging')

        when:
        exit = info('--plugin-index', INDEX, 'zebra-grails')

        then:
        exit == 0
        out.toString().contains("* Version for Grails ${GRAILS}: none is known")
        out.toString().contains('* Latest Version: 1.0.0')
        out.toString().contains("    implementation 'com.acme:zebra-grails:1.0.0'")
    }

    void "plugin-info --json writes what it says as one document"() {
        given:
        cacheInRepository()

        when:
        int exit = info('--plugin-index', INDEX, 'cache', '--json')
        Map document = (Map) new JsonSlurper().parseText(out.toString())

        then:
        exit == 0
        err.toString().isEmpty()
        document.format == 1
        document.pluginIndex == [address: INDEX, read: true]
        document.notes == []
        document.plugin == [name         : 'Cache', coordinates: CACHE, ofGrails: true, title: 'Cache Plugin',
                            description  : 'Adds caching of method results and of parts of views. With more than one line.',
                            grailsVersion: GRAILS, version: managed(CACHE), managedBy: 'org.apache.grails:grails-bom', latestVersion: '8.0.1',
                            versions     : ['7.0.0', '8.0.0-RC1', '8.0.0', '8.0.1'], versionsFrom: REPOSITORY, requiresGrails: '8.0.0 > *',
                            license      : 'Apache 2.0 License', documentation: 'https://docs.example.org/cache',
                            issueTracker : 'https://github.com/example/cache/issues', source: 'https://github.com/example/cache', deprecated: null,
                            dependency   : "implementation '${CACHE}'", features: ['cache'], featurePacks: []]

        when: 'a plugin nobody knows'
        exit = info('--plugin-index', INDEX, 'nothing-like-it', '--json')
        document = (Map) new JsonSlurper().parseText(out.toString())

        then:
        exit == 1
        document.plugin == null
        document.error.startsWith('No plugin nothing-like-it is known')
        err.toString().readLines().size() == 1
    }

    void "a plugin nobody knows is a failure of one line, with the plugins whose names are like it"() {
        when:
        int exit = info('--plugin-index', INDEX, 'securty')

        then:
        exit == 1
        out.toString().isEmpty()
        err.toString().readLines() == ['| Error No plugin securty is known among the plugins of this release of Grails and of the plugin index. plugin-info list-plugins lists the ' +
                                               'plugins, and a plugin that is in neither list is looked for in the repositories when it is given by its coordinates, as group:artifact']
        asked == [INDEX]

        when:
        exit = info('--plugin-index', INDEX, 'security')

        then: 'what was typed is part of the names of some'
        exit == 1
        err.toString().readLines().size() == 1
        err.toString().contains('Did you mean: grails-spring-security, grails-spring-security-acl, grails-spring-security-cas, grails-spring-security-ldap, ' +
                'grails-spring-security-oauth2 (and 6 more)?')

        when: 'the name of a plugin with something after it'
        exit = info('--plugin-index', INDEX, 'audit-logging-plugin')

        then:
        exit == 1
        err.toString().contains('Did you mean: audit-logging?')

        when: 'without the index only the plugins of this release are looked at, and that is said'
        served[INDEX] = 503
        exit = info('--plugin-index', INDEX, 'audit-logging')

        then:
        exit == 1
        err.toString().readLines().size() == 2
        err.toString().readLines()[0].startsWith("| Warning The plugin index ${INDEX} was not read (HTTP status 503)")
        err.toString().readLines()[1].startsWith('| Error No plugin audit-logging is known among the plugins of this release of Grails. ')

        when: 'something a terminal acts on'
        served[INDEX] = index()
        exit = info('--plugin-index', INDEX, "cache${ESCAPE}[31m@|red x|@")

        then:
        exit == 1
        clean(err.toString())
        err.toString().readLines().size() == 1
    }

    void "a name more than one plugin has is answered with their coordinates, and a plugin of this release is the one that is meant by its name"() {
        given:
        served[INDEX] = JsonOutput.toJson([[coords: 'org.one:report-engine', name: 'Reports', versions: []], [coords: 'org.two:grails-report-maker', name: 'Reports', versions: []],
                                           [coords: 'org.old:cache', name: 'Cache', versions: []], [coords: 'org.three:mailer', name: 'Post', versions: []],
                                           [coords: 'org.four:post', name: 'Mailer', versions: []]]).bytes

        when:
        int exit = info('--plugin-index', INDEX, 'Reports')

        then:
        exit == 1
        err.toString().readLines() == ['| Error More than one plugin is called Reports: name the one that is meant by its coordinates, org.one:report-engine or org.two:grails-report-maker']

        when: 'by the artifact of one of them'
        exit = info('--plugin-index', INDEX, 'report-maker')

        then:
        exit == 0
        out.toString().readLines()[0] == 'Plugin: Reports (org.two:grails-report-maker)'

        when:
        exit = info('--plugin-index', INDEX, 'post')

        then:
        exit == 0
        out.toString().readLines()[0] == 'Plugin: Mailer (org.four:post)'

        when: 'a name a plugin of this release has too'
        exit = info('--plugin-index', INDEX, 'cache')

        then:
        exit == 0
        out.toString().readLines()[0] == "Plugin: Cache (${CACHE})"

        when: 'and the other one by its coordinates'
        exit = info('--plugin-index', INDEX, 'org.old:cache')

        then:
        exit == 0
        out.toString().readLines()[0] == 'Plugin: Cache (org.old:cache)'
    }

    void "a plugin that is in neither list is looked for in the repositories by its coordinates"() {
        given:
        served["${REPOSITORY}com/acme/internal/acme-audit/maven-metadata.xml".toString()] = ('<?xml version="1.0" encoding="UTF-8"?><metadata><groupId>com.acme.internal</groupId>' +
                '<artifactId>acme-audit</artifactId><versioning><latest>2.1.0-SNAPSHOT</latest><release>2.0.0</release><versions><version>1.0.0</version>' +
                '<version>2.0.0</version><version>2.1.0-SNAPSHOT</version></versions></versioning></metadata>').bytes
        served["${REPOSITORY}com/acme/internal/acme-audit/2.0.0/acme-audit-2.0.0-plugin.xml".toString()] = ("<plugin name='acme-audit' version='2.0.0' grailsVersion='9.0.0 &gt; *'>" +
                '<title>Acme Audit</title><description>Audits what Acme does</description><license>Proprietary</license></plugin>').bytes

        when:
        int exit = info('--plugin-index', INDEX, 'com.acme.internal:acme-audit')

        then:
        exit == 0
        err.toString().isEmpty()
        out.toString().readLines() == ['Plugin: acme-audit (com.acme.internal:acme-audit)',
                                       'Title: Acme Audit',
                                       '',
                                       'Audits what Acme does',
                                       '',
                                       "* Version for Grails ${GRAILS}: none is known",
                                       '* Latest Version: 2.0.0',
                                       '* All Versions (https://repo.example.org/maven/): 1.0.0, 2.0.0',
                                       '* Grails Version: 9.0.0 > *',
                                       '* License: Proprietary',
                                       '* Definition:',
                                       '',
                                       'dependencies {',
                                       "    implementation 'com.acme.internal:acme-audit:2.0.0'",
                                       '}']

        when: 'one no repository has'
        exit = info('--plugin-index', INDEX, 'com.acme.internal:nothing')

        then:
        exit == 1
        out.toString().isEmpty()
        err.toString().readLines() == ['| Error No plugin com.acme.internal:nothing is known: it is not among the plugins of this release of Grails or of the plugin index, ' +
                                               'and no repository that was asked has it']

        when: 'what is not group:artifact'
        exit = info('--plugin-index', INDEX, 'com.acme:audit:2.0.0')

        then:
        exit == 2
        err.toString().readLines()[0] == "'com.acme:audit:2.0.0' is not the coordinates of a plugin: they are group:artifact, without a version"
    }

    @Unroll
    void "a repository that #why is said to, and plugin-info says what is known without it"() {
        given:
        served["${REPOSITORY}org/example/plugins/audit-logging/maven-metadata.xml".toString()] = answer

        when:
        int exit = info('--plugin-index', INDEX, 'audit-logging')

        then:
        exit == 0
        err.toString().readLines()[0] == "| Warning ${said}"
        clean(err.toString())
        out.toString().contains('* All Versions (the plugin index): 5.9.0, 5.10.0, 6.0.0-RC1, 6.0.0, 4.0.0')

        and: 'the search ends there: the coordinates are sent to no repository after one that failed, for this file or the next'
        asked.findAll { it.endsWith('maven-metadata.xml') } == ["${REPOSITORY}org/example/plugins/audit-logging/maven-metadata.xml".toString()]
        asked.findAll { it.endsWith('-plugin.xml') }.size() == (ends ? 0 : 3)

        where:
        why                               | ends  | answer                                                                                              | said
        'fails'                           | true  | 500                                                                                                 | "maven-metadata.xml of org.example.plugins:audit-logging was not read from ${REPOSITORY} (HTTP status 500), and no other repository was asked for it"
        'refuses access'                  | true  | 401                                                                                                 | "maven-metadata.xml of org.example.plugins:audit-logging was not read from ${REPOSITORY} (HTTP status 401), and no other repository was asked for it"
        'cannot be reached'               | true  | new ConnectException('refused')                                                                     | "maven-metadata.xml of org.example.plugins:audit-logging was not read from ${REPOSITORY} (no connection (ConnectException)), and no other repository was asked for it"
        'answers with more than the file' | true  | new byte[PackArtifacts.MAX_METADATA_BYTES + 1]                                                      | "maven-metadata.xml of org.example.plugins:audit-logging was not read from ${REPOSITORY} (maven-metadata.xml has more than the ${PackArtifacts.MAX_METADATA_BYTES} bytes it can have), and no other repository was asked for it"
        'answers with a page'             | false | '<html><body>Welcome</body></html>'.bytes                                                           | "maven-metadata.xml of org.example.plugins:audit-logging from ${REPOSITORY} is not what a Maven repository has under that name"
        'answers with a document type'    | false | '<!DOCTYPE metadata [<!ENTITY x SYSTEM "file:///etc/passwd">]><metadata><groupId>&x;</groupId></metadata>'.bytes | "maven-metadata.xml of org.example.plugins:audit-logging from ${REPOSITORY} cannot be read"
        'answers for another artifact'    | false | '<metadata><groupId>org.other</groupId><artifactId>audit-logging</artifactId></metadata>'.bytes     | "maven-metadata.xml of org.example.plugins:audit-logging from ${REPOSITORY} is that of another artifact, and is not used"
    }

    @Unroll
    void "plugin-info reads the versions of a plugin that publishToMavenLocal installed, in a repository that is #named"() {
        given: 'a local repository of Maven as publishToMavenLocal of Gradle writes it: maven-metadata-local.xml, and no maven-metadata.xml'
        File repository = Files.createTempDirectory('grailsforgemavenlocal').toFile()
        installedByPublishToMavenLocal(repository)
        String address = repository.toPath().toAbsolutePath().normalize().toUri().toString()
        String previous = System.getProperty('maven.repo.local')
        if (entry == 'mavenLocal()') {
            System.setProperty('maven.repo.local', repository.absolutePath)
        }
        repositoriesOfEnvironment = [before, entry == 'a path' ? repository.absolutePath : entry == 'an address' ? address : entry].findAll().join(';')
        List<String> options = offline ? ['--plugin-index', 'none', '--offline'] : ['--plugin-index', 'none']

        when:
        int exit = info(*(options + ['com.acme.internal:acme-audit']))

        then: 'the versions are those of the repository, and the plugin describes itself'
        exit == 0
        err.toString().isEmpty()
        out.toString().readLines() == ['Plugin: acme-audit (com.acme.internal:acme-audit)',
                                       'Title: Acme Audit',
                                       '',
                                       'Audits what Acme does',
                                       '',
                                       "* Version for Grails ${GRAILS}: none is known",
                                       '* Latest Version: 2.0.0',
                                       "* All Versions (${address}): 2.0.0",
                                       '* Grails Version: 9.0.0 > *',
                                       '* License: Proprietary',
                                       '* Definition:',
                                       '',
                                       'dependencies {',
                                       "    implementation 'com.acme.internal:acme-audit:2.0.0'",
                                       '}']

        and: 'a repository of the network is asked for maven-metadata.xml, and for no file of the local repository of Maven'
        asked.findAll { it.contains('maven-metadata') } == askedOfTheNetwork

        when: 'with the snapshots'
        exit = info(*(options + ['--snapshots', 'com.acme.internal:acme-audit']))

        then:
        exit == 0
        out.toString().contains('* Latest Version: 2.1.0-SNAPSHOT')
        out.toString().contains("* All Versions (${address}): 2.0.0, 2.1.0-SNAPSHOT")

        cleanup:
        if (previous == null) {
            System.clearProperty('maven.repo.local')
        } else {
            System.setProperty('maven.repo.local', previous)
        }
        repository.deleteDir()

        where:
        named                                  | before     | entry          | offline || askedOfTheNetwork
        'named by its file: address'           | null       | 'an address'   | true    || []
        'named by its path'                    | null       | 'a path'       | true    || []
        'mavenLocal()'                         | null       | 'mavenLocal()' | true    || []
        'named after one of the network'       | REPOSITORY | 'an address'   | false   || ["${REPOSITORY}com/acme/internal/acme-audit/maven-metadata.xml".toString()]
    }

    void "a local repository of Maven with metadata that is not of the plugin is said to, and plugin-info says what is known without it"() {
        given:
        File repository = Files.createTempDirectory('grailsforgemavenlocal').toFile()
        installedByPublishToMavenLocal(repository)
        File metadata = new File(repository, 'com/acme/internal/acme-audit/maven-metadata-local.xml')
        metadata.text = metadata.text.replace('<artifactId>acme-audit</artifactId>', '<artifactId>other</artifactId>')
        repositoriesOfEnvironment = repository.absolutePath

        when:
        int exit = info('--plugin-index', 'none', '--offline', 'com.acme.internal:acme-audit')

        then: 'the warning names the file that was read'
        exit == 1
        err.toString().readLines()[0] == "| Warning maven-metadata-local.xml of com.acme.internal:acme-audit from ${repository.toPath().toAbsolutePath().normalize().toUri()} " +
                'is that of another artifact, and is not used'

        cleanup:
        repository.deleteDir()
    }

    void "the commands say how they are used"() {
        when:
        int exit = list('--help')

        then:
        exit == 0
        out.toString().replaceAll(/\s+/, ' ').contains('Usage: list-plugins [-hvVx] [--deprecated] [--json] [--offline] [--plugin-index=URL] [FILTER]')
        asked.isEmpty()

        when:
        exit = info('--help')

        then:
        exit == 0
        out.toString().replaceAll(/\s+/, ' ').contains('Usage: plugin-info [-hvVx] [--json] [--offline] [--snapshots] [--plugin-index=URL] PLUGIN')

        when: 'without a plugin'
        exit = info()

        then:
        exit == 2
        asked.isEmpty()
    }

    private static boolean clean(String text) {
        return !(text =~ /[\p{Cc}\p{Cf}&&[^\r\n]]/) && !(text =~ /@\|(red|bold)/)
    }

    private static String managed(String coordinates) {
        return BomVersions.of('grails-bom').versionOf(coordinates)
    }

    private void cacheInRepository() {
        served["${REPOSITORY}org/apache/grails/grails-cache/maven-metadata.xml".toString()] = ('<?xml version="1.0" encoding="UTF-8"?>\n<metadata>\n  <groupId>org.apache.grails</groupId>\n' +
                '  <artifactId>grails-cache</artifactId>\n  <versioning>\n    <latest>8.1.0-SNAPSHOT</latest>\n    <release>8.0.1</release>\n    <versions>\n' +
                '      <version>7.0.0</version>\n      <version>8.0.0-RC1</version>\n      <version>8.0.0</version>\n      <version>8.0.1</version>\n' +
                '      <version>8.1.0-SNAPSHOT</version>\n    </versions>\n    <lastUpdated>20261101000000</lastUpdated>\n  </versioning>\n</metadata>\n').bytes
        byte[] description = ("<plugin name='cache' version='8.0.1' grailsVersion='8.0.0 &gt; *'>\n  <type>grails.plugin.cache.CacheGrailsPlugin</type>\n" +
                "  <grailsVersion>8.0.0 &gt; *</grailsVersion>\n  <license>Apache 2.0 License</license>\n" +
                "  <issueManagement system='GitHub' url='https://github.com/example/cache/issues' />\n  <author>Someone</author>\n" +
                '  <documentation>https://docs.example.org/cache</documentation>\n  <name>cache</name>\n  <title>Cache Plugin</title>\n' +
                '  <description>Adds caching of method results\n    and of parts of views.\n    With more than one line.</description>\n' +
                "  <scm url='https://github.com/example/cache' />\n  <resources>\n    <resource>grails.plugin.cache.CacheTagLib</resource>\n  </resources>\n</plugin>").bytes
        // in the version of the BOM when that is a release, and in the latest release
        served["${REPOSITORY}org/apache/grails/grails-cache/8.0.1/grails-cache-8.0.1-plugin.xml".toString()] = description
        served["${REPOSITORY}org/apache/grails/grails-cache/${managed(CACHE)}/grails-cache-${managed(CACHE)}-plugin.xml".toString()] = description
    }

    /**
     * What publishToMavenLocal of Gradle writes for a plugin published in 2.0.0 and then in
     * 2.1.0-SNAPSHOT, with the descriptor of the plugin among the artifacts of its publication, as a
     * run of Gradle 9 wrote it: the metadata of what was installed, and no checksums.
     */
    private static void installedByPublishToMavenLocal(File repository) {
        File plugin = new File(repository, 'com/acme/internal/acme-audit')
        Map<String, String> files = [
                'maven-metadata-local.xml'                        : '<?xml version="1.0" encoding="UTF-8"?>\n<metadata>\n  <groupId>com.acme.internal</groupId>\n' +
                        '  <artifactId>acme-audit</artifactId>\n  <versioning>\n    <latest>2.1.0-SNAPSHOT</latest>\n    <release>2.0.0</release>\n' +
                        '    <versions>\n      <version>2.0.0</version>\n      <version>2.1.0-SNAPSHOT</version>\n    </versions>\n' +
                        '    <lastUpdated>20261123174057</lastUpdated>\n  </versioning>\n</metadata>\n',
                '2.0.0/acme-audit-2.0.0.jar'                      : 'a jar',
                '2.0.0/acme-audit-2.0.0.pom'                      : '<project/>',
                '2.0.0/acme-audit-2.0.0.module'                   : '{}',
                '2.0.0/acme-audit-2.0.0-plugin.xml'               : "<plugin name='acme-audit' version='2.0.0' grailsVersion='9.0.0 &gt; *'><title>Acme Audit</title>" +
                        '<description>Audits what Acme does</description><license>Proprietary</license></plugin>\n',
                '2.1.0-SNAPSHOT/maven-metadata-local.xml'         : '<?xml version="1.0" encoding="UTF-8"?>\n<metadata modelVersion="1.1.0">\n' +
                        '  <groupId>com.acme.internal</groupId>\n  <artifactId>acme-audit</artifactId>\n  <versioning>\n' +
                        '    <lastUpdated>20261123174057</lastUpdated>\n    <snapshot>\n      <localCopy>true</localCopy>\n    </snapshot>\n' +
                        '  </versioning>\n  <version>2.1.0-SNAPSHOT</version>\n</metadata>\n',
                '2.1.0-SNAPSHOT/acme-audit-2.1.0-SNAPSHOT.jar'    : 'a jar',
                '2.1.0-SNAPSHOT/acme-audit-2.1.0-SNAPSHOT.pom'    : '<project/>',
                '2.1.0-SNAPSHOT/acme-audit-2.1.0-SNAPSHOT-plugin.xml': "<plugin name='acme-audit' version='2.1.0-SNAPSHOT'><title>Acme Audit</title></plugin>\n"]
        files.each { String path, String text ->
            File file = new File(plugin, path)
            file.parentFile.mkdirs()
            file.setText(text, 'UTF-8')
        }
    }

    private static byte[] index() {
        return JsonOutput.prettyPrint(JsonOutput.toJson([
                [name    : 'Cache', desc: 'What the index says of the cache plugin', coords: CACHE, owner: 'apache', vcs: 'https://github.com/apache/grails-core',
                 'maven-repo': 'https://repo1.maven.org/maven2', labels: ['cache'], licenses: ['Apache-2.0'],
                 versions: [[version: '8.0.0', date: '2026-01-01T00:00:00+0000', grailsVersion: '8.0.0 > *']]],
                [name    : 'Zebra', desc: 'For Grails 3 to 5', coords: 'com.acme:zebra-grails', owner: 'acme', vcs: 'https://github.com/acme/zebra',
                 'maven-repo': 'https://repo1.maven.org/maven2', licenses: ['MIT'], versions: [[version: '1.0.0', grailsVersion: '3.0.0 > 5.9.9']]],
                [name    : 'Audit Logging', desc: 'Tracks changes to domain objects', coords: 'org.example.plugins:audit-logging', owner: 'example',
                 vcs     : 'https://github.com/example/audit', docs: 'https://docs.example.org/audit', 'maven-repo': OF_THE_INDEX, labels: ['audit'],
                 licenses: ['Apache-2.0', 'MIT'],
                 versions: [[version: '5.9.0', grailsVersion: '7.0.0 > *'], [version: '6.1.0-SNAPSHOT', grailsVersion: '9.0.0 > *'], [version: '5.10.0', grailsVersion: '7.0.0 > *'],
                            [version: '6.0.0-RC1', grailsVersion: '9.1.0 > *'], [version: '6.0.0', grailsVersion: '9.1.0 > *'], [version: '4.0.0', grailsVersion: '3.0.0 > 6.9.9'],
                            [version: '3.0.0', grailsVersion: '3.0.0 > *', coords: 'org.example.plugins:audit', 'maven-repo': 'https://repo.grails.org/plugins']]],
                [name    : 'Old Thing', desc: 'Did things', coords: 'org.example.plugins:old-thing', owner: 'example', vcs: 'https://github.com/example/old',
                 'maven-repo': 'https://repo1.maven.org/maven2', licenses: ['Apache-2.0'], deprecated: 'Use audit-logging',
                 versions: [[version: '1.2.0', grailsVersion: '7.0.0 > *']]]])).getBytes('UTF-8')
    }

    private int list(String... args) {
        return run(new ListPluginsCommand(generator) {

            @Override
            protected PackArtifacts createPackArtifacts() {
                return new PackArtifacts(transport)
            }

            @Override
            protected String pluginIndexSetting() {
                return setting
            }

            @Override
            protected String grailsVersion() {
                return GRAILS
            }
        }, args)
    }

    private int info(String... args) {
        return run(new PluginInfoCommand(generator) {

            @Override
            protected PackArtifacts createPackArtifacts() {
                return new PackArtifacts(transport)
            }

            @Override
            protected String pluginIndexSetting() {
                return setting
            }

            @Override
            protected String grailsVersion() {
                return GRAILS
            }

            @Override
            protected String grailsRepoUrl() {
                return repositoriesOfEnvironment ?: REPOSITORY
            }
        }, args)
    }

    private int run(Object command, String... args) {
        out = new StringWriter()
        err = new StringWriter()
        CommandLine commandLine = new CommandLine(command)
        commandLine.setOut(new PrintWriter(out, true))
        commandLine.setErr(new PrintWriter(err, true))
        return commandLine.execute(args)
    }
}
