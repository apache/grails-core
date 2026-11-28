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
package org.apache.grails.cli

import java.lang.reflect.Method
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.stream.Collectors
import javax.tools.ToolProvider
import org.apache.grails.forge.cli.GrailsCli
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

/**
 * {@link DelegatingShellApplication}, the class every Grails wrapper starts, on a jar of its own with a command
 * line that writes down how it was started: it starts the {@code Main-Class} of its jar, and tells it whether the
 * wrapper that started it is one of Grails 9 and later. The class is loaded as a wrapper loads it, from a class
 * loader of the jar, here with nothing but the JDK behind it.
 */
class DelegatingShellApplicationSpec extends Specification {

    private static final String COMMAND_LINE = 'example.cli.CommandLine'
    private static final String STARTED = 'grails.cli.test.started'
    private static final String TOLD = 'grails.cli.test.earlierWrapper'

    @TempDir
    File dir

    void cleanup() {
        [STARTED, TOLD, DelegatingShellApplication.WRAPPER_VERSION, DelegatingShellApplication.EARLIER_WRAPPER].each { String name ->
            System.clearProperty(name)
        }
    }

    void "started without the property of the wrapper of Grails 9, the command line of the jar is told that an earlier wrapper started it"() {
        given:
        File jar = cliJar(COMMAND_LINE)

        when:
        start(jar, 'create-app', 'shop', '')

        then:
        System.getProperty(STARTED) == "${COMMAND_LINE}:create-app,shop,"
        System.getProperty(TOLD) == 'true'
    }

    void "started by the wrapper of Grails 9 or later the command line is only started, also where the JVM was given the other property"() {
        given:
        File jar = cliJar(COMMAND_LINE)
        System.setProperty(DelegatingShellApplication.WRAPPER_VERSION, '9.0.0')
        System.setProperty(DelegatingShellApplication.EARLIER_WRAPPER, 'true')

        when:
        start(jar, '--version')

        then:
        System.getProperty(STARTED) == "${COMMAND_LINE}:--version"
        System.getProperty(TOLD) == 'null'
        System.getProperty(DelegatingShellApplication.EARLIER_WRAPPER) == null
    }

    void "what the command line throws reaches the caller as it is"() {
        given:
        File jar = cliJar(COMMAND_LINE)

        when:
        start(jar, 'fail')

        then:
        IllegalArgumentException e = thrown()
        e.message == 'the command line failed'
    }

    @Unroll
    void "a jar whose manifest #situation is refused with what is wrong"() {
        given:
        File jar = cliJar(mainClass, withManifest)

        when:
        start(jar)

        then:
        IllegalStateException e = thrown()
        e.message == message.replace('JAR', jar.absoluteFile.toPath().toString())
        System.getProperty(STARTED) == null

        where:
        situation                         | withManifest | mainClass                                    | message
        'is absent'                       | false        | null                                         | 'The Grails command line at [JAR] names no Main-Class in its manifest, and org.apache.grails.cli.DelegatingShellApplication starts that class'
        'names no Main-Class'             | true         | null                                         | 'The Grails command line at [JAR] names no Main-Class in its manifest, and org.apache.grails.cli.DelegatingShellApplication starts that class'
        'has an empty Main-Class'         | true         | '  '                                         | 'The Grails command line at [JAR] names no Main-Class in its manifest, and org.apache.grails.cli.DelegatingShellApplication starts that class'
        'names the class itself'          | true         | DelegatingShellApplication.name              | 'The Grails command line at [JAR] names org.apache.grails.cli.DelegatingShellApplication as its Main-Class, which would start itself'
    }

    void "a Main-Class the jar does not have is not found"() {
        given:
        File jar = cliJar('example.cli.Missing')

        when:
        start(jar)

        then:
        ClassNotFoundException e = thrown()
        e.message == 'example.cli.Missing'
    }

    void "loaded from a directory and not from a jar, the class has no manifest to read and says so"() {
        given:
        File classes = new File(dir, 'classes/org/apache/grails/cli')
        classes.mkdirs()
        new File(classes, 'DelegatingShellApplication.class').bytes = shimClass()
        URLClassLoader loader = new URLClassLoader([new File(dir, 'classes').toURI().toURL()] as URL[], ClassLoader.platformClassLoader)

        when:
        main(loader, [] as String[])

        then:
        IllegalStateException e = thrown()
        e.message.startsWith('The Grails command line at [')
        e.message.contains('does not open as a jar')

        cleanup:
        loader.close()
    }

    void "the JVM ends with the exit code of the command line, which is started with the arguments as they are"() {
        given:
        File jar = cliJar(COMMAND_LINE)

        when:
        Process process = new ProcessBuilder(new File(System.getProperty('java.home'), 'bin/java').path, '-cp', jar.path, DelegatingShellApplication.name,
                'exit', '3', 'two words', '').redirectErrorStream(true).start()
        String said = process.inputStream.text

        then:
        process.waitFor(60, TimeUnit.SECONDS)
        process.exitValue() == 3
        said.readLines() == ["${COMMAND_LINE}:exit,3,two words, told true".toString()]
    }

    void "the jar of the artifact has this class alone, in a package no jar of the command line has, and is named by it as a module"() {
        given:
        File thin = new File(System.getProperty('grails.cli.jar'))
        File commandLine = new File(GrailsCli.protectionDomain.codeSource.location.toURI())

        when:
        Map<String, Object> ofThin = contents(thin)
        Map<String, Object> ofCommandLine = contents(commandLine)

        then:
        ofThin.classes == ['org/apache/grails/cli/DelegatingShellApplication.class']
        ofThin.packages == ['org.apache.grails.cli'] as Set
        ofThin.moduleName == 'org.apache.grails.cli'

        and: 'the command line is in grails-forge-cli, which has no package of this one and none under it'
        (ofCommandLine.packages as Set<String>).every { String name -> name != 'org.apache.grails.cli' && !name.startsWith('org.apache.grails.cli.') }
        (ofCommandLine.packages as Set<String>).contains('org.apache.grails.forge.cli')
        !commandLine.isFile() || ofCommandLine.moduleName == 'org.apache.grails.forge.cli'
    }

    void "the class names no class of the command line: its constant pool has the names of the JDK and its own"() {
        when: 'class names in the constant pool of a class file are written with slashes'
        String constants = new String(shimClass(), 'ISO-8859-1')

        then:
        !constants.contains('org/apache/grails/forge')
        !constants.contains('org/grails')
        !constants.contains('groovy')
    }

    private static void start(File jar, String... args) {
        URLClassLoader loader = new URLClassLoader([jar.toURI().toURL()] as URL[], ClassLoader.platformClassLoader)
        try {
            main(loader, args)
        } finally {
            loader.close()
        }
    }

    private static void main(URLClassLoader loader, String[] args) {
        Class<?> shim = Class.forName(DelegatingShellApplication.name, true, loader)
        assert shim.classLoader.is(loader)
        Method main = shim.getMethod('main', String[])
        try {
            main.invoke(null, (Object) args)
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw e.cause
        }
    }

    private static byte[] shimClass() {
        return DelegatingShellApplication.getResourceAsStream('DelegatingShellApplication.class').withCloseable { InputStream stream -> stream.readAllBytes() }
    }

    /**
     * A jar with this class and a command line whose {@code main} writes down its name, its arguments and what it
     * was told, throws for {@code fail} and ends the JVM for {@code exit CODE}.
     */
    private File cliJar(String mainClass, boolean withManifest = true) {
        File sources = new File(dir, 'sources')
        File source = new File(sources, COMMAND_LINE.replace('.', '/') + '.java')
        source.parentFile.mkdirs()
        source.text = """\
            package example.cli;
            public class CommandLine {
                public static void main(String[] args) {
                    if (args.length > 0 && args[0].equals("fail")) {
                        throw new IllegalArgumentException("the command line failed");
                    }
                    String started = "${COMMAND_LINE}:" + String.join(",", args);
                    String told = String.valueOf(System.getProperty("${DelegatingShellApplication.EARLIER_WRAPPER}"));
                    if (args.length > 1 && args[0].equals("exit")) {
                        System.out.println(started + " told " + told);
                        System.exit(Integer.parseInt(args[1]));
                    }
                    System.setProperty("${STARTED}", started);
                    System.setProperty("${TOLD}", told);
                }
            }
            """.stripIndent()
        File classes = new File(dir, 'command-line-classes')
        classes.mkdirs()
        assert ToolProvider.systemJavaCompiler.run(null, null, null, '-d', classes.path, source.path) == 0

        Manifest manifest = new Manifest()
        manifest.mainAttributes.put(Attributes.Name.MANIFEST_VERSION, '1.0')
        if (mainClass != null) {
            manifest.mainAttributes.put(Attributes.Name.MAIN_CLASS, mainClass)
        }
        File jar = new File(dir, "cli-${UUID.randomUUID()}.jar")
        jar.withOutputStream { OutputStream stream ->
            JarOutputStream out = withManifest ? new JarOutputStream(stream, manifest) : new JarOutputStream(stream)
            out.putNextEntry(new JarEntry('org/apache/grails/cli/DelegatingShellApplication.class'))
            out << shimClass()
            out.closeEntry()
            String entry = COMMAND_LINE.replace('.', '/') + '.class'
            out.putNextEntry(new JarEntry(entry))
            out << new File(classes, entry).bytes
            out.closeEntry()
            out.close()
        }
        return jar
    }

    // the class files of a jar or a directory of classes, their packages, and the module name of a jar
    private static Map<String, Object> contents(File location) {
        List<String> classes
        String moduleName = null
        if (location.isFile()) {
            new JarFile(location).withCloseable { JarFile jar ->
                classes = jar.entries().toList()*.name.findAll { String name -> name.endsWith('.class') && !name.startsWith('META-INF/') }
                moduleName = jar.manifest?.mainAttributes?.getValue('Automatic-Module-Name')
            }
        } else {
            Path root = location.toPath()
            classes = Files.walk(root).withCloseable { stream ->
                stream.filter { Path path -> path.toString().endsWith('.class') }
                        .map { Path path -> root.relativize(path).toString().replace(File.separatorChar, '/' as char) }
                        .collect(Collectors.toList())
            }
        }
        Set<String> packages = classes.collect { String name -> name.substring(0, Math.max(0, name.lastIndexOf('/'))).replace('/', '.') } as Set<String>
        return [classes: classes.sort(), packages: packages, moduleName: moduleName]
    }
}
