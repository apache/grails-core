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
package org.grails.compiler.beans

import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.classgen.GeneratorContext
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.Phases
import org.codehaus.groovy.control.SourceUnit
import spock.lang.Specification
import spock.lang.TempDir

/**
 * The class a {@code beans} closure compiles to is generated rather than written, so nobody can
 * list it in {@code AutoConfiguration.imports} without first knowing it exists. These drive a real
 * compilation with a target directory, which is the only thing that makes the file observable.
 */
class AutoConfigurationImportsWriterSpec extends Specification {

    private static final String IMPORTS =
            'META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports'

    @TempDir
    File projectDir

    private File targetDir
    private String previousBaseDir

    void setup() {
        targetDir = new File(projectDir, 'build/classes/groovy/main')
        targetDir.mkdirs()
        previousBaseDir = System.setProperty('base.dir', projectDir.absolutePath)
    }

    void cleanup() {
        if (previousBaseDir == null) {
            System.clearProperty('base.dir')
        }
        else {
            System.setProperty('base.dir', previousBaseDir)
        }
    }

    void 'the generated sibling registers itself'() {
        when:
        compile(plugin('Greeting'))

        then: 'the name only the compiler knows is written where Spring Boot reads it'
        importsEntries() == ['com.example.GreetingAutoConfiguration']
    }

    void 'autoConfigurationName registers under the name it asks for'() {
        when:
        compile(plugin('Renamed', "autoConfigurationName = 'LegacyAutoConfiguration'"))

        then: 'what is registered is the name the class is actually generated under'
        importsEntries() == ['com.example.LegacyAutoConfiguration']
    }

    void 'siblings from separate source units accumulate rather than replacing one another'() {
        when: 'two descriptors compile separately, as they do in a real build'
        compile(plugin('First'))
        compile(plugin('Second'))

        then:
        importsEntries() == ['com.example.FirstAutoConfiguration', 'com.example.SecondAutoConfiguration']
    }

    void 'a renamed descriptor does not leave its old entry behind'() {
        given: 'a descriptor compiles, is renamed, and the stale output is cleaned - an incremental build'
        compile(plugin('Greeting'))
        new File(targetDir, 'com/example/GreetingAutoConfiguration.class').delete()
        new File(targetDir, 'com/example/GreetingGrailsPlugin.class').delete()

        when:
        compile(plugin('Farewell'))

        then: 'an entry naming a class that is no longer generated fails Spring Boot at startup'
        importsEntries() == ['com.example.FarewellAutoConfiguration']
    }

    void 'a descriptor left untouched by an incremental build keeps its entry'() {
        given: 'two descriptors, then only the one about to be rebuilt is cleaned, as Gradle does'
        compile(plugin('Greeting'))
        compile(plugin('Farewell'))
        new File(targetDir, 'com/example/FarewellAutoConfiguration.class').delete()

        when:
        compile(plugin('Farewell'))

        then: 'the one that was not rebuilt is still generated, so it is still registered'
        importsEntries() == ['com.example.FarewellAutoConfiguration', 'com.example.GreetingAutoConfiguration']
    }

    void 'two descriptors recompiling together do not prune one another'() {
        given: 'both were built before, and both stale outputs are cleaned'
        compile(plugin('Greeting'))
        compile(plugin('Farewell'))
        new File(targetDir, 'com/example/GreetingAutoConfiguration.class').delete()
        new File(targetDir, 'com/example/FarewellAutoConfiguration.class').delete()

        when: 'they compile as one unit, so neither class file exists while the other registers'
        compileTogether([plugin('Greeting'), plugin('Farewell')])

        then:
        importsEntries() == ['com.example.FarewellAutoConfiguration', 'com.example.GreetingAutoConfiguration']
    }

    void 'a descriptor deleted with nothing to replace it takes its entry with it'() {
        given: 'the descriptor is gone, so nothing generates a sibling and register is never called'
        compile(plugin('Greeting'))
        new File(targetDir, 'com/example/GreetingAutoConfiguration.class').delete()
        new File(targetDir, 'com/example/GreetingGrailsPlugin.class').delete()

        when: 'the compilation reconciles, which it does whether or not anything was generated'
        AutoConfigurationImportsWriter.reconcile(targetDir, null, null)

        then: 'the file goes with the last entry - an empty imports file is a resource saying nothing'
        importsEntries() == []
        !new File(targetDir, IMPORTS).exists()
    }

    void 'reconciling leaves an entry whose class is still generated'() {
        given:
        compile(plugin('Greeting'))

        when:
        AutoConfigurationImportsWriter.reconcile(targetDir, null, null)

        then:
        importsEntries() == ['com.example.GreetingAutoConfiguration']
    }

    void 'reconciling does not create a file for a module that generates nothing'() {
        when: 'a module with no beans closure anywhere, which is most of them'
        AutoConfigurationImportsWriter.reconcile(targetDir, null, null)

        then:
        !new File(targetDir, IMPORTS).exists()
    }

    void 'reconciling leaves a hand-authored file alone'() {
        given: 'no generated file, which is how a module keeping its own looks from here'
        File handAuthored = new File(projectDir, "src/main/resources/${IMPORTS}")
        handAuthored.parentFile.mkdirs()
        handAuthored.text = 'com.elsewhere.FromAnotherJar\n'

        when:
        AutoConfigurationImportsWriter.reconcile(targetDir, null, null)

        then:
        handAuthored.readLines() == ['com.elsewhere.FromAnotherJar']
        !new File(targetDir, IMPORTS).exists()
    }

    void 'a call that only drops a stale entry still writes'() {
        given: 'a generated file holding one class that is still generated and one that is not'
        compile(plugin('Greeting'))
        new File(targetDir, IMPORTS).text =
                'com.example.GreetingAutoConfiguration\ncom.example.StaleAutoConfiguration\n'
        new File(targetDir, 'com/example/GreetingAutoConfiguration.class').delete()

        when: 'the surviving class registers again, which adds nothing that was not already listed'
        AutoConfigurationImportsWriter.register(
                'com.example.GreetingAutoConfiguration', targetDir, null, null)

        then: 'the entry naming a class that is gone does not survive the write it triggered'
        importsEntries() == ['com.example.GreetingAutoConfiguration']
    }

    void 'the directory the global transform resolved is preferred over the compiler configuration'() {
        given: "a compilation whose configured output is not where the class files go - Groovy-Eclipse's shape"
        File resolved = new File(projectDir, 'build/eclipse-output')
        resolved.mkdirs()

        when: 'the descriptor compiles with that directory seeded on the class, as the global transform seeds it'
        compileWithResolvedTargetDirectory(plugin('Greeting'), resolved)

        then: 'the entry is written where the class files actually are'
        new File(resolved, IMPORTS).readLines().findAll { it.trim() } == ['com.example.GreetingAutoConfiguration']

        and: 'and not where the compiler configuration pointed'
        !new File(targetDir, IMPORTS).exists()
    }

    void 'a module that keeps the file by hand keeps it'() {
        given: 'a hand-authored file, which may hold entries no compilation can discover'
        File handAuthored = new File(projectDir, "src/main/resources/${IMPORTS}")
        handAuthored.parentFile.mkdirs()
        handAuthored.text = 'com.example.GreetingAutoConfiguration\ncom.elsewhere.FromAnotherJar\n'

        when:
        compile(plugin('Greeting'))

        then: 'nothing is generated beside it, which would put the same resource at the same path twice'
        !new File(targetDir, IMPORTS).exists()

        and: 'and the entries it alone knows about are untouched'
        handAuthored.readLines().contains('com.elsewhere.FromAnotherJar')
    }

    void 'adding a hand-authored file removes a previously generated copy'() {
        given:
        compile(plugin('Greeting'))
        File generated = new File(targetDir, IMPORTS)
        assert generated.isFile()
        File handAuthored = new File(projectDir, "src/main/resources/${IMPORTS}")
        handAuthored.parentFile.mkdirs()
        handAuthored.text = 'com.example.GreetingAutoConfiguration\n'

        when:
        compile(plugin('Greeting'))

        then:
        !generated.exists()
        handAuthored.readLines() == ['com.example.GreetingAutoConfiguration']
    }

    void 'deleting a hand-authored file opts into generation'() {
        given:
        File handAuthored = new File(projectDir, "src/main/resources/${IMPORTS}")
        handAuthored.parentFile.mkdirs()
        handAuthored.text = 'com.example.GreetingAutoConfiguration\n'
        compile(plugin('Greeting'))
        assert !new File(targetDir, IMPORTS).exists()

        when:
        assert handAuthored.delete()
        compile(plugin('Greeting'))

        then:
        importsEntries() == ['com.example.GreetingAutoConfiguration']
    }

    void 'a hand-authored file missing the generated class is warned about'() {
        given:
        File handAuthored = new File(projectDir, "src/main/resources/${IMPORTS}")
        handAuthored.parentFile.mkdirs()
        handAuthored.text = 'com.elsewhere.FromAnotherJar\n'

        when:
        compile(plugin('Greeting'))

        then: 'silently registering nothing is the failure this exists to prevent'
        warnings().any {
            it.contains('com.example.GreetingAutoConfiguration') && it.contains(IMPORTS)
        }
    }

    void 'a generated imports write failure is a compilation error'() {
        given: 'a regular file blocks creation of the META-INF/spring directory'
        File blockedParent = new File(targetDir, 'META-INF')
        blockedParent.text = 'not a directory'
        SourceUnit source = SourceUnit.create('Broken.groovy', 'class Broken {}')

        when:
        AutoConfigurationImportsWriter.register('com.example.BrokenAutoConfiguration', targetDir, source, null)

        then:
        source.errorCollector.hasErrors()
        source.errorCollector.getSyntaxError(0).message.contains('Could not write generated auto-configuration imports')
    }

    void 'the utility class cannot be instantiated by accident'() {
        given:
        def constructor = AutoConfigurationImportsWriter.getDeclaredConstructor()

        expect:
        java.lang.reflect.Modifier.isPrivate(constructor.modifiers)

        when:
        constructor.accessible = true
        constructor.newInstance()

        then:
        noExceptionThrown()
    }

    void 'nothing is written without a class name or an output directory'() {
        expect:
        !AutoConfigurationImportsWriter.register(className, hasDirectory ? targetDir : null, null, null)

        and:
        !new File(targetDir, IMPORTS).exists()

        where:
        className                    | hasDirectory
        null                         | true
        ''                           | true
        'com.example.GreetingConfig' | false
    }

    void 'reconciling without an output directory does nothing'() {
        expect:
        !AutoConfigurationImportsWriter.reconcile(null, null, null)
    }

    void 'registering the same class twice writes once'() {
        expect: 'the second call finds the file already says what it would write'
        AutoConfigurationImportsWriter.register('com.example.GreetingAutoConfiguration', targetDir, null, 'build one')
        !AutoConfigurationImportsWriter.register('com.example.GreetingAutoConfiguration', targetDir, null, 'build one')
        importsEntries() == ['com.example.GreetingAutoConfiguration']
    }

    void 'a registration with no compilation to scope to is pruned only by the class files present'() {
        given:
        AutoConfigurationImportsWriter.register('com.example.FirstAutoConfiguration', targetDir, null, null)

        when: 'the second registers with nothing remembering the first, whose class file was never written'
        AutoConfigurationImportsWriter.register('com.example.SecondAutoConfiguration', targetDir, null, null)

        then:
        importsEntries() == ['com.example.SecondAutoConfiguration']
    }

    void 'reconciling keeps an entry registered by the compilation before its class file is written'() {
        given:
        Object compilation = new Object()
        AutoConfigurationImportsWriter.register('com.example.PendingAutoConfiguration', targetDir, null, compilation)

        expect:
        !AutoConfigurationImportsWriter.reconcile(targetDir, compilation, null)
        importsEntries() == ['com.example.PendingAutoConfiguration']
    }

    void 'reconciling drops an entry registered by a different compilation whose class is gone'() {
        given:
        AutoConfigurationImportsWriter.register('com.example.GoneAutoConfiguration', targetDir, null, new Object())

        expect:
        AutoConfigurationImportsWriter.reconcile(targetDir, new Object(), null)
        importsEntries() == []
    }

    void 'reconciling removes a generated file left beside a hand-authored one'() {
        given:
        AutoConfigurationImportsWriter.register('com.example.GreetingAutoConfiguration', targetDir, null, null)
        File handAuthored = new File(projectDir, "src/main/resources/${IMPORTS}")
        handAuthored.parentFile.mkdirs()
        handAuthored.text = 'com.example.GreetingAutoConfiguration\n'

        expect:
        AutoConfigurationImportsWriter.reconcile(targetDir, null, null)
        !new File(targetDir, IMPORTS).exists()
    }

    void 'a hand-authored file missing the generated class is tolerated when there is no source to warn on'() {
        given:
        File handAuthored = new File(projectDir, "src/main/resources/${IMPORTS}")
        handAuthored.parentFile.mkdirs()
        handAuthored.text = 'com.elsewhere.FromAnotherJar\n'

        expect:
        !AutoConfigurationImportsWriter.register('com.example.GreetingAutoConfiguration', targetDir, null, null)
        !new File(targetDir, IMPORTS).exists()
    }

    void 'a write failure with no source to report to is an exception'() {
        given:
        new File(targetDir, 'META-INF').text = 'not a directory'

        when:
        AutoConfigurationImportsWriter.register('com.example.BrokenAutoConfiguration', targetDir, null, null)

        then:
        IllegalStateException e = thrown()
        e.message.contains('Could not write generated auto-configuration imports')
        e.cause instanceof IOException
    }

    void 'comments and blank lines in an existing file are not carried over as entries'() {
        given:
        File imports = new File(targetDir, IMPORTS)
        imports.parentFile.mkdirs()
        imports.text = '# generated\n\n   \ncom.example.GreetingAutoConfiguration\n'
        new File(targetDir, 'com/example').mkdirs()
        new File(targetDir, 'com/example/GreetingAutoConfiguration.class').text = 'class'

        when:
        AutoConfigurationImportsWriter.register('com.example.FarewellAutoConfiguration', targetDir, null, null)

        then:
        imports.readLines() == ['com.example.FarewellAutoConfiguration', 'com.example.GreetingAutoConfiguration']
    }

    void 'an imports file that cannot be read is replaced by the entry being registered'() {
        given: 'bytes that are not valid UTF-8, which the reader rejects'
        File imports = new File(targetDir, IMPORTS)
        imports.parentFile.mkdirs()
        imports.bytes = [0xC3, 0x28, 0xFF] as byte[]

        when:
        AutoConfigurationImportsWriter.register('com.example.GreetingAutoConfiguration', targetDir, null, null)

        then:
        imports.readLines() == ['com.example.GreetingAutoConfiguration']
    }

    void 'a base directory that does not exist falls back to the directory above the build output'() {
        given:
        System.setProperty('base.dir', new File(projectDir, 'missing').absolutePath)
        File handAuthored = new File(projectDir, "src/main/resources/${IMPORTS}")
        handAuthored.parentFile.mkdirs()
        handAuthored.text = 'com.elsewhere.FromAnotherJar\n'

        expect: 'the hand-authored file is still found, so nothing is generated beside it'
        !AutoConfigurationImportsWriter.register('com.example.GreetingAutoConfiguration', targetDir, null, null)
        !new File(targetDir, IMPORTS).exists()
    }

    void 'a maven style target directory marks the module root as well'() {
        given:
        System.clearProperty('base.dir')
        File mavenTarget = new File(projectDir, 'target/classes')
        mavenTarget.mkdirs()
        File handAuthored = new File(projectDir, "src/main/resources/${IMPORTS}")
        handAuthored.parentFile.mkdirs()
        handAuthored.text = 'com.elsewhere.FromAnotherJar\n'

        expect:
        !AutoConfigurationImportsWriter.register('com.example.GreetingAutoConfiguration', mavenTarget, null, null)
        !new File(mavenTarget, IMPORTS).exists()
    }

    void 'an output directory outside any build or target directory has no hand-authored file to find'() {
        given:
        System.clearProperty('base.dir')
        File elsewhere = new File(projectDir, 'out/classes')
        elsewhere.mkdirs()

        expect:
        AutoConfigurationImportsWriter.register('com.example.GreetingAutoConfiguration', elsewhere, null, null)
        new File(elsewhere, IMPORTS).readLines() == ['com.example.GreetingAutoConfiguration']
    }

    private List<String> collectedWarnings = []

    private List<String> warnings() {
        collectedWarnings
    }

    private List<String> importsEntries() {
        File file = new File(targetDir, IMPORTS)
        file.exists() ? file.readLines().findAll { it.trim() && !it.startsWith('#') } : []
    }

    private static String plugin(String name, String grailsBeansMembers = '') {
        """
            package com.example

            import grails.compiler.beans.GrailsBeans
            import grails.plugins.Plugin
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans(${grailsBeansMembers})
            @AutoConfiguration
            class ${name}GrailsPlugin extends Plugin {
                String version = '1.0.0'

                def beans = {
                    bean('${name.uncapitalize()}Greeting', String) { 'hello' }
                }
            }
        """
    }

    private CompilerConfiguration compileTogether(List<String> sources) {
        CompilationUnit unit = newUnit()
        sources.eachWithIndex { String source, int index -> unit.addSource("Together${index}.groovy", source) }
        run(unit)
    }

    /** A real compilation, since only a target directory makes the generated file observable. */
    private CompilerConfiguration compile(String source) {
        CompilationUnit unit = newUnit()
        unit.addSource("Source${System.identityHashCode(source)}.groovy", source)
        run(unit)
    }

    /** Compiles with the resolved-directory metadata the global Grails transform seeds. */
    private void compileWithResolvedTargetDirectory(String source, File resolved) {
        CompilationUnit unit = newUnit()
        unit.addSource("Resolved${System.identityHashCode(source)}.groovy", source)
        unit.addPhaseOperation({ SourceUnit su, GeneratorContext ctx, ClassNode cn ->
            cn.putNodeMetaData(GrailsBeansASTTransformation.RESOLVED_TARGET_DIRECTORY_METADATA, resolved)
        } as CompilationUnit.IPrimaryClassNodeOperation, Phases.CONVERSION)
        run(unit)
    }

    private CompilationUnit newUnit() {
        CompilerConfiguration configuration = new CompilerConfiguration()
        configuration.targetDirectory = targetDir
        new CompilationUnit(configuration, null, new GroovyClassLoader(getClass().classLoader, configuration))
    }

    /**
     * Compiled through to OUTPUT, which is the phase that writes the class files. Stopping earlier
     * would leave the output directory empty, and what is still generated there is exactly what
     * decides whether an entry is kept.
     */
    private CompilerConfiguration run(CompilationUnit unit) {
        unit.compile(Phases.OUTPUT)
        List warningMessages = unit.errorCollector.warnings
        if (warningMessages) {
            collectedWarnings.addAll(warningMessages.collect { it.message?.toString() ?: it.toString() })
        }
        unit.configuration
    }

}
