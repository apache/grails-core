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

import groovy.json.JsonOutput
import org.apache.grails.forge.cli.pack.PackArtifactException
import org.apache.grails.forge.cli.pack.PackArtifacts
import org.apache.grails.forge.cli.plugin.PluginRecord.Release
import spock.lang.Requires
import spock.lang.Specification
import spock.lang.Unroll

/**
 * The plugin index of Grails read as a list of plugins: the part of the published index that is among
 * the resources of these tests (see the README beside it), and documents that are no index.
 */
class PluginIndexSpec extends Specification {

    void "the plugin index as it is published is read whole"() {
        when:
        PluginIndex index = PluginIndex.parse(getClass().getResourceAsStream('/plugins-index/plugins-index.json').bytes)

        then: 'every plugin of it'
        index.plugins.size() == 23
        index.leftOut == 0
        index.plugins.every { PluginRecord plugin -> PluginIndex.isCoordinates(plugin.coordinates) && plugin.name && !plugin.ofGrails && plugin.features.isEmpty() }

        and: 'with what the index says of it'
        PluginRecord mail = index.plugins.find { it.coordinates == 'org.grails.plugins:grails-mail' }
        mail.name == 'Mail'
        mail.description
        mail.source.startsWith('https://github.com/')
        mail.license == 'Apache-2.0'
        mail.deprecated == null
        mail.repository == URI.create(PackArtifacts.MAVEN_CENTRAL)

        and: 'the versions it was published in under these coordinates, and not those it had under others'
        mail.versions*.version.containsAll(['5.0.3', '5.0.2'])
        !mail.versions*.version.contains('4.0.2')
        mail.versions.find { it.version == '5.0.3' }.grailsVersion == '7.0.0 > *'
        mail.latestFor('9.0.0').version == '5.0.3'
        mail.latestFor('9.0.0-SNAPSHOT').version == '5.0.3'
        mail.latestFor('6.2.0') == null

        and: 'a version the index says has no artifact is not one, and a deprecated plugin says why'
        index.plugins.find { it.coordinates == 'org.grails.plugins:grails-hibernate-filter' }.versions*.version.every { it != '0.2.0' }
        index.plugins.find { it.coordinates == 'com.agileorbit:schwartz' }.deprecated == 'Source repository is archived.'

        and: 'every text is one line of text'
        index.plugins.every { PluginRecord plugin ->
            [plugin.name, plugin.description, plugin.license, plugin.deprecated].findAll { it != null }.every { String text ->
                !(text =~ /[\p{Cc}\p{Cf}]/) && !text.contains('@|') && text.length() <= PluginRecord.MAX_DESCRIPTION + 3
            }
        }
    }

    @Requires({ System.getProperty('forge.pluginIndex') })
    void "the whole plugin index, of a copy that -PpluginIndex names, is read with every plugin it has"() {
        when:
        PluginIndex index = PluginIndex.parse(new File(System.getProperty('forge.pluginIndex')).bytes)

        then:
        index.plugins.size() > 200
        index.leftOut == 0
        index.plugins.count { it.description } > 200
        index.plugins.count { it.source } > 200
        index.plugins.count { it.repository } == index.plugins.size()
        index.plugins.count { it.latestFor('9.0.0') } > 100
        index.plugins.every { PluginRecord plugin ->
            [plugin.name, plugin.description, plugin.license, plugin.deprecated].findAll { it != null }.every { String text -> !(text =~ /[\p{Cc}\p{Cf}]/) }
        }
    }

    void "what an entry says is kept as text that neither a terminal nor the command line acts on"() {
        given:
        String document = JsonOutput.toJson([
                [coords     : 'org.example:evil', name: 'Evil\u001b[31m @|red x|@', desc: 'first line\nsecond\u0007 line ‮ reversed ' + ('long ' * 200),
                 docs       : 'javascript:alert(1)', vcs: 'https://example.org/evil\u001b[2J', licenses: ['MIT\u001b]0;title\u0007', 7, 'BSD'],
                 deprecated : 'gone\r\nfor good', 'maven-repo': 'https://127.0.0.1/repository',
                 versions   : [[version: '1.0.0', grailsVersion: '7.0.0 > *\u001b[1m'], [version: '../../etc', grailsVersion: '7.0.0 > *'],
                               [version: '0.9.0', coords: 'org.other:evil'], [version: '0.8.0', artifactAvailable: false], 'no version', [version: 3]]],
                [coords: 'org.example:plain', versions: 'none'],
                [coords: 'not coordinates'],
                [coords: 'org.example:plain', name: 'A second one'],
                'no plugin',
                [name: 'Nameless']])

        when:
        PluginIndex index = PluginIndex.parse(document.getBytes('UTF-8'))
        PluginRecord evil = index.plugins[0]

        then: 'an entry that has no coordinates, or those of one before it, is left out'
        index.plugins*.coordinates == ['org.example:evil', 'org.example:plain']
        index.leftOut == 4

        and:
        evil.name == 'Evil [31m @ |red x|@'
        evil.description.startsWith('first line second line reversed long long')
        evil.description.endsWith('...')
        evil.description.length() <= PluginRecord.MAX_DESCRIPTION + 3
        evil.documentation == null
        evil.source == null
        evil.license == 'MIT ]0;title , BSD'
        evil.deprecated == 'gone for good'

        and: 'a repository of this computer is not one the index can name'
        evil.repository == null

        and: 'only what is a version of these coordinates is one'
        evil.versions*.version == ['1.0.0']
        evil.versions[0].grailsVersion == '7.0.0 > * [1m'

        and: 'a plugin of which the index says nothing else is still a plugin, by its artifact'
        index.plugins[1].name == 'plain'
        index.plugins[1].versions.isEmpty()
        index.plugins[1].description == null
    }

    @Unroll
    void "a document that is no plugin index is refused: #why"() {
        when:
        PluginIndex.parse(bytes)

        then:
        PackArtifactException e = thrown()
        e.message == said
        e.message ==~ /[\x20-\x7e]+/

        where:
        why                         | bytes                                                         | said
        'not JSON'                  | '<html>Not Found</html>'.bytes                                | 'it is not JSON'
        'JSON that ends early'      | '[{"coords":"a:b"}'.bytes                                     | 'it is not JSON'
        'an object'                 | '{"plugins":[]}'.bytes                                        | 'it is not an array of plugins, as the plugin index is'
        'not UTF-8'                 | [0x5b, 0x22, 0xff, 0xfe, 0x22, 0x5d] as byte[]                | 'it is not written in UTF-8'
        'nested too deep'           | (('[' * 20) + (']' * 20)).bytes                               | "it is nested deeper than ${PluginIndex.MAX_DEPTH}"
        'more plugins than any has' | ('[' + (['{}'] * (PluginIndex.MAX_PLUGINS + 1)).join(',') + ']').bytes | "it has more than the ${PluginIndex.MAX_PLUGINS} plugins a plugin index can have"
        'a key twice'               | '[{"coords":"a:b","coords":"c:d"}]'.bytes                     | 'it has an object with the same key twice'
        'larger than an index is'   | new byte[PluginIndex.MAX_BYTES + 1]                           | "it has more than the ${PluginIndex.MAX_BYTES} bytes a plugin index can have"
    }

    void "an empty array is an index without plugins"() {
        expect:
        PluginIndex.parse('[]'.bytes).plugins.isEmpty()
        PluginIndex.parse('﻿ [ ] '.getBytes('UTF-8')).plugins.isEmpty()
    }

    @Unroll
    void "a version with grailsVersion #range is #said for Grails #grails"() {
        expect:
        new Release('1.0.0', range).isFor(grails) == fits

        where:
        range             | grails           | fits
        '7.0.0 > *'       | '9.0.0'          | true
        '7.0.0 > *'       | '9.0.0-SNAPSHOT' | true
        '7.0.0 > *'       | '7.0.0'          | true
        '7.0.0 > *'       | '6.2.3'          | false
        '9.1.0 > *'       | '9.0.5'          | false
        '7.0.0  > *'      | '9.0.0'          | true
        '2.4 >= *'        | '9.0.0'          | true
        '3.1.* > *'       | '3.0.9'          | false
        '3.1.* > *'       | '3.1.0'          | true
        '1.2 > 1.3.7'     | '1.3.7'          | true
        '1.2 > 1.3.7'     | '1.3.8'          | false
        '5.3.0 > 6.2.3'   | '9.0.0'          | false
        '5.3.0 > 6.*'     | '6.9.9'          | true
        '7.0.0-M1 > *'    | '7.0.0'          | true
        null              | '9.0.0'          | false
        'whatever'        | '9.0.0'          | false
        '* > *'           | '9.0.0'          | true
        '7.0.0 > *'       | 'unknown'        | false
        said = fits ? 'one' : 'none'
    }

    void "the latest version for a Grails is the latest release that says it is for it, whatever the order of the index"() {
        given:
        PluginRecord plugin = new PluginRecord(coordinates: 'org.example:audit', name: 'audit',
                versions: [new Release('5.9.0', '7.0.0 > *'), new Release('6.0.0-SNAPSHOT', '9.0.0 > *'), new Release('5.10.0', '7.0.0 > *'),
                           new Release('6.0.0-RC1', '9.0.0 > *'), new Release('7.0.0', '10.0.0 > *'), new Release('4.0.0', '3.0.0 > 6.9.9')])

        expect:
        plugin.latestFor('9.0.0').version == '5.10.0'
        plugin.latestFor('10.1.0').version == '7.0.0'
        plugin.latestFor('5.0.0').version == '4.0.0'
        plugin.latestFor('2.0.0') == null

        and: 'of a plugin without a release, a version that is none, and no snapshot'
        new PluginRecord(coordinates: 'a:b', name: 'b', versions: [new Release('1.0.0-SNAPSHOT', '7.0.0 > *'), new Release('1.0.0-M2', '7.0.0 > *')])
                .latestFor('9.0.0').version == '1.0.0-M2'
        new PluginRecord(coordinates: 'a:b', name: 'b', versions: [new Release('1.0.0-SNAPSHOT', '7.0.0 > *')]).latestFor('9.0.0') == null
    }

    @Unroll
    void "the address #text is #said"() {
        expect:
        PluginRecord.address(text) == (shown ? text.trim() : null)

        where:
        text                                        | shown
        'https://example.org/docs/index.html#top'   | true
        ' http://example.org/a?b=c&d=e '            | true
        'javascript:alert(1)'                       | false
        'file:///etc/passwd'                        | false
        'https://example.org/a b'                   | false
        'https://example.org/\u001b[31m'            | false
        'https://example.org/@|red x|@'             | false
        'https://' + ('a' * 600)                    | false
        ''                                          | false
        null                                        | false
        said = shown ? 'shown' : 'not shown'
    }
}
