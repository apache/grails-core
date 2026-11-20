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
package grails.init

import java.net.Authenticator
import java.nio.file.Files
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

import javax.tools.JavaCompiler
import javax.tools.ToolProvider

import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll
import uk.org.webcompere.systemstubs.SystemStubs

/**
 * The class the wrapper starts in the {@code grails-cli} jar it downloaded: the one the jar names,
 * so that a wrapper committed in an application starts a later version of Grails.
 */
class StartClassSpec extends Specification {

    private static final String STARTED = 'grails.wrapper.test.started'
    // whether the context class loader of the thread was that of the downloaded jar when the command line started
    private static final String CONTEXT = 'grails.wrapper.test.contextClassLoader'

    @TempDir
    File dir

    void cleanup() {
        System.clearProperty(STARTED)
        System.clearProperty(CONTEXT)
    }

    def "the start class is the Main-Class of the manifest of the jar"() {
        given:
        File jar = cliJar('grails-cli-all.jar', 'org.example.cli.Moved', ['org.example.cli.Moved', StartClass.DEFAULT])

        expect:
        StartClass.resolve(jar) == 'org.example.cli.Moved'
        StartClass.load(jar).name == 'org.example.cli.Moved'
    }

    def "a jar of Grails 7 or 8 names the class those versions start with"() {
        given:
        File jar = cliJar('grails-cli-all.jar', 'org.apache.grails.cli.DelegatingShellApplication', ['org.apache.grails.cli.DelegatingShellApplication'])

        expect:
        StartClass.resolve(jar) == 'org.apache.grails.cli.DelegatingShellApplication'
    }

    @Unroll
    def "the start class is that of Grails 9 when the manifest #situation"() {
        given:
        File jar = cliJar('grails-cli-all.jar', mainClass, [StartClass.DEFAULT], manifest)

        expect:
        StartClass.resolve(jar) == 'org.apache.grails.cli.GrailsCli'
        StartClass.load(jar).name == 'org.apache.grails.cli.GrailsCli'

        where:
        situation                               | manifest | mainClass
        'is absent'                             | false    | null
        'names no start class'                  | true     | null
        'has an empty start class'              | true     | '   '
        'names a class the jar does not have'   | true     | 'org.example.cli.Missing'
        'names a class of the JDK'              | true     | 'java.lang.Runtime'
        'names a class of the wrapper'          | true     | 'grails.init.Start'
        'names a path and not a class'          | true     | '../../etc/passwd'
        'names a resource of the jar'           | true     | 'org/apache/grails/cli/GrailsCli'
        'has markup where the name should be'   | true     | '@|red org.example|@'
        'has a name longer than any class has'  | true     | (['a'] * 400).join('.')
    }

    def "a jar that has a class by the name of a class of the wrapper does not start the wrapper again"() {
        given: 'the jar has grails.init.Start and names it, and the class loader of the jar asks its parent first'
        File jar = cliJar('grails-cli-all.jar', 'grails.init.Start', ['grails.init.Start'])

        when:
        StartClass.load(jar)

        then:
        IllegalStateException e = thrown()
        e.message == "The start class [grails.init.Start] of the Grails command line at [${jar.absolutePath}] is not a class of that file."
    }

    def "a jar without a start class is refused with the file and what to do"() {
        given:
        File jar = cliJar('grails-cli-all.jar', null, ['org.example.cli.Other'])

        when:
        StartClass.load(jar)

        then:
        IllegalStateException e = thrown()
        e.message == "The Grails command line at [${jar.absolutePath}] has no start class [org.apache.grails.cli.GrailsCli]. " +
                'It is not a version of Grails this wrapper can start, or the download is incomplete: delete the file and run the command again.'
        e.cause instanceof ClassNotFoundException
    }

    def "a file that is no jar is refused"() {
        given:
        File jar = new File(dir, 'grails-cli-all.jar')
        jar.text = 'not a jar'

        when:
        StartClass.resolve(jar)

        then:
        thrown(IOException)
    }

    @Unroll
    def "the wrapper downloads the command line and starts #startClass, with the arguments it was given"() {
        given: 'a repository with the jar of a version, and a wrapper home that has nothing'
        File repository = new File(dir, 'repository')
        File versionDirectory = new File(repository, 'org/apache/grails/grails-cli/9.0.0')
        versionDirectory.mkdirs()
        File published = cliJar('published.jar', mainClass, [startClass])
        Files.copy(published.toPath(), new File(versionDirectory, 'grails-cli-9.0.0-all.jar').toPath())
        File home = new File(dir, 'home')
        home.mkdirs()
        Authenticator authenticator = Authenticator.getDefault()
        ClassLoader contextClassLoader = Thread.currentThread().contextClassLoader
        System.setProperty('grails.repo.url', repository.absolutePath)

        when:
        SystemStubs.withEnvironmentVariable('GRAILS_WRAPPER_HOME', home.absolutePath)
                .and('PREFERRED_GRAILS_VERSION', '9.0.0')
                .execute {
                    new GrailsWrapper().execute(['help', 'create-app'] as String[])
                }

        then:
        System.getProperty(STARTED) == "${startClass}:help,create-app"
        System.getProperty(CONTEXT) == 'true'
        new File(home, 'wrapper/9.0.0/grails-cli-9.0.0-all.jar').isFile()

        cleanup:
        System.clearProperty('grails.repo.url')
        Authenticator.setDefault(authenticator)
        Thread.currentThread().contextClassLoader = contextClassLoader

        where:
        mainClass                                          | startClass
        'org.example.cli.Moved'                            | 'org.example.cli.Moved'
        'org.apache.grails.cli.DelegatingShellApplication' | 'org.apache.grails.cli.DelegatingShellApplication'
        null                                               | 'org.apache.grails.cli.GrailsCli'
    }

    /**
     * A jar with a class for each name, whose {@code main} records its name and its arguments.
     */
    private File cliJar(String fileName, String mainClass, List<String> classNames, boolean withManifest = true) {
        File sources = new File(dir, "sources-${fileName}")
        File classes = new File(dir, "classes-${fileName}")
        classes.mkdirs()
        List<String> sourceFiles = classNames.collect { String className ->
            int dot = className.lastIndexOf('.')
            String packageName = className.substring(0, dot)
            String simpleName = className.substring(dot + 1)
            File source = new File(sources, className.replace('.', '/') + '.java')
            source.parentFile.mkdirs()
            source.text = """\
                package ${packageName};
                public class ${simpleName} {
                    public static void main(String[] args) {
                        System.setProperty("${STARTED}", "${className}:" + String.join(",", args));
                        System.setProperty("${CONTEXT}", String.valueOf(Thread.currentThread().getContextClassLoader() == ${simpleName}.class.getClassLoader()));
                    }
                }
                """.stripIndent()
            source.path
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler()
        assert compiler.run(null, null, null, (['-d', classes.path] + sourceFiles) as String[]) == 0

        File jar = new File(dir, fileName)
        Manifest manifest = new Manifest()
        manifest.mainAttributes.put(Attributes.Name.MANIFEST_VERSION, '1.0')
        if (mainClass != null) {
            manifest.mainAttributes.put(Attributes.Name.MAIN_CLASS, mainClass)
        }
        jar.withOutputStream { OutputStream stream ->
            JarOutputStream out = withManifest ? new JarOutputStream(stream, manifest) : new JarOutputStream(stream)
            classNames.each { String className ->
                String entry = className.replace('.', '/') + '.class'
                out.putNextEntry(new JarEntry(entry))
                out << new File(classes, entry).bytes
                out.closeEntry()
            }
            out.close()
        }
        jar
    }
}
