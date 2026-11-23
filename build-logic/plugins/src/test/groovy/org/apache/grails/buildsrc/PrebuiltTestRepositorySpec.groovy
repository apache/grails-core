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

import java.time.Duration

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Requires
import spock.lang.Specification
import spock.lang.TempDir

/**
 * The property that has the build of Forge use the repository of the tests as it is, in a checkout that is
 * laid out as the real one: a framework build and a build of the Grails Gradle plugins that publish into
 * {@code build/local-maven} and leave their record there, and a build of Forge whose settings are the lines
 * of {@code grails-forge/settings.gradle} that read the property, and whose test task is wired by the real
 * {@code grails-forge/gradle/test-config.gradle}.
 */
@Requires({ GitCheckout.available })
class PrebuiltTestRepositorySpec extends Specification {

    private static final String VERSION = '1.2.3-SNAPSHOT'
    private static final String PUBLISH = 'publishAllPublicationsToTestCaseMavenRepoRepository'

    @TempDir
    File tmp

    GitCheckout checkout
    File forge
    File repository

    def setup() {
        checkout = new GitCheckout(new File(tmp, 'checkout'))
        forge = new File(checkout.directory, 'grails-forge')
        repository = new File(checkout.directory, 'build/local-maven')
        checkout.write('.asf.yaml', '')
        checkout.write('.gitignore', 'build/\n.gradle/\n')
        checkout.write('gradle.properties', "projectVersion=${VERSION}\norg.gradle.configuration-cache=true\n")
        checkout.write('grails-core/src/main/groovy/A.groovy', 'class A {}')

        // the two builds that fill the repository: a publish task that writes the artifact the property looks
        // for, and the plugin that has it end with the record
        checkout.write('settings.gradle', "rootProject.name = 'framework'\ninclude 'grails-wrapper'\n")
        checkout.write('build.gradle', publishingBuild('grails-bom', 'pom'))
        checkout.write('grails-wrapper/build.gradle', """
            tasks.register('installDist') {
                File install = layout.buildDirectory.dir('install/apache-grails-wrapper-bin').get().asFile
                outputs.dir(install)
                doLast {
                    ['grailsw', 'grailsw.bat', 'grails-wrapper.jar'].each { String name -> new File(install, name).text = name }
                }
            }
        """)
        checkout.write('grails-gradle/settings.gradle', "rootProject.name = 'grails-gradle'\n")
        checkout.write('grails-gradle/build.gradle', publishingBuild('grails-gradle-plugins', 'jar'))

        checkout.write('grails-forge/gradle.properties', 'org.gradle.configuration-cache=true\n')
        // Exercise the actual settings wiring, without the unrelated Develocity and build-cache setup.
        String realSettings = new File('../../grails-forge/settings.gradle').text
        int start = realSettings.indexOf('// -PprebuiltTestRepository:')
        int end = realSettings.indexOf('// No build packages', start)
        assert start >= 0 && end > start
        checkout.write('grails-forge/settings.gradle', '''
            plugins {
                id 'org.apache.grails.buildsrc.repo'
            }
            rootProject.name = 'grails-forge'
        ''' + realSettings.substring(start, end) + '''
            if (prebuiltTestRepository) {
                println 'DEPENDENCY_REPOSITORY=' + prebuiltTestRepository.directory
            }
        ''')
        checkout.write('grails-forge/build.gradle', """
            plugins {
                id 'java'
            }
            ext {
                doNotCacheTests = false
                configuredTestParallel = 1
                forgeTestsNeedLocalMaven = true
            }
            apply from: '${new File('../../grails-forge/gradle/test-config.gradle').canonicalFile.toURI()}'

            // a test task that needs nothing resolved: it finds a class to run, and then no JUnit to run it with
            tasks.register('specs', Test) {
                testClassesDirs = files('classes')
                classpath = files()
            }

            println 'INCLUDED_BUILDS=' + gradle.includedBuilds*.name.sort()
            println 'GRAILS_REPO_URL=' + tasks.named('specs', Test).get().environment['GRAILS_REPO_URL']
        """)
        checkout.write('grails-forge/classes/SomeSpec.class', 'no class')
        checkout.write('grails-forge/src/test/java/SomeTest.java', 'class SomeTest {}')
        checkout.commit('first')
    }

    void "without the property the specs depend on the publish of both builds, and are given build/local-maven"() {
        when:
        BuildResult result = forge('specs', '--dry-run', *arguments)

        then:
        result.output.contains(':grails-core:publishAllPublicationsToTestCaseMavenRepoRepository SKIPPED')
        result.output.contains(':grails-gradle:publishAllPublicationsToTestCaseMavenRepoRepository SKIPPED')
        result.output.contains('INCLUDED_BUILDS=[grails-core, grails-gradle]')
        result.output.readLines().any { String line -> line ==~ /GRAILS_REPO_URL=file:.*\/checkout\/build\/local-maven\/?/ }
        !result.output.contains('DEPENDENCY_REPOSITORY=')

        where:
        arguments << [[], ['-PprebuiltTestRepository=false']]
    }

    void "with the property nothing is published, the framework build is not included, and the specs are given the repository: #arguments"() {
        given:
        publish()

        when:
        BuildResult result = forge('specs', '--dry-run', *arguments)

        then:
        !result.output.contains(PUBLISH)
        result.output.contains(':specs SKIPPED')
        result.output.contains('INCLUDED_BUILDS=[]')
        result.output.contains("GRAILS_REPO_URL=${repository.canonicalFile.toURI()}")
        result.output.contains("DEPENDENCY_REPOSITORY=${repository.canonicalPath}")
        !result.output.contains('WARNING')

        where:
        arguments << [['-PprebuiltTestRepository'], ['-PprebuiltTestRepository=true'], ['-PprebuiltTestRepository=../build/local-maven']]
    }

    void "the property takes the path of a repository, as a workflow gives one it downloaded"() {
        given:
        publish()
        File downloaded = new File(tmp, 'downloaded repository')
        assert repository.renameTo(downloaded)

        when:
        BuildResult result = forge('specs', '--dry-run', "-PprebuiltTestRepository=${downloaded.absolutePath}")

        then:
        result.output.contains("GRAILS_REPO_URL=${downloaded.canonicalFile.toURI()}")
        result.output.contains("DEPENDENCY_REPOSITORY=${downloaded.canonicalPath}")

        when: 'in another checkout of the same sources'
        GitCheckout other = checkout.cloneTo(new File(tmp, 'other'))
        result = run(new File(other.directory, 'grails-forge'), 'specs', '--dry-run', "-PprebuiltTestRepository=${downloaded.absolutePath}").build()

        then:
        result.output.contains("GRAILS_REPO_URL=${downloaded.canonicalFile.toURI()}")
    }

    void "the publish leaves the record of each build, the POMs of the BOMs and the Grails wrapper in the repository"() {
        when:
        publish()
        Map<String, String> framework = TestRepositoryMarker.read(repository, TestRepositoryMarker.FRAMEWORK)
        Map<String, String> plugins = TestRepositoryMarker.read(repository, TestRepositoryMarker.GRADLE_PLUGINS)

        then:
        framework.version == VERSION
        framework.callSites == 'indy'
        framework.publication == 'slim'
        framework.sbom == 'true'
        framework.fingerprint == FrameworkSources.of(checkout.directory).fingerprint
        framework.published ==~ /\d{4}-\d\d-\d\dT\d\d:\d\d:\d\dZ/
        plugins.version == VERSION
        plugins.fingerprint == framework.fingerprint
        TestRepositoryMarker.readSources(repository, TestRepositoryMarker.FRAMEWORK).commit == checkout.head()

        and:
        TestRepositoryMarker.FRAMEWORK_OUTPUTS.every { String path -> TestRepositoryMarker.frameworkOutput(repository, path).isFile() }
        TestRepositoryMarker.frameworkOutput(repository, 'grails-bom/hibernate7/build/publications/maven/pom-default.xml').text.contains('hibernate7')
        !new File(repository, "${TestRepositoryMarker.FRAMEWORK_OUTPUTS_DIRECTORY}/grails-gradle").exists()
    }

    void "the environment property selects the repository without command-line properties"() {
        given:
        publish()
        Map<String, String> environment = new LinkedHashMap<>(System.getenv())
        environment.keySet().removeAll { String key -> key.startsWith('ORG_GRADLE_PROJECT_') }
        environment.put('ORG_GRADLE_PROJECT_prebuiltTestRepository', repository.absolutePath)

        when:
        BuildResult result = run(forge, 'specs', '--dry-run').withEnvironment(environment).build()

        then:
        result.output.contains("GRAILS_REPO_URL=${repository.canonicalFile.toURI()}")
        !result.output.contains(PUBLISH)
    }

    void "framework coordinates resolve exclusively from the selected repository"() {
        given:
        File artifact = checkout.write("build/local-maven/org/apache/grails/probe/${VERSION}/probe-${VERSION}.jar", 'fixture jar')
        checkout.write("build/local-maven/org/apache/grails/probe/${VERSION}/probe-${VERSION}.pom", """
            <project><modelVersion>4.0.0</modelVersion><groupId>org.apache.grails</groupId>
            <artifactId>probe</artifactId><version>${VERSION}</version></project>
        """)
        new File(forge, 'build.gradle') << """
            configurations { frameworkProbe }
            dependencies { frameworkProbe 'org.apache.grails:probe:${VERSION}' }
            tasks.register('resolveFramework') {
                def artifacts = configurations.frameworkProbe
                doLast { println 'RESOLVED=' + artifacts.singleFile.name }
            }
        """
        publish()

        when:
        BuildResult result = forge('resolveFramework', '-PprebuiltTestRepository')

        then:
        result.output.contains("RESOLVED=probe-${VERSION}.jar")
        !result.output.contains(PUBLISH)

        when: 'a missing artifact cannot fall back to a remote copy with the same coordinates'
        assert artifact.delete()
        result = run(forge, 'resolveFramework', '-PprebuiltTestRepository').buildAndFail()

        then:
        result.output.contains("Could not find probe-${VERSION}.jar")
        !result.output.contains('https://repo.maven.apache.org/maven2/org/apache/grails/probe')
    }

    void "skipping Forge specs leaves out the default publication prerequisites"() {
        when:
        BuildResult result = forge('specs', '--dry-run', flag)

        then:
        !result.output.contains(PUBLISH)

        where:
        flag << ['-PskipTests', '-PskipForgeTests']
    }

    void "a partial or failed Maven publication invalidates the previous complete record"() {
        given:
        File script = new File(checkout.directory, 'build.gradle')
        script.text = script.text.replace("tasks.register('${PUBLISH}')", "tasks.register('prepareRepository')")
        script << '''
            apply plugin: 'maven-publish'
            publishing {
                publications {
                    probe(MavenPublication) {
                        groupId = 'org.apache.grails'
                        artifactId = 'probe'
                        version = projectVersion
                    }
                }
                repositories {
                    maven {
                        name = 'TestCaseMavenRepo'
                        url = layout.buildDirectory.dir('local-maven')
                    }
                }
            }
            boolean failPublish = providers.gradleProperty('failPublish').isPresent()
            tasks.named('publishAllPublicationsToTestCaseMavenRepoRepository') {
                dependsOn 'prepareRepository'
            }
            tasks.withType(PublishToMavenRepository).configureEach {
                doLast {
                    if (failPublish) {
                        throw new GradleException('interrupted publication')
                    }
                }
            }
        '''
        publish()

        when:
        GradleRunner runner = run(checkout.directory, 'publishProbePublicationToTestCaseMavenRepoRepository', *arguments)
        BuildResult result = fail ? runner.buildAndFail() : runner.build()

        then:
        !TestRepositoryMarker.file(repository, TestRepositoryMarker.FRAMEWORK).exists()
        TestRepositoryMarker.file(repository, TestRepositoryMarker.GRADLE_PLUGINS).isFile()
        result.task(':publishProbePublicationToTestCaseMavenRepoRepository').outcome == (fail ? TaskOutcome.FAILED : TaskOutcome.SUCCESS)

        when:
        result = run(forge, 'specs', '--dry-run', '-PprebuiltTestRepository').buildAndFail()

        then:
        result.output.contains('It has no record of a complete publish of grails-core')

        where:
        arguments                       | fail
        []                              | false
        ['-PfailPublish']                | true
        ['-PgrailsIndy=false']           | false
    }

    void "a malformed, wrong-version or incompatible record fails even when stale sources are allowed"() {
        given:
        publish()
        File marker = TestRepositoryMarker.file(repository, TestRepositoryMarker.FRAMEWORK)
        marker.text = marker.text.replace(original, replacement)

        when:
        BuildResult result = run(forge, 'specs', '--dry-run', '-PprebuiltTestRepository', '-PallowStaleTestRepository=true').buildAndFail()

        then:
        result.output.contains(message)

        where:
        original                        | replacement           | message
        'format=2'                      | 'format=999'          | 'incomplete or unsupported record'
        'callSites=indy'                 | 'callSites=unknown'   | 'incomplete or unsupported record'
        "version=${VERSION}"            | 'version=0.1'         | 'as 0.1, and this build is for'
        'javaVersion=21'                 | 'javaVersion=99'      | 'for Java 99, newer than this build'
    }

    void "the record says what kind of publish it was"() {
        when:
        run(checkout.directory, PUBLISH, '-PgrailsIndy=false', '-PcompleteTestRepository=true').build()

        then:
        TestRepositoryMarker.read(repository, TestRepositoryMarker.FRAMEWORK).callSites == 'classic'
        TestRepositoryMarker.read(repository, TestRepositoryMarker.FRAMEWORK).publication == 'complete'

        when:
        run(checkout.directory, PUBLISH, '-PtestRepositoryWithoutSbom=true').build()

        then:
        TestRepositoryMarker.read(repository, TestRepositoryMarker.FRAMEWORK).callSites == 'indy'
        TestRepositoryMarker.read(repository, TestRepositoryMarker.FRAMEWORK).sbom == 'false'
    }

    void "the publish of Forge's own specs writes the record too, so the repository can be used as it is afterwards"() {
        when: 'the specs run without the property, which publishes through the included builds'
        BuildResult result = run(forge, 'specs').buildAndFail()

        then: 'the build of the plugins is in the directory of the framework build, which is where Gradle puts its path'
        result.task(':grails-core:publishAllPublicationsToTestCaseMavenRepoRepository').outcome == TaskOutcome.SUCCESS
        result.task(':grails-core:grails-gradle:publishAllPublicationsToTestCaseMavenRepoRepository').outcome == TaskOutcome.SUCCESS
        result.task(':specs').outcome == TaskOutcome.FAILED
        TestRepositoryMarker.read(repository, TestRepositoryMarker.FRAMEWORK).version == VERSION
        TestRepositoryMarker.read(repository, TestRepositoryMarker.GRADLE_PLUGINS).version == VERSION

        when:
        result = forge('specs', '--dry-run', '-PprebuiltTestRepository')

        then:
        !result.output.contains(PUBLISH)
    }

    void "a repository that is not there fails the build before anything is configured"() {
        when:
        BuildResult result = run(forge, 'specs', '--dry-run', '-PprebuiltTestRepository').buildAndFail()

        then:
        result.output.contains("The repository of the tests that -PprebuiltTestRepository names, ${repository.canonicalPath}, cannot be used. There is no such directory.")
        result.output.contains("'./gradlew publishAllPublicationsToTestCaseMavenRepoRepository'")
        result.output.contains('or run without -PprebuiltTestRepository to have this build publish it.')
        !result.output.contains('INCLUDED_BUILDS=')
    }

    void "a repository of another version fails the build, and the message names the version it holds"() {
        given:
        publish()
        checkout.write('gradle.properties', 'projectVersion=2.0.0-SNAPSHOT\n')

        when:
        BuildResult result = run(forge, 'specs', '--dry-run', '-PprebuiltTestRepository').buildAndFail()

        then:
        result.output.contains("It holds no org.apache.grails:grails-bom of 2.0.0-SNAPSHOT, the version this build is for: it holds ${VERSION}, published ") && result.output =~ /published \d+ minutes? ago \(\d{4}-/
        result.output.contains("It holds no org.apache.grails:grails-gradle-plugins of 2.0.0-SNAPSHOT, the version this build is for: it holds ${VERSION}, published ") && result.output =~ /published \d+ minutes? ago \(\d{4}-/
    }

    void "a repository only one of the two builds published to fails the build"() {
        given:
        run(checkout.directory, PUBLISH).build()

        when:
        BuildResult result = run(forge, 'specs', '--dry-run', '-PprebuiltTestRepository').buildAndFail()

        then:
        result.output.contains("It holds no org.apache.grails:grails-gradle-plugins of ${VERSION}, the version this build is for: it holds none at all.")
    }

    void "a repository without the record of a complete publish fails the build"() {
        given:
        publish()
        assert TestRepositoryMarker.file(repository, TestRepositoryMarker.FRAMEWORK).delete()

        when:
        BuildResult result = run(forge, 'specs', '--dry-run', '-PprebuiltTestRepository').buildAndFail()

        then:
        result.output.contains('It has no record of a complete publish of grails-core (grails-test-repository/grails-core.properties): ' +
                'it was published before that record was written, by the publish of single modules, or by a publish that failed.')
    }

    void "a framework compiled with the other call sites fails the build: published with #published, used with #used"() {
        given:
        publish(*published)

        when:
        BuildResult result = run(forge, 'specs', '--dry-run', '-PprebuiltTestRepository', *used).buildAndFail()

        then:
        result.output.contains(message)
        result.output.contains('those are different jars.')

        when: 'used as it was published'
        result = forge('specs', '--dry-run', '-PprebuiltTestRepository', *published)

        then:
        !result.output.contains(PUBLISH)

        where:
        published               | used                    | message
        ['-PgrailsIndy=false']  | []                      | 'with -PgrailsIndy=false, so with classic call sites in its Grails plugin modules, and this build is run without -PgrailsIndy=false'
        ['-PgrailsIndy=false']  | ['-PgrailsIndy=true']   | 'with -PgrailsIndy=false, so with classic call sites in its Grails plugin modules, and this build is run without -PgrailsIndy=false'
        []                      | ['-PgrailsIndy=false']  | 'with invokedynamic call sites, and this build is run with -PgrailsIndy=false, so for classic call sites'
    }

    void "a repository that lacks what Forge takes from the framework build besides its modules fails the build"() {
        given:
        publish()
        assert TestRepositoryMarker.frameworkOutput(repository, 'grails-wrapper/build/install/apache-grails-wrapper-bin/grailsw').delete()

        when:
        BuildResult result = run(forge, 'specs', '--dry-run', '-PprebuiltTestRepository').buildAndFail()

        then:
        result.output.contains('(grails-test-repository/framework): grails-wrapper/build/install/apache-grails-wrapper-bin/grailsw.')
    }

    void "a change of the framework since the publish fails the build, and the message says where and since when"() {
        given:
        publish()
        String published = checkout.head().take(10)

        when: 'a file of the framework is changed and not committed'
        checkout.write('grails-core/src/main/groovy/A.groovy', 'class A { int changed }')
        BuildResult result = run(forge, 'specs', '--dry-run', '-PprebuiltTestRepository').buildAndFail()

        then:
        result.output.contains("The repository of the tests that -PprebuiltTestRepository names, ${repository.canonicalPath}, is older than the sources.")
        result.output =~ /grails-core was published to it \d+ minutes? ago \(\d{4}-/
        result.output.contains("from commit ${published}, and the sources of the framework have changed since (now commit ${published}, 1 file not committed), in: grails-core.")
        result.output =~ /grails-gradle was published to it \d+ minutes? ago \(\d{4}-/
        result.output.contains('or pass -PallowStaleTestRepository=true to test against the jars as they are.')

        when: 'and committed'
        checkout.commit('change the framework')
        result = run(forge, 'specs', '--dry-run', '-PprebuiltTestRepository').buildAndFail()

        then:
        result.output.contains("from commit ${published}, and the sources of the framework have changed since (now commit ${checkout.head().take(10)}), in: grails-core.")

        when: 'published again'
        publish()
        result = forge('specs', '--dry-run', '-PprebuiltTestRepository')

        then:
        !result.output.contains(PUBLISH)
        !result.output.contains('WARNING')
    }

    void "a change of Forge since the publish is no change of the framework"() {
        given:
        publish()

        when:
        checkout.write('grails-forge/src/test/java/SomeTest.java', 'class SomeTest { int changed; }')
        checkout.write('grails-forge/src/test/java/OtherTest.java', 'class OtherTest {}')
        BuildResult result = forge('specs', '--dry-run', '-PprebuiltTestRepository')

        then:
        result.output.contains("GRAILS_REPO_URL=${repository.canonicalFile.toURI()}")
        !result.output.contains('WARNING')

        when:
        checkout.commit('change Forge')
        result = forge('specs', '--dry-run', '-PprebuiltTestRepository')

        then:
        !result.output.contains('WARNING')
    }

    void "with -PallowStaleTestRepository=true an old repository is used, and the build and the test task say so"() {
        given:
        publish()
        checkout.write('grails-core/src/main/groovy/A.groovy', 'class A { int changed }')

        when: 'the task runs: it says so, and then fails for want of a JUnit, which this build does not give it'
        BuildResult result = run(forge, 'specs', '-PprebuiltTestRepository', '-PallowStaleTestRepository=true').buildAndFail()

        then: 'once when the build is configured and once by the task'
        result.task(':specs').outcome == TaskOutcome.FAILED
        result.output.count("WARNING: The repository of the tests that -PprebuiltTestRepository names, ${repository.canonicalPath}, is older than the sources, " +
                'and -PallowStaleTestRepository=true has it used as it is.') == 2
        result.output.contains('in: grails-core.')
        !result.output.contains(PUBLISH)

        when: 'it does not excuse a repository that cannot be used at all'
        checkout.write('gradle.properties', 'projectVersion=2.0.0-SNAPSHOT\n')
        result = run(forge, 'specs', '-PprebuiltTestRepository', '-PallowStaleTestRepository=true').buildAndFail()

        then:
        result.output.contains('cannot be used. It holds no org.apache.grails:grails-bom of 2.0.0-SNAPSHOT')
    }

    void "the check is made again when the configuration is taken from the cache"() {
        given:
        publish()

        when:
        forge('specs', '--dry-run', '-PprebuiltTestRepository')
        BuildResult result = forge('specs', '--dry-run', '-PprebuiltTestRepository')

        then:
        result.output.contains('Reusing configuration cache.')

        when: 'the framework changes between two builds'
        checkout.write('grails-core/src/main/groovy/A.groovy', 'class A { int changed }')
        result = run(forge, 'specs', '--dry-run', '-PprebuiltTestRepository').buildAndFail()

        then:
        result.output.contains('is older than the sources.')

        when: 'a fresh repository is then removed after caching a successful check'
        publish()
        forge('specs', '--dry-run', '-PprebuiltTestRepository')
        assert TestRepositoryMarker.file(repository, TestRepositoryMarker.FRAMEWORK).delete()
        result = run(forge, 'specs', '--dry-run', '-PprebuiltTestRepository').buildAndFail()

        then:
        result.output.contains('It has no record of a complete publish of grails-core')
    }

    void "where Git does not say what the sources are the repository is used, and the build says that it cannot tell"() {
        given:
        publish()
        assert new File(checkout.directory, '.git').deleteDir()

        when:
        BuildResult result = forge('specs', '--dry-run', '-PprebuiltTestRepository')

        then:
        result.output =~ /WARNING: grails-core was published to it \d+ minutes? ago \(/
        result.output.contains('and whether the sources of the framework have changed since cannot be told: Git does not say what the sources are now.')
        result.output.contains("GRAILS_REPO_URL=${repository.canonicalFile.toURI()}")
    }

    void "how long ago is said in the unit that fits"() {
        expect:
        PrebuiltTestRepository.age(Duration.ofSeconds(seconds)) == said

        where:
        seconds        | said
        -5             | '0 minutes'
        59             | '0 minutes'
        60             | '1 minute'
        59 * 60        | '59 minutes'
        60 * 60        | '1 hour'
        47 * 3600      | '47 hours'
        48 * 3600      | '2 days'
        40 * 86400     | '40 days'
    }

    private void publish(String... arguments) {
        run(checkout.directory, PUBLISH, *arguments).build()
        run(new File(checkout.directory, 'grails-gradle'), PUBLISH, *arguments).build()
    }

    private BuildResult forge(String... arguments) {
        return run(forge, arguments).build()
    }

    private static GradleRunner run(File directory, String... arguments) {
        return GradleRunner.create()
                .withProjectDir(directory)
                .withArguments((arguments.toList() + ['--offline', '--stacktrace', '--max-workers=2', '-Dorg.gradle.jvmargs=-Xmx768m']) as String[])
                .withPluginClasspath()
                .withEnvironment(System.getenv().findAll { it.key != 'GRAILS_INCLUDE_MAVEN_LOCAL' && !it.key.startsWith('ORG_GRADLE_PROJECT_') })
    }

    // a root build script with the plugin, and a publish task that writes the artifact the property looks for and,
    // in the framework build, the POMs of the BOMs where the real build generates them
    private static String publishingBuild(String artifact, String extension) {
        return """
            plugins {
                id 'org.apache.grails.buildsrc.properties'
                id 'org.apache.grails.buildsrc.test-repository-marker'
            }
            boolean framework = rootProject.name == 'framework'
            File checkout = framework ? rootDir : rootDir.parentFile
            File published = new File(checkout, "build/local-maven/org/apache/grails/${artifact}/\${project.property('projectVersion')}/${artifact}.${extension}")
            List<File> poms = framework ? ['base', 'default', 'hibernate7'].collect { String bom -> new File(checkout, "grails-bom/\${bom}/build/publications/maven/pom-default.xml") } : []
            // as in the real builds, the task of the root project only depends on the tasks that publish
            def publishModule = tasks.register('publishModule') {
                doLast {
                    (poms + [published]).each { File file ->
                        file.parentFile.mkdirs()
                        file.text = "<project><!-- \${file.parentFile.parentFile.parentFile.parentFile.name} --></project>"
                    }
                }
            }
            tasks.register('${PUBLISH}') {
                dependsOn publishModule
            }
        """
    }
}
