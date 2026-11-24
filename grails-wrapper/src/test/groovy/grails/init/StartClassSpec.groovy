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
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.concurrent.TimeUnit
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

import javax.tools.JavaCompiler
import javax.tools.ToolProvider

import com.sun.nio.file.ExtendedOpenOption
import spock.lang.Requires
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll
import uk.org.webcompere.systemstubs.SystemStubs

/**
 * The class the wrapper starts in the {@code grails-cli} jar it downloaded: always
 * {@code org.apache.grails.cli.DelegatingShellApplication}, which every {@code grails-cli} jar since Grails 7 has
 * and which starts the command line of its jar, with the system property {@code grails.wrapper.version} set to the
 * version of the wrapper. The wrapper reads nothing else of the jar.
 */
class StartClassSpec extends Specification {

    private static final String START_CLASS = 'org.apache.grails.cli.DelegatingShellApplication'
    private static final String STARTED = 'grails.wrapper.test.started'
    // whether the context class loader of the thread was that of the downloaded jar when the command line started
    private static final String CONTEXT = 'grails.wrapper.test.contextClassLoader'
    // what the system property the wrapper sets for the start class was when it started
    private static final String SIGNAL = 'grails.wrapper.test.signal'

    @TempDir
    File dir

    void cleanup() {
        System.clearProperty(STARTED)
        System.clearProperty(CONTEXT)
        System.clearProperty(SIGNAL)
        System.clearProperty(StartClass.WRAPPER_VERSION_PROPERTY)
    }

    def "the start class has the name the wrappers of Grails 7 and 8 start, and the system property is that the command line reads"() {
        expect:
        StartClass.NAME == START_CLASS
        StartClass.WRAPPER_VERSION_PROPERTY == 'grails.wrapper.version'
    }

    @Unroll
    def "the start class is loaded from the jar whatever the manifest #situation"() {
        given:
        File jar = cliJar('grails-cli-all.jar', mainClass, [START_CLASS, 'org.example.cli.Moved'], manifest)

        when:
        Class<?> loaded = StartClass.load(jar)

        then:
        loaded.name == START_CLASS
        loaded.classLoader instanceof URLClassLoader
        (loaded.classLoader as URLClassLoader).URLs.toList() == [jar.toURI().toURL()]
        StartClass.defect(jar) == null

        cleanup:
        (loaded?.classLoader as URLClassLoader)?.close()

        where:
        situation                          | manifest | mainClass
        'is absent'                        | false    | null
        'names no start class'             | true     | null
        'names another class of the jar'   | true     | 'org.example.cli.Moved'
        'names the start class'            | true     | START_CLASS
        'names a class the jar lacks'      | true     | 'org.example.cli.Missing'
        'names a class of the wrapper'     | true     | 'grails.init.Start'
    }

    @Unroll
    def "a jar without the start class is refused with the file and what to do, although its manifest #situation"() {
        given:
        File jar = cliJar('grails-cli-all.jar', mainClass, ['org.example.cli.Other', 'org.apache.grails.cli.GrailsCli'])

        when:
        StartClass.load(jar)

        then:
        IllegalStateException e = thrown()
        e.message == "The Grails command line at [${jar.absolutePath}] has no start class [${START_CLASS}]. " +
                'It is not a version of Grails this wrapper can start, or the download is incomplete: delete the file and run the command again.'
        e.cause instanceof ClassNotFoundException

        and: 'a download that is so is not installed, and an installed jar that is so is downloaded again'
        StartClass.defect(jar) == "it has no start class [${START_CLASS}]"

        where:
        situation                             | mainClass
        'names no start class'                | null
        'names a class the jar has'           | 'org.example.cli.Other'
        'names the start class of earlier builds of Grails 9' | 'org.apache.grails.cli.GrailsCli'
    }

    def "the jar of a start class is held by its class loader, and a jar that is refused is let go of at once"() {
        given:
        File started = cliJar('started.jar', null, [START_CLASS])
        File refused = cliJar('refused.jar', null, ['org.example.cli.Other'])

        when: 'the start class is loaded'
        Class<?> loaded = StartClass.load(started)

        then: 'its class loader has the jar open until it is closed'
        isOpen(started)

        when:
        (loaded.classLoader as URLClassLoader).close()

        then:
        !isOpen(started)

        when: 'a jar without the start class is refused'
        StartClass.load(refused)

        then: 'nothing holds it, so that on Windows another wrapper can put its download in its place'
        thrown(IllegalStateException)
        !isOpen(refused)
    }

    @Unroll
    def "a file that cannot be read as a jar is said to be one, and not one without the start class: #situation"() {
        given:
        File jar = new File(dir, 'grails-cli-all.jar')
        if (text != null) {
            jar.text = text
        }

        when:
        StartClass.load(jar)

        then:
        IOException e = thrown()
        e.message.startsWith("The Grails command line at [${jar.absolutePath}] cannot be read (")
        e.cause instanceof IOException

        where:
        situation       | text
        'it is no jar'  | 'not a jar'
        'it is not there' | null
    }

    def "a file that is no jar, and one that is not there, cannot be started"() {
        given:
        File jar = new File(dir, 'grails-cli-all.jar')
        jar.text = 'not a jar'

        expect:
        StartClass.defect(jar) == 'it does not open as a jar: ZipException'
        StartClass.defect(new File(dir, 'missing.jar')) == 'it is not a file'
        StartClass.defect(dir) == 'it is not a file'
    }

    // While a wrapper moves its download into the place of the jar, Windows holds the file in a way that an open
    // by a reader does not share, and such an open fails as if the file were not there. A channel that does not
    // share reading holds the file the same way, for as long as the test wants.
    @Requires({ os.windows })
    @Unroll
    def "a jar that cannot be opened for a moment, as on Windows while another wrapper moves its download into its place, is #outcome once it opens"() {
        given: 'the jar, held so that no other open of it can read it'
        File jar = cliJar('grails-cli-all.jar', null, [START_CLASS])
        FileChannel held = FileChannel.open(jar.toPath(), StandardOpenOption.READ, ExtendedOpenOption.NOSHARE_READ)
        assert !opens(jar)
        Object result = 'nothing yet'
        Thread looking = new Thread({ result = look(jar) } as Runnable, 'looking at the jar')

        when: 'the jar is let go once the look has found that it cannot open it, and waits'
        looking.start()
        boolean waiting = false
        long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(1)
        while (!waiting && looking.alive && System.nanoTime() < deadline) {
            waiting = looking.state == Thread.State.TIMED_WAITING
            if (!waiting) {
                Thread.sleep(10)
            }
        }
        held.close()
        looking.join(TimeUnit.MINUTES.toMillis(1))

        then:
        waiting
        !looking.alive
        result == expected

        cleanup:
        held?.close()

        where:
        outcome       | look                                   || expected
        'found whole' | { File file -> StartClass.defect(file) } || null
        'started'     | { File file -> startedClassName(file) } || START_CLASS
    }

    def "a start class on the class path of the wrapper is not started for the jar"() {
        given: 'a class of the name on the class path the wrapper is started with, and the jar of a version that is installed'
        File other = new File(dir, 'other-classes')
        compile(other, START_CLASS, 'System.out.println("the class of the class path");')
        File home = new File(dir, 'home')
        File installed = new File(home, 'wrapper/9.0.0/grails-cli-9.0.0-all.jar')
        installed.parentFile.mkdirs()
        Files.copy(cliJar('published.jar', null, [START_CLASS]).toPath(), installed.toPath())
        String wrapperClasses = new File(StartClass.protectionDomain.codeSource.location.toURI()).path

        when:
        Map result = run([wrapperClasses, other.path].join(File.pathSeparator), home, ['help'])

        then:
        result.status == 1
        result.out.contains("Unable to execute Grails Wrapper: The start class [${START_CLASS}] of the Grails command line at [${installed.canonicalPath}] is not a class of that file.")
        !result.out.contains('the class of the class path')
    }

    @Unroll
    def "the wrapper downloads the command line of Grails #version and starts the start class in it, with the arguments, the context class loader and the property"() {
        given: 'a repository with the jar of the version, and a wrapper home that has nothing'
        File repository = new File(dir, 'repository')
        File versionDirectory = new File(repository, "org/apache/grails/grails-cli/${version}")
        versionDirectory.mkdirs()
        File published = cliJar('published.jar', mainClass, [START_CLASS])
        Files.copy(published.toPath(), new File(versionDirectory, "grails-cli-${version}-all.jar").toPath())
        File home = new File(dir, 'home')
        home.mkdirs()
        Authenticator authenticator = Authenticator.getDefault()
        ClassLoader contextClassLoader = Thread.currentThread().contextClassLoader
        System.setProperty('grails.repo.url', repository.absolutePath)

        when:
        SystemStubs.withEnvironmentVariable('GRAILS_WRAPPER_HOME', home.absolutePath)
                .and('PREFERRED_GRAILS_VERSION', version)
                .execute {
                    new GrailsWrapper().execute(['help', 'create-app'] as String[])
                }

        then: 'a wrapper that runs from its classes, as here, says that its version is unknown'
        System.getProperty(STARTED) == "${START_CLASS}:help,create-app"
        System.getProperty(CONTEXT) == 'true'
        System.getProperty(SIGNAL) == 'unknown'
        new File(home, "wrapper/${version}/grails-cli-${version}-all.jar").isFile()

        cleanup:
        System.clearProperty('grails.repo.url')
        Authenticator.setDefault(authenticator)
        Thread.currentThread().contextClassLoader = contextClassLoader

        where:
        version | mainClass
        '9.0.0' | 'org.apache.grails.forge.cli.GrailsCli'
        '8.1.0' | START_CLASS
        '7.0.0' | START_CLASS
    }

    @Unroll
    def "update-wrapper is given to the command line of Grails #version, which #does"() {
        given: 'a repository with the jar of the version'
        File repository = new File(dir, 'repository')
        File versionDirectory = new File(repository, "org/apache/grails/grails-cli/${version}")
        versionDirectory.mkdirs()
        File published = cliJar('published.jar', null, [START_CLASS])
        Files.copy(published.toPath(), new File(versionDirectory, "grails-cli-${version}-all.jar").toPath())
        File home = new File(dir, 'home')
        home.mkdirs()
        Authenticator authenticator = Authenticator.getDefault()
        ClassLoader contextClassLoader = Thread.currentThread().contextClassLoader
        System.setProperty('grails.repo.url', repository.absolutePath)

        when: 'the command line is there already, and update-wrapper as the first argument asks for it again'
        SystemStubs.withEnvironmentVariable('GRAILS_WRAPPER_HOME', home.absolutePath)
                .and('PREFERRED_GRAILS_VERSION', version)
                .execute {
                    new GrailsWrapper().execute(['help'] as String[])
                    // The wrapper is started anew for every command line, and the one before has ended and holds
                    // nothing. Here both are in one JVM, and the class loader of the first still has the jar open,
                    // which on Windows keeps the second from putting the download in its place.
                    ((URLClassLoader) Thread.currentThread().contextClassLoader).close()
                    if (typed[0] == 'update-wrapper') {
                        new File(home, "wrapper/${version}/grails-cli-${version}-all.jar").text = 'what was downloaded before'
                    }
                    new GrailsWrapper().execute(typed as String[])
                }

        then: 'it is downloaded again, and started with the command or without it'
        new File(home, "wrapper/${version}/grails-cli-${version}-all.jar").bytes == published.bytes
        System.getProperty(STARTED) == "${START_CLASS}:${given.join(',')}"

        cleanup:
        System.clearProperty('grails.repo.url')
        Authenticator.setDefault(authenticator)
        Thread.currentThread().contextClassLoader = contextClassLoader

        where:
        version          | typed                                | given                                | does
        '9.0.0'          | ['update-wrapper']                   | ['update-wrapper']                   | 'has the command'
        '9.1.0-RC1'      | ['update-wrapper', '--plain-output'] | ['update-wrapper', '--plain-output'] | 'has the command'
        '10.0.0'         | ['update-wrapper']                   | ['update-wrapper']                   | 'has the command'
        '8.1.0'          | ['update-wrapper']                   | []                                   | 'has no such command and is started without it'
        '7.0.14'         | ['update-wrapper', 'help']           | ['help']                             | 'has no such command and is started without it'
        '9.0.0'          | ['help', 'update-wrapper']           | ['help', 'update-wrapper']           | 'is given every command line as it is'
    }

    def "the jar of the wrapper gives the start class its version, and the exit code of the command line is the one of the wrapper"() {
        given: 'the jar this build makes, and a command line that is installed and ends the JVM with an exit code of its own'
        File wrapperJar = new File(System.getProperty('grails.wrapper.jar'))
        String version = new JarFile(wrapperJar).withCloseable { JarFile jar -> jar.manifest.mainAttributes.getValue('Implementation-Version') }
        File classes = new File(dir, 'exiting')
        compile(classes, START_CLASS, """\
            System.out.println("started with " + String.join(",", args) + " by the wrapper of " + System.getProperty("grails.wrapper.version"));
            System.exit(Integer.parseInt(args[0]));
            """)
        File home = new File(dir, 'home')
        File installed = new File(home, 'wrapper/9.0.0/grails-cli-9.0.0-all.jar')
        installed.parentFile.mkdirs()
        jar(installed, classes, [START_CLASS])

        when:
        Map result = run(wrapperJar.path, home, ['3', 'more'])

        then:
        version
        result.status == 3
        result.out.readLines() == ["started with 3,more by the wrapper of ${version}".toString()]
    }

    /**
     * Runs the wrapper in a JVM of its own with the class path, for the version 9.0.0, which is installed in the home.
     */
    private Map run(String classPath, File home, List<String> arguments) {
        File working = new File(dir, 'working')
        working.mkdirs()
        List<String> command = [new File(System.getProperty('java.home'), 'bin/java').path, "-Duser.home=${new File(dir, 'user').path}".toString(),
                                '-cp', classPath, 'grails.init.Start'] + arguments
        File out = new File(dir, 'out.txt')
        File err = new File(dir, 'err.txt')
        ProcessBuilder builder = new ProcessBuilder(command).directory(working).redirectOutput(out).redirectError(err)
        ['GRAILS_REPO_URL', 'GRAILS_WRAPPER_ALLOWED_TYPES', 'JAVA_TOOL_OPTIONS'].each { String name -> builder.environment().remove(name) }
        builder.environment().put('GRAILS_WRAPPER_HOME', home.path)
        builder.environment().put('PREFERRED_GRAILS_VERSION', '9.0.0')
        Process process = builder.start()
        try {
            assert process.waitFor(2, TimeUnit.MINUTES)
            return [status: process.exitValue(), out: out.text, err: err.text]
        } finally {
            process.destroyForcibly()
        }
    }

    /**
     * Whether this JVM has the file open: on Windows a file that is open cannot be moved, elsewhere the system says.
     */
    private static boolean isOpen(File file) {
        String path = file.canonicalPath
        if (File.separatorChar == ('\\' as char)) {
            File moved = new File(file.parentFile, file.name + '.moved')
            boolean open = !file.renameTo(moved)
            assert open || moved.renameTo(file)
            return open
        }
        File descriptors = new File('/proc/self/fd')
        if (descriptors.isDirectory()) {
            return descriptors.listFiles().any { File each ->
                try {
                    return Files.readSymbolicLink(each.toPath()).toString() == path
                } catch (IOException ignored) {
                    return false
                }
            }
        }
        Process lsof = new ProcessBuilder('lsof', '-w', '-n', '-P', '-Fn', '-p', String.valueOf(ProcessHandle.current().pid())).redirectErrorStream(true).start()
        String listed = lsof.inputStream.text
        assert lsof.waitFor(1, TimeUnit.MINUTES)
        return listed.readLines().contains('n' + path)
    }

    private static boolean opens(File jar) {
        try {
            new JarFile(jar).close()
            return true
        } catch (FileNotFoundException ignored) {
            return false
        }
    }

    /**
     * Loads the start class of a jar and lets the jar go, as a wrapper that has ended does.
     */
    private static String startedClassName(File jar) {
        Class<?> loaded = StartClass.load(jar)
        (loaded.classLoader as URLClassLoader).close()
        loaded.name
    }

    /**
     * Compiles a class whose {@code main} has the body.
     */
    private static void compile(File classes, String className, String body) {
        int dot = className.lastIndexOf('.')
        File source = new File(classes.parentFile, "sources-${classes.name}/${className.replace('.', '/')}.java")
        source.parentFile.mkdirs()
        source.text = """\
            package ${className.substring(0, dot)};
            public class ${className.substring(dot + 1)} {
                public static void main(String[] args) {
            ${body}
                }
            }
            """.stripIndent()
        classes.mkdirs()
        assert ToolProvider.getSystemJavaCompiler().run(null, null, null, '-d', classes.path, source.path) == 0
    }

    private static void jar(File jar, File classes, List<String> classNames) {
        jar.withOutputStream { OutputStream stream ->
            JarOutputStream out = new JarOutputStream(stream)
            classNames.each { String className ->
                String entry = className.replace('.', '/') + '.class'
                out.putNextEntry(new JarEntry(entry))
                out << new File(classes, entry).bytes
                out.closeEntry()
            }
            out.close()
        }
    }

    /**
     * A jar with a class for each name, whose {@code main} records its name, its arguments, whether the context
     * class loader was its own and the property the wrapper sets.
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
                        System.setProperty("${SIGNAL}", String.valueOf(System.getProperty("${StartClass.WRAPPER_VERSION_PROPERTY}")));
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
