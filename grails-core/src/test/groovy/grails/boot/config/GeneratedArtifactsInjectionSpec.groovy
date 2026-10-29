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
package grails.boot.config

import java.nio.file.Files
import java.nio.file.Path

import javax.tools.JavaCompiler
import javax.tools.StandardJavaFileManager
import javax.tools.ToolProvider

import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import jakarta.annotation.Resource

import org.springframework.aot.generate.ClassNameGenerator
import org.springframework.aot.generate.DefaultGenerationContext
import org.springframework.aot.generate.GeneratedFiles
import org.springframework.aot.generate.InMemoryGeneratedFiles
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.config.AutowireCapableBeanFactory
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.beans.factory.support.RootBeanDefinition
import org.springframework.cglib.proxy.Enhancer
import org.springframework.cglib.proxy.NoOp
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.annotation.AnnotationConfigUtils
import org.springframework.context.aot.ApplicationContextAotGenerator
import org.springframework.context.support.GenericApplicationContext
import org.springframework.javapoet.ClassName
import spock.lang.Specification
import spock.lang.TempDir

/**
 * Covers the injection of an application started from the code generated ahead of time for it. The code is
 * generated, compiled and run here, and the processors Grails registers again on generated artifacts process
 * the context it builds, as they do when an application starts on generated artifacts.
 *
 * <p>Every annotated member and lifecycle method of each bean runs exactly once: whatever declared the bean,
 * whatever type its definition declares, and however the instance reached the container.</p>
 */
class GeneratedArtifactsInjectionSpec extends Specification {

    private static final String AOT_KEY = 'spring.aot.enabled'

    /** What injecting and initializing an {@link InjectedFixture} calls, each once. */
    private static final Map<String, Integer> INJECTED_AND_INITIALIZED =
            [collaborator: 1, overridableCollaborator: 1, resourceCollaborator: 1, initialize: 1].asImmutable()

    @TempDir
    Path directory

    GenericApplicationContext context

    void cleanup() {
        context?.close()
    }

    void 'a bean the generated code created is injected, initialized and destroyed once'() {
        when:
            startFromGeneratedCode {
                registerBeanDefinition('generated', new RootBeanDefinition(InjectedFixture))
            }
            def generated = context.getBean('generated', InjectedFixture)

        then:
            generated.calls == INJECTED_AND_INITIALIZED

        when:
            context.close()

        then: 'the processor calls the destruction method, and the container skips the name the generated code recorded'
            generated.calls.shutdown == 1
    }

    void 'each instance of a prototype the generated code creates is injected and initialized once'() {
        when:
            startFromGeneratedCode {
                registerBeanDefinition('prototype', new RootBeanDefinition(InjectedFixture).tap {
                    scope = BeanDefinition.SCOPE_PROTOTYPE
                })
            }

        then: 'the definition is processed once, for the first instance, and the second is injected by the same code'
            context.getBean('prototype', InjectedFixture).calls == INJECTED_AND_INITIALIZED
            context.getBean('prototype', InjectedFixture).calls == INJECTED_AND_INITIALIZED
    }

    void 'a bean created from a factory method declaring a superclass is injected once through it, and through the rest by the processors'() {
        when:
            startFromGeneratedCode {
                registerBeanDefinition('declaredAsSuperclass', factoryMethod('declaredAsSuperclass'))
            }
            def bean = context.getBean('declaredAsSuperclass', SubclassFixture)

        then: 'what the generated code injected is not injected again, nor is the override of it that code called, ' +
                'and what only the subclass declares, which that code never saw, is injected by the processors'
            bean.calls == INJECTED_AND_INITIALIZED + [subclassCollaborator: 1]

        when:
            context.close()

        then:
            bean.calls.shutdown == 1
    }

    void 'a bean created from a factory method declaring a generic superclass is injected once through the override of its setter'() {
        when:
            startFromGeneratedCode {
                registerBeanDefinition('dependency', new RootBeanDefinition(DependencyFixture))
                registerBeanDefinition('declaredAsGenericSuperclass', factoryMethod('declaredAsGenericSuperclass'))
            }

        then: 'the generated code called the override through the bridge the compiler added for the erased type'
            context.getBean('declaredAsGenericSuperclass', GenericSubclassFixture).calls == [dependency: 1]
    }

    void 'a bean created from a factory method declaring an interface is injected and initialized by the processors'() {
        when:
            startFromGeneratedCode {
                registerBeanDefinition('declaredAsInterface', factoryMethod('declaredAsInterface'))
            }

        then: 'the generated code saw none of the members of the class the method returns, as for the link generator'
            context.getBean('declaredAsInterface', InjectedFixture).calls == INJECTED_AND_INITIALIZED
    }

    void 'an inner bean the generated code created is injected and initialized once'() {
        when:
            startFromGeneratedCode {
                registerBeanDefinition('outer', new RootBeanDefinition(OuterFixture).tap {
                    propertyValues.add('nested', new RootBeanDefinition(InjectedFixture))
                })
            }

        then: 'the generated code injected it, though it is no bean of the registry'
            context.getBean('outer', OuterFixture).nested.calls == INJECTED_AND_INITIALIZED
    }

    void 'a bean declared before the generated code runs is injected, initialized and destroyed once'() {
        when: 'an initializer ordered ahead of the generated one declares beans, with or without a supplier'
            startFromGeneratedCode({
                registerBeanDefinition('generated', new RootBeanDefinition(InjectedFixture))
            }, {
                registerBean('declaredEarly', InjectedFixture)
                registerBean('suppliedEarly', InjectedFixture, { new InjectedFixture() })
            })
            def declaredEarly = context.getBean('declaredEarly', InjectedFixture)
            def suppliedEarly = context.getBean('suppliedEarly', InjectedFixture)

        then: 'neither is taken for a generated bean'
            declaredEarly.calls == INJECTED_AND_INITIALIZED
            suppliedEarly.calls == INJECTED_AND_INITIALIZED

        when:
            context.close()

        then:
            declaredEarly.calls.shutdown == 1
            suppliedEarly.calls.shutdown == 1
    }

    void 'a bean declared again in place of a generated one is injected and initialized by the processors'() {
        when: 'a plugin declares a bean the generated code also contributed, as the bean DSL does at run time'
            startFromGeneratedCode({
                registerBeanDefinition('redeclared', new RootBeanDefinition(InjectedFixture))
            }, {}, {
                registerBeanDefinition('redeclared', new RootBeanDefinition(InjectedFixture))
            })

        then:
            context.getBean('redeclared', InjectedFixture).calls == INJECTED_AND_INITIALIZED
    }

    void 'another instance configured as a generated bean is injected and initialized in full'() {
        given:
            startFromGeneratedCode {
                registerBeanDefinition('generated', new RootBeanDefinition(InjectedFixture))
            }
            def another = new InjectedFixture()

        when:
            context.beanFactory.configureBean(another, 'generated')

        then: 'the generated code injected the instance it created, not this one'
            another.calls == INJECTED_AND_INITIALIZED

        and: 'the instance it created is left as it was'
            context.getBean('generated', InjectedFixture).calls == INJECTED_AND_INITIALIZED
    }

    void 'an instance configured as a generated prototype never created yet is injected, initialized and destroyed once'() {
        given: 'a prototype serving as the template of what it configures, as @Configurable uses one'
            startFromGeneratedCode {
                registerBeanDefinition('template', new RootBeanDefinition(InjectedFixture).tap {
                    scope = BeanDefinition.SCOPE_PROTOTYPE
                })
            }
            def configured = new InjectedFixture()

        when: 'no instance has been created from the definition, so nothing has processed it'
            context.beanFactory.configureBean(configured, 'template')

        then:
            configured.calls == INJECTED_AND_INITIALIZED

        when:
            context.beanFactory.destroyBean('template', configured)

        then:
            configured.calls.shutdown == 1
    }

    void '#kind with members annotated for both processors is injected once each, with the resource they name'() {
        given: 'two more collaborators, the one autowiring by type would choose being primary'
            startFromGeneratedCode({
                registerBeanDefinition('named', new RootBeanDefinition(CollaboratorFixture))
                registerBeanDefinition('preferred', new RootBeanDefinition(CollaboratorFixture).tap { primary = true })
                registerBeanDefinition('generated', new RootBeanDefinition(DualAnnotatedFixture))
            }, {}, {
                registerBeanDefinition('declaredLater', new RootBeanDefinition(DualAnnotatedFixture))
            })
            def another = instance.call()
            def named = context.getBean('named')

        when:
            context.beanFactory.configureBean(another, 'generated')

        then: 'the common processor, which comes first, injects them, and the autowired processor leaves them to it'
            another.collaborator.is(named)
            another.wired.is(named)
            another.calls == [wire: 1]

        and: 'as for a bean declared any other way'
            with(context.getBean('declaredLater', DualAnnotatedFixture)) {
                collaborator.is(named)
                wired.is(named)
                calls == [wire: 1]
            }

        where:
            kind                                  | instance
            'another instance'                    | { new DualAnnotatedFixture() }
            'another instance of a CGLIB subclass' | { new Enhancer(superclass: DualAnnotatedFixture, callback: NoOp.INSTANCE).create() }
    }

    void 'another instance initialized under the name of a generated bean is injected, initialized and destroyed once'() {
        given: 'a bean created as the Hibernate bean container creates one it is given the name of'
            startFromGeneratedCode {
                registerBeanDefinition('generated', new RootBeanDefinition(InjectedFixture))
            }
            def beanFactory = context.beanFactory
            def another = new InjectedFixture()

        when:
            beanFactory.autowireBeanProperties(another, AutowireCapableBeanFactory.AUTOWIRE_NO, false)
            beanFactory.applyBeanPropertyValues(another, 'generated')
            beanFactory.initializeBean(another, 'generated')

        then:
            another.calls == INJECTED_AND_INITIALIZED

        when:
            beanFactory.destroyBean('generated', another)

        then:
            another.calls.shutdown == 1
    }

    /**
     * Generates the code for the beans {@code declare} declares, compiles it, and starts a context from it as
     * an application on generated artifacts starts: the generated initializer runs, and the injection processors
     * are registered again. Every context also holds a collaborator and a factory of beans.
     *
     * @param beforeGenerated declares beans before the generated initializer runs, as an initializer ordered
     *        ahead of it does
     * @param afterGenerated declares beans after it, as a plugin does
     */
    private void startFromGeneratedCode(@DelegatesTo(GenericApplicationContext) Closure declare,
                                        @DelegatesTo(GenericApplicationContext) Closure beforeGenerated = {},
                                        @DelegatesTo(GenericApplicationContext) Closure afterGenerated = {}) {
        GenericApplicationContext generating = new GenericApplicationContext()
        AnnotationConfigUtils.registerAnnotationConfigProcessors(generating)
        generating.registerBeanDefinition('collaborator', new RootBeanDefinition(CollaboratorFixture))
        generating.registerBeanDefinition('factory', new RootBeanDefinition(FactoryFixture))
        generating.with(declare)

        InMemoryGeneratedFiles generatedFiles = new InMemoryGeneratedFiles()
        DefaultGenerationContext generationContext = new DefaultGenerationContext(
                new ClassNameGenerator(ClassName.get(getClass().packageName, 'Injection')), generatedFiles)
        ClassName initializer = new ApplicationContextAotGenerator().processAheadOfTime(generating, generationContext)
        generationContext.writeGeneratedContent()
        generating.close()

        context = new GenericApplicationContext()
        context.with(beforeGenerated)
        Class<?> generated = compile(generatedFiles).loadClass(initializer.reflectionName())
        ((ApplicationContextInitializer) generated.getDeclaredConstructor().newInstance()).initialize(context)
        context.with(afterGenerated)
        String previous = System.getProperty(AOT_KEY)
        try {
            System.setProperty(AOT_KEY, 'true')
            GrailsApplicationPostProcessor.registerAnnotationConfigProcessorsForGeneratedArtifacts(context)
        }
        finally {
            previous == null ? System.clearProperty(AOT_KEY) : System.setProperty(AOT_KEY, previous)
        }
        context.refresh()
    }

    /** Compiles the generated sources against the classes these tests run with, and loads them over those. */
    private ClassLoader compile(InMemoryGeneratedFiles generatedFiles) {
        Path sources = directory.resolve('sources')
        Path classes = Files.createDirectories(directory.resolve('classes'))
        List<File> written = generatedFiles.getGeneratedFiles(GeneratedFiles.Kind.SOURCE).collect { path, source ->
            Path file = sources.resolve(path)
            Files.createDirectories(file.parent)
            source.inputStream.withCloseable { Files.copy(it, file) }
            file.toFile()
        }
        JavaCompiler compiler = ToolProvider.systemJavaCompiler
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)
        StringWriter errors = new StringWriter()
        boolean compiled = compiler.getTask(errors, fileManager, null,
                ['-classpath', System.getProperty('java.class.path'), '-d', classes.toString(), '-proc:none'],
                null, fileManager.getJavaFileObjectsFromFiles(written)).call()
        fileManager.close()
        assert compiled: errors.toString()
        new URLClassLoader([classes.toUri().toURL()] as URL[], getClass().classLoader)
    }

    private static RootBeanDefinition factoryMethod(String name) {
        new RootBeanDefinition().tap {
            factoryBeanName = 'factory'
            factoryMethodName = name
        }
    }

    static class CollaboratorFixture {
    }

    static interface InjectedType {
    }

    static class InjectedFixture implements InjectedType {

        /** How many times each member was called. */
        final Map<String, Integer> calls = [:].withDefault { 0 }

        @Autowired
        void setCollaborator(CollaboratorFixture collaborator) {
            calls.collaborator++
        }

        @Autowired
        void setOverridableCollaborator(CollaboratorFixture collaborator) {
            calls.overridableCollaborator++
        }

        @Resource
        void setResourceCollaborator(CollaboratorFixture collaborator) {
            calls.resourceCollaborator++
        }

        @PostConstruct
        void initialize() {
            calls.initialize++
        }

        @PreDestroy
        void shutdown() {
            calls.shutdown++
        }
    }

    static class SubclassFixture extends InjectedFixture {

        @Override
        @Autowired
        void setOverridableCollaborator(CollaboratorFixture collaborator) {
            super.setOverridableCollaborator(collaborator)
        }

        @Autowired
        void setSubclassCollaborator(CollaboratorFixture collaborator) {
            calls.subclassCollaborator++
        }
    }

    static interface Dependency {
    }

    static class DependencyFixture implements Dependency {
    }

    /** Bounded, for the generated code resolves the dependency by the erased type of the parameter. */
    static class GenericSuperclassFixture<T extends Dependency> {

        /** How many times each member was called. */
        final Map<String, Integer> calls = [:].withDefault { 0 }

        @Autowired
        void setDependency(T dependency) {
            calls.dependency++
        }
    }

    static class GenericSubclassFixture extends GenericSuperclassFixture<DependencyFixture> {

        @Override
        @Autowired
        void setDependency(DependencyFixture dependency) {
            super.setDependency(dependency)
        }
    }

    /** A field and a method each annotated for both processors, naming the resource to inject. */
    static class DualAnnotatedFixture {

        /** How many times each member was called. */
        final Map<String, Integer> calls = [:].withDefault { 0 }

        /** Public, for the generated code sets it directly, from classes these tests load separately. */
        @Resource(name = 'named')
        @Autowired
        public CollaboratorFixture collaborator

        public CollaboratorFixture wired

        @Resource(name = 'named')
        @Autowired
        void wire(CollaboratorFixture collaborator) {
            calls.wire++
            wired = collaborator
        }
    }

    /** Holds an inner bean, set as a property. */
    static class OuterFixture {

        InjectedFixture nested
    }

    /** Creates, from factory methods, beans of a class other than the type the methods declare. */
    static class FactoryFixture {

        InjectedFixture declaredAsSuperclass() {
            new SubclassFixture()
        }

        GenericSuperclassFixture<DependencyFixture> declaredAsGenericSuperclass() {
            new GenericSubclassFixture()
        }

        InjectedType declaredAsInterface() {
            new InjectedFixture()
        }
    }
}
