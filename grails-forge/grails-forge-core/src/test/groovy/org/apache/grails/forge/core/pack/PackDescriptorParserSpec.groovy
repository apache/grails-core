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
package org.apache.grails.forge.core.pack

import org.grails.forge.application.ApplicationType
import spock.lang.Specification
import spock.lang.Timeout
import spock.lang.Unroll

class PackDescriptorParserSpec extends Specification {

    static final String COMPATIBILITY = "compatibility:\n  grails: '[9.0.0,10.0.0)'\n  forge: '[9.0.0,10.0.0)'\n"

    static final String MINIMAL = 'format: 1\nkind: feature\nname: acme-audit\ntemplateApi: 1\n' + COMPATIBILITY

    static final String DEPENDENCY = 'dependencies:\n  - scope: implementation\n    coordinates: '

    static final String FILE = 'files:\n  - from: a.gsp\n    to: '

    static final String PATH_RULE = "must be a path below the root with '/' between its at most 32 steps, where a step is not '.' or '..', " +
            "does not start or end with a space or end with '.', is not a name an operating system or Git reserves, " +
            'and has no character a file system gives a meaning, but is '

    static final String VERSION_RULE = "must be one version, of letters, digits, '.', '-', '+' and '_', but is "

    static final String PROPERTY_RULE = 'must be one line of at most 2000 printable ASCII characters, without a backslash ' +
            'and without a space at its start or end, but is '

    static final String LINE_RULE = 'must be one line of text of at most 200 characters that is not blank and has no control characters, but is '

    PackDescriptorParser parser = new PackDescriptorParser()

    void "a descriptor with only what is required gets the defaults"() {
        when:
        PackDescriptor descriptor = parser.parse(MINIMAL, 'acme.yml')

        then:
        descriptor.name == 'acme-audit'
        descriptor.title == 'acme-audit'
        descriptor.description == 'acme-audit'
        descriptor.category == 'Other'
        descriptor.documentation == null
        descriptor.grailsCompatibility == '[9.0.0,10.0.0)'
        descriptor.forgeCompatibility == '[9.0.0,10.0.0)'
        descriptor.applicationTypes == ApplicationType.values() as Set
        descriptor.requires == []
        descriptor.conflicts == []
        descriptor.oneOf == null
        descriptor.dependencies == []
        descriptor.platforms == []
        descriptor.buildPlugins == []
        descriptor.buildProperties == [:]
        descriptor.buildExtensions == [:]
        descriptor.configuration == [:]
        descriptor.files == []
        descriptor.readme == null
        descriptor.instructions == null
    }

    void "every key of a descriptor is read"() {
        when:
        PackDescriptor descriptor = parser.parse(MINIMAL + '''\
            title: Acme Audit Trail
            description: Adds the Acme audit plugin and its default configuration
            category: Logging
            documentation: https://acme.example/docs/grails
            applicationTypes: [web, rest-api]
            requires: [gorm-hibernate7, 'com.acme:acme-base#acme-base']
            conflicts: [gorm-mongodb]
            oneOf: com.acme:audit
            dependencies:
              - scope: implementation
                coordinates: com.acme:acme-audit-grails
                version: 2.3.0
              - scope: runtimeOnly
                coordinates: com.acme:acme-audit-h2
                excludes: ['org.slf4j:slf4j-simple', commons-logging]
              - scope: testImplementation
                coordinates: com.acme:acme-audit-h2
            platforms:
              - coordinates: com.acme:acme-bom
                version: 2.3.0-RC1
            buildPlugins:
              - id: com.acme.audit
                version: '2.3'
              - id: idea
            build:
              properties:
                acme.audit.verbose: false
                acme.audit.retention: 90
                acme.audit.label: 'Audit trail'
              extensions:
                acmeAudit: |
                  retention = 90
                tasks.bootRun: systemProperty 'acme.audit.console', 'true'
            configuration:
              acme.audit.enabled: true
              acme.audit.ratio: 1.5
              environments:
                production:
                  acme.audit.flush: 30s
                  acme.audit.sinks: [file, table]
            files:
              - from: AuditConfig.groovy.gsp
                to: 'grails-app/conf/{packagePath}/{className}AuditConfig.groovy'
              - from: css/audit.css
                to: grails-app/assets/stylesheets/audit.css
              - from: gitignore
                to: .github/workflows/.gitignore
            readme: |
              Audit events are written to the `audit_event` table.
            instructions: Run `./gradlew dbmGormDiff`.
            '''.stripIndent(), 'acme.yml')

        then:
        descriptor.title == 'Acme Audit Trail'
        descriptor.description == 'Adds the Acme audit plugin and its default configuration'
        descriptor.category == 'Logging'
        descriptor.documentation == 'https://acme.example/docs/grails'
        descriptor.applicationTypes == [ApplicationType.WEB, ApplicationType.REST_API] as Set
        descriptor.requires == ['gorm-hibernate7', 'com.acme:acme-base#acme-base']
        descriptor.conflicts == ['gorm-mongodb']
        descriptor.oneOf == 'com.acme:audit'

        and:
        descriptor.dependencies.collect { [it.scope, it.groupId, it.artifactId, it.version, it.excludes] } == [
                ['implementation', 'com.acme', 'acme-audit-grails', '2.3.0', []],
                ['runtimeOnly', 'com.acme', 'acme-audit-h2', null, ['org.slf4j:slf4j-simple', 'commons-logging']],
                ['testImplementation', 'com.acme', 'acme-audit-h2', null, []],
        ]
        descriptor.dependencies.every { PackDescriptorParser.SCOPES.containsKey(it.scope) }
        descriptor.platforms.collect { [it.scope, it.groupId, it.artifactId, it.version] } == [[null, 'com.acme', 'acme-bom', '2.3.0-RC1']]
        descriptor.buildPlugins.collect { [it.id, it.version] } == [['com.acme.audit', '2.3'], ['idea', null]]

        and:
        descriptor.buildProperties == ['acme.audit.verbose': 'false', 'acme.audit.retention': '90', 'acme.audit.label': 'Audit trail']
        descriptor.buildExtensions == [acmeAudit: 'retention = 90\n', 'tasks.bootRun': "systemProperty 'acme.audit.console', 'true'"]
        descriptor.configuration == ['acme.audit.enabled': true, 'acme.audit.ratio': 1.5d,
                                     environments: [production: ['acme.audit.flush': '30s', 'acme.audit.sinks': ['file', 'table']]]]
        descriptor.files.collect { [it.from, it.to] } == [
                ['AuditConfig.groovy.gsp', 'grails-app/conf/{packagePath}/{className}AuditConfig.groovy'],
                ['css/audit.css', 'grails-app/assets/stylesheets/audit.css'],
                ['gitignore', '.github/workflows/.gitignore'],
        ]
        descriptor.readme == 'Audit events are written to the `audit_event` table.\n'
        descriptor.instructions == 'Run `./gradlew dbmGormDiff`.'
    }

    void "descriptors read from the same text are equal and say what they are"() {
        given:
        String text = MINIMAL + DEPENDENCY + 'com.acme:a\nconfiguration:\n  a: [1, 2]\n'

        when:
        PackDescriptor first = parser.parse(text, 'one.yml')
        PackDescriptor second = parser.parse(text, 'two.yml')

        then:
        first == second
        first.hashCode() == second.hashCode()
        first != parser.parse(text.replace('com.acme:a', 'com.acme:b'), 'three.yml')
        first.toString().contains('name:acme-audit')
        first.dependencies.first().toString() == 'PackDescriptor$PackDependency(scope:implementation, groupId:com.acme, artifactId:a, version:null, excludes:[], versionOfForge:false, when:always)'
    }

    @Unroll
    void "the #what of a descriptor cannot be changed once it is read"() {
        given:
        PackDescriptor descriptor = parser.parse(MINIMAL + DEPENDENCY + '''com.acme:a
    excludes: [org.slf4j]
requires: [gsp]
files:
  - from: a
    to: a
configuration:
  nested:
    list: [1, 2]
''', 'acme.yml')

        when:
        change(descriptor)

        then:
        thrown(UnsupportedOperationException)

        where:
        what                          | change
        'requirements'                | { PackDescriptor it -> it.requires << 'other' }
        'application types'           | { PackDescriptor it -> it.applicationTypes.clear() }
        'configuration'               | { PackDescriptor it -> it.configuration.put('a', 'b') }
        'nested configuration'        | { PackDescriptor it -> ((Map) it.configuration.nested).put('a', 'b') }
        'lists of the configuration'  | { PackDescriptor it -> ((List) ((Map) it.configuration.nested).list) << 3 }
        'build properties'            | { PackDescriptor it -> it.buildProperties.put('a', 'b') }
        'dependencies'                | { PackDescriptor it -> it.dependencies.clear() }
        'exclusions of a dependency'  | { PackDescriptor it -> it.dependencies.first().excludes << 'x' }
        'files'                       | { PackDescriptor it -> it.files.clear() }
    }

    @Unroll
    void "a descriptor is rejected when #problem"() {
        when:
        parser.parse(replaced ? MINIMAL.replace(replaced, with) : MINIMAL + with, 'acme.yml')

        then:
        PackDescriptorException e = thrown()
        e.problems == [message]
        e.source == 'acme.yml'
        e.message == 'The feature pack descriptor acme.yml is not valid:\n  - ' + message
        !(message =~ /[\p{Cc}\p{Cf}\p{Cs}\p{Zl}\p{Zp}]/)

        where:
        problem                                    | replaced              | with                                                           || message
        'the format is missing'                    | 'format: 1\n'         | 'title: T\n'                                                   || 'format: is required, and 1 is the one this version of Forge reads'
        'the format is a later one'                | 'format: 1'           | 'format: 2'                                                    || 'format: must be the number 1, the one this version of Forge reads, but is the number 2'
        'the format is text'                       | 'format: 1'           | "format: '1'"                                                  || "format: must be the number 1, the one this version of Forge reads, but is '1'"
        'the format is a fraction'                 | 'format: 1'           | 'format: 1.0'                                                  || 'format: must be the number 1, the one this version of Forge reads, but is the number 1.0'
        'the format is a list'                     | 'format: 1'           | 'format: [1]'                                                  || 'format: must be the number 1, the one this version of Forge reads, but is a list'
        'the template API is missing'              | 'templateApi: 1\n'    | 'title: T\n'                                                   || 'templateApi: is required, and this version of Forge renders the template APIs [1]'
        'the template API is a later one'          | 'templateApi: 1'      | 'templateApi: 2'                                               || 'templateApi: must be one of the template APIs this version of Forge renders, [1], but is the number 2'
        'the template API is an earlier one'       | 'templateApi: 1'      | 'templateApi: 0'                                               || 'templateApi: must be one of the template APIs this version of Forge renders, [1], but is the number 0'
        'the template API is text'                 | 'templateApi: 1'      | "templateApi: '1'"                                             || "templateApi: must be one of the template APIs this version of Forge renders, [1], but is '1'"
        'the template API is a fraction'           | 'templateApi: 1'      | 'templateApi: 1.0'                                             || 'templateApi: must be one of the template APIs this version of Forge renders, [1], but is the number 1.0'
        'the kind is missing'                      | 'kind: feature\n'     | 'title: T\n'                                                   || 'kind: is required'
        'the kind is neither feature nor preset'   | 'kind: feature'       | 'kind: profile'                                                || "kind: must be 'feature' or 'preset', the kinds this version of Forge reads, but is 'profile'"
        'the name is missing'                      | 'name: acme-audit\n'  | 'title: T\n'                                                   || 'name: is required'
        'the name is empty'                        | 'name: acme-audit'    | 'name: ~'                                                      || 'name: is required'
        'the name has upper case'                  | 'name: acme-audit'    | 'name: AcmeAudit'                                              || "name: must be lower case letters and digits in words joined by hyphens, starting with a letter, but is 'AcmeAudit'"
        'the name has coordinates'                 | 'name: acme-audit'    | "name: 'com.acme:audit'"                                       || "name: must be lower case letters and digits in words joined by hyphens, starting with a letter, but is 'com.acme:audit'"
        'the name is read as true'                 | 'name: acme-audit'    | 'name: true'                                                   || 'name: must be text, but is the value true; put it in quotes'
        'the compatibility is missing'             | COMPATIBILITY         | 'title: T\n'                                                   || 'compatibility: is required'
        'the compatibility is empty'               | COMPATIBILITY         | 'compatibility: ~\n'                                           || 'compatibility: is required'
        'the compatibility is a list'              | COMPATIBILITY         | 'compatibility: []\n'                                          || 'compatibility: must be a mapping of keys to values, but is a list'
        'the Forge compatibility is missing'       | "  forge: '[9.0.0,10.0.0)'\n" | 'title: T\n'                                           || 'compatibility.forge: is required'
        'the Grails compatibility is missing'      | "  grails: '[9.0.0,10.0.0)'\n" | ''                                                    || 'compatibility.grails: is required'
        'the compatibility has another key'        | "  forge: '[9.0.0,10.0.0)'\n" | "  forge: '[9.0.0,10.0.0)'\n  java: '21'\n"            || "compatibility: has the key 'java', which this version of Forge does not read here; the keys are [grails, forge]"
        'a compatibility is not a range'           | "grails: '[9.0.0,10.0.0)'" | "grails: '9.0\"; x'"                                      || "compatibility.grails: must be a version or a range of versions, such as '[9.0.0,10.0.0)', but is '9.0\"; x'"
        'a compatibility is read as a number'      | "grails: '[9.0.0,10.0.0)'" | 'grails: 9.0'                                             || 'compatibility.grails: must be text, but is the number 9.0; put it in quotes'
        'an application type is unknown'           | null                  | 'applicationTypes: [web, rest_api]\n'                          || "applicationTypes[1]: must be among [plugin, rest-api, rest-api-plugin, web, web-plugin], but is 'rest_api'"
        'an application type is a number'          | null                  | 'applicationTypes: [1]\n'                                      || 'applicationTypes[0]: must be text, but is the number 1; put it in quotes'
        'no application type is left'              | null                  | 'applicationTypes: []\n'                                       || 'applicationTypes: must name at least one application type, or be left out for all of them'
        'the application types are empty'          | null                  | 'applicationTypes: ~\n'                                        || 'applicationTypes: must name at least one application type, or be left out for all of them'
        'the requirements are not a list'          | null                  | 'requires: gsp\n'                                              || "requires: must be a list, but is 'gsp'"
        'a requirement is listed twice'            | null                  | 'requires: [gsp, gsp]\n'                                       || "requires[1]: 'gsp' is listed earlier too"
        'a requirement is empty'                   | null                  | 'requires: [~]\n'                                              || 'requires[0]: must be text, but is empty'
        'a requirement is not the id of a feature' | null                  | "requires: ['../x']\n"                                         || "requires[0]: must be the name of a feature, or 'group:artifact#name' for the feature of another pack, but is '../x'"
        'a requirement is a choice of a group'     | null                  | 'requires:\n  - gsp\n  - oneOf: { group: database-driver }\n'  || 'requires[1]: is a mapping; this version of Forge reads only the names of features here'
        'the feature requires itself'              | null                  | 'requires: [acme-audit]\n'                                     || "requires[0]: must not name the feature itself, 'acme-audit'"
        'the feature conflicts with itself'        | null                  | 'conflicts: [gsp, acme-audit]\n'                               || "conflicts[1]: must not name the feature itself, 'acme-audit'"
        'the feature requires itself by full name' | null                  | "requires: ['com.acme:acme-audit-grails#acme-audit']\n"        || "requires[0]: must not name the feature itself, 'acme-audit'"
        'it conflicts with itself by full name'    | null                  | "conflicts: ['com.acme:other#acme-audit']\n"                   || "conflicts[0]: must not name the feature itself, 'acme-audit'"
        'a requirement is also a conflict'         | null                  | 'requires: [gsp]\nconflicts: [gsp]\n'                          || "conflicts: 'gsp' is also required"
        'the group is not an identifier'           | null                  | "oneOf: 'a b'\n"                                               || "oneOf: must be the identifier of a group, such as 'database-driver' or 'com.acme:audit', but is 'a b'"
        'the dependencies are a mapping'           | null                  | 'dependencies: {}\n'                                           || 'dependencies: must be a list of mappings, but is a mapping'
        'a dependency has no scope'                | null                  | 'dependencies:\n  - coordinates: com.acme:a\n'                 || 'dependencies[0].scope: is required'
        'a dependency has an unknown scope'        | null                  | 'dependencies:\n  - scope: api\n    coordinates: com.acme:a\n' || "dependencies[0].scope: must be one of [implementation, compileOnly, runtimeOnly, developmentOnly, testImplementation, testCompileOnly, testRuntimeOnly], but is 'api'"
        'coordinates include a version'            | null                  | DEPENDENCY + 'com.acme:a:1.0\n'                                || "dependencies[0].coordinates: must be 'group:artifact', with the version given separately, but is 'com.acme:a:1.0'"
        'coordinates end a Groovy string'          | null                  | DEPENDENCY + "'com.acme:a\"'\n"                                || "dependencies[0].coordinates: must be 'group:artifact', with the version given separately, but is 'com.acme:a\"'"
        'coordinates hold a Groovy expression'     | null                  | DEPENDENCY + "'com.acme:\${x}'\n"                              || "dependencies[0].coordinates: must be 'group:artifact', with the version given separately, but is 'com.acme:\${x}'"
        'coordinates are steps of a path'          | null                  | DEPENDENCY + "'..:..'\n"                                       || "dependencies[0].coordinates: must be 'group:artifact', with the version given separately, but is '..:..'"
        'a dependency is declared twice'           | null                  | DEPENDENCY + 'com.acme:a\n  - scope: implementation\n    coordinates: com.acme:a\n    version: 2.0.0\n' || "dependencies[1].coordinates: 'com.acme:a' is declared by an earlier entry too"
        'an exclusion has a version'               | null                  | DEPENDENCY + "com.acme:a\n    excludes: ['a:b:1']\n"           || "dependencies[0].excludes[0]: must be 'group' or 'group:artifact', but is 'a:b:1'"
        'an exclusion has an empty artifact'       | null                  | DEPENDENCY + "com.acme:a\n    excludes: ['a:']\n"              || "dependencies[0].excludes[0]: must be 'group' or 'group:artifact', but is 'a:'"
        'a version ends a Groovy string'           | null                  | DEPENDENCY + "com.acme:a\n    version: '1.0\"); println(\"x'\n" || "dependencies[0].version: ${VERSION_RULE}'1.0\"); println(\"x'"
        'a version holds a Groovy expression'      | null                  | DEPENDENCY + "com.acme:a\n    version: '\${pack.version}'\n"   || "dependencies[0].version: ${VERSION_RULE}'\${pack.version}'"
        'a version has a second line'              | null                  | DEPENDENCY + 'com.acme:a\n    version: "1.0\\nexec { }"\n'      || "dependencies[0].version: ${LINE_RULE}'1.0?exec { }'"
        'a version is read as a number'            | null                  | DEPENDENCY + 'com.acme:a\n    version: 1.10\n'                 || 'dependencies[0].version: must be text, but is the number 1.1; put it in quotes'
        'a platform has a scope'                   | null                  | 'platforms:\n  - scope: implementation\n    coordinates: com.acme:bom\n' || "platforms[0]: has the key 'scope', which this version of Forge does not read here; the keys are [coordinates, version]"
        'a platform has a version that is code'    | null                  | "platforms:\n  - coordinates: com.acme:bom\n    version: '1\"'\n" || "platforms[0].version: ${VERSION_RULE}'1\"'"
        'a build plugin has no id'                 | null                  | "buildPlugins:\n  - version: '1.0'\n"                          || 'buildPlugins[0].id: is required'
        'a build plugin id ends a Groovy string'   | null                  | "buildPlugins:\n  - id: 'x\"; println 1; \"'\n"                || "buildPlugins[0].id: must be the id of a Gradle plugin, such as 'com.acme.audit', but is 'x\"; println 1; \"'"
        'a build plugin version is code'           | null                  | "buildPlugins:\n  - id: idea\n    version: '1\" apply false; \"'\n" || "buildPlugins[0].version: ${VERSION_RULE}'1\" apply false; \"'"
        'a build plugin is applied twice'          | null                  | 'buildPlugins:\n  - id: idea\n  - id: idea\n'                  || "buildPlugins[1].id: 'idea' is declared by an earlier entry too"
        'the build is a list'                      | null                  | 'build: []\n'                                                  || 'build: must be a mapping of keys to values, but is a list'
        'the build has another key'                | null                  | 'build:\n  blocks: []\n'                                       || "build: has the key 'blocks', which this version of Forge does not read here; the keys are [properties, extensions]"
        'a build property name sets a second one'  | null                  | "build:\n  properties:\n    'a=b': c\n"                        || "build.properties: the name of a property is letters, digits, '.', '-' and '_', but one is 'a=b'"
        'a build property has a second line'       | null                  | 'build:\n  properties:\n    a: "1\\norg.gradle.jvmargs=-javaagent:x"\n' || "build.properties.a: ${PROPERTY_RULE}'1?org.gradle.jvmargs=-javaagent:x'"
        'a build property is read as a fraction'   | null                  | 'build:\n  properties:\n    a: 1.10\n'                         || 'build.properties.a: must be text, true or false, or a whole number, but is the number 1.1; put any other value in quotes'
        'a build property is a list'               | null                  | 'build:\n  properties:\n    a: [1]\n'                          || 'build.properties.a: must be text, true or false, or a whole number, but is a list; put any other value in quotes'
        'a build extension name is code'           | null                  | "build:\n  extensions:\n    'x }; exec { }; y': body\n"        || "build.extensions: the name of an extension is one or more identifiers joined by '.', but one is 'x }; exec { }; y'"
        'a build extension is a mapping'           | null                  | 'build:\n  extensions:\n    acme:\n      a: b\n'               || 'build.extensions.acme: must be text, but is a mapping'
        'the documentation is not a web address'   | null                  | "documentation: 'javascript:alert(1)'\n"                       || "documentation: must be an http or https address, without spaces, quotes, '<', '>' or '\$', but is 'javascript:alert(1)'"
        'the title is blank'                       | null                  | "title: ' '\n"                                                 || "title: ${LINE_RULE}' '"
        'the title would act on a terminal'        | null                  | 'title: "a\\e[2Jb"\n'                                           || "title: ${LINE_RULE}'a?[2Jb'"
        'the title is read as a number'            | null                  | 'title: 9\n'                                                   || 'title: must be text, but is the number 9; put it in quotes'
        'a nested key is not text'                 | null                  | 'configuration:\n  nested:\n    1: a\n'                        || "It has the key '1' without quotes, and a key that is a number, true, false or empty is put in quotes"
        'a key is empty'                           | null                  | 'build:\n  properties:\n    ~: a\n'                            || "It has the key '~' without quotes, and a key that is a number, true, false or empty is put in quotes"
        'a file has no destination'                | null                  | 'files:\n  - from: a.gsp\n'                                    || 'files[0].to: is required'
        'a destination is an unquoted placeholder' | null                  | FILE + '{packagePath}\n'                                       || "files[0].to: must be text, but is a mapping; put a path that starts with '{' in quotes"
        'a destination has an unknown placeholder' | null                  | FILE + "'src/{userHome}/a'\n"                                  || "files[0].to: can use the placeholders [packageName, packagePath, className, naturalName, propertyName, name], but has '{userHome}'"
        'a destination has half a placeholder'     | null                  | FILE + "'src/{name'\n"                                         || "files[0].to: ${PATH_RULE}'src/{name'"
        'a file is written above the application'  | null                  | FILE + '../a\n'                                                || "files[0].to: ${PATH_RULE}'../a'"
        'a file climbs in the middle'              | null                  | FILE + 'a/../../b\n'                                           || "files[0].to: ${PATH_RULE}'a/../../b'"
        'a file is written to an absolute path'    | null                  | FILE + '/etc/a\n'                                              || "files[0].to: ${PATH_RULE}'/etc/a'"
        'a file is written through a drive'        | null                  | FILE + "'C:/a'\n"                                              || "files[0].to: ${PATH_RULE}'C:/a'"
        'a file is written to a data stream'       | null                  | FILE + "'a.txt:hidden'\n"                                      || "files[0].to: ${PATH_RULE}'a.txt:hidden'"
        'a path has a backslash'                   | null                  | FILE + "'a\\b'\n"                                              || "files[0].to: ${PATH_RULE}'a\\b'"
        'a path has an empty step'                 | null                  | FILE + 'a//b\n'                                                || "files[0].to: ${PATH_RULE}'a//b'"
        'a path ends with a separator'             | null                  | FILE + 'a/\n'                                                  || "files[0].to: ${PATH_RULE}'a/'"
        'a path starts in the current directory'   | null                  | FILE + './a\n'                                                 || "files[0].to: ${PATH_RULE}'./a'"
        'a step is two dots and a space'           | null                  | FILE + "'.. /x'\n"                                             || "files[0].to: ${PATH_RULE}'.. /x'"
        'a step ends with a dot'                   | null                  | FILE + "'a./b'\n"                                              || "files[0].to: ${PATH_RULE}'a./b'"
        'a step is the name of a device'           | null                  | FILE + 'a/NUL.txt\n'                                           || "files[0].to: ${PATH_RULE}'a/NUL.txt'"
        'a path starts in a home directory'        | null                  | FILE + "'~root/a'\n"                                           || "files[0].to: ${PATH_RULE}'~root/a'"
        'a path has a control character'           | null                  | FILE + '"a\\tb"\n'                                             || "files[0].to: must be one line of text of at most 2000 characters that is not blank and has no control characters, but is 'a?b'"
        'a path has a wildcard'                    | null                  | FILE + "'a*'\n"                                                || "files[0].to: ${PATH_RULE}'a*'"
        'a file is read from above the pack'       | null                  | 'files:\n  - from: ../../secret\n    to: a\n'                  || "files[0].from: ${PATH_RULE}'../../secret'"
        'the source of a file has a placeholder'   | null                  | "files:\n  - from: '{name}.gsp'\n    to: a\n"                  || "files[0].from: ${PATH_RULE}'{name}.gsp'"
        'two files have one destination'           | null                  | 'files:\n  - from: a.gsp\n    to: src/A.txt\n  - from: b.gsp\n    to: src/a.txt\n' || "files[1].to: 'src/a.txt' is the destination of an earlier file too, at least where the case of letters does not count"
        'the readme has a control character'       | null                  | 'readme: "a\\u0007b"\n'                                         || "readme: must be text of at most 20000 characters that is not blank and has no control characters, but is 'a?b'"
        'a value has markup of the command line'   | null                  | "build:\n  properties:\n    a: '@|bogus x|@'\n"              || "build.properties.a: has '@|' in it, which the command line reads as the start of markup"
        'a key has markup of the command line'     | null                  | "configuration:\n  'a@|red b|@': 1\n"                          || "configuration: has a key with '@|' in it, which the command line reads as the start of markup"
        'the environments are not a mapping'       | null                  | 'configuration:\n  environments: prod\n'                      || "configuration.environments: must be a mapping of the names of environments to their settings, but is 'prod'"
        'an environment has no settings'           | null                  | 'configuration:\n  environments:\n    production: 5\n'       || 'configuration.environments.production: must be a mapping of settings, but is the number 5'
        'an environment is named like a path'      | null                  | "configuration:\n  environments:\n    'x/../../y':\n      a: 1\n" || "configuration.environments: the name of an environment is letters, digits, '-' and '_', starting with a letter, but one is 'x/../../y'"
        'a property value ends with a backslash'   | null                  | "build:\n  properties:\n    a: 'trailing\\'\n"                || "build.properties.a: ${PROPERTY_RULE}'trailing\\'"
        'a property value has an escape'           | null                  | "build:\n  properties:\n    a: 'a\\u000ab=c'\n"              || "build.properties.a: ${PROPERTY_RULE}'a\\u000ab=c'"
        'a property value starts with a space'     | null                  | "build:\n  properties:\n    a: ' lead'\n"                     || "build.properties.a: ${PROPERTY_RULE}' lead'"
        'a property value is not ASCII'            | null                  | "build:\n  properties:\n    a: 'caf\u00E9'\n"                 || "build.properties.a: ${PROPERTY_RULE}'caf\u00E9'"
        'an extension body is empty'               | null                  | 'build:\n  extensions:\n    acme: ~\n'                        || 'build.extensions.acme: must be text, but is empty'
        'the title has a C1 control character'     | null                  | 'title: "a\\x9B2Jb"\n'                                         || "title: ${LINE_RULE}'a?2Jb'"
        'the title has a line separator'           | null                  | 'title: "a\\Lb"\n'                                             || "title: ${LINE_RULE}'a?b'"
        'the title has half a surrogate pair'      | null                  | 'title: "a\\uD800b"\n'                                         || "title: ${LINE_RULE}'a?b'"
        'the title reorders its letters'           | null                  | 'title: "a\\u202Eb"\n'                                         || "title: ${LINE_RULE}'a?b'"
        'a path has a next line character'         | null                  | FILE + '"a\\Nb"\n'                                             || "files[0].to: must be one line of text of at most 2000 characters that is not blank and has no control characters, but is 'a?b'"
        'a path has a zero width character'        | null                  | FILE + '"a\\u200Db"\n'                                         || "files[0].to: ${PATH_RULE}'a?b'"
        'a step is a device with a space'          | null                  | FILE + "'aux .txt'\n"                                         || "files[0].to: ${PATH_RULE}'aux .txt'"
        'a step is the console input'              | null                  | FILE + "'a/CONIN\$'\n"                                        || "files[0].to: ${PATH_RULE}'a/CONIN\$'"
        'a step is the directory of Git'           | null                  | FILE + '.git/hooks/pre-commit\n'                              || "files[0].to: ${PATH_RULE}'.git/hooks/pre-commit'"
        'a file is also a directory'               | null                  | 'files:\n  - from: a\n    to: src/a\n  - from: b\n    to: SRC/A/b\n' || "files[1].to: 'SRC/A/b' and the earlier destination 'src/a' make one path both a file and a directory"
        'two destinations differ by a long s'      | null                  | "files:\n  - from: a\n    to: 'src/a'\n  - from: b\n    to: '\u017Frc/a'\n" || "files[1].to: '\u017Frc/a' is the destination of an earlier file too, at least where the case of letters does not count"
        'the compatibility uses an operator'       | "grails: '[9.0.0,10.0.0)'" | "grails: '>=9.0.0'"                                      || "compatibility.grails: must be a version or a range of versions, such as '[9.0.0,10.0.0)', but is '>=9.0.0'"
        'a dependency version is a range'          | null                  | DEPENDENCY + "com.acme:a\n    version: '[1.0,2.0)'\n"         || "dependencies[0].version: ${VERSION_RULE}'[1.0,2.0)'"
        'the documentation has markup'             | null                  | "documentation: 'https://a/\"><script>'\n"                    || "documentation: must be an http or https address, without spaces, quotes, '<', '>' or '\$', but is 'https://a/\"><script>'"
    }

    void "a key Forge does not read is rejected with the keys it does"() {
        when:
        parser.parse(MINIMAL + 'artefacts: []\nwhen: {}\n', 'acme.yml')

        then:
        PackDescriptorException e = thrown()
        e.problems.size() == 2
        e.problems[0].startsWith("the descriptor: has the key 'artefacts', which this version of Forge does not read here; the keys are [format, kind, name, ")
        e.problems[1].startsWith("the descriptor: has the key 'when', ")
    }

    void "an unknown category is rejected with the categories there are"() {
        when:
        parser.parse(MINIMAL + 'category: Auditing\n', 'acme.yml')

        then:
        PackDescriptorException e = thrown()
        e.problems.size() == 1
        e.problems.first().startsWith('category: must be one of [AI, API, ')
        e.problems.first().endsWith(", but is 'Auditing'")
    }

    void "every problem of a descriptor is reported at once, each once"() {
        when:
        parser.parse('''\
            format: 3
            name: Acme
            applicationTypes: [1]
            dependencies:
              - scope: api
                coordinates: acme
              - ~
            files:
              - from: /etc/passwd
                to: ../../out
            '''.stripIndent(), 'com.acme:acme-audit:2.3.0')

        then:
        PackDescriptorException e = thrown()
        e.source == 'com.acme:acme-audit:2.3.0'
        e.problems*.takeWhile { it != ':' } == ['format', 'templateApi', 'kind', 'name', 'compatibility', 'applicationTypes[0]',
                                              'dependencies[1]', 'dependencies[0].scope', 'dependencies[0].coordinates',
                                              'files[0].from', 'files[0].to']
    }

    @Unroll
    void "text that is #what is rejected"() {
        when:
        parser.parse(text, 'acme.yml')

        then:
        PackDescriptorException e = thrown()
        e.problems.size() == 1
        e.problems.first().startsWith(message)
        e.problems.first().contains(detail)

        where:
        what                             | text                                                  || message                                           | detail
        'missing'                        | null                                                  || 'It is empty'                                     | ''
        'blank'                          | ' \n'                                                 || 'It is empty'                                     | ''
        'not YAML'                       | 'format: [1'                                          || 'It is not YAML this version of Forge can read: ' | 'flow sequence'
        'a list'                         | '- format: 1'                                         || 'It must be a mapping of keys to values'          | ''
        'YAML with a key twice'          | MINIMAL + 'name: other\n'                             || 'It is not YAML this version of Forge can read: ' | 'duplicate key name'
        'YAML with a nested key twice'   | MINIMAL + 'build:\n  properties:\n    a: 1\n    a: 2\n' || 'It is not YAML this version of Forge can read: ' | 'duplicate key a'
        'YAML that names a Java class'   | MINIMAL + 'title: !!java.io.File /tmp\n'              || 'It gives a value the tag '                       | 'java.io.File'
        'YAML with a number that is not' | MINIMAL + 'configuration:\n  a: !!int foo\n'          || 'It gives a value the tag '                       | ':int'
        'YAML with binary that is not'   | MINIMAL + "configuration:\n  a: !!binary '@@@@'\n"    || 'It gives a value the tag '                       | ':binary'
        'two documents'                  | MINIMAL + '---\n' + MINIMAL                           || 'It is not YAML this version of Forge can read: ' | 'single document'
        'YAML that repeats a list'       | MINIMAL + 'configuration:\n  a: &a [x]\n  b: *a\n'    || 'It names a value with an anchor (&)'             | ''
        'YAML that contains itself'      | MINIMAL + 'configuration: &c\n  self: *c\n'           || 'It names a value with an anchor (&)'             | ''
        'YAML that merges a mapping'     | MINIMAL + 'build:\n  properties: &p\n    a: b\nconfiguration:\n  <<: *p\n' || 'It names a value with an anchor (&)'             | ''
        'nested too deeply'              | MINIMAL + 'configuration: ' + ('[' * 30) + (']' * 30) + '\n' || 'It has mappings and lists more than 20 deep'      | ''
        'too long'                       | MINIMAL + 'readme: ' + ('a' * 262144) + '\n'          || 'It has 262'                                      | 'characters, and a descriptor can have 262144'
    }

    void "a descriptor that doubles a list many times over is rejected at once and in a few words"() {
        given:
        String doubling = (1..24).collect { "  - &a${it} [*a${it - 1}, *a${it - 1}]" }.join('\n')

        when:
        parser.parse(MINIMAL.replace('format: 1\n', 'format:\n  - &a0 [x, x]\n' + doubling + '\n'), 'acme.yml')

        then:
        PackDescriptorException e = thrown()
        e.message.length() < 1000
    }

    void "what a message quotes of a descriptor is short"() {
        when:
        parser.parse(MINIMAL + 'oneOf: "' + ('a b ' * 1000) + '"\n', 'acme.yml')

        then:
        PackDescriptorException e = thrown()
        e.message.length() < 400
        e.problems.first().endsWith("a b a b a b a b a b a b a b a b a b a b a b a b a b a b a b a b a b a b a b a b ...'")
    }

    void "a problem names the place of its entry in a list, whatever came before it"() {
        when:
        parser.parse(MINIMAL + "requires: [gsp, gsp, '../x', 1, acme-audit]\n", 'acme.yml')

        then:
        PackDescriptorException e = thrown()
        e.problems*.takeWhile { it != ':' } == ['requires[1]', 'requires[3]', 'requires[2]', 'requires[4]']
    }

    void "a name and a mapping in one list are both checked"() {
        when:
        parser.parse(MINIMAL + "requires: ['../x', { oneOf: a }, gsp]\nconflicts: [gsp]\n", 'acme.yml')

        then:
        PackDescriptorException e = thrown()
        e.problems*.takeWhile { it != ':' } == ['requires[1]', 'requires[0]', 'conflicts']
    }

    void "a key that merges a mapping is a key like any other"() {
        when:
        PackDescriptor descriptor = parser.parse(MINIMAL + 'configuration:\n  <<: { a: 1 }\n', 'acme.yml')

        then:
        descriptor.configuration == ['<<': [a: 1]]

        when:
        parser.parse(MINIMAL + '<<: { title: T }\n', 'acme.yml')

        then:
        PackDescriptorException e = thrown()
        e.problems.size() == 1
        e.problems.first().startsWith("the descriptor: has the key '<<', which this version of Forge does not read here")
    }

    void "a value without quotes is text unless it is true, false, empty or a decimal number"() {
        when:
        PackDescriptor descriptor = parser.parse(MINIMAL + 'configuration:\n' +
                '  answer: yes\n  switch: on\n  octal: 010\n  hex: 0x10\n  grouped: 1_000\n  clock: 1:30\n' +
                '  date: 2024-01-01\n  exponent: 1e3\n  infinite: .inf\n  whole: 42\n  negative: -7\n' +
                '  large: 3000000000\n  fraction: 1.50\n  truth: true\n  nothing: ~\n' +
                'build:\n  properties:\n    octal: 010\n    clock: 1:30\n    answer: yes\n    large: 3000000000\n', 'acme.yml')

        then:
        descriptor.configuration == [answer: 'yes', switch: 'on', octal: '010', hex: '0x10', grouped: '1_000', clock: '1:30',
                                     date: '2024-01-01', exponent: '1e3', infinite: '.inf', whole: 42, negative: -7,
                                     large: 3000000000L, fraction: 1.5d, truth: true, nothing: null]
        descriptor.buildProperties == [octal: '010', clock: '1:30', answer: 'yes', large: '3000000000']
    }

    @Unroll
    void "the #what #value is accepted"() {
        when:
        PackDescriptor descriptor = parser.parse(MINIMAL + text, 'acme.yml')

        then:
        read(descriptor) == value

        where:
        what                    | text                                                             | read                                                         || value
        'version'               | DEPENDENCY + 'com.acme:a\n    version: 1.0.0-SNAPSHOT\n'         | { PackDescriptor it -> it.dependencies[0].version }          || '1.0.0-SNAPSHOT'
        'version'               | DEPENDENCY + 'com.acme:a\n    version: 2.3.0.RELEASE\n'          | { PackDescriptor it -> it.dependencies[0].version }          || '2.3.0.RELEASE'
        'version'               | DEPENDENCY + 'com.acme:a\n    version: 1.0+build.5\n'            | { PackDescriptor it -> it.dependencies[0].version }          || '1.0+build.5'
        'version'               | DEPENDENCY + "com.acme:a\n    version: '2024.01'\n"              | { PackDescriptor it -> it.dependencies[0].version }          || '2024.01'
        'artifact'              | DEPENDENCY + 'org.apache.grails:grails-data_core.x\n'            | { PackDescriptor it -> it.dependencies[0].artifactId }       || 'grails-data_core.x'
        'plugin id'             | 'buildPlugins:\n  - id: com.acme.my-plugin_2\n'                  | { PackDescriptor it -> it.buildPlugins[0].id }               || 'com.acme.my-plugin_2'
        'extension name'        | 'build:\n  extensions:\n    grails.plugins.acme: a = 1\n'        | { PackDescriptor it -> it.buildExtensions.keySet().first() } || 'grails.plugins.acme'
        'property value'        | "build:\n  properties:\n    a: '-Xmx1g -Dfile.encoding=UTF-8'\n" | { PackDescriptor it -> it.buildProperties.a }                || '-Xmx1g -Dfile.encoding=UTF-8'
        'group'                 | 'oneOf: database-driver\n'                                       | { PackDescriptor it -> it.oneOf }                            || 'database-driver'
        'documentation address' | "documentation: 'https://acme.example/docs/a%20b?x=1&y=2#top'\n" | { PackDescriptor it -> it.documentation }                    || 'https://acme.example/docs/a%20b?x=1&y=2#top'
        'path'                  | FILE + "'src/main/My File (1)+a@b#c \u00E9/.gitignore'\n"        | { PackDescriptor it -> it.files[0].to }                      || 'src/main/My File (1)+a@b#c \u00E9/.gitignore'
        'path'                  | FILE + "'{packagePath}'\n"                                       | { PackDescriptor it -> it.files[0].to }                      || '{packagePath}'
        'path'                  | FILE + 'a/..b/c...d\n'                                           | { PackDescriptor it -> it.files[0].to }                      || 'a/..b/c...d'
        'description'           | 'description: >\n  some text\n  and more\n'                      | { PackDescriptor it -> it.description }                      || 'some text and more'
        'title'                 | 'title: |\n  Acme Audit\n'                                       | { PackDescriptor it -> it.title }                            || 'Acme Audit'
        'title'                 | 'title: "pair \\U0001F468\\u200D\\U0001F469 \\u0633\\u200C\\u0647"\n' | { PackDescriptor it -> it.title }                       || 'pair \uD83D\uDC68\u200D\uD83D\uDC69 \u0633\u200C\u0647'
        'readme'                | 'readme: "one\\r\\n\\ttwo \\U0001F600\\r\\n"\n'                  | { PackDescriptor it -> it.readme }                           || 'one\n\ttwo \uD83D\uDE00\n'
    }

    void "a range of versions can have a space after its comma, and be open"() {
        expect:
        parser.parse(MINIMAL.replace("grails: '[9.0.0,10.0.0)'", "grails: '[9.0.0, )'"), 'acme.yml').grailsCompatibility == '[9.0.0, )'
    }

    void "a descriptor that starts with a byte order mark and ends its lines with a carriage return is read"() {
        expect:
        parser.parse('\uFEFF' + MINIMAL.replace('\n', '\r\n') + 'readme: |\r\n  one\r\n  two\r\n', 'acme.yml').readme == 'one\ntwo\n'
    }

    @Unroll
    void "a key of #parts dotted parts in the build extensions is refused without failing"() {
        when:
        parser.parse(MINIMAL + 'build:\n  extensions:\n    ? ' + (['a'] * parts).join('.') + '\n    : body\n', 'acme.yml')

        then:
        PackDescriptorException e = thrown()
        e.problems == ["build.extensions: has the key '" + ('a.' * 40) + "...', and a key is one line of at most 200 characters, none of them a control character"]

        where:
        parts << [150, 5000, 100000]
    }

    void "a long key above many values that cannot be used gives a short message"() {
        when:
        parser.parse(MINIMAL + 'configuration:\n  ? ' + ('k' * 100000) + '\n  : [' + (['x'] * 4000).join(', ') + ']\n', 'acme.yml')

        then:
        PackDescriptorException e = thrown()
        e.problems.size() == 1
        e.message.length() < 400
    }

    void "of many problems the first fifty are reported, with how many more there are"() {
        when:
        parser.parse(MINIMAL + 'files: [' + (['~'] * 30000).join(',') + ']\n', 'acme.yml')

        then:
        PackDescriptorException e = thrown()
        e.problems.size() == 51
        e.problems.first() == 'files[0]: must be a mapping of keys to values, but is empty'
        e.problems.last() == 'and 29950 more'
        e.message.length() < 5000
    }

    @Unroll
    void "a key with #what does not reach a message as it is"() {
        when:
        parser.parse(MINIMAL + 'configuration:\n  "' + key + '": ' + value + '\n', 'acme.yml')

        then:
        PackDescriptorException e = thrown()
        e.problems == [message]
        !(e.problems.first() =~ /[\p{Cc}\p{Cf}\p{Cs}\p{Zl}\p{Zp}]/)

        where:
        what                 | key                   | value                    || message
        'an escape sequence' | 'a\\e[2J\\u202Eb\\nc' | 'x'                      || "configuration: has the key 'a?[2J?b?c', and a key is one line of at most 200 characters, none of them a control character"
        'a C1 control'       | 'a\\x9B2Jb'           | '{ b: x }'               || "configuration: has the key 'a?2Jb', and a key is one line of at most 200 characters, none of them a control character"
        'nothing in it'      | ' '                   | 'x'                      || "configuration: has the key ' ', and a key is one line of at most 200 characters, none of them a control character"
    }

    void "what a message quotes is cut between characters, not inside one"() {
        when:
        parser.parse(MINIMAL + 'oneOf: "' + ('a' * 79) + '\\U0001F600 and more"\n', 'acme.yml')

        then:
        PackDescriptorException e = thrown()
        e.problems.first().endsWith("but is '" + ('a' * 79) + "\uD83D\uDE00...'")
    }

    void "what SnakeYAML says of a descriptor reaches a message without control characters"() {
        when:
        parser.parse(MINIMAL + '"a\\x9B2J\\e[2J": 1\n"a\\x9B2J\\e[2J": 2\n', 'acme.yml')

        then:
        PackDescriptorException e = thrown()
        e.problems.first().contains('duplicate key a?2J?[2J')
        !(e.problems.first() =~ /[\p{Cc}\p{Cf}\p{Cs}\p{Zl}\p{Zp}]/)
    }

    @Unroll
    void "a descriptor that #what is refused, whatever the tag or anchor is on"() {
        when:
        parser.parse(MINIMAL + text, 'acme.yml')

        then:
        PackDescriptorException e = thrown()
        e.problems == [message]

        where:
        what                             | text                                             || message
        'orders a mapping with a tag'    | 'configuration: !!omap [a: 1, a: 2]\n'           || "It gives a value the tag 'tag:yaml.org,2002:omap', and a descriptor cannot use tags"
        'merges a mapping with a tag'    | '!!merge m: { title: T }\n'                      || "It gives a value the tag 'tag:yaml.org,2002:merge', and a descriptor cannot use tags"
        'reads a number with a tag'      | 'configuration:\n  a: !!int 0x10\n'              || "It gives a value the tag 'tag:yaml.org,2002:int', and a descriptor cannot use tags"
        'reads a truth with a tag'       | 'title: !!bool foo\n'                            || "It gives a value the tag 'tag:yaml.org,2002:bool', and a descriptor cannot use tags"
        'reads a fraction with a tag'    | 'configuration:\n  a: !!float .nan\n'            || "It gives a value the tag 'tag:yaml.org,2002:float', and a descriptor cannot use tags"
        'tags text as text'              | 'title: !!str Acme\n'                            || "It gives a value the tag 'tag:yaml.org,2002:str', and a descriptor cannot use tags"
        'has a tag of its own'           | 'title: !acme Acme\n'                            || "It gives a value the tag '!acme', and a descriptor cannot use tags"
        'names a line of text'           | 'title: &t Acme\n'                               || 'It names a value with an anchor (&), which a descriptor cannot use'
        'names a key'                    | 'configuration:\n  &k a: 1\n'                    || 'It names a value with an anchor (&), which a descriptor cannot use'
    }

    @Timeout(60)
    void "a descriptor that repeats one long text thousands of times is refused at once"() {
        when:
        parser.parse(MINIMAL + 'x: &a ' + ('y' * 100000) + '\nrequires: [' + (['*a'] * 40000).join(',') + ']\n', 'acme.yml')

        then:
        PackDescriptorException e = thrown()
        e.problems == ['It names a value with an anchor (&), which a descriptor cannot use']
    }

    @Timeout(60)
    @Unroll
    void "a #field of #lines empty lines and one more is refused at once"() {
        when:
        parser.parse(MINIMAL + field + ': "a' + ('\\n' * lines) + 'x"\n', 'acme.yml')

        then:
        PackDescriptorException e = thrown()
        e.problems.size() == 1
        e.problems.first().startsWith(field + ': must be ')

        where:
        field         | lines
        'title'       | 80000
        'description' | 80000
        'readme'      | 80000
        'oneOf'       | 80000
    }

    @Timeout(60)
    void "as many build extensions as a descriptor has room for are read without multiplying them"() {
        given:
        String extensions = (1..2000).collect { "    e${it}: \"a = 1\\r\\nb = 2\"" }.join('\n')

        when:
        PackDescriptor descriptor = parser.parse(MINIMAL + 'build:\n  extensions:\n' + extensions + '\n', 'acme.yml')

        then:
        descriptor.buildExtensions.size() == 2000
        descriptor.buildExtensions.e1 == 'a = 1\nb = 2'
    }

    @Timeout(60)
    void "as many files as a descriptor has room for are checked against each other in time"() {
        given:
        String files = (1..9000).collect { "- {from: a, to: d${it}/f}" }.join('\n')

        when:
        PackDescriptor descriptor = parser.parse(MINIMAL + 'files:\n' + files + '\n', 'acme.yml')

        then:
        descriptor.files.size() == 9000
    }

    void "a number of more digits than a number keeps is text"() {
        when:
        PackDescriptor descriptor = parser.parse(MINIMAL + 'configuration:\n' +
                '  eighteen: 123456789012345678\n  nineteen: 1234567890123456789\n  huge: ' + ('9' * 1100) + '\n' +
                '  fraction: 0.12345678901234\n  precise: 0.1234567890123456789012345\n  vast: 1' + ('0' * 400) + '.0\n' +
                '  sixteen: 123456789.1234567\n  fifteen: 12345678.1234567\n  zero: -0\n  point: -0.5\n', 'acme.yml')

        then:
        descriptor.configuration == [eighteen: 123456789012345678L, nineteen: '1234567890123456789', huge: '9' * 1100,
                                     fraction: 0.12345678901234d, precise: '0.1234567890123456789012345',
                                     vast: '1' + ('0' * 400) + '.0', sixteen: '123456789.1234567', fifteen: 12345678.1234567d,
                                     zero: '-0', point: -0.5d]
    }

    void "descriptors with a whole number and with the same number as a fraction are different"() {
        given:
        PackDescriptor whole = parser.parse(MINIMAL + 'configuration: { a: 1, b: [1] }\n', 'acme.yml')
        PackDescriptor fraction = parser.parse(MINIMAL + 'configuration: { a: 1.0, b: [1.0] }\n', 'acme.yml')

        expect:
        whole != fraction
        [whole, fraction, parser.parse(MINIMAL + 'configuration: { b: [1], a: 1 }\n', 'other.yml')].toSet().size() == 2
    }

    void "the places of problems under long keys are short"() {
        given:
        String nested = (0..16).collect { ('  ' * it) + ('k' * 200) + ':' }.join('\n')
        String leaves = (1..60).collect { ('  ' * 17) + "\"l${it}\\e\": 1" }.join('\n')

        when:
        parser.parse(MINIMAL + 'configuration:\n  ' + nested.replace('\n', '\n  ') + '\n  ' + leaves.replace('\n', '\n  ') + '\n', 'acme.yml')

        then:
        PackDescriptorException e = thrown()
        e.problems.size() == 51
        e.problems.every { it.length() < 1100 }
        e.problems.first().startsWith('configuration.' + ('k' * 40) + '....' + ('k' * 40) + '...')
    }

    @Unroll
    void "the #what #value is accepted too"() {
        when:
        PackDescriptor descriptor = parser.parse(MINIMAL + text, 'acme.yml')

        then:
        read(descriptor) == value

        where:
        what                    | text                                                                    | read                                          || value
        'path'                  | FILE + 'src/backup~\n'                                                  | { PackDescriptor it -> it.files[0].to }       || 'src/backup~'
        'path'                  | FILE + 'com10/console.txt\n'                                            | { PackDescriptor it -> it.files[0].to }       || 'com10/console.txt'
        'path'                  | FILE + '.github/workflows/.gitattributes\n'                             | { PackDescriptor it -> it.files[0].to }       || '.github/workflows/.gitattributes'
        'property value'        | "build:\n  properties:\n    a: ''\n"                                    | { PackDescriptor it -> it.buildProperties.a } || ''
        'property value'        | "build:\n  properties:\n    a: 'k=v:#!\${x}'\n"                         | { PackDescriptor it -> it.buildProperties.a } || 'k=v:#!${x}'
        'documentation address' | "documentation: 'https://en.wikipedia.org/wiki/Grails_(framework)#!/a*'\n" | { PackDescriptor it -> it.documentation }  || 'https://en.wikipedia.org/wiki/Grails_(framework)#!/a*'
        'title'                 | 'title: "new \\U0001FAE9"\n'                                            | { PackDescriptor it -> it.title.length() }    || 6
    }

    @Unroll
    void "a descriptor is also rejected when #problem"() {
        when:
        parser.parse(MINIMAL + with, 'acme.yml')

        then:
        PackDescriptorException e = thrown()
        e.problems == [message]

        where:
        problem                                 | with                                             || message
        'a property value ends with a space'    | "build:\n  properties:\n    a: 'trail '\n"       || "build.properties.a: ${PROPERTY_RULE}'trail '"
        'a step is a short name'                | FILE + "'PROGRA~1/x'\n"                          || "files[0].to: ${PATH_RULE}'PROGRA~1/x'"
        'the title has characters never shown'  | 'title: "a\\U000E0041b"\n'                       || "title: ${LINE_RULE}'a?b'"
        'the documentation has a dollar'        | "documentation: 'https://a/\${x}'\n"             || "documentation: must be an http or https address, without spaces, quotes, '<', '>' or '\$', but is 'https://a/\${x}'"
    }

    void "where a descriptor came from is shown in few and safe characters"() {
        when:
        parser.parse('format: 2', 'a\u001B[2Jb\nc' + ('d' * 500))

        then:
        PackDescriptorException e = thrown()
        e.source == 'a?[2Jb?c' + ('d' * 192) + '...'
        e.message.startsWith('The feature pack descriptor a?[2Jb?c')

        when:
        parser.parse('format: 2', null)

        then:
        PackDescriptorException unknown = thrown()
        unknown.source == 'of an unknown origin'
    }

    @Timeout(60)
    @Unroll
    void "destinations of a thousand steps of #step are refused without being multiplied"() {
        given:
        String files = (1..120).collect { "- {from: f${it}, to: \"q${it}/" + ([step] * 990).join('/') + '"}' }.join('\n')

        when:
        parser.parse(MINIMAL + 'files:\n' + files + '\n', 'acme.yml')

        then:
        PackDescriptorException e = thrown()
        e.problems.size() == 51
        e.problems.first().startsWith("files[0].to: must be a path below the root with '/' between its at most 32 steps")

        where:
        step << ['a', '\u0390']
    }

    void "a destination can have thirty-two steps and no more"() {
        expect:
        parser.parse(MINIMAL + FILE + (['a'] * 32).join('/') + '\n', 'acme.yml').files[0].to.count('/') == 31

        when:
        parser.parse(MINIMAL + FILE + (['a'] * 33).join('/') + '\n', 'acme.yml')

        then:
        thrown(PackDescriptorException)
    }

    @Timeout(60)
    @Unroll
    void "a descriptor of nothing but #opener is refused at once"() {
        when:
        parser.parse(prefix + (opener * (200000 / opener.length()).intValue()), 'acme.yml')

        then:
        PackDescriptorException e = thrown()
        e.problems in [['It has mappings and lists more than 20 deep'], ['It has a mapping or a list as a key, and a key is text']]

        where:
        opener | prefix
        '['    | ''
        '{'    | ''
        '[{'   | ''
        '['    | MINIMAL + 'configuration: '
    }

    void "a descriptor can be twenty mappings and lists deep and no deeper"() {
        expect:
        parser.parse(MINIMAL + 'configuration:\n  a: ' + ('[' * 18) + (']' * 18) + '\n', 'acme.yml').configuration.a instanceof List

        when:
        parser.parse(MINIMAL + 'configuration:\n  a: ' + ('[' * 19) + (']' * 19) + '\n', 'acme.yml')

        then:
        PackDescriptorException e = thrown()
        e.problems == ['It has mappings and lists more than 20 deep']
    }

    @Unroll
    void "a descriptor is refused for the #what"() {
        when:
        parser.parse(text, 'acme.yml')

        then:
        PackDescriptorException e = thrown()
        e.problems.size() == 1
        e.problems.first().startsWith(message)

        where:
        what                           | text                                                      || message
        'tag on a list'                | MINIMAL + 'requires: !!seq [gsp]\n'                       || 'It gives a value the tag '
        'tag on the whole of it'       | '--- !!map\n' + MINIMAL                                   || 'It gives a value the tag '
        'tag that says nothing'        | MINIMAL + 'title: ! Acme\n'                               || 'It gives a value the tag '
        'tag written out'              | MINIMAL + 'title: !<tag:yaml.org,2002:str> Acme\n'        || 'It gives a value the tag '
        'tag with a handle of its own' | '%TAG !a! tag:acme.example,2026:\n---\n' + MINIMAL + 'title: !a!t Acme\n' || 'It gives a value the tag '
        'alias to nothing'             | MINIMAL + 'title: *t\n'                                   || 'It repeats a value with an alias (*)'
    }

    void "the characters of anchors, aliases and tags are only characters inside a value"() {
        when:
        PackDescriptor descriptor = parser.parse(MINIMAL + 'title: Q&A\ndescription: "*bold* and !loud"\n' +
                "documentation: 'https://acme.example/?a=1&b=2'\ninstructions: Run it!\n" +
                'readme: |\n  &anchor *alias !tag\nconfiguration:\n  a!: b&c\n  d*: [e*f, h!i]\n', 'acme.yml')

        then:
        descriptor.title == 'Q&A'
        descriptor.description == '*bold* and !loud'
        descriptor.documentation == 'https://acme.example/?a=1&b=2'
        descriptor.instructions == 'Run it!'
        descriptor.readme == '&anchor *alias !tag\n'
        descriptor.configuration == ['a!': 'b&c', 'd*': ['e*f', 'h!i']]
    }

    void "a build property with no value is an empty property, with quotes or without"() {
        expect:
        parser.parse(MINIMAL + "build:\n  properties:\n    a:\n    b: ~\n    c: ''\n", 'acme.yml').buildProperties == [a: '', b: '', c: '']
    }

    @Timeout(60)
    @Unroll
    void "thousands of #kind as keys, made to look alike to a hash table, are refused at once"() {
        given: 'texts of three characters that all have one hash code'
        List<String> alike = []
        int hash = 961 * 0x4E30 + 31 * 0x7000 + 0x7000
        for (int first = 0x4E00; first < 0x4E60 && alike.size() < 40000; first++) {
            for (int second = 0x4E00; second < 0x9F00 && alike.size() < 40000; second++) {
                int third = hash - (961 * first + 31 * second)
                if (third >= 0x4E00 && third <= 0x9FA0) {
                    alike << new String([first, second, third] as int[], 0, 3)
                }
            }
        }

        expect:
        alike.size() == 40000
        alike*.hashCode().toSet().size() == 1

        when:
        parser.parse(prefix + '{' + alike.collect { open + it + close }.join(',') + '}', 'acme.yml')

        then:
        PackDescriptorException e = thrown()
        e.problems == ['It has a mapping or a list as a key, and a key is text']

        where:
        kind       | open | close | prefix
        'lists'    | '['  | ']'   | ''
        'mappings' | '{'  | '}'   | ''
        'lists'    | '['  | ']'   | MINIMAL + 'configuration: '
    }

    @Unroll
    void "a key that is #what is refused while the descriptor is scanned"() {
        when:
        parser.parse(MINIMAL + text, 'acme.yml')

        then:
        PackDescriptorException e = thrown()
        e.problems == ['It has a mapping or a list as a key, and a key is text']

        where:
        what                 | text
        'a list'             | 'configuration:\n  ? [a, b]\n  : c\n'
        'a mapping'          | 'configuration:\n  ? { a: b }\n  : c\n'
        'a list in a flow'   | 'configuration: { [a]: b }\n'
        'a list, lower down' | 'configuration:\n  a:\n    - b: 1\n      ? [c]\n      : d\n'
    }

    void "mappings and lists as values, at every place, are not taken for keys"() {
        when:
        PackDescriptor descriptor = parser.parse(MINIMAL +
                'configuration:\n  a: [1, [2, { b: [3] }]]\n  c: { d: { e: [] }, f: {} }\n  g:\n    - h: [i]\n      j: k\n    - []\n  l: m\n', 'acme.yml')

        then:
        descriptor.configuration == [a: [1, [2, [b: [3]]]], c: [d: [e: []], f: [:]], g: [[h: ['i'], j: 'k'], []], l: 'm']
    }

    void "the words for an empty value are read as one, and other spellings of them are text"() {
        when:
        PackDescriptor descriptor = parser.parse(MINIMAL + 'title: null\nconfiguration:\n  a: null\n  b: ~\n  c:\n  d: Null\n  e: NULL\n', 'acme.yml')

        then:
        descriptor.title == 'acme-audit'
        descriptor.configuration == [a: null, b: null, c: null, d: 'Null', e: 'NULL']
    }

    void "a fraction keeps its value, and its sign when it is zero"() {
        when:
        Map<String, Object> configuration = parser.parse(MINIMAL +
                'configuration:\n  a: 12345678901234.5\n  b: -0.0\n  c: 0.0001\n  d: -1.23456789012345\n  e: -1.234567890123456\n  f: +1\n  g: 5.\n', 'acme.yml').configuration

        then:
        new BigDecimal(configuration.a.toString()) == 12345678901234.5
        configuration.b.toString() == '-0.0'
        new BigDecimal(configuration.c.toString()) == 0.0001
        configuration.d == -1.23456789012345d
        configuration.e == '-1.234567890123456'
        configuration.f == '+1'
        configuration.g == '5.'
    }

    @Timeout(60)
    void "destinations at every limit at once are accepted in time"() {
        given:
        String steps = (['\u0390' * 61] * 32).join('/')
        String files = (1..120).collect { "- {from: f${it}, to: \"q${it}${steps}\"}" }.join('\n')

        when:
        PackDescriptor descriptor = parser.parse(MINIMAL + 'files:\n' + files + '\n', 'acme.yml')

        then:
        descriptor.files.size() == 120
        descriptor.files.every { it.to.count('/') == 31 }
    }

    @Timeout(60)
    @Unroll
    void "thousands of text and number keys, made to look alike to a hash table, are refused at once #where"() {
        given: 'texts of three characters and whole numbers that all have one hash code'
        int hash = 961 * 0x4E30 + 31 * 0x7000 + 0x7000
        List<String> texts = []
        for (int first = 0x4E00; first < 0x4E60 && texts.size() < 40000; first++) {
            for (int second = 0x4E00; second < 0x9F00 && texts.size() < 40000; second++) {
                int third = hash - (961 * first + 31 * second)
                if (third >= 0x4E00 && third <= 0x9FA0) {
                    texts << new String([first, second, third] as int[], 0, 3)
                }
            }
        }
        List<String> numbers = (1L..6000L).collect { long high -> String.valueOf((high << 32) | (high ^ hash)) }
        Collections.shuffle(texts, new Random(7))

        expect:
        (texts*.hashCode() + numbers.collect { Long.valueOf(it).hashCode() }).toSet() == [hash] as Set

        when:
        parser.parse(prefix + '{' + (texts + numbers).join(',') + '}', 'acme.yml')

        then:
        PackDescriptorException e = thrown()
        e.problems == ["It has the key '${numbers.first()}' without quotes, and a key that is a number, true, false or empty is put in quotes".toString()]

        where:
        where                    | prefix
        'at the top'             | ''
        'in the configuration'   | MINIMAL + 'configuration: '
    }

    @Timeout(60)
    void "thousands of text keys that look alike to a hash table are read in time"() {
        given:
        int hash = 961 * 0x4E30 + 31 * 0x7000 + 0x7000
        List<String> texts = []
        for (int first = 0x4E00; first < 0x4E60 && texts.size() < 30000; first++) {
            for (int second = 0x4E00; second < 0x9F00 && texts.size() < 30000; second++) {
                int third = hash - (961 * first + 31 * second)
                if (third >= 0x4E00 && third <= 0x9FA0) {
                    texts << new String([first, second, third] as int[], 0, 3)
                }
            }
        }
        Collections.shuffle(texts, new Random(7))

        when:
        PackDescriptor descriptor = parser.parse(MINIMAL + 'configuration: {' + texts.collect { it + ': 1' }.join(',') + '}\n', 'acme.yml')

        then:
        descriptor.configuration.size() == 30000
    }

    @Unroll
    void "the key #key without quotes is refused, and with quotes it is a key"() {
        when:
        parser.parse(MINIMAL + 'configuration:\n  ' + key + ': a\n', 'acme.yml')

        then:
        PackDescriptorException e = thrown()
        e.problems == ["It has the key '${key}' without quotes, and a key that is a number, true, false or empty is put in quotes".toString()]

        expect:
        parser.parse(MINIMAL + "configuration:\n  '" + key + "': a\n", 'acme.yml').configuration == [(key): 'a']

        where:
        key << ['404', '-7', '1.5', 'true', 'false', 'null', '~']
    }

    void "keys that only look like numbers or words of YAML are text without quotes"() {
        expect:
        parser.parse(MINIMAL + 'configuration: { yes: 1, 010: 2, 1e3: 3, Null: 4, 2024-01-01: 5, 1.2.3: 6 }\n', 'acme.yml').configuration ==
                [yes: 1, '010': 2, '1e3': 3, Null: 4, '2024-01-01': 5, '1.2.3': 6]
    }

    void "a key that is left out is refused"() {
        when:
        parser.parse(MINIMAL + 'configuration:\n  ?\n  : a\n', 'acme.yml')

        then:
        PackDescriptorException e = thrown()
        e.problems == ['It has an entry without a key']
    }

    void "single pairs in a list, and values that are left out, keep keys and values apart"() {
        when:
        PackDescriptor descriptor = parser.parse(MINIMAL + 'configuration:\n  a:\n  b: [c: d, e: f]\n  g: { h, i: [j] }\n  k:\n', 'acme.yml')

        then:
        descriptor.configuration == [a: null, b: [[c: 'd'], [e: 'f']], g: [h: null, i: ['j']], k: null]

        when:
        parser.parse(MINIMAL + text, 'acme.yml')

        then:
        PackDescriptorException e = thrown()
        e.problems == ['It has a mapping or a list as a key, and a key is text']

        where:
        text << ['configuration: [[x]: y]\n', 'configuration: [? [x] : y]\n', 'configuration: { a, [b] }\n',
                 'configuration:\n  a:\n  ? [x]\n  : y\n', 'configuration:\n  []: 1\n', '? - x\n: y\n']
    }

    @Unroll
    void "a number as a key is refused when it is written as #text"() {
        when:
        parser.parse(MINIMAL + text, 'acme.yml')

        then:
        PackDescriptorException e = thrown()
        e.problems == ["It has the key '1' without quotes, and a key that is a number, true, false or empty is put in quotes"]

        where:
        text << ['configuration:\n  ? 1\n  : a\n', 'configuration: { a, 1 }\n', 'configuration: [1: a]\n', 'configuration:\n  1 : a\n']
    }

    void "a key in a block of text is text, and a key of nothing in quotes is still no key"() {
        expect:
        parser.parse(MINIMAL + 'configuration:\n  ? |-\n    1\n  : a\n', 'acme.yml').configuration == ['1': 'a']

        when:
        parser.parse(MINIMAL + "configuration:\n  '': a\n", 'acme.yml')

        then:
        PackDescriptorException e = thrown()
        e.problems == ["configuration: has the key '', and a key is one line of at most 200 characters, none of them a control character"]
    }

    void "a whole number of eighteen digits is a number as a key and one of nineteen is text"() {
        expect:
        parser.parse(MINIMAL + 'configuration: { 1234567890123456789: a, -0: b, +1: c, 0x10: d, 1_000: e }\n', 'acme.yml').configuration ==
                ['1234567890123456789': 'a', '-0': 'b', '+1': 'c', '0x10': 'd', '1_000': 'e']

        when:
        parser.parse(MINIMAL + 'configuration: { 123456789012345678: a }\n', 'acme.yml')

        then:
        PackDescriptorException e = thrown()
        e.problems == ["It has the key '123456789012345678' without quotes, and a key that is a number, true, false or empty is put in quotes"]
    }

    void "a pack of Forge can say what a compiled feature of the framework does"() {
        given:
        String text = MINIMAL + '''\
versionsOfForge:
  asciidoctorj: '3.0.0'
  asciidoctorj-diagram: 2.3.1
readmeLinks:
  - label: Shadow Gradle Plugin
    href: https://plugins.gradle.org/plugin/com.gradleup.shadow
buildPlugins:
  - id: com.gradleup.shadow
    artifactOfForge: shadow-gradle-plugin
  - id: com.acme.audit
    version: 1.2.0
build:
  blocks:
    afterPlugins: 'apply from: "gradle/asciidoc.gradle"'
    end: |
      tasks.register('audit')
'''

        when:
        PackDescriptor descriptor = parser.parseOfForge(text, 'forge.yml')

        then:
        descriptor.versionsOfForge == [asciidoctorj: '3.0.0', 'asciidoctorj-diagram': '2.3.1']
        descriptor.readmeLinks.collect { [it.label, it.href] } == [['Shadow Gradle Plugin', 'https://plugins.gradle.org/plugin/com.gradleup.shadow']]
        descriptor.buildPlugins.collect { [it.id, it.version, it.artifactOfForge] } ==
                [['com.gradleup.shadow', null, 'shadow-gradle-plugin'], ['com.acme.audit', '1.2.0', null]]
        descriptor.buildBlocks == [afterPlugins: 'apply from: "gradle/asciidoc.gradle"', end: "tasks.register('audit')\n"]
        descriptor.buildPlugins.first().toString() == 'PackDescriptor$PackBuildPlugin(id:com.gradleup.shadow, version:null, artifactOfForge:shadow-gradle-plugin, apply:false, when:always)'

        and: 'what it says is part of what it is'
        descriptor == parser.parseOfForge(text, 'again.yml')
        descriptor.hashCode() == parser.parseOfForge(text, 'again.yml').hashCode()
        [["asciidoctorj: '3.0.0'", "asciidoctorj: '3.0.1'"], ['label: Shadow', 'label: Shade'], ['shadow-gradle-plugin', 'shadow'],
         ['afterPlugins:', 'afterDependencies:']].every { List<String> change ->
            descriptor != parser.parseOfForge(text.replace(change[0], change[1]), 'other.yml')
        }
    }

    void "what only a pack of Forge can say cannot be changed once it is read"() {
        given:
        PackDescriptor descriptor = parser.parseOfForge(MINIMAL + "versionsOfForge:\n  a: '1'\nreadmeLinks:\n  - label: a\n    href: https://acme.example\n" +
                'build:\n  blocks:\n    end: x\n', 'forge.yml')

        when:
        change(descriptor)

        then:
        thrown(UnsupportedOperationException)

        where:
        change << [
                { PackDescriptor it -> it.versionsOfForge.put('b', '2') },
                { PackDescriptor it -> it.buildBlocks.clear() },
                { PackDescriptor it -> it.readmeLinks.clear() },
        ]
    }

    void "a pack of Forge that says nothing more is the descriptor a plugin would have"() {
        given:
        String text = MINIMAL + DEPENDENCY + 'com.acme:a\nbuildPlugins:\n  - id: com.acme.audit\n'

        when:
        PackDescriptor descriptor = parser.parseOfForge(text, 'forge.yml')

        then:
        descriptor == parser.parse(text, 'acme.yml')
        descriptor.versionsOfForge == [:]
        descriptor.buildBlocks == [:]
        descriptor.readmeLinks == []
        descriptor.buildPlugins.first().artifactOfForge == null
    }

    @Unroll
    void "the pack of a plugin cannot say #key, which is for the packs of Forge"() {
        when:
        parser.parse(MINIMAL + text, 'acme.yml')

        then:
        PackDescriptorException e = thrown()
        e.problems.size() == 1
        e.problems.first().startsWith("${place}: has the key '${key}', which this version of Forge does not read here; the keys are [")
        e.problems.first().endsWith(keys)
        !e.problems.first().contains("${key},") && !e.problems.first().contains("${key}]")

        where:
        key               | place             | keys                       | text
        'versionsOfForge' | 'the descriptor'  | 'thirdPartyDocumentation]' | "versionsOfForge:\n  asciidoctorj: '3.0.0'\n"
        'readmeLinks'     | 'the descriptor'  | 'thirdPartyDocumentation]' | 'readmeLinks:\n  - label: Shadow\n    href: https://acme.example\n'
        'artifactOfForge' | 'buildPlugins[0]' | '[id, version]'            | 'buildPlugins:\n  - id: com.gradleup.shadow\n    artifactOfForge: shadow\n'
        'blocks'          | 'build'           | '[properties, extensions]' | 'build:\n  blocks:\n    end: x\n'
    }

    @Unroll
    void "what only a pack of Forge can say is held to what it is: #problem"() {
        when:
        parser.parseOfForge(MINIMAL + text, 'forge.yml')

        then:
        PackDescriptorException e = thrown()
        e.problems == [problem]

        where:
        text                                                                       | problem
        'versionsOfForge: [a]\n'                                                   | 'versionsOfForge: must be a mapping of keys to values, but is a list'
        "versionsOfForge:\n  'a:b': '1.0'\n"                                       | "versionsOfForge: is a mapping of artifacts to versions, but one artifact is 'a:b'"
        'versionsOfForge:\n  asciidoctorj:\n'                                      | 'versionsOfForge.asciidoctorj: is required'
        'versionsOfForge:\n  asciidoctorj: 1.5\n'                                  | 'versionsOfForge.asciidoctorj: must be text, but is the number 1.5; put it in quotes'
        "versionsOfForge:\n  asciidoctorj: \"1.0'\"\n"                             | "versionsOfForge.asciidoctorj: must be one version, of letters, digits, '.', '-', '+' and '_', for when Forge knows none, but is '1.0''"
        'versionsOfForge:\n' + (1..21).collect { "  a${it}: '1'\n" }.join('')      | 'versionsOfForge: can name 20 artifacts, but names 21'
        "versionsOfForge:\n  asciidoctorj: '@|red 1|@'\n"                          | "versionsOfForge.asciidoctorj: has '@|' in it, which the command line reads as the start of markup"
        'buildPlugins:\n  - id: a.b\n    artifactOfForge: a:b\n'                   | "buildPlugins[0].artifactOfForge: must be the artifact Forge knows the version of the plugin by, but is 'a:b'"
        "buildPlugins:\n  - id: a.b\n    artifactOfForge: a\n    version: '1'\n"   | 'buildPlugins[0].artifactOfForge: has the version Forge knows, and then there is no version'
        'buildPlugins:\n  - id: a.b\n    artifactOfForge: true\n'                  | 'buildPlugins[0].artifactOfForge: must be text, but is the value true; put it in quotes'
        'build:\n  blocks:\n    start: x\n'                                        | "build.blocks: has text for [afterPlugins, afterDependencies, end], but one place is 'start'"
        'build:\n  blocks:\n    end:\n'                                            | 'build.blocks.end: must be text, but is empty'
        'build:\n  blocks:\n    end: 1\n'                                          | 'build.blocks.end: must be text, but is the number 1; put it in quotes'
        'build:\n  blocks: x\n'                                                    | "build.blocks: must be a mapping of keys to values, but is 'x'"
        'readmeLinks:\n  - label: a\n'                                             | 'readmeLinks[0].href: is required'
        'readmeLinks:\n  - href: https://acme.example\n'                           | 'readmeLinks[0].label: is required'
        "readmeLinks:\n  - label: 'a](https://b.example) [c'\n    href: https://acme.example\n" | "readmeLinks[0].label: must be text without '[', ']', '<', '>' or a backslash, but is 'a](https://b.example) [c'"
        'readmeLinks:\n  - label: a\n    href: javascript:alert(1)\n'              | "readmeLinks[0].href: must be an http or https address, without spaces, quotes, '<', '>' or '\$', but is 'javascript:alert(1)'"
        'readmeLinks:\n  - label: a\n    href: https://acme.example\n    title: b\n' | "readmeLinks[0]: has the key 'title', which this version of Forge does not read here; the keys are [label, href]"
        'readmeLinks:\n' + ('  - label: a\n    href: https://acme.example\n' * 21) | 'readmeLinks: can have 20 links, but has 21'
    }
}
