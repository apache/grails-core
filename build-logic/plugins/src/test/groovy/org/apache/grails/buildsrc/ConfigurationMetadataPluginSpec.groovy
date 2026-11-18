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
package org.apache.grails.buildsrc

import groovy.json.JsonSlurper
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path
import java.util.zip.ZipFile

class ConfigurationMetadataPluginSpec extends Specification {

    // the fixture compiles against the versions the framework ships with, supplied by build.gradle
    private static final String GROOVY = "org.apache.groovy:groovy:${System.getProperty('fixture.groovy.version')}"
    private static final String SPRING_BOOT =
            "org.springframework.boot:spring-boot:${System.getProperty('fixture.spring-boot.version')}"
    private static final String PAYLOADS = GenerateConfigurationMetadataTask.PAYLOAD_DIRECTORY

    @TempDir
    Path projectDir

    def setup() {
        File workspace = findWorkspace()
        write('settings.gradle', "rootProject.name = 'metadata-fixture'\ninclude 'grails-configuration-metadata'\n")
        write('grails-configuration-metadata/build.gradle', """
            plugins {
                id 'groovy'
                id 'java-library'
            }
            repositories { mavenCentral() }
            dependencies {
                implementation '${GROOVY}'
            }
            sourceSets.main.groovy.srcDir '${path(new File(workspace, 'grails-configuration-metadata/src/main/groovy'))}'
            sourceSets.main.resources.srcDir '${path(new File(workspace, 'grails-configuration-metadata/src/main/resources'))}'
        """.stripIndent())
        writeBuildScript()
        writeJavaConfiguration(false)
        write('src/main/java/fixture/RootConfiguration.java', '''
            package fixture;

            import org.springframework.boot.context.properties.ConfigurationProperties;

            @ConfigurationProperties
            public class RootConfiguration {
                private String rootValue;
                public String getRootValue() { return rootValue; }
                public void setRootValue(String rootValue) { this.rootValue = rootValue; }
            }
        '''.stripIndent())
        write('src/main/groovy/fixture/GroovyConfiguration.groovy', """
            package fixture

            import org.springframework.boot.context.properties.ConfigurationProperties

            @ConfigurationProperties('fixture.groovy')
            class GroovyConfiguration {
                String constantValue = 'constant'
                String dynamicValue = System.getProperty('fixture.dynamic')
                List<String> names
                GroovyNested nested
                private String internalSecret

                static class GroovyNested {
                    boolean enabled
                }
            }
        """.stripIndent())
        write('src/main/resources/META-INF/spring-configuration-metadata.json', '{"obsolete":true}')
        write('src/main/resources/META-INF/additional-spring-configuration-metadata.json', '''
            {
              "groups": [
                {"name":"fixture.java","description":"Java overlay","x-group":"retained"},
                {"name":"overlay.only","type":"overlay.Only","sourceType":"overlay.Source"}
              ],
              "properties": [
                {"name":"fixture.java.names","description":"Names overlay","defaultValue":["a"],"deprecation":{"level":"warning","reason":"test"},"x-property":true},
                {"name":"overlay.only.value","type":"java.lang.String","sourceType":"overlay.Source","defaultValue":"only","description":"Overlay only"}
              ],
              "hints": [
                {"name":"fixture.java.names","values":[{"value":"second"},{"value":"first"}],"providers":[{"name":"any","parameters":{"z":1,"a":2}}],"x-hint":"retained"}
              ],
              "ignored": {
                "properties": [
                  {"name":"ignored.b","reason":"b"},
                  {"name":"ignored.a","reason":"a","x-ignored":true}
                ],
                "x-ignored-root":"retained"
              },
              "custom": {"group":"current-custom-group","z":1,"a":2}
            }
        '''.stripIndent())
    }

    def "generates canonical metadata across clean incremental edit and source deletion builds"() {
        when: 'a clean jar is built'
        BuildResult clean = run('clean', 'jar')
        Map metadata = readMetadata()

        then: 'equivalent Java and Groovy bean shapes are discovered'
        clean.task(':generateConfigurationMetadata').outcome == TaskOutcome.SUCCESS
        metadata.groups*.name == [
                'fixture.groovy',
                'fixture.groovy.nested',
                'fixture.java',
                'fixture.java.nested',
                'overlay.only'
        ]
        property(metadata, 'fixture.java.names').type == 'java.util.List<java.lang.String>'
        property(metadata, 'fixture.groovy.names').type == 'java.util.List<java.lang.String>'
        property(metadata, 'fixture.groovy.constantValue').defaultValue == 'constant'
        !property(metadata, 'fixture.groovy.dynamicValue').containsKey('defaultValue')
        group(metadata, 'fixture.java.nested').type == 'fixture.JavaNested'
        group(metadata, 'fixture.java.nested').sourceType == 'fixture.JavaConfiguration'
        group(metadata, 'fixture.groovy.nested').type == 'fixture.GroovyConfiguration$GroovyNested'
        group(metadata, 'fixture.groovy.nested').sourceType == 'fixture.GroovyConfiguration'
        property(metadata, 'fixture.java.nested') == null
        property(metadata, 'fixture.groovy.nested') == null
        property(metadata, 'fixture.java.nested.enabled').type == 'java.lang.Boolean'
        property(metadata, 'fixture.groovy.nested.enabled').type == 'java.lang.Boolean'
        property(metadata, 'rootValue').type == 'java.lang.String'
        !metadata.groups*.name.contains('')
        property(metadata, 'fixture.java.readOnlyNames').type == 'java.util.List<java.lang.String>'
        property(metadata, 'fixture.java.resource').type == 'org.springframework.core.io.Resource'
        property(metadata, 'fixture.java.inheritedValue').type == 'java.lang.String'
        property(metadata, 'fixture.java.immutableValue').type == 'java.lang.String'
        property(metadata, 'fixture.java.immutableNames').type == 'java.util.List<java.lang.String>'
        property(metadata, 'fixture.java.computedValue') == null
        group(metadata, 'fixture.java.resource') == null
        property(metadata, 'fixture.java.internalSecret') == null
        property(metadata, 'fixture.java.privateValue') == null
        property(metadata, 'fixture.groovy.internalSecret') == null

        and: 'the overlay wins fields while every supported and unknown field is retained'
        metadata.groups.find { it.name == 'fixture.java' }.description == 'Java overlay'
        metadata.groups.find { it.name == 'fixture.java' }.'x-group' == 'retained'
        property(metadata, 'fixture.java.names').defaultValue == ['a']
        property(metadata, 'fixture.java.names').deprecation.reason == 'test'
        property(metadata, 'fixture.java.names').'x-property'
        property(metadata, 'overlay.only.value').description == 'Overlay only'
        metadata.hints[0].values*.value == ['second', 'first']
        metadata.hints[0].providers[0].parameters.keySet() as List == ['a', 'z']
        metadata.hints[0].'x-hint' == 'retained'
        metadata.ignored.properties*.name == ['ignored.a', 'ignored.b']
        metadata.ignored.properties[0].'x-ignored'
        metadata.ignored.'x-ignored-root' == 'retained'
        metadata.custom == [a: 2, group: 'current-custom-group', z: 1]

        and: 'the source metadata is replaced by exactly one generated jar entry'
        metadataFile().text != '{"obsolete":true}'
        metadataEntryCount() == 1

        and: 'the Groovy payload is a build-time side-car file that never reaches the jar'
        projectDir.resolve("build/classes/groovy/main/${PAYLOADS}/fixture.GroovyConfiguration.json").toFile().isFile()
        jarEntries().every { String name -> !name.startsWith(PAYLOADS) }

        when: 'nothing changes'
        BuildResult unchanged = run('jar')

        then:
        unchanged.task(':generateConfigurationMetadata').outcome == TaskOutcome.UP_TO_DATE

        when: 'one Java source is edited without cleaning'
        writeJavaConfiguration(true)
        BuildResult edited = run('jar')
        Map editedMetadata = readMetadata()

        then:
        edited.task(':generateConfigurationMetadata').outcome == TaskOutcome.SUCCESS
        property(editedMetadata, 'fixture.java.timeout').type == 'java.time.Duration'
        property(editedMetadata, 'fixture.java.names').description == 'Names overlay'
        property(editedMetadata, 'fixture.groovy.names').type == 'java.util.List<java.lang.String>'

        when: 'the Groovy source is deleted without cleaning'
        projectDir.resolve('src/main/groovy/fixture/GroovyConfiguration.groovy').toFile().delete()
        BuildResult deleted = run('jar')
        Map deletedMetadata = readMetadata()

        then:
        deleted.task(':generateConfigurationMetadata').outcome == TaskOutcome.SUCCESS
        !deletedMetadata.groups*.name.contains('fixture.groovy')
        !deletedMetadata.properties*.name.any { String name -> name.startsWith('fixture.groovy.') }
        property(deletedMetadata, 'fixture.java.timeout').type == 'java.time.Duration'
        metadataEntryCount() == 1
    }

    def "generates static Groovy DSL metadata without executing the DSL source"() {
        given: 'a mapped DSL source and curated metadata for one of its properties'
        writeGroovyDslConfiguration(false)
        configureDslMetadata('src/dsl/fixture/SecurityConfig.groovy')
        write('src/main/resources/META-INF/additional-spring-configuration-metadata.json', '''
            {
              "properties": [
                {
                  "name": "grails.plugin.springsecurity.userLookup.userDomainClassName",
                  "type": "java.lang.String",
                  "defaultValue": "overlay.User",
                  "description": "Curated user domain class"
                }
              ]
            }
        '''.stripIndent())

        when: 'the jar is built without executing the DSL script'
        BuildResult clean = run('clean', 'jar')
        Map metadata = readMetadata()

        then: 'nested closures use the mapped root prefix and literal defaults retain their types'
        clean.task(':generateConfigurationMetadata').outcome == TaskOutcome.SUCCESS
        property(metadata, 'grails.plugin.springsecurity.userLookup.enabled').type == 'java.lang.Boolean'
        property(metadata, 'grails.plugin.springsecurity.userLookup.enabled').defaultValue == true
        property(metadata, 'grails.plugin.springsecurity.oauth.client.clientId').type == 'java.lang.String'
        property(metadata, 'grails.plugin.springsecurity.oauth.client.clientId').defaultValue == 'client'
        property(metadata, 'grails.plugin.springsecurity.authentication.maxAttempts').type == 'java.lang.Integer'
        property(metadata, 'grails.plugin.springsecurity.authentication.maxAttempts').defaultValue == 3
        property(metadata, 'grails.plugin.springsecurity.authentication.roles').type == 'java.util.List'
        property(metadata, 'grails.plugin.springsecurity.authentication.roles').defaultValue == ['ROLE_USER', 'ROLE_ADMIN']
        property(metadata, 'grails.plugin.springsecurity.authentication.options').type == 'java.util.Map'
        property(metadata, 'grails.plugin.springsecurity.authentication.options').defaultValue == [maxSessions: 1, rememberMe: true]
        property(metadata, 'grails.plugin.springsecurity.authentication.session.timeout').type == 'java.lang.Integer'
        property(metadata, 'grails.plugin.springsecurity.authentication.session.timeout').defaultValue == 30

        and: 'dynamic and environment-conditional expressions have no static default'
        property(metadata, 'grails.plugin.springsecurity.authentication.dynamicDefault').type == 'java.lang.String'
        !property(metadata, 'grails.plugin.springsecurity.authentication.dynamicDefault').containsKey('defaultValue')
        property(metadata, 'grails.plugin.springsecurity.authentication.environmentDefault').type == 'java.lang.String'
        !property(metadata, 'grails.plugin.springsecurity.authentication.environmentDefault').containsKey('defaultValue')

        and: 'curated metadata remains the final overlay'
        property(metadata, 'grails.plugin.springsecurity.userLookup.userDomainClassName').defaultValue == 'overlay.User'
        property(metadata, 'grails.plugin.springsecurity.userLookup.userDomainClassName').description == 'Curated user domain class'

        and: 'the generated jar has one canonical metadata entry'
        metadataEntryCount() == 1

        when: 'nothing changes'
        BuildResult unchanged = run('jar')

        then: 'the task is up to date'
        unchanged.task(':generateConfigurationMetadata').outcome == TaskOutcome.UP_TO_DATE

        when: 'the DSL source is edited without cleaning'
        writeGroovyDslConfiguration(true)
        BuildResult edited = run('jar')
        Map editedMetadata = readMetadata()

        then: 'metadata is regenerated from the edited DSL source'
        edited.task(':generateConfigurationMetadata').outcome == TaskOutcome.SUCCESS
        property(editedMetadata, 'grails.plugin.springsecurity.authentication.sessionTimeout').type == 'java.lang.Integer'
        property(editedMetadata, 'grails.plugin.springsecurity.authentication.sessionTimeout').defaultValue == 30
        metadataEntryCount() == 1

        when: 'the registered DSL source is deleted'
        File dslSource = projectDir.resolve('src/dsl/fixture/SecurityConfig.groovy').toFile()
        dslSource.delete()
        BuildResult deleted = runner('jar').buildAndFail()

        then: 'the stale registration fails the build instead of silently dropping the DSL metadata'
        deleted.output.contains("Groovy DSL configuration source '${dslSource.canonicalPath}' does not exist")
    }

    def "merges same-name DSL typed and curated metadata fields by precedence"() {
        given: 'a DSL property also declared by a typed configuration class and curated overlay'
        writeGroovyDslConfiguration(false)
        writeTypedSecurityConfiguration()
        configureDslMetadata('src/dsl/fixture/SecurityConfig.groovy')
        write('src/main/resources/META-INF/additional-spring-configuration-metadata.json', '''
            {
              "properties": [
                {
                  "name": "grails.plugin.springsecurity.authentication.maxAttempts",
                  "description": "Curated maximum attempts"
                }
              ]
            }
        '''.stripIndent())

        when:
        BuildResult result = run('generateConfigurationMetadata')
        Map metadata = readMetadata()

        then: 'typed fields override the DSL while the DSL default and curated fields remain'
        result.task(':generateConfigurationMetadata').outcome == TaskOutcome.SUCCESS
        property(metadata, 'grails.plugin.springsecurity.authentication.maxAttempts').type == 'java.lang.Long'
        property(metadata, 'grails.plugin.springsecurity.authentication.maxAttempts').sourceType == 'fixture.SecurityConfiguration'
        property(metadata, 'grails.plugin.springsecurity.authentication.maxAttempts').defaultValue == 3
        property(metadata, 'grails.plugin.springsecurity.authentication.maxAttempts').description == 'Curated maximum attempts'
    }

    def "layers a DSL property under every configuration class that repeats its name"() {
        given: 'two configuration classes share the prefix of a DSL root and expose the same property'
        write('src/dsl/fixture/SharedConfig.groovy', '''
            security {
                enabled = true
                dslOnly = 'x'
            }
        '''.stripIndent())
        ['SharedA', 'SharedB'].each { String className ->
            write("src/main/java/fixture/${className}.java", """
                package fixture;

                import org.springframework.boot.context.properties.ConfigurationProperties;

                @ConfigurationProperties("grails.plugin.springsecurity")
                public class ${className} {
                    private boolean enabled;
                    public boolean isEnabled() { return enabled; }
                    public void setEnabled(boolean enabled) { this.enabled = enabled; }
                }
            """.stripIndent())
        }
        configureDslMetadata('src/dsl/fixture/SharedConfig.groovy')

        when:
        BuildResult result = run('generateConfigurationMetadata')
        Map metadata = readMetadata()
        List shared = metadata.properties.findAll { Map property -> property.name == 'grails.plugin.springsecurity.enabled' }

        then: 'both typed entries keep their provenance and receive the DSL default'
        result.task(':generateConfigurationMetadata').outcome == TaskOutcome.SUCCESS
        shared*.sourceType == ['fixture.SharedA', 'fixture.SharedB']
        shared*.defaultValue == [true, true]

        and: 'a DSL property that no configuration class declares stands on its own'
        property(metadata, 'grails.plugin.springsecurity.dslOnly').defaultValue == 'x'
        !property(metadata, 'grails.plugin.springsecurity.dslOnly').containsKey('sourceType')
    }

    def "parses DSL sources without executing transforms or dynamic nested calls"() {
        given: 'a DSL source outside the compiled source set with an impossible dependency transform'
        write('src/dsl/fixture/TransformConfig.groovy', '''
            @GrabResolver(name = 'missing', root = 'file:///not-a-repository')
            @Grab('missing.group:missing-artifact:1.0')
            import missing.Dependency

            security {
                enabled = true
                this."${System.getProperty('fixture.dynamic.section')}" {
                    ignored = true
                }
            }
        '''.stripIndent())
        configureDslMetadata('src/dsl/fixture/TransformConfig.groovy')

        when:
        BuildResult result = run('generateConfigurationMetadata')
        Map metadata = readMetadata()

        then: 'the transform is not discovered or executed and dynamic nested names are ignored'
        result.task(':generateConfigurationMetadata').outcome == TaskOutcome.SUCCESS
        property(metadata, 'grails.plugin.springsecurity.enabled').defaultValue == true
        property(metadata, 'grails.plugin.springsecurity.null.ignored') == null
        !metadata.properties*.name.any { String name -> name.contains('.null.') }
    }

    def "preserves null literals and omits map defaults with non-string keys"() {
        given:
        write('src/dsl/fixture/LiteralConfig.groovy', '''
            security {
                literals {
                    nothing = null
                    nullList = [null, 'present']
                    nullMap = [first: null, second: 'present']
                    unsupportedMap = [(1): 'one']
                }
            }
        '''.stripIndent())
        configureDslMetadata('src/dsl/fixture/LiteralConfig.groovy')

        when:
        BuildResult result = run('generateConfigurationMetadata')
        Map metadata = readMetadata()

        then:
        result.task(':generateConfigurationMetadata').outcome == TaskOutcome.SUCCESS
        property(metadata, 'grails.plugin.springsecurity.literals.nullList').defaultValue == [null, 'present']

        and: 'a setting that is null by default has no default, rather than an explicit null one'
        property(metadata, 'grails.plugin.springsecurity.literals.nothing') == [
                name: 'grails.plugin.springsecurity.literals.nothing'
        ]
        property(metadata, 'grails.plugin.springsecurity.literals.nullMap').defaultValue == [first: null, second: 'present']
        property(metadata, 'grails.plugin.springsecurity.literals.unsupportedMap').type == 'java.util.Map'
        !property(metadata, 'grails.plugin.springsecurity.literals.unsupportedMap').containsKey('defaultValue')
    }

    def "merges same-name properties assigned differently across if/else branches"() {
        given: 'branches assign different literal types, and one branch assigns null, under the same name'
        write('src/dsl/fixture/BranchTypeConfig.groovy', '''
            security {
                password {
                    if (System.getProperty('env') == 'test') {
                        key = 'test-key'
                    } else {
                        key = null
                    }
                }
                authentication {
                    if (System.getProperty('env') == 'test') {
                        retries = 1
                    } else {
                        retries = 1
                    }
                    if (System.getProperty('env') == 'test') {
                        timeout = 'PT30S'
                    } else {
                        timeout = 5
                    }
                }
            }
        '''.stripIndent())
        configureDslMetadata('src/dsl/fixture/BranchTypeConfig.groovy')

        when:
        BuildResult result = run('generateConfigurationMetadata')
        Map metadata = readMetadata()

        then: 'the build succeeds instead of failing on a spurious conflict'
        result.task(':generateConfigurationMetadata').outcome == TaskOutcome.SUCCESS

        and: 'a null branch carries no type information, so the other branch\'s type still applies'
        Map keyProperty = property(metadata, 'grails.plugin.springsecurity.password.key')
        keyProperty.type == 'java.lang.String'
        !keyProperty.containsKey('defaultValue')

        and: 'a property with agreeing branch types and values keeps its inferred type'
        property(metadata, 'grails.plugin.springsecurity.authentication.retries').type == 'java.lang.Integer'

        and: 'branches disagreeing on a concrete, non-null type drop the type entirely'
        !property(metadata, 'grails.plugin.springsecurity.authentication.timeout').containsKey('type')
        !property(metadata, 'grails.plugin.springsecurity.authentication.timeout').containsKey('defaultValue')
    }

    def "keeps the unconditional default when a conditional branch overrides it"() {
        given: 'an unconditional assignment is overridden only under some environments'
        write('src/dsl/fixture/UnconditionalDefaultConfig.groovy', '''
            security {
                foo = 'default'
                if (System.getProperty('env') == 'test') {
                    foo = 'test'
                }
            }
        '''.stripIndent())
        configureDslMetadata('src/dsl/fixture/UnconditionalDefaultConfig.groovy')

        when:
        BuildResult result = run('generateConfigurationMetadata')
        Map metadata = readMetadata()

        then: 'the unconditional default and agreeing type both survive'
        result.task(':generateConfigurationMetadata').outcome == TaskOutcome.SUCCESS
        Map fooProperty = property(metadata, 'grails.plugin.springsecurity.foo')
        fooProperty.type == 'java.lang.String'
        fooProperty.defaultValue == 'default'
    }

    def "still fails on two genuinely conflicting unconditional assignments"() {
        given: 'the same property is assigned two different unconditional values'
        write('src/dsl/fixture/GenuineConflictConfig.groovy', '''
            security {
                foo = 'first'
                foo = 'second'
            }
        '''.stripIndent())
        configureDslMetadata('src/dsl/fixture/GenuineConflictConfig.groovy')

        when:
        BuildResult result = runner('generateConfigurationMetadata').buildAndFail()

        then:
        result.output.contains("Conflicting DSL properties metadata for 'grails.plugin.springsecurity.foo'")
    }

    def "parses an environments block under the unchanged prefix instead of as a nested section"() {
        given: 'a ConfigSlurper environments block picks one environment at runtime'
        write('src/dsl/fixture/EnvironmentsConfig.groovy', '''
            security {
                environments {
                    production {
                        foo = 'prod-value'
                    }
                    test {
                        foo = 'test-value'
                    }
                }
            }
        '''.stripIndent())
        configureDslMetadata('src/dsl/fixture/EnvironmentsConfig.groovy')

        when:
        BuildResult result = run('generateConfigurationMetadata')
        Map metadata = readMetadata()

        then: 'the environment name is not folded into the property path'
        result.task(':generateConfigurationMetadata').outcome == TaskOutcome.SUCCESS
        Map fooProperty = property(metadata, 'grails.plugin.springsecurity.foo')
        fooProperty != null
        fooProperty.type == 'java.lang.String'
        !fooProperty.containsKey('defaultValue')

        and: 'no property is emitted under the environments/production/test path'
        !metadata.properties*.name.any { String name -> name.contains('.environments.') }
    }

    def "parses an environment declared inside an if statement and ignores ordinary calls in an environments block"() {
        given:
        write('src/dsl/fixture/ConditionalEnvironmentConfig.groovy', '''
            security {
                environments {
                    if (System.getProperty('flag')) {
                        production {
                            foo = 1
                        }
                    } else {
                        test {
                            bar = 'x'
                        }
                    }
                    candidates.each { candidate -> looped = 1 }
                }
            }
        '''.stripIndent())
        configureDslMetadata('src/dsl/fixture/ConditionalEnvironmentConfig.groovy')

        when:
        run('generateConfigurationMetadata')
        Map metadata = readMetadata()

        then: 'only a call on the script itself names an environment'
        metadata.properties*.name.findAll { String name -> name.startsWith('grails.plugin.springsecurity.') } == [
                'grails.plugin.springsecurity.bar',
                'grails.plugin.springsecurity.foo'
        ]
        property(metadata, 'grails.plugin.springsecurity.foo').type == 'java.lang.Integer'
        !property(metadata, 'grails.plugin.springsecurity.foo').containsKey('defaultValue')
    }

    def "ignores local variables and closures passed to ordinary method calls"() {
        given:
        write('src/dsl/fixture/CodeConfig.groovy', '''
            security {
                def helper = 'x'
                String other = 'y'
                def (first, second) = [1, 2]
                helper = 'reassigned'
                helper.nested = 'ignored'
                enabled = true
                items.each { item -> visited = true }
                settings.with { applied = true }
                this.explicit { skipped = true }
                helper { viaLocal = true }
                section {
                    helper = 'still local'
                    value = 1
                }
            }
        '''.stripIndent())
        configureDslMetadata('src/dsl/fixture/CodeConfig.groovy')

        when:
        run('generateConfigurationMetadata')
        Map metadata = readMetadata()

        then: 'only assignments in sections opened on the script itself are settings'
        metadata.properties*.name.findAll { String name -> name.startsWith('grails.plugin.springsecurity.') } == [
                'grails.plugin.springsecurity.enabled',
                'grails.plugin.springsecurity.section.value'
        ]
    }

    def "drops the type when any assignment of a property cannot be inferred"() {
        given:
        write('src/dsl/fixture/DynamicTypeConfig.groovy', '''
            security {
                if (System.getProperty('env') == 'test') {
                    timeout = 'PT30S'
                } else {
                    timeout = computeTimeout()
                }
                retries = 5
                environments {
                    production {
                        retries = computeRetries()
                    }
                }
                environment = System.getenv()
                home = System.getenv('HOME')
            }
        '''.stripIndent())
        configureDslMetadata('src/dsl/fixture/DynamicTypeConfig.groovy')

        when:
        run('generateConfigurationMetadata')
        Map metadata = readMetadata()

        then: 'a dynamic branch may yield any type'
        !property(metadata, 'grails.plugin.springsecurity.timeout').containsKey('type')
        !property(metadata, 'grails.plugin.springsecurity.retries').containsKey('type')
        property(metadata, 'grails.plugin.springsecurity.retries').defaultValue == 5

        and: 'only a named environment variable is known to be a string'
        !property(metadata, 'grails.plugin.springsecurity.environment').containsKey('type')
        property(metadata, 'grails.plugin.springsecurity.home').type == 'java.lang.String'
    }

    def "parses dotted assignments, environments and if blocks at the top level of the script"() {
        given:
        write('src/dsl/fixture/TopLevelConfig.groovy', '''
            security.top = 5
            security.nested.flag = true
            unrelated.value = 'ignored'
            standalone = 'ignored'

            environments {
                production {
                    security {
                        envOnly = 1
                    }
                    security.envDotted = 'prod'
                }
            }

            if (System.getProperty('env') == 'test') {
                security {
                    conditionalOnly = 'test'
                }
            }
        '''.stripIndent())
        configureDslMetadata('src/dsl/fixture/TopLevelConfig.groovy')

        when:
        run('generateConfigurationMetadata')
        Map metadata = readMetadata()

        then:
        metadata.properties*.name.findAll { String name -> name.startsWith('grails.plugin.springsecurity.') } == [
                'grails.plugin.springsecurity.conditionalOnly',
                'grails.plugin.springsecurity.envDotted',
                'grails.plugin.springsecurity.envOnly',
                'grails.plugin.springsecurity.nested.flag',
                'grails.plugin.springsecurity.top'
        ]
        property(metadata, 'grails.plugin.springsecurity.top').defaultValue == 5

        and: 'values that depend on the environment or a condition have no static default'
        property(metadata, 'grails.plugin.springsecurity.envOnly').type == 'java.lang.Integer'
        !property(metadata, 'grails.plugin.springsecurity.envOnly').containsKey('defaultValue')
        !property(metadata, 'grails.plugin.springsecurity.conditionalOnly').containsKey('defaultValue')
        !metadata.properties*.name.any { String name -> name.startsWith('unrelated') || name == 'standalone' }
    }

    def "ignores a top-level local variable that shares its name with a registered root"() {
        given:
        write('src/dsl/fixture/ShadowedRootConfig.groovy', '''
            security {
                enabled = true
            }
            if (System.getProperty('flag')) {
                def security = [:]
                security.shadowed = true
            }
        '''.stripIndent())
        configureDslMetadata('src/dsl/fixture/ShadowedRootConfig.groovy')

        when:
        run('generateConfigurationMetadata')
        Map metadata = readMetadata()

        then:
        metadata.properties*.name.findAll { String name -> name.startsWith('grails.plugin.springsecurity.') } == [
                'grails.plugin.springsecurity.enabled'
        ]
    }

    def "does not take a top-level local variable for the declaration of a registered root"() {
        given:
        write('src/dsl/fixture/LocalRootConfig.groovy', '''
            def security = [:]
            security.enabled = true
        '''.stripIndent())
        configureDslMetadata('src/dsl/fixture/LocalRootConfig.groovy')

        when:
        BuildResult result = runner('generateConfigurationMetadata').buildAndFail()

        then:
        result.output.contains('No Groovy DSL configuration source declares the registered root(s) [security]')
    }

    def "fails when a registered DSL root is not declared by any source"() {
        given:
        write('src/dsl/fixture/RenamedRootConfig.groovy', '''
            springSecurity {
                enabled = true
            }
        '''.stripIndent())
        configureDslMetadata('src/dsl/fixture/RenamedRootConfig.groovy')

        when:
        BuildResult result = runner('generateConfigurationMetadata').buildAndFail()

        then:
        result.output.contains('No Groovy DSL configuration source declares the registered root(s) [security]')
    }

    def "fails when the overlay restates a default the DSL source already declares"() {
        given:
        writeGroovyDslConfiguration(false)
        configureDslMetadata('src/dsl/fixture/SecurityConfig.groovy')
        write('src/main/resources/META-INF/additional-spring-configuration-metadata.json', '''
            {
              "properties": [
                {"name": "grails.plugin.springsecurity.authentication.maxAttempts", "defaultValue": 3},
                {"name": "grails.plugin.springsecurity.authentication.roles", "defaultValue": ["ROLE_USER", "ROLE_ADMIN"]},
                {"name": "grails.plugin.springsecurity.userLookup.enabled", "defaultValue": false},
                {"name": "grails.plugin.springsecurity.authentication.dynamicDefault", "defaultValue": "curated"}
              ]
            }
        '''.stripIndent())

        when:
        BuildResult result = runner('generateConfigurationMetadata').buildAndFail()

        then: 'only identical defaults are rejected, a differing one is a deliberate override'
        result.output.contains('restates the default that the Groovy DSL source already declares for 2 properties')
        result.output.contains('grails.plugin.springsecurity.authentication.maxAttempts, ' +
                'grails.plugin.springsecurity.authentication.roles')
        !result.output.contains('grails.plugin.springsecurity.userLookup.enabled')
    }

    def "reports the actual source file for malformed DSL"() {
        given:
        File malformedDsl = write('src/dsl/fixture/BrokenConfig.groovy', '''
            security {
                enabled =
            }
        '''.stripIndent())
        configureDslMetadata('src/dsl/fixture/BrokenConfig.groovy')

        when:
        BuildResult result = runner('generateConfigurationMetadata').buildAndFail()

        then:
        result.output.contains(malformedDsl.absolutePath)
    }

    def "fails on conflicting duplicate identities in an overlay source"() {
        given:
        write('src/main/resources/META-INF/additional-spring-configuration-metadata.json', '''
            {"properties":[
              {"name":"duplicate","type":"java.lang.String"},
              {"name":"duplicate","type":"java.lang.Integer"}
            ]}
        '''.stripIndent())

        when:
        BuildResult result = runner('generateConfigurationMetadata').buildAndFail()

        then:
        result.output.contains("Conflicting overlay properties metadata for 'duplicate'")
    }

    def "rejects any duplicate compiled class name across input directories"() {
        given: 'a second source set compiles the same binary name as the main source set'
        writeBuildScript("""
            sourceSets {
                dup {
                    java.srcDir 'src/dup/java'
                }
            }

            dependencies {
                dupImplementation '${GROOVY}'
                dupImplementation '${SPRING_BOOT}'
            }

            tasks.named('generateConfigurationMetadata') {
                classesDirs.from(sourceSets.dup.output.classesDirs)
                dependsOn sourceSets.dup.output
            }
        """)
        write('src/main/java/fixture/Duplicate.java', '''
            package fixture;

            import org.springframework.boot.context.properties.ConfigurationProperties;

            @ConfigurationProperties("fixture.dup")
            public class Duplicate {
                private String value;
                public String getValue() { return value; }
                public void setValue(String value) { this.value = value; }
            }
        '''.stripIndent())
        write('src/dup/java/fixture/Duplicate.java', '''
            package fixture;

            import org.springframework.boot.context.properties.ConfigurationProperties;

            @ConfigurationProperties("fixture.dup")
            public class Duplicate {
                private String value;
                public String getValue() { return value; }
                public void setValue(String value) { this.value = value; }
            }
        '''.stripIndent())

        when: 'metadata generation runs over both output directories'
        BuildResult result = runner('generateConfigurationMetadata').buildAndFail()

        then: 'the duplicate binary name is rejected with both input origins reported'
        result.output.contains("Duplicate compiled class 'fixture.Duplicate' in configuration metadata inputs " +
                "(first seen in '${path(projectDir.resolve('build/classes/java/dup').toFile())}', " +
                "also in '${path(projectDir.resolve('build/classes/java/main').toFile())}')")
    }

    def "preserves repeated group names from two configuration classes sharing a prefix"() {
        given: 'two configuration classes declare the same prefix'
        resetFixture()
        writeSharedPrefixClasses(false)

        when:
        BuildResult result = run('generateConfigurationMetadata')

        then: 'both groups survive because their provenance differs'
        result.task(':generateConfigurationMetadata').outcome == TaskOutcome.SUCCESS
        Map metadata = readMetadata()
        List shared = metadata.groups.findAll { Map group -> group.name == 'fixture.shared' }
        shared.size() == 2
        shared*.sourceType as Set == ['fixture.SharedA', 'fixture.SharedB'] as Set
        property(metadata, 'fixture.shared.firstValue').type == 'java.lang.String'
        property(metadata, 'fixture.shared.secondValue').type == 'java.lang.String'
    }

    def "preserves a property name repeated by two configuration classes sharing a prefix"() {
        given: 'two configuration classes expose the same property under the same prefix'
        resetFixture()
        ['RepeatedA', 'RepeatedB'].each { String className ->
            write("src/main/java/fixture/${className}.java", """
                package fixture;

                import org.springframework.boot.context.properties.ConfigurationProperties;

                @ConfigurationProperties("fixture.repeated")
                public class ${className} {
                    private boolean enabled;
                    public boolean isEnabled() { return enabled; }
                    public void setEnabled(boolean enabled) { this.enabled = enabled; }
                }
            """.stripIndent())
        }
        write('src/main/resources/META-INF/additional-spring-configuration-metadata.json', '''
            {"properties":[
              {"name":"fixture.repeated.enabled","description":"Shared"},
              {"name":"fixture.repeated.enabled","sourceType":"fixture.RepeatedB","defaultValue":true}
            ]}
        '''.stripIndent())

        when:
        BuildResult result = run('generateConfigurationMetadata')

        then: 'both properties survive because their provenance differs'
        result.task(':generateConfigurationMetadata').outcome == TaskOutcome.SUCCESS
        List repeated = readMetadata().properties.findAll { Map property -> property.name == 'fixture.repeated.enabled' }
        repeated*.sourceType == ['fixture.RepeatedA', 'fixture.RepeatedB']

        and: 'a name-only overlay entry augments both while a source-qualified one targets its own'
        repeated*.description == ['Shared', 'Shared']
        repeated*.defaultValue == [null, true]
    }

    def "preserves a nested property that another configuration class binds by its own prefix"() {
        given: 'a nested property of one class is the prefix of another'
        resetFixture()
        write('src/main/java/fixture/Outer.java', '''
            package fixture;

            import org.springframework.boot.context.properties.ConfigurationProperties;

            @ConfigurationProperties("fixture.outer")
            public class Outer {
                private Inner inner;
                public Inner getInner() { return inner; }
                public void setInner(Inner inner) { this.inner = inner; }
            }
        '''.stripIndent())
        write('src/main/java/fixture/Inner.java', '''
            package fixture;

            import org.springframework.boot.context.properties.ConfigurationProperties;

            @ConfigurationProperties("fixture.outer.inner")
            public class Inner {
                private String label;
                public String getLabel() { return label; }
                public void setLabel(String label) { this.label = label; }
            }
        '''.stripIndent())

        when:
        BuildResult result = run('generateConfigurationMetadata')

        then:
        result.task(':generateConfigurationMetadata').outcome == TaskOutcome.SUCCESS
        readMetadata().properties.findAll { Map property -> property.name == 'fixture.outer.inner.label' }*.sourceType ==
                ['fixture.Inner', 'fixture.Outer']
    }

    def "preserves repeated nested group names from different source types"() {
        given: 'two configuration classes expose different nested types under the same property name'
        resetFixture()
        writeSharedPrefixClasses(true)

        when:
        BuildResult result = run('generateConfigurationMetadata')

        then: 'the repeated nested group is kept per source type with its own properties'
        result.task(':generateConfigurationMetadata').outcome == TaskOutcome.SUCCESS
        Map metadata = readMetadata()
        List nested = metadata.groups.findAll { Map group -> group.name == 'fixture.shared.nested' }
        nested.size() == 2
        nested*.sourceType as Set == ['fixture.SharedA', 'fixture.SharedB'] as Set
        nested*.type as Set == ['fixture.NestedA', 'fixture.NestedB'] as Set
        property(metadata, 'fixture.shared.nested.enabled').type == 'java.lang.Boolean'
        property(metadata, 'fixture.shared.nested.label').type == 'java.lang.String'
    }

    def "rejects exact same group identity conflicts in an overlay"() {
        given:
        write('src/main/resources/META-INF/additional-spring-configuration-metadata.json', '''
            {"groups":[
              {"name":"fixture.java","sourceType":"fixture.JavaConfiguration","description":"one"},
              {"name":"fixture.java","sourceType":"fixture.JavaConfiguration","description":"two"}
            ]}
        '''.stripIndent())

        when:
        BuildResult result = runner('generateConfigurationMetadata').buildAndFail()

        then:
        result.output.contains("Conflicting overlay groups metadata for 'fixture.java'")
    }

    def "name-only overlay augments all matching generated groups"() {
        given: 'two classes share a prefix and the overlay carries no source qualification'
        resetFixture()
        writeSharedPrefixClasses(false)
        write('src/main/resources/META-INF/additional-spring-configuration-metadata.json', '''
            {"groups":[{"name":"fixture.shared","description":"shared overlay"}]}
        '''.stripIndent())

        when:
        BuildResult result = run('generateConfigurationMetadata')

        then: 'every generated group with that name receives the overlay fields'
        result.task(':generateConfigurationMetadata').outcome == TaskOutcome.SUCCESS
        Map metadata = readMetadata()
        List shared = metadata.groups.findAll { Map group -> group.name == 'fixture.shared' }
        shared.size() == 2
        shared.every { Map group -> group.description == 'shared overlay' }
    }

    def "source-qualified overlay targets a single group identity"() {
        given: 'two classes share a prefix and the overlay names one source type'
        resetFixture()
        writeSharedPrefixClasses(false)
        write('src/main/resources/META-INF/additional-spring-configuration-metadata.json', '''
            {"groups":[{"name":"fixture.shared","sourceType":"fixture.SharedA","description":"A only"}]}
        '''.stripIndent())

        when:
        BuildResult result = run('generateConfigurationMetadata')

        then: 'only the matching identity is augmented'
        result.task(':generateConfigurationMetadata').outcome == TaskOutcome.SUCCESS
        Map metadata = readMetadata()
        List shared = metadata.groups.findAll { Map group -> group.name == 'fixture.shared' }
        shared.find { Map group -> group.sourceType == 'fixture.SharedA' }.description == 'A only'
        shared.find { Map group -> group.sourceType == 'fixture.SharedB' }.description == null
    }

    def "orders repeated group identities by name, sourceType, then sourceMethod"() {
        given: 'the overlay contributes two identities that differ only by sourceMethod'
        write('src/main/resources/META-INF/additional-spring-configuration-metadata.json', '''
            {"groups":[
              {"name":"fixture.shared","sourceType":"fixture.SharedA","sourceMethod":"beta","description":"beta"},
              {"name":"fixture.shared","sourceType":"fixture.SharedA","sourceMethod":"alpha","description":"alpha"}
            ]}
        '''.stripIndent())

        when:
        run('generateConfigurationMetadata')

        then: 'the two identities are kept distinct and ordered by their sourceMethod'
        Map metadata = readMetadata()
        List shared = metadata.groups.findAll { Map group -> group.name == 'fixture.shared' }
        shared.size() == 2
        shared*.sourceMethod == ['alpha', 'beta']
    }

    def "generates metadata when no additional metadata file exists"() {
        given:
        projectDir.resolve('src/main/resources/META-INF/additional-spring-configuration-metadata.json').toFile().delete()

        when:
        BuildResult result = run('generateConfigurationMetadata')

        then:
        result.task(':generateConfigurationMetadata').outcome == TaskOutcome.SUCCESS
        property(readMetadata(), 'fixture.java.names').type == 'java.util.List<java.lang.String>'
    }

    def "ignores the payload of a Groovy class that is no longer annotated"() {
        given:
        run('generateConfigurationMetadata')

        when: 'the annotation is removed without cleaning and an old payload is still around'
        write('src/main/groovy/fixture/GroovyConfiguration.groovy', """
            package fixture

            class GroovyConfiguration {
                String constantValue = 'constant'
            }
        """.stripIndent())
        File stale = projectDir.resolve("build/classes/groovy/main/${PAYLOADS}/fixture.GroovyConfiguration.json").toFile()
        run('generateConfigurationMetadata')
        stale.parentFile.mkdirs()
        stale.text = '{"prefix":"fixture.groovy","properties":[{"name":"fixture.groovy.stale","type":"java.lang.String"}]}'
        run('generateConfigurationMetadata')
        Map metadata = readMetadata()

        then:
        !metadata.groups*.name.contains('fixture.groovy')
        !metadata.properties*.name.any { String name -> name.startsWith('fixture.groovy.') }
    }

    def "applies the payload of a Groovy class whose package contains a json segment"() {
        given: 'a payload file name in which .json occurs before the extension'
        resetFixture()
        write('src/main/groovy/fixture/json/view/SegmentConfiguration.groovy', """
            package fixture.json.view

            import org.springframework.boot.context.properties.ConfigurationProperties

            @ConfigurationProperties('fixture.segment')
            class SegmentConfiguration {
                String mimeType = 'application/json'
            }
        """.stripIndent())
        write('src/main/groovy/fixture/view/SegmentConfiguration.groovy', """
            package fixture.view

            import org.springframework.boot.context.properties.ConfigurationProperties

            @ConfigurationProperties('fixture.plain')
            class SegmentConfiguration {
                String encoding = 'UTF-8'
            }
        """.stripIndent())

        when:
        run('generateConfigurationMetadata')
        Map metadata = readMetadata()

        then: 'each class receives its own payload, of which the default value is the evidence'
        projectDir.resolve("build/classes/groovy/main/${PAYLOADS}/fixture.json.view.SegmentConfiguration.json").toFile().isFile()
        property(metadata, 'fixture.segment.mimeType').defaultValue == 'application/json'
        property(metadata, 'fixture.segment.mimeType').sourceType == 'fixture.json.view.SegmentConfiguration'
        property(metadata, 'fixture.plain.encoding').defaultValue == 'UTF-8'
        property(metadata, 'fixture.plain.encoding').sourceType == 'fixture.view.SegmentConfiguration'
        metadata.properties*.name == ['fixture.plain.encoding', 'fixture.segment.mimeType']
    }

    def "binds the constructor of a Java class that is joint-compiled from the Groovy source directory"() {
        given:
        resetFixture()
        write('src/main/groovy/fixture/JointConfiguration.java', '''
            package fixture;

            import java.util.List;
            import org.springframework.boot.context.properties.ConfigurationProperties;

            @ConfigurationProperties("fixture.joint")
            public class JointConfiguration {
                private final String host;
                private final List<String> aliases;
                public JointConfiguration(String host, List<String> aliases) {
                    this.host = host;
                    this.aliases = aliases;
                }
            }
        '''.stripIndent())
        write('src/main/groovy/fixture/JointCompanion.groovy', 'package fixture\n\nclass JointCompanion { }\n')

        when:
        run('generateConfigurationMetadata')
        Map metadata = readMetadata()

        then:
        property(metadata, 'fixture.joint.host').type == 'java.lang.String'
        property(metadata, 'fixture.joint.aliases').type == 'java.util.List<java.lang.String>'
    }

    def "types the constructor parameters of an inner class from its generic signature"() {
        given: 'a constructor whose mandated outer instance is absent from the generic signature'
        resetFixture()
        write('src/main/java/fixture/InnerConfiguration.java', """
            package fixture;

            import java.util.List;
            import org.springframework.boot.context.properties.ConfigurationProperties;

            @ConfigurationProperties("fixture.inner")
            public class InnerConfiguration {
                private Inner inner;
                public Inner getInner() { return inner; }
                public void setInner(Inner inner) { this.inner = inner; }

                public class Inner {
                    private final List<String> labels;
                    private final int size;
                    public Inner(List<String> labels, int size) {
                        this.labels = labels;
                        this.size = size;
                    }
                }
            }
        """.stripIndent())

        when:
        run('generateConfigurationMetadata')
        Map metadata = readMetadata()

        then:
        group(metadata, 'fixture.inner.inner').type == 'fixture.InnerConfiguration$Inner'
        property(metadata, 'fixture.inner.inner.labels').type == 'java.util.List<java.lang.String>'
        property(metadata, 'fixture.inner.inner.size').type == 'java.lang.Integer'
    }

    def "excludes framework accessors when a Groovy class falls back to bytecode scanning"() {
        given: 'a prefix the compiler cannot read as a literal'
        resetFixture()
        write('src/main/groovy/fixture/DeferredConfiguration.groovy', """
            package fixture

            import org.springframework.boot.context.properties.ConfigurationProperties

            @ConfigurationProperties(prefix = (String) 'fixture.deferred')
            class DeferredConfiguration {
                String value
                boolean enabled

                void setGrailsApplication(Object grailsApplication) {
                }
            }
        """.stripIndent())

        when:
        run('generateConfigurationMetadata')
        Map metadata = readMetadata()

        then:
        metadata.properties*.name == ['fixture.deferred.enabled', 'fixture.deferred.value']
        property(metadata, 'fixture.deferred.enabled').type == 'java.lang.Boolean'
    }

    def "discovers getter-only nested objects"() {
        given:
        resetFixture()
        write('src/main/java/fixture/PoolConfiguration.java', """
            package fixture;

            import org.springframework.boot.context.properties.ConfigurationProperties;

            @ConfigurationProperties("fixture.pooled")
            public class PoolConfiguration {
                private final Pool pool = new Pool();
                private final Empty empty = new Empty();
                public Pool getPool() { return pool; }
                public Empty getEmpty() { return empty; }
                public PoolConfiguration getSelf() { return this; }

                public static class Pool {
                    private int size;
                    private final Limits limits = new Limits();
                    public int getSize() { return size; }
                    public void setSize(int size) { this.size = size; }
                    public Limits getLimits() { return limits; }
                }

                public static class Limits {
                    private long max;
                    public long getMax() { return max; }
                    public void setMax(long max) { this.max = max; }
                }

                public static class Empty {
                    public String getComputed() { return "computed"; }
                }
            }
        """.stripIndent())

        when:
        run('generateConfigurationMetadata')
        Map metadata = readMetadata()

        then: 'only read-only objects that have something to bind become groups'
        metadata.groups*.name == ['fixture.pooled', 'fixture.pooled.pool', 'fixture.pooled.pool.limits']
        metadata.properties*.name == ['fixture.pooled.pool.limits.max', 'fixture.pooled.pool.size']
        property(metadata, 'fixture.pooled.pool.limits.max').type == 'java.lang.Long'
    }

    def "resolves the property type from the accessors regardless of their declaration order"() {
        given:
        resetFixture()
        write('src/main/java/fixture/AccessorConfiguration.java', """
            package fixture;

            import java.util.Collection;
            import java.util.List;
            import java.util.Set;
            import org.springframework.boot.context.properties.ConfigurationProperties;

            @ConfigurationProperties("fixture.accessor")
            public class AccessorConfiguration {
                public void setValues(List<String> values) { }
                public List<String> getValues() { return null; }
                public void setValues(Set<String> values) { }

                public void setNames(List<String> names) { }
                public Collection<String> getNames() { return null; }

                public void setImports(List<String> imports) { }
                public void setImports(String[] imports) { }
            }
        """.stripIndent())

        when:
        run('generateConfigurationMetadata')
        Map metadata = readMetadata()

        then: 'the setter matching the getter wins, then a setter over the getter, then the first setter by type name'
        property(metadata, 'fixture.accessor.values').type == 'java.util.List<java.lang.String>'
        property(metadata, 'fixture.accessor.names').type == 'java.util.List<java.lang.String>'
        property(metadata, 'fixture.accessor.imports').type == 'java.lang.String[]'
    }

    def "warns about a delegate compiled elsewhere only while the overlay leaves it undocumented"() {
        given:
        resetFixture()
        write('src/main/groovy/fixture/DelegatingConfiguration.groovy', """
            package fixture

            import org.springframework.boot.context.properties.ConfigurationProperties

            @ConfigurationProperties('fixture.delegating')
            class DelegatingConfiguration {
                @Delegate URI endpoint
                String plain
            }
        """.stripIndent())

        when:
        BuildResult undocumented = run('generateConfigurationMetadata')

        then:
        undocumented.output.contains("uses @Delegate field 'endpoint' of type 'java.net.URI'")
        readMetadata().properties*.name == ['fixture.delegating.plain']

        when: 'the overlay documents a delegated property'
        write('src/main/resources/META-INF/additional-spring-configuration-metadata.json',
                '{"properties":[{"name":"fixture.delegating.host","type":"java.lang.String"}]}')
        BuildResult documented = run('generateConfigurationMetadata')

        then:
        !documented.output.contains('@Delegate')
        readMetadata().properties*.name == ['fixture.delegating.host', 'fixture.delegating.plain']
    }

    def "generates the properties of a delegate compiled by the same project"() {
        given:
        resetFixture()
        write('src/main/groovy/fixture/LocalDelegatingConfiguration.groovy', """
            package fixture

            import org.springframework.boot.context.properties.ConfigurationProperties

            class LocalDefaults {
                String origin
                int maxAge
            }

            @ConfigurationProperties('fixture.local')
            class LocalDelegatingConfiguration {
                @Delegate LocalDefaults defaults = new LocalDefaults()
                String plain
            }
        """.stripIndent())

        when:
        BuildResult result = run('generateConfigurationMetadata')
        Map metadata = readMetadata()

        then:
        !result.output.contains('@Delegate')
        metadata.properties*.name == ['fixture.local.maxAge', 'fixture.local.origin', 'fixture.local.plain']
        metadata.properties*.sourceType.unique() == ['fixture.LocalDelegatingConfiguration']
        property(metadata, 'fixture.local.maxAge').type == 'java.lang.Integer'
    }

    private void writeJavaConfiguration(boolean includeTimeout) {
        String timeout = includeTimeout ? '''
                private java.time.Duration timeout;
                public java.time.Duration getTimeout() { return timeout; }
                public void setTimeout(java.time.Duration timeout) { this.timeout = timeout; }
        ''' : ''
        write('src/main/java/fixture/JavaConfiguration.java', """
            package fixture;

            import java.util.List;
            import org.springframework.boot.context.properties.ConfigurationProperties;
            import org.springframework.core.io.Resource;

            @ConfigurationProperties("fixture.java")
            public class JavaConfiguration extends JavaBaseConfiguration {
                private List<String> names;
                private JavaNested nested;
                private String internalSecret;
                private String privateValue;
                private final List<String> readOnlyNames = new java.util.ArrayList<>();
                private final String immutableValue;
                private final List<String> immutableNames;
                private Resource resource;
                public JavaConfiguration(String immutableValue, List<String> immutableNames) {
                    this.immutableValue = immutableValue;
                    this.immutableNames = immutableNames;
                }
                public List<String> getNames() { return names; }
                public void setNames(List<String> names) { this.names = names; }
                public JavaNested getNested() { return nested; }
                public void setNested(JavaNested nested) { this.nested = nested; }
                public List<String> getReadOnlyNames() { return readOnlyNames; }
                public String getImmutableValue() { return immutableValue; }
                public String getComputedValue() { return "computed"; }
                public Resource getResource() { return resource; }
                public void setResource(Resource resource) { this.resource = resource; }
                private void setPrivateValue(String privateValue) { this.privateValue = privateValue; }
                ${timeout}
            }

            class JavaNested {
                private boolean enabled;
                public boolean isEnabled() { return enabled; }
                public void setEnabled(boolean enabled) { this.enabled = enabled; }
            }

            class JavaBaseConfiguration {
                private String inheritedValue;
                public String getInheritedValue() { return inheritedValue; }
                public void setInheritedValue(String inheritedValue) { this.inheritedValue = inheritedValue; }
            }

            class GenericConstructor {
                <T> GenericConstructor(T value) { }
            }
        """.stripIndent())
    }

    private void writeBuildScript(String additionalConfiguration = '') {
        write('build.gradle', """
            plugins {
                id 'groovy'
                id 'org.apache.grails.buildsrc.configuration-metadata'
            }

            repositories { mavenCentral() }

            dependencies {
                implementation '${GROOVY}'
                implementation '${SPRING_BOOT}'
            }
        """.stripIndent() + additionalConfiguration.stripIndent())
    }

    private void resetFixture() {
        ['src/main/java/fixture/RootConfiguration.java',
         'src/main/java/fixture/JavaConfiguration.java',
         'src/main/groovy/fixture/GroovyConfiguration.groovy',
         'src/main/resources/META-INF/additional-spring-configuration-metadata.json'].each { String relativePath ->
            projectDir.resolve(relativePath).toFile().delete()
        }
    }

    private void writeSharedPrefixClasses(boolean withNested) {
        String nestedA = withNested ? '''
                private NestedA nested;
                public NestedA getNested() { return nested; }
                public void setNested(NestedA nested) { this.nested = nested; }
        ''' : ''
        String nestedB = withNested ? '''
                private NestedB nested;
                public NestedB getNested() { return nested; }
                public void setNested(NestedB nested) { this.nested = nested; }
        ''' : ''
        write('src/main/java/fixture/SharedA.java', """
            package fixture;

            import org.springframework.boot.context.properties.ConfigurationProperties;

            @ConfigurationProperties("fixture.shared")
            public class SharedA {
                private String firstValue;
                ${nestedA}
                public String getFirstValue() { return firstValue; }
                public void setFirstValue(String firstValue) { this.firstValue = firstValue; }
            }
        """.stripIndent())
        write('src/main/java/fixture/SharedB.java', """
            package fixture;

            import org.springframework.boot.context.properties.ConfigurationProperties;

            @ConfigurationProperties("fixture.shared")
            public class SharedB {
                private String secondValue;
                ${nestedB}
                public String getSecondValue() { return secondValue; }
                public void setSecondValue(String secondValue) { this.secondValue = secondValue; }
            }
        """.stripIndent())
        if (withNested) {
            write('src/main/java/fixture/NestedA.java', '''
                package fixture;

                public class NestedA {
                    private boolean enabled;
                    public boolean isEnabled() { return enabled; }
                    public void setEnabled(boolean enabled) { this.enabled = enabled; }
                }
            '''.stripIndent())
            write('src/main/java/fixture/NestedB.java', '''
                package fixture;

                public class NestedB {
                    private String label;
                    public String getLabel() { return label; }
                    public void setLabel(String label) { this.label = label; }
                }
            '''.stripIndent())
        }
    }

    private void writeGroovyDslConfiguration(boolean includeSessionTimeout) {
        String sessionTimeout = includeSessionTimeout ? 'sessionTimeout = 30' : ''
        write('src/dsl/fixture/SecurityConfig.groovy', """
            package fixture

            throw new AssertionError('The configuration metadata task must not execute DSL sources')

            security {
                userLookup {
                    userDomainClassName = 'fixture.User'
                    enabled = true
                }
                oauth {
                    client {
                        clientId = 'client'
                    }
                }
                authentication {
                    maxAttempts = 3
                    session.timeout = 30
                    roles = ['ROLE_USER', 'ROLE_ADMIN']
                    options = [maxSessions: 1, rememberMe: true]
                    dynamicDefault = System.getProperty('fixture.security.dynamic')
                    environmentDefault = System.getenv('FIXTURE_SECURITY_ENABLED') ? 'enabled' : 'disabled'
                    ${sessionTimeout}
                }
            }
        """.stripIndent())
    }

    private void writeTypedSecurityConfiguration() {
        write('src/main/java/fixture/SecurityConfiguration.java', '''
            package fixture;

            import org.springframework.boot.context.properties.ConfigurationProperties;

            @ConfigurationProperties("grails.plugin.springsecurity.authentication")
            public class SecurityConfiguration {
                private Long maxAttempts;

                public Long getMaxAttempts() { return maxAttempts; }
                public void setMaxAttempts(Long maxAttempts) { this.maxAttempts = maxAttempts; }
            }
        '''.stripIndent())
    }

    private BuildResult run(String... arguments) {
        runner(arguments).build()
    }

    private GradleRunner runner(String... arguments) {
        GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withArguments(*arguments, '--stacktrace')
                .withPluginClasspath()
    }

    private void configureDslMetadata(String relativePath) {
        projectDir.resolve('build.gradle').toFile() << """
            tasks.named('generateConfigurationMetadata') {
                dslConfigurationFiles.from(file('${relativePath}'))
                dslRootPrefixes.put('security', 'grails.plugin.springsecurity')
            }
        """.stripIndent()
    }

    private File write(String relativePath, String contents) {
        File file = projectDir.resolve(relativePath).toFile()
        file.parentFile.mkdirs()
        file.text = contents
        file
    }

    private File metadataFile() {
        projectDir.resolve('build/generated/configurationMetadata/META-INF/spring-configuration-metadata.json').toFile()
    }

    private Map readMetadata() {
        new JsonSlurper().parse(metadataFile()) as Map
    }

    private static Map property(Map metadata, String name) {
        metadata.properties.find { Map property -> property.name == name } as Map
    }

    private static Map group(Map metadata, String name) {
        metadata.groups.find { Map group -> group.name == name } as Map
    }

    private int metadataEntryCount() {
        jarEntries().count { String name -> name == 'META-INF/spring-configuration-metadata.json' }
    }

    private List<String> jarEntries() {
        File jar = projectDir.resolve('build/libs/metadata-fixture.jar').toFile()
        ZipFile zip = new ZipFile(jar)
        try {
            zip.entries().toList()*.name
        } finally {
            zip.close()
        }
    }

    private static File findWorkspace() {
        File current = new File(System.getProperty('user.dir')).absoluteFile
        while (current != null && !new File(current, 'grails-configuration-metadata').isDirectory()) {
            current = current.parentFile
        }
        assert current != null
        current
    }

    private static String path(File file) {
        // Canonical, not absolute: Gradle reports the resolved path, and on macOS the temporary
        // directory these tests run in is reached through the /var -> /private/var symlink, so an
        // absolute path never matches what the build prints.
        file.canonicalPath.replace('\\', '/')
    }
}
