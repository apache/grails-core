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
package grails.doc.gradle

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification
import spock.lang.TempDir

class ConvertAsciidocTaskSpec extends Specification {

    static final byte[] LOGO = [0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A] as byte[]

    @TempDir
    File projectDir

    @TempDir
    File otherProjectDir

    @TempDir
    File buildCacheDir

    void setup() {
        writeProject(projectDir)
    }

    void 'documents are converted, partials are only included, and the images are copied'() {
        when:
        BuildResult result = run(projectDir, 'convert')

        then:
        result.task(':convert').outcome == TaskOutcome.SUCCESS
        File output = new File(projectDir, 'build/docs')
        new File(output, 'index.html').file
        new File(output, 'sub/page.html').file
        !new File(output, '_partial.html').exists()
        new File(output, 'images/logo.png').bytes == LOGO

        and: 'the project attributes, includedir and the path attributes reach the documents'
        String index = new File(output, 'index.html').text
        index.contains('Revision 1.2.3')
        index.contains('Project docs-fixture of org.example at 1.2.3')
        index.contains('Partial content')
        index.contains('Example content')
    }

    void 'the attributes override the attributes describing the project'() {
        given:
        new File(projectDir, 'build.gradle') << """
            tasks.named('convert') { ConvertAsciidocTask it ->
                it.attributes.put('revnumber', 'overridden')
            }
        """

        when:
        run(projectDir, 'convert')

        then:
        new File(projectDir, 'build/docs/index.html').text.contains('Revision overridden')
    }

    void 'a change to an included file converts the documents again, also when the configuration cache entry is reused'() {
        given:
        run(projectDir, 'convert')

        when:
        new File(projectDir, 'examples/Example.groovy').text = "println 'Changed example content'"
        BuildResult changed = run(projectDir, 'convert')

        then:
        changed.output.contains('Configuration cache entry reused')
        changed.task(':convert').outcome == TaskOutcome.SUCCESS
        new File(projectDir, 'build/docs/index.html').text.contains('Changed example content')

        when:
        BuildResult unchanged = run(projectDir, 'convert')

        then:
        unchanged.task(':convert').outcome == TaskOutcome.UP_TO_DATE
    }

    void 'the build cache reuses the result in a copy of the project at another path'() {
        given:
        writeProject(otherProjectDir)

        when:
        run(projectDir, 'convert', '--build-cache')
        BuildResult copy = run(otherProjectDir, 'convert', '--build-cache')

        then:
        copy.task(':convert').outcome == TaskOutcome.FROM_CACHE
        new File(otherProjectDir, 'build/docs/index.html').text.contains('Example content')
    }

    private BuildResult run(File dir, String... arguments) {
        GradleRunner.create()
                .withProjectDir(dir)
                .withArguments(arguments.toList() + ['--stacktrace'])
                .build()
    }

    private void writeProject(File dir) {
        String classpath = System.getProperty('docsCoreClasspath').split(File.pathSeparator)
                .collect { "'${it.replace('\\', '/')}'" }.join(', ')
        new File(dir, 'settings.gradle').text = """
            rootProject.name = 'docs-fixture'
            buildCache {
                local {
                    directory = file('${buildCacheDir.absolutePath.replace('\\', '/')}')
                }
            }
        """
        new File(dir, 'gradle.properties').text = '''
            org.gradle.configuration-cache=true
            org.gradle.configuration-cache.problems=fail
        '''.stripIndent()
        new File(dir, 'build.gradle').text = """
            buildscript {
                dependencies {
                    classpath files(${classpath})
                }
            }

            import grails.doc.gradle.ConvertAsciidocTask

            group = 'org.example'
            version = '1.2.3'

            tasks.register('convert', ConvertAsciidocTask) { ConvertAsciidocTask it ->
                it.sourceDir = layout.projectDirectory.dir('src/docs')
                it.outputDir = layout.buildDirectory.dir('docs')
                it.pathAttributes = [examples: layout.projectDirectory.dir('examples').asFile]
                it.includedFiles.from(layout.projectDirectory.dir('examples'))
            }
        """
        write(dir, 'src/docs/index.adoc', '''
            = Fixture

            Revision {revnumber}

            Project {gradle-project-name} of {gradle-project-group} at {gradle-project-version}

            include::{includedir}/_partial.adoc[]

            include::{examples}/Example.groovy[]
        ''')
        write(dir, 'src/docs/_partial.adoc', 'Partial content')
        write(dir, 'src/docs/sub/page.adoc', '''
            = Page

            A page of its own
        ''')
        File logo = new File(dir, 'src/docs/images/logo.png')
        logo.parentFile.mkdirs()
        logo.bytes = LOGO
        write(dir, 'examples/Example.groovy', "println 'Example content'")
    }

    private static void write(File dir, String path, String text) {
        File file = new File(dir, path)
        file.parentFile.mkdirs()
        // AsciiDoc reads an indented line as a literal block, so the fixtures have none
        file.text = text.readLines()*.trim().join('\n').trim() + '\n'
    }
}
