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
package org.apache.grails.forge.cli.mcp.tools

import org.apache.grails.forge.cli.mcp.McpException
import org.apache.grails.forge.cli.mcp.McpTool
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

/**
 * How the arguments of a call of a tool of {@code grails mcp} become the command line the call runs, and
 * which arguments are refused before anything runs: the tools are those {@code grails mcp} makes from the
 * sample of {@code help --json} of an application with tools of its own and of its plugins.
 */
class ArgumentMappingSpec extends Specification {

    @Shared
    Map<String, McpTool> tools = McpToolSamples.tools('application-with-tools').tools.collectEntries { [(it.name): it] }

    @Unroll
    void "#tool with #arguments runs #commandLine"() {
        expect:
        tools[tool].commandLine(arguments) == commandLine

        where:
        tool                 | arguments                                                                                        || commandLine
        // a leading @ would name a file of arguments: it is doubled, which the command line reads as one @
        'create-controller'  | ['controller-name': '@/home/me/.ssh/id_rsa']                                                    || ['create-controller', '@@/home/me/.ssh/id_rsa']
        'create-controller'  | ['controller-name': 'Book', force: true]                                                         || ['create-controller', '--force', 'Book']
        'create-controller'  | ['controller-name': 'Book', force: false]                                                        || ['create-controller', 'Book']
        'generate-all'       | ['domain-class': '@Book']                                                                        || ['generate-all', '@@Book']
        'legacy-task'        | [arguments: ['@args.txt', '--verbose', '-x']]                                                    || ['legacy-task', '@@args.txt', '--verbose', '-x']
        // an option's value is one argument, so one that begins with - is never read as an option
        'audit-report'       | [since: '-1d', output: 'report.txt']                                                             || ['audit-report', '--since=-1d', 'report.txt']
        'audit-report'       | [since: '@yesterday', output: 'report.txt']                                                      || ['audit-report', '--since=@yesterday', 'report.txt']
        // -D maps
        'compile'            | ['system-properties': ['grails.env': 'test', 'a.b': 'x=y']]                                      || ['compile', '-Dgrails.env=test', '-Da.b=x=y']
        'run-app'            | ['project-properties': [profile: 'dev'], port: 8081]                                             || ['run-app', '--detach', '-Pprofile=dev', '--port=8081']
        // repeatable values, each its own argument
        'test-app'           | [tests: ['BookSpec', 'AuthorSpec'], unit: true]                                                  || ['test-app', '--tests=BookSpec', '--tests=AuthorSpec', '--unit']
        'test-app'           | [test: ['*BookSpec', 'example.AuthorSpec']]                                                      || ['test-app', '*BookSpec', 'example.AuthorSpec']
        'create-app'         | [features: ['postgres', 'spring-security'], 'exclude-features': ['h2'], name: 'shop']            || ['create-app', '--exclude-features=h2', '--features=postgres', '--features=spring-security', 'shop']
        // an enum, an integer
        'create-app'         | [servlet: 'jetty', jdk: 21]                                                                      || ['create-app', '--jdk=21', '--servlet=jetty']
        'audit-report'       | [format: 'JSON', 'include-deleted': true, output: 'r.json']                                      || ['audit-report', '--format=JSON', '--include-deleted', 'r.json']
        'run-app'            | [port: 8081.0]                                                                                   || ['run-app', '--detach', '--port=8081']
        'run-app'            | ['ready-timeout': 600L]                                                                          || ['run-app', '--detach', '--ready-timeout=600']
        // a camel-case declared option
        'dbm-update'         | [dataSource: 'reporting', defaultSchema: 'public']                                               || ['dbm-update', '--dataSource=reporting', '--defaultSchema=public']
        // the options a policy gives on every call
        'project_regenerate' | [:]                                                                                              || ['project', 'regenerate', '--diff', '--json']
        'feature_find'       | [text: 'security', type: 'web']                                                                  || ['feature', 'find', '--json', '--type=web', 'security']
        'plugin_show'        | [plugin: 'spring-security-core']                                                                 || ['plugin', 'show', '--json', 'spring-security-core']
        'feature_add'        | [feature: ['postgres'], 'dry-run': true]                                                         || ['feature', 'add', '--dry-run', '--json', 'postgres']
        'feature_add'        | [feature: ['postgres'], 'dry-run': true, diff: true]                                             || ['feature', 'add', '--diff', '--dry-run', 'postgres']
        'feature_add'        | [feature: ['postgres', 'mongodb']]                                                               || ['feature', 'add', 'postgres', 'mongodb']
        // run-tool: the environment before the name, and the tool's arguments as they are
        'run-tool'           | ['system-properties': ['grails.env': 'production'], name: 'audit-report', arg: ['--format=JSON', 'r.txt']] || ['run-tool', '-Dgrails.env=production', 'audit-report', '--format=JSON', 'r.txt']
        'refresh_tools'      | [:]                                                                                              || ['gradle', 'grailsToolCatalog', '--rerun']
        'add-property'       | ['domain-class': 'Book', 'property-type': 'title:String', 'max-size': 255]                       || ['add-property', '--max-size=255', 'Book', 'title:String']
    }

    @Unroll
    void "#tool refuses #arguments, naming #property"() {
        when:
        tools[tool].commandLine(arguments)

        then:
        McpException e = thrown()
        e.code == McpException.INVALID_PARAMS
        e.message.startsWith("${property}: ")
        e.message.contains(says)

        where:
        tool                | arguments                                                         || property                | says
        // a parameter that begins with - would be read as an option
        'create-controller' | ['controller-name': '-x']                                         || 'controller-name'       | 'a value that begins with - is refused'
        'create-controller' | ['controller-name': '--force']                                    || 'controller-name'       | 'begins with -'
        'run-tool'          | [name: '--help']                                                  || 'name'                  | 'begins with -'
        'test-app'          | [test: ['BookSpec', '-Dx=y']]                                     || 'test'                  | 'begins with -'
        // NUL, and half of a character of two code units
        'create-controller' | ['controller-name': 'Bo\u0000ok']                                 || 'controller-name'       | 'U+0000'
        'legacy-task'       | [arguments: ['a\u0000']]                                          || 'arguments'             | 'U+0000'
        'compile'           | ['system-properties': [a: 'b\u0000']]                             || 'system-properties'     | 'U+0000'
        'create-controller' | ['controller-name': 'Book\ud800']                                 || 'controller-name'       | 'half of a character'
        // a -D map: a name is not empty and has no =, and a value is a string
        'compile'           | ['system-properties': ['': 'x']]                                  || 'system-properties'     | 'a name is not empty and has no ='
        'compile'           | ['system-properties': ['a=b': 'x']]                               || 'system-properties'     | 'a name is not empty and has no ='
        'compile'           | ['system-properties': [a: 1]]                                     || 'system-properties'     | 'the value of a is not a string'
        'compile'           | ['system-properties': ['a=b']]                                    || 'system-properties'     | 'an object of names'
        // repeatable values are a list
        'test-app'          | [tests: 'BookSpec']                                               || 'tests'                 | 'a list of string values is expected'
        'feature_add'       | [feature: []]                                                     || 'feature'               | 'at least 1 value is expected'
        // an enum, an integer, a boolean
        'create-app'        | [servlet: 'netty']                                                || 'servlet'               | 'one of tomcat, jetty, undertow, none is expected'
        'audit-report'      | [format: 'json', output: 'r']                                     || 'format'                | 'one of TEXT, JSON is expected'
        'run-app'           | [port: '8081']                                                    || 'port'                  | 'a whole number is expected'
        'run-app'           | [port: 8081.5]                                                    || 'port'                  | 'a whole number is expected'
        'create-controller' | ['controller-name': 'Book', force: 'yes']                         || 'force'                 | 'true or false is expected'
        'create-controller' | ['controller-name': 7]                                            || 'controller-name'       | 'a string is expected'
        // coordinates of a plugin name its feature pack, which is code
        'create-app'        | [features: ['com.example:audit-pack:1.0.0']]                      || 'features'              | 'coordinates of a plugin'
        'create-app'        | ['exclude-features': ['acme#audit']]                              || 'exclude-features'      | 'coordinates of a plugin'
        'feature_add'       | [feature: ['postgres', 'org.example:pack:1.0']]                   || 'feature'               | 'coordinates of a plugin'
        'feature_show'      | [feature: 'org.example:pack:1.0#audit']                           || 'feature'               | 'coordinates of a plugin'
        'feature_diff'      | [feature: ['x:y']]                                                || 'feature'               | 'coordinates of a plugin'
        'project_migrate'   | [features: ['a:b:c']]                                             || 'features'              | 'coordinates of a plugin'
        // what the schema does not have, and what it requires
        'create-controller' | ['controller-name': 'Book', 'feature-pack-dir': '/tmp/pack']      || 'feature-pack-dir'      | 'create-controller has no such argument; it takes force, controller-name'
        'create-app'        | ['feature-catalog': 'https://example.com/catalog.json']           || 'feature-catalog'       | 'no such argument'
        'plugin_list'       | ['plugin-index': 'file:/tmp/index.json']                          || 'plugin-index'          | 'no such argument'
        'project_regenerate' | [output: '/tmp/elsewhere']                                       || 'output'                | 'no such argument'
        'run-app'           | [foreground: true]                                                || 'foreground'            | 'no such argument'
        'refresh_tools'     | [all: true]                                                       || 'all'                   | 'it takes none'
        'create-controller' | [:]                                                               || 'controller-name'       | 'it is required, and was not given'
        'audit-report'      | [since: 'monday']                                                 || 'output'                | 'it is required'
    }

    void "a call that comes to more than 4,096 arguments or more than a MiB is refused"() {
        when:
        tools['legacy-task'].commandLine([arguments: ['x'] * 4095])

        then:
        notThrown(McpException)

        when:
        tools['legacy-task'].commandLine([arguments: ['x'] * 4096])

        then:
        McpException many = thrown()
        many.code == McpException.INVALID_PARAMS
        many.message == 'The call comes to 4097 arguments of the command line, and grails mcp gives a command 4096 at most'

        when:
        tools['legacy-task'].commandLine([arguments: ['x' * (1024 * 1024)]])

        then:
        McpException large = thrown()
        large.message.startsWith('The arguments of the call come to 1048587 bytes')
    }

    @Unroll
    void "#tool with #arguments writes JSON: #json"() {
        expect:
        tools[tool].jsonResult(arguments) == json

        where:
        tool                 | arguments                                       || json
        'feature_find'       | [text: 'x']                                     || true
        'feature_show'       | [feature: 'x']                                  || true
        'plugin_list'        | [:]                                             || true
        'project_show'       | [:]                                             || true
        'project_health'     | [:]                                             || true
        'project_regenerate' | [:]                                             || true
        'feature_add'        | [feature: ['x'], 'dry-run': true]               || true
        'feature_add'        | [feature: ['x'], 'dry-run': true, diff: true]   || false
        'feature_add'        | [feature: ['x']]                                || false
        'feature_list'       | [:]                                             || false
        'feature_diff'       | [feature: ['x']]                                || false
        'compile'            | [:]                                             || false
        'audit-report'       | [output: 'r']                                   || false
        'refresh_tools'      | [:]                                             || false
    }

    void "an argument that begins with @ is given with a second one, and any other as it is"() {
        expect:
        ArgumentMapping.escaped('@file') == '@@file'
        ArgumentMapping.escaped('@@file') == '@@@file'
        ArgumentMapping.escaped('@') == '@@'
        ArgumentMapping.escaped('a@b') == 'a@b'
        ArgumentMapping.escaped('--since=@x') == '--since=@x'
    }
}
