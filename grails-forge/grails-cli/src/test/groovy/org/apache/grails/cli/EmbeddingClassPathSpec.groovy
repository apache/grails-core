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

import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

import javax.tools.JavaCompiler
import javax.tools.ToolProvider

import spock.lang.Specification
import spock.lang.TempDir

/**
 * A program that calls {@code GrailsCli.execute} in a JVM of its own, as "Starting the Command Line from a Tool" in
 * the guide describes it: compiled against the class path a build of its own gives it for the one artifact it
 * declares, in Java, which needs every type of the signature it calls, and run on the class path it runs with. The
 * class paths are those of the variants the artifacts are published with (see the build of this project).
 */
class EmbeddingClassPathSpec extends Specification {

    // the call the guide shows
    private static final String PROGRAM = '''\
        package example;

        import org.apache.grails.forge.cli.GrailsCli;

        public class Embed {
            public static void main(String[] args) {
                int exitCode = GrailsCli.execute("--plain-output", "create-app", "com.example.myapp");
                System.out.println("exit code " + exitCode);
                System.exit(exitCode);
            }
        }
        '''.stripIndent()

    @TempDir
    File dir

    void 'a program compiles the call of the guide against grails-forge-cli alone, and runs it with what that artifact brings'() {
        when:
        Map<String, Object> compiled = compile('grails.cli.embeddingForgeCliApi')

        then:
        compiled.status == 0
        compiled.said == ''

        when:
        File work = new File(dir, 'work')
        work.mkdirs()
        Map<String, Object> ran = run(work, classPath('grails.cli.embeddingForgeCliRuntime') + [compiled.classes as File])

        then:
        ran.status == 0
        ran.out.contains('Application created at')
        ran.out.readLines().last() == 'exit code 0'
        new File(work, 'myapp/grails-app').isDirectory()
        new File(work, 'myapp/build.gradle').isFile()
    }

    void 'a program compiled against grails-cli does not find GrailsCli: the one class of that artifact needs the JDK alone'() {
        when:
        Map<String, Object> compiled = compile('grails.cli.embeddingCliApi')

        then:
        classPath('grails.cli.embeddingCliApi')*.name.every { String name -> name.startsWith('grails-cli') }
        compiled.status != 0
        (compiled.said as String).contains('package org.apache.grails.forge.cli does not exist')
    }

    private static List<File> classPath(String property) {
        String value = System.getProperty(property)
        assert value: "the build gives the class path as the system property ${property}"
        return value.split(File.pathSeparator).collect { String path -> new File(path) }
    }

    private Map<String, Object> compile(String classPathProperty) {
        File source = new File(dir, 'src/example/Embed.java')
        source.parentFile.mkdirs()
        source.setText(PROGRAM, StandardCharsets.UTF_8.name())
        File classes = new File(dir, 'classes')
        classes.mkdirs()
        JavaCompiler compiler = ToolProvider.systemJavaCompiler
        ByteArrayOutputStream said = new ByteArrayOutputStream()
        int status = compiler.run(null, said, said, '-proc:none', '-encoding', 'UTF-8', '-d', classes.path,
                '-classpath', classPath(classPathProperty)*.path.join(File.pathSeparator), source.path)
        return [status: status, said: said.toString(StandardCharsets.UTF_8.name()), classes: classes]
    }

    private Map<String, Object> run(File directory, List<File> classPath) {
        File out = new File(dir, 'out.txt')
        File err = new File(dir, 'err.txt')
        // a home of its own, and no network: what the call does is the command line and nothing else
        List<String> command = [new File(System.getProperty('java.home'), 'bin/java').path,
                                "-Duser.home=${new File(dir, 'home').path}".toString(),
                                '-Dhttps.proxyHost=127.0.0.1', '-Dhttps.proxyPort=9', '-Dhttp.proxyHost=127.0.0.1', '-Dhttp.proxyPort=9',
                                '-cp', classPath*.path.join(File.pathSeparator), 'example.Embed']
        ProcessBuilder builder = new ProcessBuilder(command).directory(directory).redirectOutput(out).redirectError(err)
        builder.redirectInput(ProcessBuilder.Redirect.from(new File(File.separatorChar == ('\\' as char) ? 'NUL' : '/dev/null')))
        builder.environment().put('GRAILS_FORGE_CATALOG', 'none')
        builder.environment().put('GRAILS_PLUGIN_INDEX', 'none')
        Process process = builder.start()
        if (!process.waitFor(180, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            assert false: "the program did not end:\n${out.text}\n${err.text}"
        }
        return [status: process.exitValue(), out: out.getText(StandardCharsets.UTF_8.name()), err: err.getText(StandardCharsets.UTF_8.name())]
    }
}
