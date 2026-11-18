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
package org.grails.compiler.injection

import java.lang.reflect.Modifier
import java.nio.charset.StandardCharsets

import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MixinNode
import spock.lang.Specification
import spock.lang.TempDir
import spock.util.environment.RestoreSystemProperties

/**
 * Drives {@link FactoriesFileWriter#updateFactoriesWithType} — the mechanism behind both the
 * {@code grails.factories} and {@code grails-cli.factories} registrations — to lock in that a
 * concrete subtype is registered, an abstract subtype is excluded, distinct commands whose
 * fully-qualified names share a prefix are both kept, and a class compiled again after it stopped
 * qualifying is dropped unless a hand-authored file registers it.
 */
class FactoriesFileWriterSpec extends Specification {

    static final String LOCATION = 'META-INF/grails-cli.factories'
    static final ClassNode CONTRACT = ClassHelper.make(SampleContract)
    static final String CONTRACT_NAME = SampleContract.name

    @TempDir
    File targetDir

    def "registers a concrete subtype under the contract"() {
        when:
        boolean registered = FactoriesFileWriter.updateFactoriesWithType(
                ClassHelper.make(ConcreteCommand), CONTRACT, targetDir, LOCATION, [])

        then:
        registered
        factories().getProperty(CONTRACT_NAME) == ConcreteCommand.name
    }

    def "excludes an abstract subtype and writes no entry"() {
        when:
        boolean registered = FactoriesFileWriter.updateFactoriesWithType(
                ClassHelper.make(AbstractBaseCommand), CONTRACT, targetDir, LOCATION, [])

        then:
        !registered
        !new File(targetDir, LOCATION).exists()
    }

    def "registers a subtype that implements the contract indirectly through a trait"() {
        when: 'the command implements the contract only via a trait, like GrailsApplicationCommand'
        boolean registered = FactoriesFileWriter.updateFactoriesWithType(
                ClassHelper.make(TraitBasedCommand), CONTRACT, targetDir, LOCATION, [])

        then:
        registered
        factories().getProperty(CONTRACT_NAME) == TraitBasedCommand.name
    }

    def "does not register a type that is not a subtype of the contract"() {
        when:
        boolean registered = FactoriesFileWriter.updateFactoriesWithType(
                ClassHelper.make(Unrelated), CONTRACT, targetDir, LOCATION, [])

        then:
        !registered
    }

    def "keeps distinct commands whose fully-qualified names share a prefix"() {
        when: 'FooCommand is registered first, then the prefix-named Foo'
        FactoriesFileWriter.updateFactoriesWithType(ClassHelper.make(FooCommand), CONTRACT, targetDir, LOCATION, [])
        FactoriesFileWriter.updateFactoriesWithType(ClassHelper.make(Foo), CONTRACT, targetDir, LOCATION, [])

        then: 'both survive — a substring dedup would have dropped Foo as a prefix of FooCommand'
        def registered = factories().getProperty(CONTRACT_NAME).tokenize(',')*.trim()
        registered.toSet() == [FooCommand.name, Foo.name].toSet()
    }

    def "does not duplicate a command registered twice"() {
        when:
        2.times {
            FactoriesFileWriter.updateFactoriesWithType(ClassHelper.make(ConcreteCommand), CONTRACT, targetDir, LOCATION, [])
        }

        then:
        factories().getProperty(CONTRACT_NAME).tokenize(',')*.trim() == [ConcreteCommand.name]
    }

    def "drops a class compiled again without the contract, keeping the others"() {
        given:
        FactoriesFileWriter.updateFactoriesWithType(ClassHelper.make(ConcreteCommand), CONTRACT, targetDir, LOCATION, [])
        FactoriesFileWriter.updateFactoriesWithType(ClassHelper.make(FooCommand), CONTRACT, targetDir, LOCATION, [])

        when: 'incremental compilation recompiles ConcreteCommand alone, no longer implementing the contract'
        boolean registered = FactoriesFileWriter.updateFactoriesWithType(
                new ClassNode(ConcreteCommand.name, Modifier.PUBLIC, ClassHelper.OBJECT_TYPE), CONTRACT, targetDir, LOCATION, [])

        then:
        !registered
        factories().getProperty(CONTRACT_NAME) == FooCommand.name
    }

    def "drops a class compiled again as abstract"() {
        given:
        FactoriesFileWriter.updateFactoriesWithType(ClassHelper.make(ConcreteCommand), CONTRACT, targetDir, LOCATION, [])
        FactoriesFileWriter.updateFactoriesWithType(ClassHelper.make(FooCommand), CONTRACT, targetDir, LOCATION, [])

        when:
        ClassNode nowAbstract = new ClassNode(ConcreteCommand.name, Modifier.PUBLIC | Modifier.ABSTRACT,
                ClassHelper.OBJECT_TYPE, [CONTRACT] as ClassNode[], MixinNode.EMPTY_ARRAY)
        boolean registered = FactoriesFileWriter.updateFactoriesWithType(nowAbstract, CONTRACT, targetDir, LOCATION, [])

        then:
        !registered
        factories().getProperty(CONTRACT_NAME) == FooCommand.name
    }

    def "removes the generated file once its last entry is dropped"() {
        given:
        FactoriesFileWriter.updateFactoriesWithType(ClassHelper.make(ConcreteCommand), CONTRACT, targetDir, LOCATION, [])

        when:
        boolean removed = FactoriesFileWriter.removeFromFactories(ConcreteCommand.name, CONTRACT, targetDir, LOCATION, [])

        then:
        removed
        !new File(targetDir, LOCATION).exists()
    }

    def "leaves the file alone when the class was never registered"() {
        given:
        FactoriesFileWriter.updateFactoriesWithType(ClassHelper.make(ConcreteCommand), CONTRACT, targetDir, LOCATION, [])
        String before = new File(targetDir, LOCATION).text

        when:
        boolean removed = FactoriesFileWriter.removeFromFactories(Unrelated.name, CONTRACT, targetDir, LOCATION, [])

        then:
        !removed
        new File(targetDir, LOCATION).text == before
    }

    @RestoreSystemProperties
    def "keeps a class a hand-authored factories file registers"() {
        given: 'a project whose hand-authored file registers ConcreteCommand, compiled into its build directory'
        System.clearProperty('base.dir')
        File classes = new File(targetDir, 'project/build/classes/groovy/main')
        File handAuthored = new File(targetDir, "project/src/main/resources/${LOCATION}")
        handAuthored.parentFile.mkdirs()
        handAuthored.text = "${CONTRACT_NAME}=${ConcreteCommand.name}\n"
        List<String> sourceLocations = ["src/main/resources/${LOCATION}".toString()]
        FactoriesFileWriter.updateFactoriesWithType(ClassHelper.make(FooCommand), CONTRACT, classes, LOCATION, sourceLocations)

        when:
        boolean removed = FactoriesFileWriter.removeFromFactories(ConcreteCommand.name, CONTRACT, classes, LOCATION, sourceLocations)

        then:
        !removed
        factories(classes).getProperty(CONTRACT_NAME).tokenize(',').toSet() == [ConcreteCommand.name, FooCommand.name].toSet()
    }

    private Properties factories(File directory = targetDir) {
        Properties props = new Properties()
        new File(directory, LOCATION).withReader(StandardCharsets.ISO_8859_1.name()) { props.load(it) }
        props
    }

    interface SampleContract {}

    static class ConcreteCommand implements SampleContract {}

    abstract static class AbstractBaseCommand implements SampleContract {}

    static class FooCommand implements SampleContract {}

    static class Foo implements SampleContract {}

    static class Unrelated {}
}

// analog of the GrailsApplicationCommand trait: implements the contract on behalf of commands
trait SampleTraitContract implements FactoriesFileWriterSpec.SampleContract {}

class TraitBasedCommand implements SampleTraitContract {}
