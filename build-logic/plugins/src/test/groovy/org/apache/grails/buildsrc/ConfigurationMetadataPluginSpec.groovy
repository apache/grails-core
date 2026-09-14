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

            sourceSets {
                dup {
                    java.srcDir 'src/dup/java'
                }
            }

            dependencies {
                dupImplementation 'org.apache.groovy:groovy:5.0.7'
                dupImplementation 'org.springframework.boot:spring-boot:4.1.0'
            }

            tasks.named('generateConfigurationMetadata') {
                classesDirs.from(sourceSets.dup.output.classesDirs)
                dependsOn sourceSets.dup.output
            }
        '''.stripIndent())
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
        property(metadata, 'fixture.shared.nested.enabled').type == 'boolean'
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

    private BuildResult run(String... args) {
        runner(args).build()
    }

    private GradleRunner runner(String... arguments) {
        GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withArguments(*arguments, '--stacktrace')
                .withPluginClasspath()
    }

    private void write(String relativePath, String contents) {
        File file = projectDir.resolve(relativePath).toFile()
        file.parentFile.mkdirs()
        file.text = contents
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
