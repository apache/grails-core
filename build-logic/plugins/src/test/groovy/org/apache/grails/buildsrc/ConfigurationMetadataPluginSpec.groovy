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
                implementation 'org.apache.groovy:groovy:5.0.7'
            }
            sourceSets.main.groovy.srcDir '${path(new File(workspace, 'grails-configuration-metadata/src/main/groovy'))}'
            sourceSets.main.resources.srcDir '${path(new File(workspace, 'grails-configuration-metadata/src/main/resources'))}'
        """.stripIndent())
        write('build.gradle', '''
            plugins {
                id 'groovy'
                id 'org.apache.grails.buildsrc.configuration-metadata'
            }

            repositories { mavenCentral() }

            dependencies {
                implementation 'org.apache.groovy:groovy:5.0.7'
                implementation 'org.springframework.boot:spring-boot:4.1.0'
            }

        '''.stripIndent())
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
            }

            class GroovyNested {
                boolean enabled
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
        group(metadata, 'fixture.groovy.nested').type == 'fixture.GroovyNested'
        group(metadata, 'fixture.groovy.nested').sourceType == 'fixture.GroovyConfiguration'
        property(metadata, 'fixture.java.nested') == null
        property(metadata, 'fixture.groovy.nested') == null
        property(metadata, 'fixture.java.nested.enabled').type == 'boolean'
        property(metadata, 'fixture.groovy.nested.enabled').type == 'boolean'
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

        when: 'the DSL source is deleted without cleaning'
        projectDir.resolve('src/dsl/fixture/SecurityConfig.groovy').toFile().delete()
        BuildResult deleted = run('jar')
        Map deletedMetadata = readMetadata()

        then: 'DSL-derived metadata is removed while the curated overlay remains'
        deleted.task(':generateConfigurationMetadata').outcome == TaskOutcome.SUCCESS
        property(deletedMetadata, 'grails.plugin.springsecurity.oauth.client.clientId') == null
        property(deletedMetadata, 'grails.plugin.springsecurity.authentication.sessionTimeout') == null
        property(deletedMetadata, 'grails.plugin.springsecurity.userLookup.userDomainClassName').defaultValue == 'overlay.User'
        metadataEntryCount() == 1
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
        property(metadata, 'grails.plugin.springsecurity.literals.nullMap').defaultValue == [first: null, second: 'present']
        property(metadata, 'grails.plugin.springsecurity.literals.unsupportedMap').type == 'java.util.Map'
        !property(metadata, 'grails.plugin.springsecurity.literals.unsupportedMap').containsKey('defaultValue')
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

    def "generates metadata when no additional metadata file exists"() {
        given:
        projectDir.resolve('src/main/resources/META-INF/additional-spring-configuration-metadata.json').toFile().delete()

        when:
        BuildResult result = run('generateConfigurationMetadata')

        then:
        result.task(':generateConfigurationMetadata').outcome == TaskOutcome.SUCCESS
        property(readMetadata(), 'fixture.java.names').type == 'java.util.List<java.lang.String>'
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
        File jar = projectDir.resolve('build/libs/metadata-fixture.jar').toFile()
        ZipFile zip = new ZipFile(jar)
        try {
            zip.entries().toList().count { it.name == 'META-INF/spring-configuration-metadata.json' }
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
        file.absolutePath.replace('\\', '/')
    }
}
