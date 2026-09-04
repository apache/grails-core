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

import java.lang.annotation.Annotation
import java.lang.reflect.Modifier

import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.codehaus.groovy.control.Phases
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.AutoConfigureBefore
import org.springframework.boot.autoconfigure.AutoConfigureOrder
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.autoconfigure.condition.SearchStrategy
import org.springframework.boot.autoconfigure.context.MessageSourceAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import org.springframework.context.annotation.ImportResource
import org.springframework.context.annotation.Lazy
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.PropertySource
import org.springframework.context.annotation.PropertySources
import org.springframework.context.annotation.Scope
import org.springframework.context.annotation.ScopedProxyMode
import org.springframework.core.annotation.Order
import org.springframework.core.env.MapPropertySource
import org.springframework.context.annotation.Conditional
import org.springframework.core.type.AnnotatedTypeMetadata
import org.springframework.context.annotation.ConditionContext
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

import grails.compiler.beans.ConditionalOnGrailsEnv
import grails.plugins.Plugin
import grails.util.Environment

class GrailsBeansASTTransformationSpec extends Specification {

    @TempDir
    File tempDir

    private static final String FIXTURE = '''
        import grails.compiler.beans.GrailsBeans
        import org.springframework.beans.factory.annotation.Value
        import org.springframework.boot.autoconfigure.AutoConfiguration
        import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
        import org.springframework.core.annotation.Order

        @GrailsBeans
        @AutoConfiguration
        class FixtureBeans {
            def beans = {
                bean('greeting', String) {
                    'hello'
                }

                bean('answer', Integer).conditionalOnMissingBean(Integer) {
                    42
                }

                bean('shout', String) { String input ->
                    input.toUpperCase()
                }

                bean('primaryGreeting', String).primary() {
                    'primary hello'
                }

                bean('lazyGreeting', String).lazy() {
                    'lazy hello'
                }

                bean('scopedGreeting', String).scope('prototype') {
                    'scoped hello'
                }

                bean('combinedGreeting', String).primary().lazy().scope('prototype').conditionalOnMissingBean(String) {
                    'combined hello'
                }

                bean('orderedGreeting', String).annotate(Order, value: 1) {
                    'ordered hello'
                }

                bean('webOnlyGreeting', String).annotate(ConditionalOnWebApplication) {
                    'web hello'
                }

                bean('multiAnnotatedGreeting', String).primary().annotate(Order, value: 2).annotate(ConditionalOnWebApplication) {
                    'multi hello'
                }

                field('suffix', String).annotate(Value, value: '${greeting.suffix:!!!}')

                method('yell', String) { String input ->
                    input.toUpperCase() + (suffix ?: '')
                }

                bean('yelledGreeting', String) {
                    yell('hello')
                }
            }
        }
    '''

    def "rejects a call to a sibling bean method when the host's bean methods are not proxied"() {
        given: "@AutoConfiguration is @Configuration(proxyBeanMethods = false), so the call builds a second Greeter"
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class UnproxiedSiblingBeans {
                def beans = {
                    bean('greeter', String) {
                        'hello'
                    }

                    bean('shout', String) {
                        greeter().toUpperCase()
                    }
                }
            }
        '''

        when:
        compile(source)

        then:
        MultipleCompilationErrorsException e = thrown(MultipleCompilationErrorsException)
        e.message.contains('is another bean declared in this block')
        e.message.contains('constructs a second instance')
    }

    def "rejects a sibling bean call from a method(...) helper too"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class HelperSiblingBeans {
                def beans = {
                    bean('greeter', String) {
                        'hello'
                    }

                    method('shouted', String) {
                        greeter().toUpperCase()
                    }
                }
            }
        '''

        when:
        compile(source)

        then:
        MultipleCompilationErrorsException e = thrown(MultipleCompilationErrorsException)
        e.message.contains('is another bean declared in this block')
    }

    def "allows a sibling bean call on a full @Configuration class, where Spring does return the singleton"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.context.annotation.Configuration

            @GrailsBeans
            @Configuration
            class ProxiedSiblingBeans {
                def beans = {
                    bean('greeter', StringBuilder) {
                        new StringBuilder('hello')
                    }

                    bean('shout', String) {
                        greeter().toString().toUpperCase()
                    }
                }
            }
        '''

        when:
        Class<?> compiled = compile(source)

        then:
        noExceptionThrown()
        compiled.getDeclaredMethod('shout') != null
    }

    def "rejects a sibling bean call on a @Configuration class that has switched proxying off"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.context.annotation.Configuration

            @GrailsBeans
            @Configuration(proxyBeanMethods = false)
            class LiteConfigurationBeans {
                def beans = {
                    bean('greeter', StringBuilder) {
                        new StringBuilder('hello')
                    }

                    bean('shout', String) {
                        greeter().toString().toUpperCase()
                    }
                }
            }
        '''

        when:
        compile(source)

        then:
        MultipleCompilationErrorsException e = thrown(MultipleCompilationErrorsException)
        e.message.contains('is not a proxied @Configuration class')
    }

    def "rejects a sibling bean call on a class carrying no Spring configuration annotation, as a Grails Application does"() {
        given: "the shape a Grails Application class has - a configuration source Spring never proxies"
        String source = '''
            import grails.compiler.beans.GrailsBeans

            @GrailsBeans
            class UnannotatedHostBeans {
                def beans = {
                    bean('greeter', String) {
                        'hello'
                    }

                    bean('shout', String) {
                        greeter().toUpperCase()
                    }
                }
            }
        '''

        when:
        compile(source)

        then:
        MultipleCompilationErrorsException e = thrown(MultipleCompilationErrorsException)
        e.message.contains('is another bean declared in this block')
    }

    def "rejects a sibling bean call on a plugin descriptor's generated sibling, which is always @AutoConfiguration"() {
        given: "the check runs on the sibling, so the annotations moved onto it must already be there"
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import grails.plugins.Plugin
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class SiblingCallGrailsPlugin extends Plugin {
                def beans = {
                    bean('greeter', String) {
                        'hello'
                    }

                    bean('shout', String) {
                        greeter().toUpperCase()
                    }
                }
            }
        '''

        when:
        compile(source)

        then:
        MultipleCompilationErrorsException e = thrown(MultipleCompilationErrorsException)
        e.message.contains('is another bean declared in this block')
        e.message.contains('SiblingCallAutoConfiguration is not a proxied @Configuration class')
    }

    def "a static @Bean method really is uninterceptable on a proxied @Configuration class"() {
        given: "hand-written, not the DSL - this pins Spring's behaviour, which is the reason for the rule"
        String source = '''
            import org.springframework.context.annotation.Bean
            import org.springframework.context.annotation.Configuration

            class Leaf { }

            class Holder {
                Leaf leaf

                Holder(Leaf leaf) {
                    this.leaf = leaf
                }
            }

            @Configuration
            class HandWrittenStaticConfig {
                @Bean
                static Leaf leaf() {
                    new Leaf()
                }

                @Bean
                Holder holder() {
                    new Holder(leaf())
                }
            }
        '''

        and:
        GroovyClassLoader loader = new GroovyClassLoader(getClass().classLoader)
        loader.parseClass(source)
        def context = new AnnotationConfigApplicationContext()
        context.classLoader = loader
        context.register(loader.loadClass('HandWrittenStaticConfig'))

        when:
        context.refresh()

        then: "CGLIB cannot override a static method, so the call was not intercepted"
        !context.getBean('holder').leaf.is(context.getBean('leaf'))

        cleanup:
        context.close()
    }

    def "rejects a call to a static sibling bean even on a proxied @Configuration class"() {
        given: "the DSL spelling of the shape above"
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.context.annotation.Configuration

            @GrailsBeans
            @Configuration
            class ProxiedStaticSiblingBeans {
                def beans = {
                    bean('greeter', StringBuilder).staticMethod() {
                        new StringBuilder('hello')
                    }

                    bean('shout', String) {
                        greeter().toString().toUpperCase()
                    }
                }
            }
        '''

        when:
        compile(source)

        then:
        MultipleCompilationErrorsException e = thrown(MultipleCompilationErrorsException)
        e.message.contains('is declared .staticMethod()')
        e.message.contains('never intercepted by the container')
    }

    def "a proxying @Configuration written alongside a non-proxying composed annotation still counts as proxied"() {
        given: "@AutoConfiguration prunes @Configuration(proxyBeanMethods = false) on the way past it;\
               the author's own @Configuration must not then be skipped as already-seen"
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration
            import org.springframework.context.annotation.Configuration

            @GrailsBeans
            @AutoConfiguration
            @Configuration
            class DoublyAnnotatedBeans {
                def beans = {
                    bean('greeter', StringBuilder) {
                        new StringBuilder('hello')
                    }

                    bean('shout', String) {
                        greeter().toString().toUpperCase()
                    }
                }
            }
        '''

        when:
        Class<?> compiled = compile(source)

        then: "Spring reads the directly-declared @Configuration, so the sibling call is legitimate"
        noExceptionThrown()
        compiled.getDeclaredMethod('shout') != null
    }

    def "still allows a non-static sibling bean call on a proxied @Configuration class"() {
        given: "the narrowing must not have swallowed the exemption the proxied case earns"
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.context.annotation.Configuration

            @GrailsBeans
            @Configuration
            class ProxiedInstanceSiblingBeans {
                def beans = {
                    bean('greeter', StringBuilder) {
                        new StringBuilder('hello')
                    }

                    bean('shout', String) {
                        greeter().toString().toUpperCase()
                    }
                }
            }
        '''

        when:
        Class<?> compiled = compile(source)

        then:
        noExceptionThrown()
        compiled.getDeclaredMethod('shout') != null
    }

    def "leaves a call to a method(...) helper alone - only bean methods are singletons to miss"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class HelperCallBeans {
                def beans = {
                    method('salutation', String) {
                        'hello'
                    }

                    bean('greeter', String) {
                        salutation()
                    }
                }
            }
        '''

        when:
        Class<?> compiled = compile(source)

        then:
        noExceptionThrown()
        compiled.getDeclaredConstructor().newInstance().greeter() == 'hello'
    }

    def "leaves an unqualified call inside a nested closure alone, where a delegate may be answering it"() {
        given: "the shape DataBindingGrailsPlugin uses - tap { initialize() } calls the registry, not this"
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            class Registry {
                boolean ready

                void initialize() {
                    ready = true
                }
            }

            @GrailsBeans
            @AutoConfiguration
            class DelegateCallBeans {
                def beans = {
                    bean('initialize', String) {
                        'a bean that happens to share the name'
                    }

                    bean('registry', Registry) {
                        new Registry().tap {
                            initialize()
                        }
                    }
                }
            }
        '''

        and: "loaded by name - compile() hands back the first class in the source, which is Registry"
        GroovyClassLoader loader = new GroovyClassLoader(getClass().classLoader)
        loader.parseClass(source)

        when:
        def fixture = loader.loadClass('DelegateCallBeans').getDeclaredConstructor().newInstance()

        then: "no error: tap resolves delegate-first, so the call reaches Registry, not this class"
        noExceptionThrown()
        fixture.registry().ready
    }

    def "leaves a same-named call on another receiver alone"() {
        given: "a call with a real receiver is somebody else's method that happens to share the name"
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class OtherReceiverBeans {
                def beans = {
                    bean('trim', String) {
                        'hello'
                    }

                    bean('padded', String) { StringBuilder source ->
                        source.toString().trim()
                    }
                }
            }
        '''

        when:
        Class<?> compiled = compile(source)

        then:
        noExceptionThrown()
        compiled.getDeclaredMethod('padded', StringBuilder) != null
    }

    def "typeArguments declares a parameterized bean type, which is what Spring resolves an injection point against"() {
        given: "two handlers differing only in their type argument - raw beans could not tell them apart"
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            interface Handler<T> { }
            class Order { }
            class Refund { }
            class OrderHandler implements Handler<Order> { }
            class RefundHandler implements Handler<Refund> { }

            class Dispatcher {
                Handler<Order> handler

                Dispatcher(Handler<Order> handler) {
                    this.handler = handler
                }
            }

            @GrailsBeans
            @AutoConfiguration
            class GenericBeansFixture {
                def beans = {
                    bean('orderHandler', Handler).typeArguments(Order) { new OrderHandler() }
                    bean('refundHandler', Handler).typeArguments(Refund) { new RefundHandler() }

                    bean('dispatcher', Dispatcher) { Handler<Order> handler ->
                        new Dispatcher(handler)
                    }
                }
            }
        '''

        and:
        GroovyClassLoader loader = new GroovyClassLoader(getClass().classLoader)
        loader.parseClass(source)
        def context = new AnnotationConfigApplicationContext()
        context.classLoader = loader
        context.register(loader.loadClass('GenericBeansFixture'))

        when:
        context.refresh()

        then: "both are Handler beans, so the type argument is the only thing that can discriminate"
        context.getBeanNamesForType(loader.loadClass('Handler')).length == 2

        and: "and Spring picked the one whose declared type argument matches the injection point"
        loader.loadClass('OrderHandler').isInstance(context.getBean('dispatcher').handler)

        cleanup:
        context.close()
    }

    def "typeArguments reaches the generated method's signature, not just its erasure"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class GenericSignatureFixture {
                def beans = {
                    bean('names', ArrayList).typeArguments(String) {
                        new ArrayList<String>()
                    }
                }
            }
        '''

        when:
        Class<?> compiled = compile(source)
        def method = compiled.getDeclaredMethod('names')

        then:
        method.returnType == ArrayList
        method.genericReturnType.typeName == 'java.util.ArrayList<java.lang.String>'
    }

    def "typeArguments works on a bodyless bean, where the synthesized construction carries them too"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class BodylessGenericFixture {
                def beans = {
                    bean('names', ArrayList).typeArguments(String)
                }
            }
        '''

        when:
        Class<?> compiled = compile(source)
        def method = compiled.getDeclaredMethod('names')

        then:
        method.genericReturnType.typeName == 'java.util.ArrayList<java.lang.String>'
        compiled.getDeclaredConstructor().newInstance().names() == []
    }

    def "typeArguments applies to field(...) and method(...) as well as bean(...)"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class GenericMemberFixture {
                def beans = {
                    field('cache', Map).typeArguments(String, Integer)

                    method('names', ArrayList).typeArguments(String) {
                        new ArrayList<String>()
                    }

                    bean('greeting', String) {
                        'hello'
                    }
                }
            }
        '''

        when:
        Class<?> compiled = compile(source)

        then:
        compiled.getDeclaredField('cache').genericType.typeName == 'java.util.Map<java.lang.String, java.lang.Integer>'
        compiled.getDeclaredMethod('names').genericReturnType.typeName == 'java.util.ArrayList<java.lang.String>'
    }

    @Unroll
    def "typeArguments rejects #description"() {
        given:
        String source = """
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class BadGenericsFixture${fixture} {
                def beans = {
                    bean('value', ${type}).typeArguments(${arguments}) {
                        ${body}
                    }
                }
            }
        """

        when:
        compile(source)

        then:
        MultipleCompilationErrorsException e = thrown(MultipleCompilationErrorsException)
        e.message.contains(expected)

        where:
        description                          | fixture   | type        | arguments         | body                       | expected
        'too few type arguments'             | 'Few'     | 'Map'       | 'String'          | 'new HashMap<>()'          | 'declares 2 type parameters'
        'too many type arguments'            | 'Many'    | 'ArrayList' | 'String, Integer' | 'new ArrayList<>()'        | 'declares 1 type parameter,'
        'a type that is not generic'         | 'Plain'   | 'String'    | 'Integer'         | "'hello'"                  | 'is not a generic type'
        'something that is not a type'       | 'NotType' | 'ArrayList' | "'String'"        | 'new ArrayList<>()'        | '.typeArguments(...) takes types'
        'no type arguments at all'           | 'Empty'   | 'ArrayList' | ''                | 'new ArrayList<>()'        | 'requires at least one type'
    }

    @Unroll
    def "annotate carries an array-valued attribute written as #description"() {
        given: "@DependsOn.value() is a String[], the shape a single value has to widen into"
        String source = """
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration
            import org.springframework.context.annotation.DependsOn

            @GrailsBeans
            @AutoConfiguration
            class ArrayAttributeFixture${fixture} {
                def beans = {
                    bean('first', String) {
                        'first'
                    }

                    bean('second', String) {
                        'second'
                    }

                    bean('third', String).annotate(DependsOn, value: ${written}) {
                        'third'
                    }
                }
            }
        """

        when:
        Class<?> compiled = compile(source)
        def method = compiled.getDeclaredMethod('third')

        then: "the transform builds the annotation after Groovy's own verifier has run, so this is\
               worth pinning rather than assuming"
        method.getAnnotation(DependsOn).value() == expected as String[]

        where:
        description        | fixture  | written               | expected
        'a single value'   | 'Scalar' | "'first'"             | ['first']
        'a list'           | 'List'   | "['first', 'second']" | ['first', 'second']
    }

    def "dumps the generated members when grails.beans.dsl.dumpdir is set"() {
        given: "a block whose interesting parts are all declaration, not body"
        File dumpDir = new File(tempDir, 'beans-dump')
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.beans.factory.annotation.Autowired
            import org.springframework.boot.autoconfigure.AutoConfiguration
            import org.springframework.context.annotation.DependsOn

            @GrailsBeans
            @AutoConfiguration
            class DumpedBeans {
                def beans = {
                    field('encoding', String).value('app.encoding', 'UTF-8')

                    bean('names', ArrayList).typeArguments(String) {
                        new ArrayList<String>()
                    }

                    bean('greeter', String).primary().annotate(DependsOn, value: 'names') {
                        'hello'
                    }

                    bean('shout', String) { @Autowired(required = false) StringBuilder input ->
                        input?.toString() ?: 'none'
                    }
                }
            }
        '''

        when:
        System.setProperty('grails.beans.dsl.dumpdir', dumpDir.absolutePath)
        compile(source)
        String dumped = new File(dumpDir, 'DumpedBeans.beans.txt').text

        then: "the bean name Spring resolves by, and the annotations the qualifiers became"
        dumped.contains('@Bean(value = ["greeter"])')
        dumped.contains('@Primary')
        dumped.contains('@DependsOn(value = "names")')

        and: "the declared type, including type arguments it ended up carrying"
        dumped.contains('java.util.ArrayList<java.lang.String> names()')

        and: "a raw type is not dressed up in its own type parameters"
        !dumped.contains('ArrayList<E>')

        and: "parameter annotations, which is where optional and qualified live"
        dumped.contains('@Autowired(required = false) java.lang.StringBuilder input')

        and: "fields too, with the @Value the config key became"
        dumped.contains('private java.lang.String encoding')
        dumped.contains('@Value(value = "${app.encoding:UTF-8}")')

        and: "bodies are deliberately absent - they are the author's own closure bodies"
        !dumped.contains("'hello'")

        cleanup:
        System.clearProperty('grails.beans.dsl.dumpdir')
    }

    def "writes nothing when the dump directory is not set"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class UndumpedBeans {
                def beans = {
                    bean('greeting', String) { 'hello' }
                }
            }
        '''

        when:
        Class<?> compiled = compile(source)

        then:
        System.getProperty('grails.beans.dsl.dumpdir') == null
        compiled.getDeclaredMethod('greeting') != null
    }

    def "rejects a generated bean calling a hand-written @Bean method on the same class"() {
        given: "the mixed shape a migration passes through, where the call was correct before the move"
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration
            import org.springframework.context.annotation.Bean

            @GrailsBeans
            @AutoConfiguration
            class MixedHandWrittenBeans {
                @Bean
                StringBuilder greeter() {
                    new StringBuilder('hello')
                }

                def beans = {
                    bean('shout', String) {
                        greeter().toString().toUpperCase()
                    }
                }
            }
        '''

        when:
        compile(source)

        then:
        MultipleCompilationErrorsException e = thrown(MultipleCompilationErrorsException)
        e.message.contains('is another bean declared in this block')
    }

    def "leaves a call to a hand-written method that is not a bean alone"() {
        given: "only @Bean methods have a singleton to miss"
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class MixedPlainMethodBeans {
                String salutation() {
                    'hello'
                }

                def beans = {
                    bean('greeting', String) {
                        salutation()
                    }
                }
            }
        '''

        when:
        Class<?> compiled = compile(source)

        then:
        noExceptionThrown()
        compiled.getDeclaredConstructor().newInstance().greeting() == 'hello'
    }

    def "a map construction settles type arguments the same way a named implementation does"() {
        given: "the one-expression form for an implementation configured by properties"
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            interface Holder<T> { }

            class StringHolder implements Holder<String> {
                String label
            }

            @GrailsBeans
            @AutoConfiguration
            class MapConstructionBeans {
                def beans = {
                    bean('label', String) { 'hello' }

                    bean('holder', Holder) { String label ->
                        new StringHolder(label: label)
                    }
                }
            }
        '''

        and:
        GroovyClassLoader loader = new GroovyClassLoader(getClass().classLoader)
        loader.parseClass(source)
        def fixture = loader.loadClass('MapConstructionBeans')

        expect: "the construction is evidence, whether its arguments are positional or named"
        fixture.getDeclaredMethod('holder', String).genericReturnType.typeName == 'Holder<java.lang.String>'

        and: "and it really does configure the instance"
        fixture.getDeclaredConstructor().newInstance().holder('hi').label == 'hi'
    }

    def "conditionalOnBean registers a bean only when the bean it names is there"() {
        given: "the shape an optional integration takes - wire the adapter only if the transport exists"
        String source = """
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            class Transport { }
            class Adapter { }

            @GrailsBeans
            @AutoConfiguration
            class ConditionalOnBeanFixture${suffix} {
                def beans = {
                    ${transport}
                    bean('adapter', Adapter).conditionalOnBean(Transport) {
                        new Adapter()
                    }
                }
            }
        """

        and:
        GroovyClassLoader loader = new GroovyClassLoader(getClass().classLoader)
        loader.parseClass(source)
        def context = new AnnotationConfigApplicationContext()
        context.classLoader = loader
        context.register(loader.loadClass("ConditionalOnBeanFixture${suffix}"))

        when:
        context.refresh()

        then:
        context.containsBean('adapter') == registered

        cleanup:
        context.close()

        where:
        description                  | suffix     | transport                                                     | registered
        'the transport is present'   | 'Present'  | "bean('transport', Transport)"                                | true
        'the transport is absent'    | 'Absent'   | ''                                                            | false
    }

    def "conditionalOnBean rejects the zero-argument form, which would condition a bean on its own type"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class BareConditionalOnBeanFixture {
                def beans = {
                    bean('greeting', String).conditionalOnBean() {
                        'hello'
                    }
                }
            }
        '''

        when:
        compile(source)

        then:
        MultipleCompilationErrorsException e = thrown(MultipleCompilationErrorsException)
        e.message.contains('needs at least one type or attribute')
    }

    def "conditionalOnBean takes the annotation's named attributes, like its opposite"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration
            import org.springframework.boot.autoconfigure.condition.ConditionalOnBean

            @GrailsBeans
            @AutoConfiguration
            class NamedConditionalOnBeanFixture {
                def beans = {
                    bean('greeting', String).conditionalOnBean(name: 'transport') {
                        'hello'
                    }
                }
            }
        '''

        when:
        Class<?> compiled = compile(source)
        def annotation = compiled.getDeclaredMethod('greeting').getAnnotation(ConditionalOnBean)

        then:
        annotation.name() == ['transport'] as String[]
    }

    def "conditionalOnClass takes a type, and registers the bean only when it is present"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class OnClassPresentBeans {
                def beans = {
                    bean('greeting', String).conditionalOnClass(StringBuilder) {
                        'hello'
                    }

                    bean('absent', String).conditionalOnClass(name: 'com.example.NotOnTheClasspath') {
                        'never'
                    }
                }
            }
        '''

        and:
        GroovyClassLoader loader = new GroovyClassLoader(getClass().classLoader)
        loader.parseClass(source)
        def context = new AnnotationConfigApplicationContext()
        context.classLoader = loader
        context.register(loader.loadClass('OnClassPresentBeans'))

        when:
        context.refresh()

        then: 'the present type registers, the absent name does not'
        context.containsBean('greeting')
        !context.containsBean('absent')

        cleanup:
        context.close()
    }

    def "conditionalOnClass keeps types and String names in their own annotation members"() {
        given: "a literal is compiler-checked; a name is the only form that survives the class being absent"
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration
            import org.springframework.boot.autoconfigure.condition.ConditionalOnClass

            @GrailsBeans
            @AutoConfiguration
            class OnClassMembersBeans {
                def beans = {
                    bean('greeting', String).conditionalOnClass(StringBuilder, 'java.lang.Integer') {
                        'hello'
                    }
                }
            }
        '''

        when:
        Class<?> compiled = compile(source)
        def annotation = compiled.getDeclaredMethod('greeting').getAnnotation(ConditionalOnClass)

        then:
        annotation.value() == [StringBuilder] as Class[]
        annotation.name() == ['java.lang.Integer'] as String[]
    }

    @Unroll
    def "conditionalOnClass rejects #description"() {
        given:
        String source = """
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class BadOnClassFixture${fixture} {
                def beans = {
                    bean('greeting', String).conditionalOnClass(${arguments}) {
                        'hello'
                    }
                }
            }
        """

        when:
        compile(source)

        then:
        MultipleCompilationErrorsException e = thrown(MultipleCompilationErrorsException)
        e.message.contains(expected)

        where:
        description                       | fixture  | arguments                                | expected
        'no class at all'                 | 'Empty'  | ''                                       | 'needs at least one class'
        'a non-String, non-type argument' | 'Number' | '42'                                     | 'takes types or non-blank String class names'
        'a blank name'                    | 'Blank'  | "'  '"                                   | 'takes types or non-blank String class names'
        'types given both ways'           | 'Both'   | "StringBuilder, value: [StringBuilder]"  | 'both positionally and via'
    }

    def "a bean name may be a compile-time String constant, however it is written"() {
        given: "the shape that motivated it - a name something else has to look the bean up by"
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            class BeanNames {
                static final String QUALIFIED = 'qualifiedName'
            }

            @GrailsBeans
            @AutoConfiguration
            class ConstantNamedBeans {
                static final String BARE = 'bareName'

                def beans = {
                    bean(BARE, String) { 'from a bare constant' }
                    bean(BeanNames.QUALIFIED, String) { 'from a qualified constant' }
                    bean('literal' + 'Name', String) { 'from a concatenation' }
                    field(BARE + 'Field', String).value('app.thing', 'x')
                }
            }
        '''

        and:
        GroovyClassLoader loader = new GroovyClassLoader(getClass().classLoader)
        loader.parseClass(source)
        Class<?> compiled = loader.loadClass('ConstantNamedBeans')

        expect: "the @Bean value is the constant's value, not its identifier"
        compiled.getDeclaredMethod('bareName').getAnnotation(Bean).value() == ['bareName'] as String[]
        compiled.getDeclaredMethod('qualifiedName').getAnnotation(Bean).value() == ['qualifiedName'] as String[]
        compiled.getDeclaredMethod('literalName').getAnnotation(Bean).value() == ['literalName'] as String[]

        and: "field(...) takes one too, since its name is a member name like any other"
        compiled.getDeclaredField('bareNameField') != null
    }

    def "a bean name that is not a compile-time constant is still rejected"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class RuntimeNamedBeans {
                def beans = {
                    bean(System.getProperty('bean.name'), String) { 'nope' }
                }
            }
        '''

        when:
        compile(source)

        then:
        MultipleCompilationErrorsException e = thrown(MultipleCompilationErrorsException)
        e.message.contains('String literal or a compile-time String constant')
    }

    def "group declares a nested configuration class holding its beans"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class GroupedBeans {
                def beans = {
                    bean('top', String) { 'top' }

                    group('imageServing').conditionalOnClass(StringBuilder) {
                        bean('inner', String) { 'inner' }
                        bean('other', String) { 'other' }
                    }
                }
            }
        '''

        and:
        GroovyClassLoader loader = new GroovyClassLoader(getClass().classLoader)
        loader.parseClass(source)
        Class<?> nested = loader.loadClass('GroupedBeans$ImageServingConfiguration')

        expect: "a nested static @Configuration(proxyBeanMethods = false) carrying the condition"
        java.lang.reflect.Modifier.isStatic(nested.modifiers)
        !nested.getAnnotation(Configuration).proxyBeanMethods()
        nested.getAnnotation(ConditionalOnClass).value() == [StringBuilder] as Class[]

        and: "the group's beans are its methods, and are not on the host"
        nested.getDeclaredMethod('inner') != null
        nested.getDeclaredMethod('other') != null
        loader.loadClass('GroupedBeans').declaredMethods.every { it.name != 'inner' }
    }

    def "a group's condition gates its beans, and Spring finds the nested class unaided"() {
        given: "no @Import anywhere - ConfigurationClassParser processes member classes"
        String source = """
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class GroupGatedBeans${fixture} {
                def beans = {
                    bean('always', String) { 'always' }

                    group('optional').conditionalOnClass(name: '${gated}') {
                        bean('sometimes', String) { 'sometimes' }
                    }
                }
            }
        """

        and:
        GroovyClassLoader loader = new GroovyClassLoader(getClass().classLoader)
        loader.parseClass(source)
        def context = new AnnotationConfigApplicationContext()
        context.classLoader = loader
        context.register(loader.loadClass("GroupGatedBeans${fixture}"))

        when:
        context.refresh()

        then:
        context.containsBean('always')
        context.containsBean('sometimes') == registered

        cleanup:
        context.close()

        where:
        fixture   | gated                            | registered
        'Present' | 'java.lang.StringBuilder'        | true
        'Absent'  | 'com.example.NotOnTheClasspath'  | false
    }

    def "a group is how a bean whose own signature names an absent class stays declarable"() {
        given: "the case that motivated it - guarding the method would not have been safe"
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class SignatureGuardedBeans {
                def beans = {
                    bean('safe', String) { 'safe' }

                    group('optional').conditionalOnClass(name: 'com.example.Missing') {
                        bean('needsMissing', StringBuilder) { StringBuilder collaborator ->
                            collaborator
                        }
                    }
                }
            }
        '''

        and:
        GroovyClassLoader loader = new GroovyClassLoader(getClass().classLoader)
        loader.parseClass(source)
        def context = new AnnotationConfigApplicationContext()
        context.classLoader = loader
        context.register(loader.loadClass('SignatureGuardedBeans'))

        when:
        context.refresh()

        then: "the nested class is never parsed, so nothing in its signatures is resolved"
        noExceptionThrown()
        context.containsBean('safe')
        !context.containsBean('needsMissing')

        cleanup:
        context.close()
    }

    @Unroll
    def "group rejects #description"() {
        given:
        String source = """
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class BadGroupFixture${fixture} {
                def beans = {
                    ${declaration}
                }
            }
        """

        when:
        compile(source)

        then:
        MultipleCompilationErrorsException e = thrown(MultipleCompilationErrorsException)
        e.message.contains(expected)

        where:
        description                    | fixture   | declaration                                                              | expected
        'an empty body'                | 'Empty'   | "group('a').conditionalOnClass(String) { }"                              | 'declares nothing'
        'nesting'                      | 'Nested'  | "group('a') { group('b') { bean('x', String) { 'x' } } }"                | 'cannot be nested'
        'closure parameters'           | 'Params'  | "group('a') { String s -> bean('x', String) { 'x' } }"                   | 'takes no closure parameters'
        'a bean-shaped qualifier'      | 'Primary' | "group('a').primary() { bean('x', String) { 'x' } }"                     | 'cannot be chained onto group'
        'a name that is not an ident'  | 'BadName' | "group('not an identifier') { bean('x', String) { 'x' } }"               | 'valid Java identifier'
    }

    private Class<?> compile() {
        compile(FIXTURE)
    }

    private Class<?> compile(String source) {
        new GroovyClassLoader(getClass().classLoader).parseClass(source)
    }

    def "compiles a plain bean() call into a public @Bean factory method"() {
        given:
        Class<?> fixtureBeans = compile()

        when:
        def method = fixtureBeans.getDeclaredMethod('greeting')

        then:
        method.returnType == String
        method.isAnnotationPresent(Bean)
        method.getAnnotation(Bean).value() == ['greeting'] as String[]

        and: "the closure body became the real method body"
        fixtureBeans.getDeclaredConstructor().newInstance().greeting() == 'hello'
    }

    def "bean(Type) with no explicit name derives the JavaBeans-conventional property name, including for acronym-prefixed types"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class DecapitalizeFixtureBeans {
                def beans = {
                    bean(String) {
                        'unnamed'
                    }

                    bean(URLHelper) {
                        new URLHelper()
                    }
                }
            }

            class URLHelper { }
        '''

        when:
        Class<?> fixtureBeans = compile(source)

        then: "an ordinary type name is decapitalized the usual way"
        fixtureBeans.getDeclaredMethod('string') != null

        and: "a type whose name starts with two-or-more uppercase letters (an acronym) is left as-is, " +
                "per java.beans.Introspector.decapitalize's convention - not naively lowercased to uRLHelper"
        fixtureBeans.getDeclaredMethod('URLHelper') != null
    }

    def "bean(Type) with no factory closure compiles to a method that constructs the declared type"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class BodylessFixtureBeans {
                def beans = {
                    bean(Greeter)
                    bean('otherGreeter', Greeter)
                }
            }

            class Greeter {
                String greet() { 'hi' }
            }
        '''

        when:
        Class<?> fixtureBeans = compile(source)
        def derived = fixtureBeans.getDeclaredMethod('greeter')
        def named = fixtureBeans.getDeclaredMethod('otherGreeter')

        then: "the name is derived from the type, or taken from the explicit name"
        derived.getAnnotation(Bean).value() == ['greeter'] as String[]
        named.getAnnotation(Bean).value() == ['otherGreeter'] as String[]

        and: "both take no parameters and return a new instance of the declared type"
        derived.parameterCount == 0
        named.parameterCount == 0

        and:
        def instance = fixtureBeans.getDeclaredConstructor().newInstance()
        derived.invoke(instance).greet() == 'hi'
        !derived.invoke(instance).is(derived.invoke(instance))
    }

    def "bean(Type) with no factory closure still accepts chained qualifiers"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class BodylessQualifiedFixtureBeans {
                def beans = {
                    bean(Widget).primary().lazy().conditionalOnMissingBean()
                }
            }

            class Widget { }
        '''

        when:
        Class<?> fixtureBeans = compile(source)
        def method = fixtureBeans.getDeclaredMethod('widget')

        then:
        method.isAnnotationPresent(Bean)
        method.isAnnotationPresent(Primary)
        method.isAnnotationPresent(Lazy)
        method.isAnnotationPresent(ConditionalOnMissingBean)
        method.parameterCount == 0
    }

    def "bean(Type) with no factory closure is rejected for a type that cannot be constructed"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class UninstantiableFixtureBeans {
                def beans = {
                    bean(Runnable)
                }
            }
        '''

        when:
        compile(source)

        then:
        def e = thrown(Exception)
        e.message.contains('cannot be done for an interface or abstract class')
    }

    def "compiles conditionalOnMissingBean(...) into a @ConditionalOnMissingBean annotation"() {
        given:
        Class<?> fixtureBeans = compile()

        when:
        def method = fixtureBeans.getDeclaredMethod('answer')

        then:
        method.returnType == Integer
        method.isAnnotationPresent(Bean)
        method.isAnnotationPresent(ConditionalOnMissingBean)
        method.getAnnotation(ConditionalOnMissingBean).value() as List == [Integer]

        and:
        fixtureBeans.getDeclaredConstructor().newInstance().answer() == 42
    }

    def "conditionalOnMissingBean(...) accepts the annotation's named attributes"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration
            import org.springframework.boot.autoconfigure.condition.SearchStrategy

            @GrailsBeans
            @AutoConfiguration
            class NamedAttributesFixture {
                def beans = {
                    bean('greeting', String).conditionalOnMissingBean(name: 'greeting', search: SearchStrategy.CURRENT) {
                        'hello'
                    }
                }
            }
        '''

        when:
        Class<?> fixtureBeans = compile(source)
        def annotation = fixtureBeans.getDeclaredMethod('greeting').getAnnotation(ConditionalOnMissingBean)

        then:
        annotation.name() == ['greeting'] as String[]
        annotation.search() == SearchStrategy.CURRENT
        annotation.value().length == 0
    }

    def "conditionalOnMissingBean(...) accepts positional types mixed with named attributes"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration
            import org.springframework.boot.autoconfigure.condition.SearchStrategy

            @GrailsBeans
            @AutoConfiguration
            class MixedAttributesFixture {
                def beans = {
                    bean('greeting', CharSequence).conditionalOnMissingBean(CharSequence, search: SearchStrategy.CURRENT) {
                        'hello'
                    }
                }
            }
        '''

        when:
        Class<?> fixtureBeans = compile(source)
        def annotation = fixtureBeans.getDeclaredMethod('greeting').getAnnotation(ConditionalOnMissingBean)

        then:
        annotation.value() as List == [CharSequence]
        annotation.search() == SearchStrategy.CURRENT
    }

    def "conditionalOnMissingBeanName(...) backs off by the bean's convention-derived name, stated once"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration
            import org.springframework.boot.autoconfigure.condition.SearchStrategy
            import org.springframework.context.MessageSource
            import org.springframework.context.support.StaticMessageSource

            @GrailsBeans
            @AutoConfiguration
            class DerivedNameConditionFixture {
                def beans = {
                    bean(MessageSource).conditionalOnMissingBeanName(search: SearchStrategy.CURRENT) {
                        new StaticMessageSource()
                    }
                }
            }
        '''

        when:
        Class<?> fixtureBeans = compile(source)
        def method = fixtureBeans.getDeclaredMethod('messageSource')
        def annotation = method.getAnnotation(ConditionalOnMissingBean)

        then: "the derived bean name feeds both @Bean and the condition - one statement of truth"
        method.getAnnotation(Bean).value() == ['messageSource'] as String[]
        annotation.name() == ['messageSource'] as String[]
        annotation.search() == SearchStrategy.CURRENT
        annotation.value().length == 0
    }

    def "conditionalOnMissingBeanName() uses an explicitly-supplied bean name, even a non-identifier one"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class ExplicitNameConditionFixture {
                def beans = {
                    bean('my-source', String).conditionalOnMissingBeanName() {
                        'hello'
                    }
                }
            }
        '''

        when:
        Class<?> fixtureBeans = compile(source)
        def annotation = fixtureBeans.getDeclaredMethod('string$0').getAnnotation(ConditionalOnMissingBean)

        then:
        annotation.name() == ['my-source'] as String[]
    }

    def "zero-argument conditionalOnMissingBean() compiles to a bare annotation, letting Spring infer the return type"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class BareConditionFixture {
                def beans = {
                    bean('greeting', String).conditionalOnMissingBean() {
                        'hello'
                    }
                }
            }
        '''

        when:
        Class<?> fixtureBeans = compile(source)
        def annotation = fixtureBeans.getDeclaredMethod('greeting').getAnnotation(ConditionalOnMissingBean)

        then: "no members set - identical to writing bare @ConditionalOnMissingBean by hand"
        annotation.value().length == 0
        annotation.name().length == 0
        annotation.type().length == 0
    }

    def "compiles primary() into a @Primary annotation"() {
        given:
        Class<?> fixtureBeans = compile()

        when:
        def method = fixtureBeans.getDeclaredMethod('primaryGreeting')

        then:
        method.isAnnotationPresent(Bean)
        method.isAnnotationPresent(Primary)

        and:
        fixtureBeans.getDeclaredConstructor().newInstance().primaryGreeting() == 'primary hello'
    }

    def "compiles lazy() into a @Lazy annotation"() {
        given:
        Class<?> fixtureBeans = compile()

        when:
        def method = fixtureBeans.getDeclaredMethod('lazyGreeting')

        then:
        method.isAnnotationPresent(Bean)
        method.isAnnotationPresent(Lazy)
        method.getAnnotation(Lazy).value()

        and:
        fixtureBeans.getDeclaredConstructor().newInstance().lazyGreeting() == 'lazy hello'
    }

    def "compiles scope(...) into a @Scope annotation"() {
        given:
        Class<?> fixtureBeans = compile()

        when:
        def method = fixtureBeans.getDeclaredMethod('scopedGreeting')

        then:
        method.isAnnotationPresent(Bean)
        method.isAnnotationPresent(Scope)
        method.getAnnotation(Scope).value() == 'prototype'

        and:
        fixtureBeans.getDeclaredConstructor().newInstance().scopedGreeting() == 'scoped hello'
    }

    def "chains primary(), lazy(), scope(...), and conditionalOnMissingBean(...) together on one bean"() {
        given:
        Class<?> fixtureBeans = compile()

        when:
        def method = fixtureBeans.getDeclaredMethod('combinedGreeting')

        then:
        method.isAnnotationPresent(Bean)
        method.isAnnotationPresent(Primary)
        method.isAnnotationPresent(Lazy)
        method.isAnnotationPresent(Scope)
        method.getAnnotation(Scope).value() == 'prototype'
        method.isAnnotationPresent(ConditionalOnMissingBean)
        method.getAnnotation(ConditionalOnMissingBean).value() as List == [String]

        and:
        fixtureBeans.getDeclaredConstructor().newInstance().combinedGreeting() == 'combined hello'
    }

    def "compiles annotate(Type, attr: value) into that annotation with its members set"() {
        given:
        Class<?> fixtureBeans = compile()

        when:
        def method = fixtureBeans.getDeclaredMethod('orderedGreeting')

        then:
        method.isAnnotationPresent(Bean)
        method.isAnnotationPresent(Order)
        method.getAnnotation(Order).value() == 1

        and:
        fixtureBeans.getDeclaredConstructor().newInstance().orderedGreeting() == 'ordered hello'
    }

    def "compiles annotate(Type) with no members into a bare annotation"() {
        given:
        Class<?> fixtureBeans = compile()

        when:
        def method = fixtureBeans.getDeclaredMethod('webOnlyGreeting')

        then:
        method.isAnnotationPresent(Bean)
        method.isAnnotationPresent(ConditionalOnWebApplication)

        and:
        fixtureBeans.getDeclaredConstructor().newInstance().webOnlyGreeting() == 'web hello'
    }

    def "chains multiple different annotate(...) calls alongside a named qualifier"() {
        given:
        Class<?> fixtureBeans = compile()

        when:
        def method = fixtureBeans.getDeclaredMethod('multiAnnotatedGreeting')

        then:
        method.isAnnotationPresent(Bean)
        method.isAnnotationPresent(Primary)
        method.isAnnotationPresent(Order)
        method.getAnnotation(Order).value() == 2
        method.isAnnotationPresent(ConditionalOnWebApplication)

        and:
        fixtureBeans.getDeclaredConstructor().newInstance().multiAnnotatedGreeting() == 'multi hello'
    }

    def "a non-identifier bean name combines with other qualifiers on the same bean"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration
            import org.springframework.core.annotation.Order

            @GrailsBeans
            @AutoConfiguration
            class CombinedNamedFixture {
                def beans = {
                    bean('my-service', String).primary().annotate(Order, value: 1) {
                        'hello'
                    }
                }
            }
        '''

        when:
        Class<?> fixtureBeans = compile(source)
        def method = fixtureBeans.getDeclaredMethod('string$0')

        then:
        method.isAnnotationPresent(Bean)
        method.getAnnotation(Bean).value() == ['my-service'] as String[]
        method.isAnnotationPresent(Primary)
        method.isAnnotationPresent(Order)
        method.getAnnotation(Order).value() == 1
    }

    def "a reserved-keyword bean name is a legal Spring bean name and gets a synthesized method name"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class KeywordBeanNameFixture {
                def beans = {
                    bean('int', String) {
                        'hello'
                    }
                }
            }
        '''

        when:
        Class<?> fixtureBeans = compile(source)

        then: "'int' can't be a Java method name, but it's a perfectly legal Spring bean name"
        fixtureBeans.getDeclaredMethod('string$0').getAnnotation(Bean).value() == ['int'] as String[]
    }

    def "bean(...) with a non-identifier name falls back to a synthesized <type>\$N method name"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class SynthesizedNameFixture {
                def beans = {
                    bean('my-service', String) {
                        'hello'
                    }
                }
            }
        '''

        when:
        Class<?> fixtureBeans = compile(source)
        def method = fixtureBeans.getDeclaredMethod('string$0')

        then: "the @Bean annotation still carries the real (hyphenated) Spring bean name"
        method.isAnnotationPresent(Bean)
        method.getAnnotation(Bean).value() == ['my-service'] as String[]

        and:
        fixtureBeans.getDeclaredConstructor().newInstance().'string$0'() == 'hello'
    }

    def "multiple beans of the same type with non-identifier names get distinct synthesized method names"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class MultipleSynthesizedNamesFixture {
                def beans = {
                    bean('my-service', String) {
                        'a'
                    }
                    bean('my-other-service', String) {
                        'b'
                    }
                }
            }
        '''

        when:
        Class<?> fixtureBeans = compile(source)

        then:
        fixtureBeans.getDeclaredMethod('string$0').getAnnotation(Bean).value() == ['my-service'] as String[]
        fixtureBeans.getDeclaredMethod('string$1').getAnnotation(Bean).value() == ['my-other-service'] as String[]
    }

    def "an arbitrary, not-even-identifier-like bean name also falls back to a synthesized method name"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class WeirdNameFixture {
                def beans = {
                    bean('123 not valid!', String) {
                        'hello'
                    }
                }
            }
        '''

        when:
        Class<?> fixtureBeans = compile(source)

        then:
        fixtureBeans.getDeclaredMethod('string$0').getAnnotation(Bean).value() == ['123 not valid!'] as String[]
    }

    def "field(name, Type).annotate(...) declares a private annotated field"() {
        given:
        Class<?> fixtureBeans = compile()

        when:
        def field = fixtureBeans.getDeclaredField('suffix')

        then:
        field.type == String
        Modifier.isPrivate(field.modifiers)
        field.isAnnotationPresent(Value)
        field.getAnnotation(Value).value() == '${greeting.suffix:!!!}'
    }

    def "field(...).value(key, default) builds the @Value placeholder, accepting a bare constant key"() {
        given: "keys given as a String literal, a BARE static-final constant reference (the shape a " +
                "directly-written annotation value rejects), and an empty default"
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            class ConfigKeys {
                static final String ENCODING_KEY = 'app.encoding'
            }

            @GrailsBeans
            @AutoConfiguration
            class ValueSugarFixture {
                def beans = {
                    field('encoding', String).value(ConfigKeys.ENCODING_KEY, 'UTF-8')
                    field('cacheSeconds', int).value('app.cache.seconds', '5')
                    field('defaultLocale', String).value('app.default.locale', '')

                    bean('greeting', String) {
                        encoding
                    }
                }
            }
        '''

        when:
        GroovyClassLoader loader = new GroovyClassLoader(getClass().classLoader)
        loader.parseClass(source)
        Class<?> fixtureBeans = loader.loadClass('ValueSugarFixture')

        then:
        fixtureBeans.getDeclaredField('encoding').getAnnotation(Value).value() == '${app.encoding:UTF-8}'
        fixtureBeans.getDeclaredField('cacheSeconds').getAnnotation(Value).value() == '${app.cache.seconds:5}'
        fixtureBeans.getDeclaredField('defaultLocale').getAnnotation(Value).value() == '${app.default.locale:}'
    }

    def "field(...).value(...) with a bare constant key folds under @CompileStatic on a Plugin subclass"() {
        given: "the exact shape that used to fail: the static compiler rewrites '+' into .plus() calls " +
                "before Groovy's annotation folding runs, so the placeholder must be folded at transform time"
        String source = '''
            import groovy.transform.CompileStatic
            import grails.compiler.beans.GrailsBeans
            import grails.plugins.Plugin
            import org.springframework.boot.autoconfigure.AutoConfiguration

            class StaticConfigKeys {
                static final String ENCODING_KEY = 'app.encoding'
            }

            @CompileStatic
            @GrailsBeans
            @AutoConfiguration
            class StaticValuePlugin extends Plugin {
                def beans = {
                    field('encoding', String).value(StaticConfigKeys.ENCODING_KEY, 'UTF-8')

                    bean('greeting', String) {
                        encoding
                    }
                }
            }
        '''

        when:
        GroovyClassLoader loader = new GroovyClassLoader(getClass().classLoader)
        loader.parseClass(source)
        Class<?> autoConfigClass = loader.loadClass('StaticValuePluginAutoConfiguration')

        then:
        autoConfigClass.getDeclaredField('encoding').getAnnotation(Value).value() == '${app.encoding:UTF-8}'
    }

    def "field(...).value(...) resolves a constant inherited from an interface in the same compilation unit"() {
        given: "the shape every grails.config.Settings key has, in a source unit that is still compiling"
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            interface BaseSameUnitKeys {
                String ENCODING_KEY = 'app.encoding'
            }

            interface SameUnitKeys extends BaseSameUnitKeys {
            }

            @GrailsBeans
            @AutoConfiguration
            class InterfaceConstantFixture {
                def beans = {
                    field('encoding', String).value(SameUnitKeys.ENCODING_KEY, 'UTF-8')
                }
            }
        '''

        when:
        GroovyClassLoader loader = new GroovyClassLoader(getClass().classLoader)
        loader.parseClass(source)
        Class<?> fixture = loader.loadClass('InterfaceConstantFixture')

        then:
        fixture.getDeclaredField('encoding').getAnnotation(Value).value() == '${app.encoding:UTF-8}'
    }

    def "an unresolvable .value(...) constant on a same-unit owner is a located error, not an internal compiler error"() {
        given: "a typo against a constant declared in the same file, for which no loaded Class exists yet"
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            class SameFileKeys {
                public static final String ENCODING_KEY = 'app.encoding'
            }

            @GrailsBeans
            @AutoConfiguration
            class UnresolvableConstantFixture {
                def beans = {
                    field('encoding', String).value(SameFileKeys.NO_SUCH_KEY, 'UTF-8')
                }
            }
        '''

        when:
        compile(source)

        then:
        MultipleCompilationErrorsException e = thrown(MultipleCompilationErrorsException)
        e.message.contains('.value(...) arguments must be compile-time String constants')
    }

    def "an .annotate(...) attribute written as a concatenation folds under @CompileStatic"() {
        given: "the same shape .value(...) folds, which the static compiler would otherwise rewrite into .plus() calls"
        String source = '''
            import groovy.transform.CompileStatic
            import grails.compiler.beans.GrailsBeans
            import grails.plugins.Plugin
            import org.springframework.beans.factory.annotation.Value
            import org.springframework.boot.autoconfigure.AutoConfiguration

            interface ConcatKeys {
                String LOCALE_RESOLVER = 'grails.i18n.locale.resolver'
            }

            @CompileStatic
            @GrailsBeans
            @AutoConfiguration
            class ConcatAnnotatePlugin extends Plugin {
                def beans = {
                    field('localeResolverType', String).annotate(Value, value: '${' + ConcatKeys.LOCALE_RESOLVER + ':session}')
                }
            }
        '''

        when:
        GroovyClassLoader loader = new GroovyClassLoader(getClass().classLoader)
        loader.parseClass(source)
        Class<?> autoConfigClass = loader.loadClass('ConcatAnnotatePluginAutoConfiguration')

        then:
        autoConfigClass.getDeclaredField('localeResolverType').getAnnotation(Value).value() ==
                '${grails.i18n.locale.resolver:session}'
    }

    def "the consumed beans property leaves no field behind for another member to compile against"() {
        given: "a class whose own method still reads beans after the transform has taken it"
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class LeftoverFieldFixture {
                def beans = {
                    bean('greeting', String) { 'hello' }
                }

                def peek() {
                    beans
                }
            }
        '''

        when:
        Class<?> fixture = compile(source)

        then: 'the backing field is gone from the class, not merely from its field list'
        fixture.declaredFields.every { it.name != 'beans' }

        when: 'the stale member is reached'
        fixture.getDeclaredConstructor().newInstance().peek()

        then: 'it fails as a missing Groovy property rather than a NoSuchFieldError'
        thrown(MissingPropertyException)
    }

    def "@TypeChecked on a Plugin subclass reaches the bean bodies lifted onto the generated sibling"() {
        given: "a body that only a type checker would reject"
        String source = '''
            import groovy.transform.TypeChecked
            import grails.compiler.beans.GrailsBeans
            import grails.plugins.Plugin
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @TypeChecked
            @GrailsBeans
            @AutoConfiguration
            class TypeCheckedPlugin extends Plugin {
                def beans = {
                    bean('greeting', String) {
                        Integer notAnInteger = 'definitely a String'
                        'hello'
                    }
                }
            }
        '''

        when:
        compile(source)

        then:
        MultipleCompilationErrorsException e = thrown(MultipleCompilationErrorsException)
        e.message.contains('Cannot assign')
    }

    def "a parameter-only bean closure constructs the declared type from its own parameters"() {
        given: "the parameters say what is injected; the constructor call is generated"
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.beans.factory.annotation.Qualifier
            import org.springframework.boot.autoconfigure.AutoConfiguration

            class Dependency {
            }

            class Wired {
                Wired(List<String> names, @Qualifier('ignored') Dependency dependency, Integer count) { }
            }

            @GrailsBeans
            @AutoConfiguration
            class ParameterOnlyFixture {
                def beans = {
                    bean('wired', Wired) { List<String> names, @Qualifier('special') Dependency dependency, Integer count ->
                    }
                }
            }
        '''

        when:
        GroovyClassLoader loader = new GroovyClassLoader(getClass().classLoader)
        loader.parseClass(source)
        Class<?> fixture = loader.loadClass('ParameterOnlyFixture')
        def method = fixture.declaredMethods.find { it.name == 'wired' }

        then: "the closure's parameters became the method's, in the order written"
        method.parameterTypes*.simpleName == ['List', 'Dependency', 'Integer']
        method.genericParameterTypes[0].typeName == 'java.util.List<java.lang.String>'

        and: "the qualifier is the one written here, not the constructor's"
        method.parameters[1].getAnnotation(Qualifier).value() == 'special'

        and: "it is a @Bean under the declared name"
        method.getAnnotation(Bean).value() == ['wired'] as String[]
    }

    def "a parameter-only closure lets the compiler pick the constructor, so a second one changes nothing"() {
        given: "the shape that broke a previous design: a type with both a no-arg and an argument-taking constructor"
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            class Overloaded {
                String via

                Overloaded() { via = 'no-arg' }

                Overloaded(String value) { via = value }
            }

            @GrailsBeans
            @AutoConfiguration
            class OverloadedCtorFixture {
                def beans = {
                    bean('picked', Overloaded) { String value ->
                    }

                    bean('defaulted', Overloaded) {
                    }
                }
            }
        '''

        when:
        GroovyClassLoader loader = new GroovyClassLoader(getClass().classLoader)
        loader.parseClass(source)
        Class<?> fixture = loader.loadClass('OverloadedCtorFixture')
        def instance = fixture.getDeclaredConstructor().newInstance()

        then: "the parameter list selects the String constructor"
        fixture.getDeclaredMethod('picked', String).parameterCount == 1
        instance.picked('chosen').via == 'chosen'

        and: "and an empty parameter list selects the no-argument one, exactly as bodyless bean(Type) does"
        fixture.getDeclaredMethod('defaulted').parameterCount == 0
        instance.defaulted().via == 'no-arg'
    }

    @Unroll
    def "a synthesized construction that matches no constructor is reported against the statement: #description"() {
        given: "the ordinary failure of the bodyless and empty-body forms"
        String source = """
            import grails.compiler.beans.GrailsBeans
            import groovy.transform.CompileStatic
            import org.springframework.boot.autoconfigure.AutoConfiguration

            class NeedsString {
                NeedsString(String required) { }
            }

            @GrailsBeans
            @CompileStatic
            @AutoConfiguration
            class ConstructorMismatchFixture {
                def beans = {
                    $statement
                }
            }
        """

        when:
        compile(source)

        then: "a located type-checking error, not an internal compiler error at line -1"
        MultipleCompilationErrorsException e = thrown(MultipleCompilationErrorsException)
        e.message.contains('Cannot find matching constructor')

        and:
        !e.message.contains('line -1')
        !e.message.contains('BUG!')

        where:
        description                   | statement
        'empty body with parameters'  | 'bean(NeedsString) { Integer n ->\n                    }'
        'no closure at all'           | 'bean(NeedsString)'
    }

    def "a parameter-only bean closure is rejected for a type that cannot be constructed"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class AbstractParameterOnlyFixture {
                def beans = {
                    bean('runnable', Runnable) { String ignored ->
                    }
                }
            }
        '''

        when:
        compile(source)

        then:
        MultipleCompilationErrorsException e = thrown(MultipleCompilationErrorsException)
        e.message.contains('cannot be done for an interface or abstract class')
    }

    @Unroll
    def "a constant declared on a Groovy class folds under @CompileStatic: #description"() {
        given: "resolution happens while the DSL is compiled, before @CompileStatic could rewrite " +
                "the reference into a getter call, so a class constant is no different from an interface one"
        String source = """
            import grails.compiler.beans.GrailsBeans
            import groovy.transform.CompileStatic
            import org.springframework.beans.factory.annotation.Value
            import org.springframework.boot.autoconfigure.AutoConfiguration

            class ClassHeldKeys {
                static final String KEY = 'probe.key'
            }

            @GrailsBeans
            @CompileStatic
            @AutoConfiguration
            class ClassConstantFixture {
                def beans = {
                    field('thing', String)$qualifier
                }
            }
        """

        when:
        GroovyClassLoader loader = new GroovyClassLoader(getClass().classLoader)
        loader.parseClass(source)
        Class<?> fixture = loader.loadClass('ClassConstantFixture')

        then:
        fixture.getDeclaredField('thing').getAnnotation(Value).value() == expected

        where:
        description                     | qualifier                                                             || expected
        '.value(key, default)'          | ".value(ClassHeldKeys.KEY, 'x')"                                      || '${probe.key:x}'
        '.annotate concatenation'       | ".annotate(Value, value: '\${' + ClassHeldKeys.KEY + ':y}')"          || '${probe.key:y}'
        '.annotate bare reference'      | '.annotate(Value, value: ClassHeldKeys.KEY)'                          || 'probe.key'
    }

    def "a bean body reading an inherited Plugin member is rejected under @CompileStatic"() {
        given: "the habit doWithSpring allows and the generated sibling cannot, since it extends Object"
        String source = '''
            import groovy.transform.CompileStatic
            import grails.compiler.beans.GrailsBeans
            import grails.plugins.Plugin
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @CompileStatic
            @GrailsBeans
            @AutoConfiguration
            class PluginMemberPlugin extends Plugin {
                def beans = {
                    bean('greeting', String) {
                        config.toString()
                    }
                }
            }
        '''

        when:
        compile(source)

        then:
        MultipleCompilationErrorsException e = thrown(MultipleCompilationErrorsException)
        e.message.contains('No such property: config')
    }

    def "the generated sibling holds no channel through which a Plugin instance could arrive"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import grails.plugins.Plugin
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class DetachedSiblingPlugin extends Plugin {
                def beans = {
                    bean('greeting', String) { 'hello' }
                }
            }
        '''

        when:
        GroovyClassLoader loader = new GroovyClassLoader(getClass().classLoader)
        loader.parseClass(source)
        Class<?> sibling = loader.loadClass('DetachedSiblingPluginAutoConfiguration')

        then: 'it extends Object and offers only a no-arg constructor'
        sibling.superclass == Object
        sibling.declaredConstructors.every { it.parameterCount == 0 }

        and: 'so it is not a Plugin and cannot hold one'
        !Plugin.isAssignableFrom(sibling)
        sibling.declaredFields*.type.every { !Plugin.isAssignableFrom(it) }
    }

    def "an error against a generated node reports the position of the DSL statement that produced it"() {
        given: "a typo in .annotate(...), which Groovy itself reports against the generated member"
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration
            import org.springframework.core.annotation.Order

            @GrailsBeans
            @AutoConfiguration
            class PositionedErrorFixture {
                def beans = {
                    bean('greeting', String).annotate(Order, valu: 1) {
                        'hello'
                    }
                }
            }
        '''

        when:
        compile(source)

        then:
        MultipleCompilationErrorsException e = thrown(MultipleCompilationErrorsException)
        e.message.contains("'valu' is not part of the annotation Order")

        and: 'located at the offending statement rather than nowhere'
        !e.message.contains('line -1')
    }

    def "field(...).value(placeholder) passes a string already carrying a placeholder or SpEL expression through verbatim"() {
        given: "a complete placeholder, a SpEL expression, and a mixed literal with an embedded placeholder"
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class VerbatimValueFixture {
                def beans = {
                    field('encoding', String).value('${app.encoding:UTF-8}')
                    field('poolSize', int).value('#{T(java.lang.Runtime).getRuntime().availableProcessors()}')
                    field('endpoint', String).value('http://${app.host}/api')

                    bean('greeting', String) {
                        encoding
                    }
                }
            }
        '''

        when:
        Class<?> fixtureBeans = compile(source)

        then:
        fixtureBeans.getDeclaredField('encoding').getAnnotation(Value).value() == '${app.encoding:UTF-8}'
        fixtureBeans.getDeclaredField('poolSize').getAnnotation(Value).value() ==
                '#{T(java.lang.Runtime).getRuntime().availableProcessors()}'
        fixtureBeans.getDeclaredField('endpoint').getAnnotation(Value).value() == 'http://${app.host}/api'
    }

    def "field(...).value(key) auto-wraps a bare config key into a placeholder"() {
        given: "a bare key - which can only ever mean 'inject this property', never its literal text"
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class BareKeyValueFixture {
                def beans = {
                    field('cacheUrls', Boolean).value('grails.web.linkGenerator.useCache')

                    bean('greeting', String) {
                        String.valueOf(cacheUrls)
                    }
                }
            }
        '''

        when:
        Class<?> fixtureBeans = compile(source)

        then:
        fixtureBeans.getDeclaredField('cacheUrls').getAnnotation(Value).value() ==
                '${grails.web.linkGenerator.useCache}'
    }

    def "annotate(...) attribute values may reference a shared String constant, not just a literal"() {
        given: "an @Value placeholder built the same way the real I18nGrailsPlugin conversion builds " +
                "its property keys from grails.config.Settings, proving .annotate(...)'s member values " +
                "aren't restricted to literals the way bean(name, Type)'s own name is"
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import grails.config.Settings
            import org.springframework.beans.factory.annotation.Value
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class SharedConstantFixture {
                def beans = {
                    field('localeResolverType', String).annotate(Value, value: '${' + Settings.I18N_LOCALE_RESOLVER + ':session}')
                }
            }
        '''

        when:
        Class<?> fixture = compile(source)
        def field = fixture.getDeclaredField('localeResolverType')

        then:
        field.getAnnotation(Value).value() == '${grails.i18n.localeResolver:session}'
    }

    def "method(name, Type) declares a private helper method usable from bean(...) and field(...)"() {
        given:
        Class<?> fixtureBeans = compile()

        when:
        def helperMethod = fixtureBeans.getDeclaredMethod('yell', String)

        then: "it is a plain private member, not itself a @Bean"
        helperMethod.returnType == String
        Modifier.isPrivate(helperMethod.modifiers)
        !helperMethod.isAnnotationPresent(Bean)

        when: "a bean() closure calls it, and it reads a sibling field(...)"
        def instance = fixtureBeans.getDeclaredConstructor().newInstance()
        def suffixField = fixtureBeans.getDeclaredField('suffix')
        suffixField.accessible = true
        suffixField.set(instance, '!!!')

        then:
        instance.yelledGreeting() == 'HELLO!!!'
    }

    def "closure parameters become method parameters for constructor-style injection"() {
        given:
        Class<?> fixtureBeans = compile()

        when:
        def method = fixtureBeans.getDeclaredMethod('shout', String)

        then:
        method.returnType == String

        and:
        fixtureBeans.getDeclaredConstructor().newInstance().shout('hi') == 'HI'
    }

    def "a @Qualifier on a closure parameter carries through to the generated method's parameter"() {
        given: "Groovy captures annotations on closure parameters at parse time, and the DSL " +
                "reuses the closure's own Parameter AST nodes directly on the generated method, " +
                "so no dedicated DSL syntax is needed for this - it already works"
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.beans.factory.annotation.Qualifier
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class QualifiedParamFixture {
                def beans = {
                    bean('special', String) {
                        'special value'
                    }

                    bean('shout', String) { @Qualifier('special') String input ->
                        input.toUpperCase()
                    }
                }
            }
        '''

        when:
        Class<?> fixture = compile(source)
        def method = fixture.getDeclaredMethod('shout', String)
        Annotation[] paramAnnotations = method.parameterAnnotations[0]

        then:
        paramAnnotations.any { it instanceof Qualifier && it.value() == 'special' }
    }

    def "an @Autowired(required = false) closure parameter lets the context start when nothing supplies the dependency"() {
        given: "a bean depending on a type no one wires - the shape of an integration another module may or may not provide"
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.beans.factory.annotation.Autowired
            import org.springframework.boot.autoconfigure.AutoConfiguration

            interface Transport { }

            class Sender {
                Transport transport

                Sender(Transport transport) {
                    this.transport = transport
                }
            }

            @GrailsBeans
            @AutoConfiguration
            class OptionalDependencyFixture {
                def beans = {
                    bean('sender', Sender) { @Autowired(required = false) Transport transport ->
                        new Sender(transport)
                    }
                }
            }
        '''

        and:
        GroovyClassLoader loader = new GroovyClassLoader(getClass().classLoader)
        loader.parseClass(source)
        def context = new AnnotationConfigApplicationContext()
        context.classLoader = loader
        context.register(loader.loadClass('OptionalDependencyFixture'))

        when:
        context.refresh()

        then: "Spring passes null rather than refusing to build the bean"
        noExceptionThrown()
        context.getBean('sender').transport == null

        cleanup:
        context.close()
    }

    def "the same parameter without it is required, which is what makes the annotation load-bearing rather than decorative"() {
        given: "identical to the fixture above but for the missing annotation"
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            interface RequiredTransport { }

            class RequiredSender {
                RequiredTransport transport

                RequiredSender(RequiredTransport transport) {
                    this.transport = transport
                }
            }

            @GrailsBeans
            @AutoConfiguration
            class RequiredDependencyFixture {
                def beans = {
                    bean('sender', RequiredSender) { RequiredTransport transport ->
                        new RequiredSender(transport)
                    }
                }
            }
        '''

        and:
        GroovyClassLoader loader = new GroovyClassLoader(getClass().classLoader)
        loader.parseClass(source)
        def context = new AnnotationConfigApplicationContext()
        context.classLoader = loader
        context.register(loader.loadClass('RequiredDependencyFixture'))

        when:
        context.refresh()

        then:
        thrown(Exception)

        cleanup:
        context.close()
    }

    def "an annotation on a closure parameter reaches the generated method as a real parameter annotation"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.beans.factory.annotation.Autowired
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class OptionalParameterAnnotationFixture {
                def beans = {
                    bean('greeting', String) { @Autowired(required = false) StringBuilder input ->
                        input?.toString() ?: 'none'
                    }
                }
            }
        '''

        when:
        Class<?> compiled = compile(source)
        def method = compiled.getDeclaredMethod('greeting', StringBuilder)

        then: "not merely present, but carrying the attribute that makes the dependency optional"
        Autowired autowired = method.parameterAnnotations[0].find { it instanceof Autowired } as Autowired
        autowired != null
        !autowired.required()
    }

    def "a @Qualifier on a closure parameter selects the intended candidate when Spring injects it"() {
        given: "two beans of the dependency type, so the injection point is genuinely ambiguous " +
                "without the qualifier - refresh would fail outright if it were not honoured"
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.beans.factory.annotation.Qualifier
            import org.springframework.boot.autoconfigure.AutoConfiguration

            class Dependency {
                String label

                Dependency(String label) {
                    this.label = label
                }
            }

            class Consumer {
                String from

                Consumer(Dependency dependency) {
                    from = dependency.label
                }
            }

            @GrailsBeans
            @AutoConfiguration
            class QualifiedInjectionFixture {
                def beans = {
                    bean('plain', Dependency) { new Dependency('plain') }
                    bean('special', Dependency) { new Dependency('special') }

                    bean('viaBody', Consumer) { @Qualifier('special') Dependency dependency ->
                        new Consumer(dependency)
                    }

                    bean('viaGeneratedCall', Consumer) { @Qualifier('special') Dependency dependency ->
                    }
                }
            }
        '''

        and:
        GroovyClassLoader loader = new GroovyClassLoader(getClass().classLoader)
        loader.parseClass(source)
        def context = new AnnotationConfigApplicationContext()
        context.classLoader = loader
        context.register(loader.loadClass('QualifiedInjectionFixture'))

        when:
        context.refresh()

        then: 'both candidates are registered, so the qualifier is doing the work'
        context.getBeanNamesForType(loader.loadClass('Dependency')).length == 2

        and: 'and Spring injected the qualified one, through a closure body and a generated constructor call alike'
        context.getBean('viaBody').from == 'special'
        context.getBean('viaGeneratedCall').from == 'special'

        cleanup:
        context.close()
    }

    def "the beans closure property does not survive compilation"() {
        given:
        Class<?> fixtureBeans = compile()

        expect:
        fixtureBeans.declaredFields*.name == fixtureBeans.declaredFields*.name.findAll { it != 'beans' }
        fixtureBeans.declaredMethods*.name.every { !(it in ['getBeans', 'setBeans']) }
    }

    def "requires a beans property at all"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class NoBeansProperty {
            }
        '''

        when:
        compile(source)

        then:
        MultipleCompilationErrorsException e = thrown(MultipleCompilationErrorsException)
        e.message.contains("requires a 'beans' property initialised to a closure")
    }

    def "the beans property must be initialised to a closure"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class BeansNotAClosure {
                def beans = 'not a closure'
            }
        '''

        when:
        compile(source)

        then:
        MultipleCompilationErrorsException e = thrown(MultipleCompilationErrorsException)
        e.message.contains("'beans' must be initialised to a closure")
    }

    @Unroll
    def "rejects a malformed beans statement: #description"() {
        given:
        String source = """
            import grails.compiler.beans.GrailsBeans
            import org.springframework.beans.factory.annotation.Value
            import org.springframework.boot.autoconfigure.AutoConfiguration
            import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
            import org.springframework.boot.autoconfigure.condition.SearchStrategy
            import org.springframework.context.annotation.Primary
            import org.springframework.core.annotation.Order

            @GrailsBeans
            @AutoConfiguration
            class MalformedBeans {
                def beans = {
                    $statement
                }
            }
        """

        when:
        compile(source)

        then:
        MultipleCompilationErrorsException e = thrown(MultipleCompilationErrorsException)
        e.message.contains(expectedMessage)

        where:
        description                                  | statement                                                          | expectedMessage
        'a statement that is not a method call'       | 'def notACall = 1'                                                 | "Each 'beans' statement must be a bean(...), field(...), or method(...) call"
        'a method call that is not bean(...)'         | "someOtherMethod(String) { 'x' }"                                  | 'Expected bean(["name", ] Type) { ... }'
        'bean(...) with a name but no type'           | "bean('NotAType') { 'x' }"                                         | 'bean(...) requires a type'
        'bean(...) with the name and type in the old order' | "bean(String, 'x') { 'y' }"                                  | 'takes the name before the type'
        'bean(...) with no factory closure on an uninstantiable type' | "bean('x', Runnable)"                               | 'cannot be done for an interface or abstract class'
        'conditionalOnMissingBean(...) with a non-type argument' |
                "bean('x', String).conditionalOnMissingBean('not a type') { 'x' }" |
                'conditionalOnMissingBean(...) arguments must be types'
        'conditionalOnMissingBean(...) with types given both positionally and via value:' |
                "bean('x', String).conditionalOnMissingBean(String, value: Integer) { 'y' }" |
                'use one or the other'
        'bean(...) with a non-constant name argument'  | "bean(someVariable, String) { 'x' }"                              | 'requires the name to be a String literal'
        'bean(...) with a non-String constant name'    | 'bean(42, String) { \'x\' }'                                       | 'requires the name to be a String literal'
        'bean(...) with an unexpected third argument'  | "bean('x', String, 'unexpected') { 'y' }"                          | 'requires a type, optionally preceded by a name'
        'bean(...) with an empty explicit name'         | "bean('', String) { 'y' }"                                        | 'requires a non-blank name'
        'bean(...) with a whitespace-only explicit name' | "bean('   ', String) { 'y' }"                                    | 'requires a non-blank name'
        'field(...) with a reserved-keyword name'        | "field('class', String)"                                          | 'is not a valid name'
        'method(...) with a reserved-keyword name'        | "method('return', String) { 'y' }"                               | 'is not a valid name'
        'an unrecognised qualifier chained after bean(...)' | "bean('x', String).unknownQualifier() { 'y' }"                | 'Expected bean(["name", ] Type) { ... }'
        'the same qualifier chained twice'             | "bean('x', String).primary().primary() { 'y' }"                    | 'may only be chained once'
        'primary() given an argument'                  | "bean('x', String).primary('oops') { 'y' }"                        | '.primary() takes no arguments'
        'lazy() given an argument'                      | "bean('x', String).lazy(true) { 'y' }"                             | '.lazy() takes no arguments'
        'staticMethod() given an argument'              | "bean('x', String).staticMethod(true) { 'y' }"                     | '.staticMethod() takes no arguments'
        'staticMethod() chained twice'                  | "bean('x', String).staticMethod().staticMethod() { 'y' }"          | 'may only be chained once'
        'staticMethod() chained onto field(...)'         | "field('x', String).staticMethod()"                                | 'cannot be chained onto field(...)'
        'staticMethod() chained onto method(...)'        | "method('x', String).staticMethod() { 'y' }"                       | 'cannot be chained onto method(...)'
        'scope(...) with no argument'                   | "bean('x', String).scope() { 'y' }"                                | 'needs a scope name'
        'scope(...) with a non-String argument'         | "bean('x', String).scope(42) { 'y' }"                              | 'non-empty String scope name'
        'annotate(...) with no arguments'                | "bean('x', String).annotate() { 'y' }"                            | 'requires an annotation type'
        'annotate(...) with a non-type argument'         | "bean('x', String).annotate('NotAType') { 'y' }"                  | 'requires an annotation type'
        'annotate(...) with a non-annotation type'       | "bean('x', String).annotate(String) { 'y' }"                      | 'is not an annotation type'
        'the same annotation attached twice via annotate(...)' |
                "bean('x', String).annotate(Order, value: 1).annotate(Order, value: 2) { 'y' }" |
                'already attached'
        'annotate(...) colliding with a named qualifier' | "bean('x', String).primary().annotate(Primary) { 'y' }"          | 'already attached'
        'field(...) with a name but no type'             | "field('NotAType')"                                               | 'field(...) requires a type'
        'field(...) chained with a bean-only qualifier'   | "field('x', String).primary()"                                    | 'cannot be chained onto field(...)'
        '.value(...) chained onto bean(...)'               | "bean('x', String).value('k', 'd') { 'y' }"                      | 'cannot be chained onto bean(...)'
        '.value(...) chained onto method(...)'              | "method('x', String).value('k', 'd') { 'y' }"                    | 'cannot be chained onto method(...)'
        '.value(...) with no arguments'                     | "field('x', String).value()"                                     | 'requires a config key'
        '.value(...) with too many arguments'               | "field('x', String).value('k', 'd', 'extra')"                    | 'requires a config key'
        '.value(...) with an empty single-argument key'      | "field('x', String).value('')"                                  | 'requires a non-blank config key'
        '.value(...) with a whitespace-only single-argument key' | "field('x', String).value('   ')"                           | 'requires a non-blank config key'
        '.value(key, default) with a blank key'              | "field('x', String).value('', 'fallback')"                      | 'requires a non-blank config key'
        '.value(key, default) with a whitespace-only key'    | "field('x', String).value('   ', 'fallback')"                   | 'requires a non-blank config key'
        '.value(...) chained twice'                          | "field('x', String).value('a', 'b').value('c', 'd')"            | 'may only be chained once'
        '.value(...) combined with .annotate(Value, ...)'    | "field('x', String).value('k', 'd').annotate(Value, value: 'v')" | '"value" is already set here'
        'conditionalOnMissingBeanName(...) given a name: attribute' |
                "bean('x', String).conditionalOnMissingBeanName(name: 'other') { 'y' }" | 'sets name automatically'
        'conditionalOnMissingBeanName(...) given a value: attribute' |
                "bean('x', String).conditionalOnMissingBeanName(value: String) { 'y' }" | 'sets name automatically'
        'conditionalOnMissingBeanName(...) given a positional type' |
                "bean('x', String).conditionalOnMissingBeanName(String) { 'y' }" | 'takes only named attributes'
        'conditionalOnMissingBeanName(...) chained onto field(...)' |
                "field('x', String).conditionalOnMissingBeanName()" | 'cannot be chained onto field(...)'
        'conditionalOnMissingBeanName(...) combined with conditionalOnMissingBean(...)' |
                "bean('x', String).conditionalOnMissingBean(String).conditionalOnMissingBeanName() { 'y' }" | 'already attached'
        'method(...) without a body closure'              | "method('x', String)"                                             | 'method(...) must end with a body closure'
        'method(...) chained with a bean-only qualifier'  | "method('x', String).conditionalOnMissingBean(String) { 'y' }"    | 'cannot be chained onto method(...)'
        'two bean(...) statements sharing the same explicit name' |
                "bean('x', String) { 'a' }; bean('x', Integer) { 1 }" | 'is already used as the Spring bean name'
        'two bean(...) statements sharing the same non-identifier Spring bean name' |
                "bean('my-service', String) { 'x' }; bean('my-service', Integer) { 1 }" |
                'is already used as the Spring bean name'
        'same-named bean(...) statements where only one carries a discriminating condition' |
                "bean('x', String).annotate(ConditionalOnWebApplication) { 'a' }; bean('x', Integer) { 1 }" |
                'carries its own discriminating condition'
        'same-named bean(...) statements guarded only by the shared-name back-off' |
                "bean('x', String).conditionalOnMissingBeanName() { 'a' }; bean('x', Integer).conditionalOnMissingBeanName() { 1 }" |
                'carries its own discriminating condition'
        'same-named bean(...) statements guarded only by the bare return-type back-off' |
                "bean('x', String).conditionalOnMissingBean() { 'a' }; bean('x', Integer).conditionalOnMissingBean() { 1 }" |
                'carries its own discriminating condition'
        'same-named bean(...) statements guarded only by a name-based conditionalOnMissingBean(...)' |
                "bean('x', String).conditionalOnMissingBean(name: 'x') { 'a' }; bean('x', Integer).conditionalOnMissingBean(name: 'x') { 1 }" |
                'carries its own discriminating condition'
        'same-named bean(...) statements guarded only by a search-scoped conditionalOnMissingBean(...)' |
                "bean('x', String).conditionalOnMissingBean(name: 'x', search: SearchStrategy.CURRENT) { 'a' }; bean('x', Integer).conditionalOnMissingBean(name: 'x', search: SearchStrategy.CURRENT) { 1 }" |
                'carries its own discriminating condition'
        'a field(...) and a method(...) sharing the same name' |
                "field('x', String); method('x', Integer) { 1 }" | 'is already used by another'
        'the removed .methodName(...) qualifier'          | "bean('x', String).methodName('y') { 'z' }"                      | 'Expected bean(["name", ] Type) { ... }'
        'a qualifier chained after the body closure'      | "bean(String) { 'hi' }.lazy()"                                   | 'the body closure comes last'
        'a qualifier chained after a named bean body'     | "bean('greeting', String) { 'hi' }.lazy()"                       | 'the body closure comes last'
        'a qualifier chained after a method(...) body'    | "method('x', String) { 'y' }.annotate(Order, value: 1)"          | 'the body closure comes last'
        'a bean(...) chained onto a field(...)'            | "field('suffix', String).bean('greeter', String) { 'hi' }"       | 'is its own statement'
        'a field(...) chained onto a bean(...)'            | "bean('greeter', String) { 'hi' }.field('suffix', String)"       | 'is its own statement'
        'field(Type) whose derived name is a reserved keyword'  | 'field(Boolean)'                                            | 'derived from Boolean, is not a valid name'
        'method(Type) whose derived name is a reserved keyword' | "method(Long) { 1L }"                                       | 'derived from Long, is not a valid name'
    }

    def "a bean(...).staticMethod() compiles to a static @Bean factory method"() {
        given: "the shape Spring recommends for BeanFactoryPostProcessor/BeanPostProcessor beans, " +
                "which must be creatable without instantiating their declaring configuration class"
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class StaticBeanFixture {
                def beans = {
                    bean('staticGreeting', String).staticMethod().conditionalOnMissingBean(String) {
                        'created without an instance'
                    }
                }
            }
        '''

        when:
        Class<?> fixture = compile(source)
        def method = fixture.getDeclaredMethod('staticGreeting')

        then:
        Modifier.isStatic(method.modifiers)
        method.getAnnotation(Bean).value() == ['staticGreeting'] as String[]
        method.isAnnotationPresent(ConditionalOnMissingBean)

        and: "invocable with no instance, the way Spring invokes a static @Bean method"
        method.invoke(null) == 'created without an instance'
    }

    def "a static bean body cannot reference instance field(...) state under @CompileStatic"() {
        given: "field(...) members are instance members of the generated class, out of reach of a static method"
        String source = '''
            import groovy.transform.CompileStatic
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @CompileStatic
            @GrailsBeans
            @AutoConfiguration
            class StaticBeanFieldFixture {
                def beans = {
                    field('suffix', String).value('app.suffix', '!')

                    bean('greeting', String).staticMethod() {
                        'hello' + suffix
                    }
                }
            }
        '''

        when:
        compile(source)

        then:
        MultipleCompilationErrorsException e = thrown(MultipleCompilationErrorsException)
        e.message.contains('suffix')
    }

    private static final String SHARED_NAME_FIXTURE = '''
        import grails.compiler.beans.GrailsBeans
        import org.springframework.boot.autoconfigure.AutoConfiguration
        import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty

        @GrailsBeans
        @AutoConfiguration
        class SharedNameFixtureBeans {
            def beans = {
                bean('converter', String).conditionalOnMissingBeanName().annotate(ConditionalOnProperty, name: 'app.converter', havingValue: 'camelCase', matchIfMissing: true) {
                    'camel case converter'
                }

                bean('converter', String).conditionalOnMissingBeanName().annotate(ConditionalOnProperty, name: 'app.converter', havingValue: 'hyphenated') {
                    'hyphenated converter'
                }
            }
        }
    '''

    def "one bean name may be declared by several bean(...) statements when each carries its own discriminating condition"() {
        given: "the standard autoconfiguration pattern for mutually exclusive variants of one bean - " +
                "the exact shape of UrlMappingsAutoConfiguration's two grailsUrlConverter beans"
        Class<?> fixture = compile(SHARED_NAME_FIXTURE)

        when: "the first declaration claims the bean name as its method name and the second synthesizes"
        def first = fixture.getDeclaredMethod('converter')
        def second = fixture.getDeclaredMethod('string$0')

        then: "both register under the same Spring bean name"
        first.getAnnotation(Bean).value() == ['converter'] as String[]
        second.getAnnotation(Bean).value() == ['converter'] as String[]

        and: "each keeps its own guards"
        first.getAnnotation(ConditionalOnProperty).havingValue() == 'camelCase'
        first.getAnnotation(ConditionalOnProperty).matchIfMissing()
        second.getAnnotation(ConditionalOnProperty).havingValue() == 'hyphenated'
        !second.getAnnotation(ConditionalOnProperty).matchIfMissing()
        first.getAnnotation(ConditionalOnMissingBean).name() == ['converter'] as String[]
        second.getAnnotation(ConditionalOnMissingBean).name() == ['converter'] as String[]
    }

    @Unroll
    def "at runtime the discriminating conditions select which of the same-named beans registers: #description"() {
        given:
        Class<?> fixture = compile(SHARED_NAME_FIXTURE)
        def context = new AnnotationConfigApplicationContext()
        if (configuredValue != null) {
            context.environment.propertySources.addFirst(
                    new MapPropertySource('test', ['app.converter': configuredValue]))
        }
        context.register(fixture)

        when:
        context.refresh()

        then:
        context.getBean('converter') == expectedBean

        cleanup:
        context.close()

        where:
        description               | configuredValue | expectedBean
        'the matchIfMissing default' | null         | 'camel case converter'
        'an explicit property value' | 'hyphenated' | 'hyphenated converter'
    }

    def "the standalone form works on an application class: a GrailsAutoConfiguration subclass, with no @AutoConfiguration"() {
        given: "the shape of a Grails app's Application class, which needs no @AutoConfiguration and no " +
                "AutoConfiguration.imports registration - Spring Boot reads @Bean methods directly off " +
                "the application class it is launched with"
        String source = '''
            import grails.boot.config.GrailsAutoConfiguration
            import grails.compiler.beans.GrailsBeans

            @GrailsBeans
            class Application extends GrailsAutoConfiguration {
                def beans = {
                    bean('applicationGreeting', String) {
                        'hello from the application class'
                    }
                }
            }
        '''

        when:
        Class<?> application = compile(source)
        def method = application.getDeclaredMethod('applicationGreeting')

        then: "the @Bean factory method landed on the application class itself"
        method.getAnnotation(Bean).value() == ['applicationGreeting'] as String[]
        method.invoke(application.getDeclaredConstructor().newInstance()) == 'hello from the application class'

        and: "no beans closure survives into the compiled application class"
        application.declaredFields.every { it.name != 'beans' }
    }

    def "@Bean methods compiled onto an unannotated class register when the class is a configuration source"() {
        given: "no @AutoConfiguration and no @Configuration - just a class registered as a source, " +
                "the same way Spring Boot processes the application class (booting a full Grails " +
                "GrailsAutoConfiguration subclass needs the framework runtime, so the source-class " +
                "mechanism itself is exercised on a plain class here)"
        String source = '''
            import grails.compiler.beans.GrailsBeans

            @GrailsBeans
            class PlainSourceBeans {
                def beans = {
                    bean('plainGreeting', String) {
                        'hello from a plain source class'
                    }
                }
            }
        '''
        Class<?> plainSource = compile(source)

        when:
        def context = new AnnotationConfigApplicationContext()
        context.register(plainSource)
        context.refresh()

        then:
        context.getBean('plainGreeting') == 'hello from a plain source class'

        cleanup:
        context?.close()
    }

    def "a synthesized method name that would collide with a pre-existing field(...) name skips forward to the next free slot"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class SyntheticSkipForwardFixture {
                def beans = {
                    field('string$0', String)

                    bean('my-service', String) {
                        'hello'
                    }
                }
            }
        '''

        when:
        Class<?> fixtureBeans = compile(source)

        then: "string\$0 was already taken by the field, so the bean skips forward to string\$1"
        fixtureBeans.getDeclaredField('string$0') != null
        fixtureBeans.getDeclaredMethod('string$1').getAnnotation(Bean).value() == ['my-service'] as String[]
    }

    def "a bean whose valid-identifier name collides with a declared field gets a synthesized method name instead of an error"() {
        given: "a private field and a Spring bean legitimately sharing the name 'config'"
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class FieldBeanNameOverlapFixture {
                def beans = {
                    field('config', String)

                    bean('config', Integer) {
                        42
                    }
                }
            }
        '''

        when:
        Class<?> fixtureBeans = compile(source)

        then: "the Java member namespace resolves via synthesis - method names are an implementation detail"
        fixtureBeans.getDeclaredField('config') != null
        fixtureBeans.getDeclaredMethod('integer$0').getAnnotation(Bean).value() == ['config'] as String[]
    }

    def "declaration order does not matter: a field declared after a same-named bean still wins the member name"() {
        given: "the same DSL as the field-first overlap test, with the statements reversed"
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class ReversedFieldBeanNameOverlapFixture {
                def beans = {
                    bean('config', Integer) {
                        42
                    }

                    field('config', String)
                }
            }
        '''

        when:
        Class<?> fixtureBeans = compile(source)

        then: "identical outcome to declaring the field first - reordering must never change validity"
        fixtureBeans.getDeclaredField('config') != null
        fixtureBeans.getDeclaredMethod('integer$0').getAnnotation(Bean).value() == ['config'] as String[]
    }

    def "declaration order does not matter: a helper method declared after a same-named bean still wins the member name"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class ReversedMethodBeanNameOverlapFixture {
                def beans = {
                    bean('helper', Integer) {
                        42
                    }

                    method('helper', String) {
                        'x'
                    }
                }
            }
        '''

        when:
        Class<?> fixtureBeans = compile(source)

        then:
        fixtureBeans.getDeclaredMethod('helper').returnType == String
        fixtureBeans.getDeclaredMethod('integer$0').getAnnotation(Bean).value() == ['helper'] as String[]
    }

    def "a bean colliding with a method the standalone class already declares gets a synthesized method name"() {
        given: "a hand-written greeting() method outside the DSL, and a bean named 'greeting'"
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class ExistingMemberFixture {
                String greeting() { 'existing' }

                def beans = {
                    bean('greeting', String) {
                        'bean'
                    }
                }
            }
        '''

        when:
        Class<?> fixtureBeans = compile(source)

        then: "the hand-written method is untouched"
        fixtureBeans.getDeclaredConstructor().newInstance().greeting() == 'existing'
        !fixtureBeans.getDeclaredMethod('greeting').isAnnotationPresent(Bean)

        and: "the bean method synthesized around it, keeping the Spring name"
        fixtureBeans.getDeclaredMethod('string$0').getAnnotation(Bean).value() == ['greeting'] as String[]
        fixtureBeans.getDeclaredConstructor().newInstance().'string$0'() == 'bean'
    }

    def "a bean named after an inherited Object method does not override it"() {
        given: "a bean whose Spring name is 'toString' - legal for Spring, lethal as a method override"
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class ToStringBeanFixture {
                def beans = {
                    bean('toString', String) {
                        'the bean'
                    }
                }
            }
        '''

        when:
        Class<?> fixtureBeans = compile(source)

        then: "toString() still behaves as Object's, not as the bean factory"
        fixtureBeans.getDeclaredConstructor().newInstance().toString() != 'the bean'

        and: "the bean method synthesized instead"
        fixtureBeans.getDeclaredMethod('string$0').getAnnotation(Bean).value() == ['toString'] as String[]
    }

    def "a bean named after an inherited Object method on the Plugin-subclass sibling does not override it either"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import grails.plugins.Plugin
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class ToStringBeanPlugin extends Plugin {
                def beans = {
                    bean('toString', String) {
                        'the bean'
                    }
                }
            }
        '''

        when:
        GroovyClassLoader loader = new GroovyClassLoader(getClass().classLoader)
        loader.parseClass(source)
        Class<?> autoConfigClass = loader.loadClass('ToStringBeanPluginAutoConfiguration')

        then:
        autoConfigClass.getDeclaredConstructor().newInstance().toString() != 'the bean'
        autoConfigClass.getDeclaredMethod('string$0').getAnnotation(Bean).value() == ['toString'] as String[]
    }

    def "a bean named after a default method from a directly implemented interface does not override it"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            interface Described {
                default String description() { 'interface behavior' }
            }

            @GrailsBeans
            @AutoConfiguration
            class InterfaceDefaultFixture implements Described {
                def beans = {
                    bean('description', String) {
                        'bean value'
                    }
                }
            }
        '''

        when:
        GroovyClassLoader loader = new GroovyClassLoader(getClass().classLoader)
        loader.parseClass(source)
        Class<?> fixtureBeans = loader.loadClass('InterfaceDefaultFixture')

        then: "the interface's default behavior is preserved"
        fixtureBeans.getDeclaredConstructor().newInstance().description() == 'interface behavior'

        and: "the bean method synthesized around it, keeping the Spring name"
        fixtureBeans.getDeclaredMethod('string$0').getAnnotation(Bean).value() == ['description'] as String[]
    }

    def "a bean named after a default method from a parent interface further up the graph does not override it either"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            interface Labeled {
                default String label() { 'parent interface behavior' }
            }

            interface ChildLabeled extends Labeled { }

            @GrailsBeans
            @AutoConfiguration
            class ParentInterfaceFixture implements ChildLabeled {
                def beans = {
                    bean('label', String) {
                        'bean value'
                    }
                }
            }
        '''

        when:
        GroovyClassLoader loader = new GroovyClassLoader(getClass().classLoader)
        loader.parseClass(source)
        Class<?> fixtureBeans = loader.loadClass('ParentInterfaceFixture')

        then:
        fixtureBeans.getDeclaredConstructor().newInstance().label() == 'parent interface behavior'
        fixtureBeans.getDeclaredMethod('string$0').getAnnotation(Bean).value() == ['label'] as String[]
    }

    def "beans named after a property's accessors do not become the accessors"() {
        given: "a Groovy property whose getter/setter are synthesized only at class generation, after this transform"
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class PropertyAccessorFixture {
                String description

                def beans = {
                    bean('getDescription', String) {
                        'bean value'
                    }

                    bean('setDescription', String) { String value ->
                        value
                    }
                }
            }
        '''

        when:
        Class<?> fixtureBeans = compile(source)
        def instance = fixtureBeans.getDeclaredConstructor().newInstance()
        instance.description = 'set value'

        then: "the property reads and writes normally - neither accessor was displaced by a bean method"
        instance.description == 'set value'

        and: "both beans synthesized around the reserved accessor names, keeping their Spring names"
        fixtureBeans.getDeclaredMethod('string$0').getAnnotation(Bean).value() == ['getDescription'] as String[]
        fixtureBeans.getDeclaredMethod('string$1', String).getAnnotation(Bean).value() == ['setDescription'] as String[]
    }

    def "a bean named after a boolean property's is-accessor does not become the accessor"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class BooleanAccessorFixture {
                boolean enabled

                def beans = {
                    bean('isEnabled', String) {
                        'bean value'
                    }
                }
            }
        '''

        when:
        Class<?> fixtureBeans = compile(source)
        def instance = fixtureBeans.getDeclaredConstructor().newInstance()
        instance.enabled = true

        then:
        instance.isEnabled() == true
        fixtureBeans.getDeclaredMethod('string$0').getAnnotation(Bean).value() == ['isEnabled'] as String[]
    }

    def "an explicit method(...) name colliding with a method the standalone class already declares is a compile error"() {
        given: "method(...) declares a real private member, so unlike a bean it cannot silently adapt"
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class ExistingMemberClashFixture {
                String helper() { 'existing' }

                def beans = {
                    method('helper', String) {
                        'duplicate'
                    }
                }
            }
        '''

        when:
        compile(source)

        then:
        MultipleCompilationErrorsException e = thrown(MultipleCompilationErrorsException)
        e.message.contains('is already used by another')
    }

    private static final String FIXTURE_PLUGIN = '''
        import grails.compiler.beans.GrailsBeans
        import grails.plugins.Plugin
        import org.springframework.boot.autoconfigure.AutoConfiguration
        import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
        import org.springframework.boot.autoconfigure.context.MessageSourceAutoConfiguration
        import org.springframework.context.annotation.PropertySource

        @GrailsBeans
        @AutoConfiguration(before = [MessageSourceAutoConfiguration])
        @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
        @PropertySource('classpath:fixture-plugin.properties')
        class FixturePlugin extends Plugin {

            String version = '1.0'

            def beans = {
                bean('greeting', String) {
                    'hello from plugin'
                }
            }

            String stillHere() {
                'plugin lifecycle members are untouched'
            }
        }
    '''

    def "applying @GrailsBeans to a Plugin subclass generates a sibling AutoConfiguration class"() {
        given:
        GroovyClassLoader loader = new GroovyClassLoader(getClass().classLoader)
        loader.parseClass(FIXTURE_PLUGIN)

        when:
        Class<?> pluginClass = loader.loadClass('FixturePlugin')
        Class<?> autoConfigClass = loader.loadClass('FixturePluginAutoConfiguration')

        then: "the plugin class keeps its own identity and members, minus the DSL"
        Plugin.isAssignableFrom(pluginClass)
        pluginClass.getDeclaredMethod('stillHere').invoke(pluginClass.getDeclaredConstructor().newInstance()) ==
                'plugin lifecycle members are untouched'
        pluginClass.declaredFields*.name.every { it != 'beans' }
        !pluginClass.isAnnotationPresent(AutoConfiguration)

        and: "@ConditionalOnWebApplication and @PropertySource are meaningless on a Plugin subclass " +
                "(Spring never processes it as a bean), so they move too, not just @AutoConfiguration"
        !pluginClass.isAnnotationPresent(ConditionalOnWebApplication)
        !pluginClass.isAnnotationPresent(PropertySource)

        and: "the generated sibling carries the compiled bean and every moved annotation"
        autoConfigClass.isAnnotationPresent(AutoConfiguration)
        autoConfigClass.getAnnotation(AutoConfiguration).before().toList() == [MessageSourceAutoConfiguration]
        autoConfigClass.isAnnotationPresent(ConditionalOnWebApplication)
        autoConfigClass.getAnnotation(ConditionalOnWebApplication).type() == ConditionalOnWebApplication.Type.SERVLET
        autoConfigClass.isAnnotationPresent(PropertySource)
        autoConfigClass.getAnnotation(PropertySource).value().toList() == ['classpath:fixture-plugin.properties']
        autoConfigClass.getDeclaredMethod('greeting').isAnnotationPresent(Bean)
        autoConfigClass.getDeclaredConstructor().newInstance().greeting() == 'hello from plugin'
    }

    def "AutoConfigureOrder, AutoConfigureBefore, AutoConfigureAfter, ImportAutoConfiguration, and EnableConfigurationProperties all move to the sibling"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import grails.plugins.Plugin
            import org.springframework.boot.autoconfigure.AutoConfiguration
            import org.springframework.boot.autoconfigure.AutoConfigureAfter
            import org.springframework.boot.autoconfigure.AutoConfigureBefore
            import org.springframework.boot.autoconfigure.AutoConfigureOrder
            import org.springframework.boot.autoconfigure.ImportAutoConfiguration
            import org.springframework.boot.context.properties.EnableConfigurationProperties

            class BeforeTarget { }
            class AfterTarget { }
            class ImportedAutoConfig { }
            class SomeProperties { }

            @GrailsBeans
            @AutoConfiguration
            @AutoConfigureOrder(1)
            @AutoConfigureBefore(BeforeTarget)
            @AutoConfigureAfter(AfterTarget)
            @ImportAutoConfiguration(ImportedAutoConfig)
            @EnableConfigurationProperties(SomeProperties)
            class ManyAnnotationsPlugin extends Plugin {
                def beans = {
                    bean("greeting", String) {
                        "hello"
                    }
                }
            }
        '''

        when:
        GroovyClassLoader loader = new GroovyClassLoader(getClass().classLoader)
        loader.parseClass(source)
        Class<?> pluginClass = loader.loadClass('ManyAnnotationsPlugin')
        Class<?> autoConfigClass = loader.loadClass('ManyAnnotationsPluginAutoConfiguration')

        then: "none of them are left behind on the Plugin class Spring never processes as a bean"
        !pluginClass.isAnnotationPresent(AutoConfigureOrder)
        !pluginClass.isAnnotationPresent(AutoConfigureBefore)
        !pluginClass.isAnnotationPresent(AutoConfigureAfter)
        !pluginClass.isAnnotationPresent(ImportAutoConfiguration)
        !pluginClass.isAnnotationPresent(EnableConfigurationProperties)

        and: "all of them moved to the sibling"
        autoConfigClass.isAnnotationPresent(AutoConfigureOrder)
        autoConfigClass.getAnnotation(AutoConfigureOrder).value() == 1
        autoConfigClass.isAnnotationPresent(AutoConfigureBefore)
        autoConfigClass.isAnnotationPresent(AutoConfigureAfter)
        autoConfigClass.isAnnotationPresent(ImportAutoConfiguration)
        autoConfigClass.isAnnotationPresent(EnableConfigurationProperties)
    }

    def "a composed condition/import annotation - meta-annotated with an existing @ConditionalOnXxx or @Import, not directly - also moves to the sibling"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import grails.plugins.Plugin
            import org.springframework.boot.autoconfigure.AutoConfiguration
            import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
            import org.springframework.context.annotation.Import
            import java.lang.annotation.ElementType
            import java.lang.annotation.Retention
            import java.lang.annotation.RetentionPolicy
            import java.lang.annotation.Target

            class ImportedConfig { }

            @Target(ElementType.TYPE)
            @Retention(RetentionPolicy.RUNTIME)
            @ConditionalOnProperty(prefix = "feature", name = "enabled")
            @interface ConditionalOnFeature { }

            @Target(ElementType.TYPE)
            @Retention(RetentionPolicy.RUNTIME)
            @Import(ImportedConfig)
            @interface EnableImportedConfig { }

            @GrailsBeans
            @AutoConfiguration
            @ConditionalOnFeature
            @EnableImportedConfig
            class ComposedAnnotationPlugin extends Plugin {
                def beans = {
                    bean("greeting", String) {
                        "hello"
                    }
                }
            }
        '''

        when:
        GroovyClassLoader loader = new GroovyClassLoader(getClass().classLoader)
        loader.parseClass(source)
        Class<?> pluginClass = loader.loadClass('ComposedAnnotationPlugin')
        Class<?> autoConfigClass = loader.loadClass('ComposedAnnotationPluginAutoConfiguration')
        Class<?> conditionalOnFeature = loader.loadClass('ConditionalOnFeature')
        Class<?> enableImportedConfig = loader.loadClass('EnableImportedConfig')

        then: "neither composed annotation is left behind on the Plugin class Spring never processes as a bean"
        !pluginClass.isAnnotationPresent(conditionalOnFeature)
        !pluginClass.isAnnotationPresent(enableImportedConfig)

        and: "both moved to the sibling, found via their meta-annotations rather than by exact name"
        autoConfigClass.isAnnotationPresent(conditionalOnFeature)
        autoConfigClass.isAnnotationPresent(enableImportedConfig)
    }

    def "@ImportResource on a Plugin subclass moves to the sibling, matching @Import's treatment"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import grails.plugins.Plugin
            import org.springframework.boot.autoconfigure.AutoConfiguration
            import org.springframework.context.annotation.ImportResource

            @GrailsBeans
            @AutoConfiguration
            @ImportResource('classpath:foo.xml')
            class ImportResourcePlugin extends Plugin {
                def beans = {
                    bean('greeting', String) {
                        'hello'
                    }
                }
            }
        '''

        when:
        GroovyClassLoader loader = new GroovyClassLoader(getClass().classLoader)
        loader.parseClass(source)
        Class<?> pluginClass = loader.loadClass('ImportResourcePlugin')
        Class<?> autoConfigClass = loader.loadClass('ImportResourcePluginAutoConfiguration')

        then: "not left behind on the Plugin class Spring never processes as a bean"
        !pluginClass.isAnnotationPresent(ImportResource)

        and: "moved to the sibling"
        autoConfigClass.isAnnotationPresent(ImportResource)
        autoConfigClass.getAnnotation(ImportResource).value() == ['classpath:foo.xml'] as String[]
    }

    def "@ComponentScan and an explicit @PropertySources container also move to the sibling"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import grails.plugins.Plugin
            import org.springframework.boot.autoconfigure.AutoConfiguration
            import org.springframework.context.annotation.ComponentScan
            import org.springframework.context.annotation.PropertySource
            import org.springframework.context.annotation.PropertySources

            @GrailsBeans
            @AutoConfiguration
            @ComponentScan('com.example.scanned')
            @PropertySources([
                @PropertySource('classpath:one.properties'),
                @PropertySource('classpath:two.properties')
            ])
            class ScanningPlugin extends Plugin {
                def beans = {
                    bean('greeting', String) {
                        'hello'
                    }
                }
            }
        '''

        when:
        GroovyClassLoader loader = new GroovyClassLoader(getClass().classLoader)
        loader.parseClass(source)
        Class<?> pluginClass = loader.loadClass('ScanningPlugin')
        Class<?> autoConfigClass = loader.loadClass('ScanningPluginAutoConfiguration')

        then: "neither is left behind on the Plugin class Spring never processes as a bean"
        !pluginClass.isAnnotationPresent(ComponentScan)
        !pluginClass.isAnnotationPresent(PropertySources)

        and: "both moved to the sibling - @PropertySources matters because the repeatable-container " +
                "form would otherwise be treated differently from writing @PropertySource twice"
        autoConfigClass.isAnnotationPresent(ComponentScan)
        autoConfigClass.getAnnotation(ComponentScan).value() == ['com.example.scanned'] as String[]
        autoConfigClass.isAnnotationPresent(PropertySources)
        autoConfigClass.getAnnotation(PropertySources).value()*.value()*.toList().flatten() ==
                ['classpath:one.properties', 'classpath:two.properties']
    }

    def "moveAnnotations moves an annotation the transform has no automatic knowledge of; without it the annotation stays put"() {
        given: "a marker annotation meta-annotated with nothing the transform recognises"
        String sourceTemplate = '''
            import grails.compiler.beans.GrailsBeans
            import grails.plugins.Plugin
            import org.springframework.boot.autoconfigure.AutoConfiguration
            import java.lang.annotation.ElementType
            import java.lang.annotation.Retention
            import java.lang.annotation.RetentionPolicy
            import java.lang.annotation.Target

            @Target(ElementType.TYPE)
            @Retention(RetentionPolicy.RUNTIME)
            @interface VendorMarker { }

            %s
            @AutoConfiguration
            @VendorMarker
            class VendorPlugin extends Plugin {
                def beans = {
                    bean('greeting', String) {
                        'hello'
                    }
                }
            }
        '''

        when: "compiled WITHOUT moveAnnotations"
        GroovyClassLoader strandedLoader = new GroovyClassLoader(getClass().classLoader)
        strandedLoader.parseClass(String.format(sourceTemplate, '@GrailsBeans'))

        then: "the marker stays on the plugin class - the automatic set can't know about it"
        strandedLoader.loadClass('VendorPlugin').annotations*.annotationType()*.simpleName.contains('VendorMarker')
        !strandedLoader.loadClass('VendorPluginAutoConfiguration').annotations*.annotationType()*.simpleName.contains('VendorMarker')

        when: "compiled WITH moveAnnotations naming it"
        GroovyClassLoader movedLoader = new GroovyClassLoader(getClass().classLoader)
        movedLoader.parseClass(String.format(sourceTemplate, '@GrailsBeans(moveAnnotations = [VendorMarker])'))

        then: "the marker moves to the sibling like the automatically-recognised annotations do"
        !movedLoader.loadClass('VendorPlugin').annotations*.annotationType()*.simpleName.contains('VendorMarker')
        movedLoader.loadClass('VendorPluginAutoConfiguration').annotations*.annotationType()*.simpleName.contains('VendorMarker')
    }

    def "moveAnnotations rejects a non-annotation class"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import grails.plugins.Plugin
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans(moveAnnotations = [String])
            @AutoConfiguration
            class BadMoveAnnotationsPlugin extends Plugin {
                def beans = {
                    bean('greeting', String) {
                        'hello'
                    }
                }
            }
        '''

        when:
        compile(source)

        then:
        MultipleCompilationErrorsException e = thrown(MultipleCompilationErrorsException)
        e.message.contains('is not an annotation type')
    }

    def "moveAnnotations is rejected on a standalone (non-Plugin) class, where it can have no effect"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration
            import org.springframework.context.annotation.ComponentScan

            @GrailsBeans(moveAnnotations = [ComponentScan])
            @AutoConfiguration
            class StandaloneWithMoveAnnotations {
                def beans = {
                    bean('greeting', String) {
                        'hello'
                    }
                }
            }
        '''

        when:
        compile(source)

        then:
        MultipleCompilationErrorsException e = thrown(MultipleCompilationErrorsException)
        e.message.contains('moveAnnotations has no effect here')
    }

    def "a *GrailsPlugin class derives its sibling name by replacing the suffix with AutoConfiguration"() {
        given: "the convention that made the real I18nGrailsPlugin regenerate I18nAutoConfiguration with no attribute"
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import grails.plugins.Plugin
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class FooGrailsPlugin extends Plugin {
                def beans = {
                    bean('greeting', String) {
                        'hello'
                    }
                }
            }
        '''

        when:
        GroovyClassLoader loader = new GroovyClassLoader(getClass().classLoader)
        loader.parseClass(source)

        then: "FooGrailsPlugin -> FooAutoConfiguration, not FooGrailsPluginAutoConfiguration"
        loader.loadClass('FooAutoConfiguration').getDeclaredMethod('greeting') != null

        when:
        loader.loadClass('FooGrailsPluginAutoConfiguration')

        then:
        thrown(ClassNotFoundException)
    }

    def "autoConfigurationName overrides the *GrailsPlugin suffix convention too"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import grails.plugins.Plugin
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans(autoConfigurationName = 'CustomName')
            @AutoConfiguration
            class BarGrailsPlugin extends Plugin {
                def beans = {
                    bean('greeting', String) {
                        'hello'
                    }
                }
            }
        '''

        when:
        GroovyClassLoader loader = new GroovyClassLoader(getClass().classLoader)
        loader.parseClass(source)

        then:
        loader.loadClass('CustomName').getDeclaredMethod('greeting') != null
    }

    def "GrailsBeans(autoConfigurationName = ...) names the generated sibling instead of the default <PluginClassName>AutoConfiguration"() {
        given:
        String source = '''
            package com.example.plugins

            import grails.compiler.beans.GrailsBeans
            import grails.plugins.Plugin
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans(autoConfigurationName = 'LegacyName')
            @AutoConfiguration
            class RenamedSiblingPlugin extends Plugin {
                def beans = {
                    bean('greeting', String) {
                        'hello'
                    }
                }
            }
        '''

        and:
        GroovyClassLoader loader = new GroovyClassLoader(getClass().classLoader)
        loader.parseClass(source)

        expect: "the sibling takes the given simple name, in the plugin's own package, not the default suffix form"
        loader.loadClass('com.example.plugins.LegacyName').getDeclaredMethod('greeting') != null

        when: "the default-suffix name is looked up instead"
        loader.loadClass('com.example.plugins.RenamedSiblingPluginAutoConfiguration')

        then: "it was never generated"
        thrown(ClassNotFoundException)
    }

    def "a qualified autoConfigurationName generates the sibling in the package it names"() {
        given: "a descriptor in the package its implementation classes sit beneath, which is where a plugin descriptor goes"
        String source = '''
            package com.example

            import grails.compiler.beans.GrailsBeans
            import grails.plugins.Plugin
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans(autoConfigurationName = 'com.example.web.ExampleAutoConfiguration')
            @AutoConfiguration
            class ExampleGrailsPlugin extends Plugin {
                def beans = {
                    bean('greeting', String) {
                        'hello'
                    }
                }
            }
        '''

        and:
        GroovyClassLoader loader = new GroovyClassLoader(getClass().classLoader)
        loader.parseClass(source)

        expect: "the class the conversion replaces keeps its qualified name, which is its identity"
        loader.loadClass('com.example.web.ExampleAutoConfiguration').getDeclaredMethod('greeting') != null

        when: "the plugin's own package is looked in instead"
        loader.loadClass('com.example.ExampleAutoConfiguration')

        then: "nothing was generated there"
        thrown(ClassNotFoundException)
    }

    def "a qualified autoConfigurationName carries the annotations that move to the sibling"() {
        given:
        String source = '''
            package com.example

            import grails.compiler.beans.GrailsBeans
            import grails.plugins.Plugin
            import org.springframework.boot.autoconfigure.AutoConfiguration
            import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication

            @GrailsBeans(autoConfigurationName = 'com.example.web.MovedAutoConfiguration')
            @AutoConfiguration(beforeName = 'com.example.OtherAutoConfiguration')
            @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
            class MovedGrailsPlugin extends Plugin {
                def beans = {
                    bean('greeting', String) {
                        'hello'
                    }
                }
            }
        '''

        and:
        GroovyClassLoader loader = new GroovyClassLoader(getClass().classLoader)
        loader.parseClass(source)
        Class<?> sibling = loader.loadClass('com.example.web.MovedAutoConfiguration')

        expect: "naming a package changes where the sibling lands and nothing else about it"
        sibling.getAnnotation(AutoConfiguration).beforeName().toList() == ['com.example.OtherAutoConfiguration']
        sibling.isAnnotationPresent(ConditionalOnWebApplication)
        !loader.loadClass('com.example.MovedGrailsPlugin').isAnnotationPresent(AutoConfiguration)
    }

    def "a bare autoConfigurationName still names the sibling in the plugin's own package"() {
        given:
        String source = '''
            package com.example

            import grails.compiler.beans.GrailsBeans
            import grails.plugins.Plugin
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans(autoConfigurationName = 'BareNamed')
            @AutoConfiguration
            class BareNamedGrailsPlugin extends Plugin {
                def beans = {
                    bean('greeting', String) {
                        'hello'
                    }
                }
            }
        '''

        and:
        GroovyClassLoader loader = new GroovyClassLoader(getClass().classLoader)
        loader.parseClass(source)

        expect:
        loader.loadClass('com.example.BareNamed').getDeclaredMethod('greeting') != null
    }

    @Unroll
    def "a qualified autoConfigurationName with #description is rejected"() {
        given:
        String source = """
            package com.example

            import grails.compiler.beans.GrailsBeans
            import grails.plugins.Plugin
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans(autoConfigurationName = '$name')
            @AutoConfiguration
            class ${simpleName}GrailsPlugin extends Plugin {
                def beans = {
                    bean('greeting', String) {
                        'hello'
                    }
                }
            }
        """

        when:
        compile(source)

        then:
        MultipleCompilationErrorsException e = thrown(MultipleCompilationErrorsException)
        e.message.contains('is not a valid name')

        where:
        description                  | name                         | simpleName
        'a keyword for a package'    | 'com.int.Example'            | 'KeywordPackage'
        'a trailing dot'             | 'com.example.'               | 'TrailingDot'
        'a leading dot'              | '.com.example.Example'       | 'LeadingDot'
        'an empty part'              | 'com..example.Example'       | 'EmptyPart'
        'a space in a part'          | 'com.exa mple.Example'       | 'SpacedPart'
    }

    def "autoConfigurationName is rejected on a standalone (non-Plugin) class, where it can have no effect"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans(autoConfigurationName = 'Ignored')
            @AutoConfiguration
            class StandaloneWithAutoConfigurationName {
                def beans = {
                    bean('greeting', String) {
                        'hello'
                    }
                }
            }
        '''

        when:
        compile(source)

        then:
        MultipleCompilationErrorsException e = thrown(MultipleCompilationErrorsException)
        e.message.contains('autoConfigurationName has no effect here')
    }

    def "autoConfigurationName that is not a valid Java identifier falls back to the default sibling name with an error"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import grails.plugins.Plugin
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans(autoConfigurationName = 'not a valid name!')
            @AutoConfiguration
            class InvalidAutoConfigurationNamePlugin extends Plugin {
                def beans = {
                    bean('greeting', String) {
                        'hello'
                    }
                }
            }
        '''

        when:
        compile(source)

        then:
        MultipleCompilationErrorsException e = thrown(MultipleCompilationErrorsException)
        e.message.contains('is not a valid name')
    }

    def "autoConfigurationName given explicitly as an empty string is rejected, not silently treated as unset"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import grails.plugins.Plugin
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans(autoConfigurationName = '')
            @AutoConfiguration
            class BlankAutoConfigurationNamePlugin extends Plugin {
                def beans = {
                    bean('greeting', String) {
                        'hello'
                    }
                }
            }
        '''

        when:
        compile(source)

        then:
        MultipleCompilationErrorsException e = thrown(MultipleCompilationErrorsException)
        e.message.contains('must not be blank')
    }

    def "autoConfigurationName that is a reserved keyword is rejected"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import grails.plugins.Plugin
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans(autoConfigurationName = 'int')
            @AutoConfiguration
            class KeywordAutoConfigurationNamePlugin extends Plugin {
                def beans = {
                    bean('greeting', String) {
                        'hello'
                    }
                }
            }
        '''

        when:
        compile(source)

        then:
        MultipleCompilationErrorsException e = thrown(MultipleCompilationErrorsException)
        e.message.contains('is not a valid name')
    }

    @Unroll
    def "autoConfigurationName colliding with #description is a plain Groovy duplicate-class compile error"() {
        given:
        String source = """
            import grails.compiler.beans.GrailsBeans
            import grails.plugins.Plugin
            import org.springframework.boot.autoconfigure.AutoConfiguration

            $extraClass

            @GrailsBeans(autoConfigurationName = '$collidingName')
            @AutoConfiguration
            class $pluginName extends Plugin {
                def beans = {
                    bean('greeting', String) {
                        'hello'
                    }
                }
            }
        """

        when:
        compile(source)

        then:
        MultipleCompilationErrorsException e = thrown(MultipleCompilationErrorsException)
        e.message.contains('duplicate class')

        where:
        description                        | extraClass               | collidingName    | pluginName
        "the plugin's own simple name"     | ''                       | 'SelfCollision'  | 'SelfCollision'
        'another class in the same source' | 'class Existing { }'     | 'Existing'       | 'OtherCollisionPlugin'
    }

    def "a Plugin subclass using @GrailsBeans without @AutoConfiguration fails to compile"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import grails.plugins.Plugin

            @GrailsBeans
            class PluginWithoutAutoConfiguration extends Plugin {
                def beans = {
                    bean('greeting', String) {
                        'hello'
                    }
                }
            }
        '''

        when:
        compile(source)

        then:
        MultipleCompilationErrorsException e = thrown(MultipleCompilationErrorsException)
        e.message.contains('must also be annotated @AutoConfiguration')
    }

    def "compiles under @CompileStatic"() {
        given:
        String source = '''
            import groovy.transform.CompileStatic
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @CompileStatic
            @AutoConfiguration
            class CompileStaticFixture {
                def beans = {
                    bean('greeting', String) {
                        'hello'
                    }
                }
            }
        '''

        when:
        Class<?> fixture = compile(source)

        then:
        fixture.getDeclaredConstructor().newInstance().greeting() == 'hello'
    }

    def "annotate(...)'s named-argument syntax compiles under @CompileStatic"() {
        given: "the beans property is stripped before static type-checking ever runs, so the " +
                "DSL's Map-literal named-args never reach the type checker"
        String source = '''
            import groovy.transform.CompileStatic
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration
            import org.springframework.core.annotation.Order

            @GrailsBeans
            @CompileStatic
            @AutoConfiguration
            class AnnotateCompileStaticFixture {
                def beans = {
                    bean('greeting', String).annotate(Order, value: 1) {
                        'hello'
                    }
                }
            }
        '''

        when:
        Class<?> fixture = compile(source)

        then:
        fixture.getDeclaredMethod('greeting').getAnnotation(Order).value() == 1
        fixture.getDeclaredConstructor().newInstance().greeting() == 'hello'
    }

    def "compiles under @CompileStatic on a Plugin subclass"() {
        given:
        String source = '''
            import groovy.transform.CompileStatic
            import grails.compiler.beans.GrailsBeans
            import grails.plugins.Plugin
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @CompileStatic
            @AutoConfiguration
            class CompileStaticPluginFixture extends Plugin {
                String version = '1.0'

                def beans = {
                    bean('greeting', String) {
                        'hello'
                    }
                }

                @Override
                void doWithApplicationContext() {
                    String x = 'statically typed local'
                }
            }
        '''

        when:
        Class<?> pluginClass = compile(source)
        Class<?> autoConfigClass = new GroovyClassLoader(pluginClass.classLoader).loadClass('CompileStaticPluginFixtureAutoConfiguration')

        then:
        autoConfigClass.getDeclaredConstructor().newInstance().greeting() == 'hello'
    }

    def "field(...) and method(...) resolve correctly from a bean() closure under @CompileStatic"() {
        given: "a field and a private helper method are relocated from the DSL closure into the " +
                "generated class's own members, and this proves a sibling bean() referencing them " +
                "by simple name still resolves once static type checking examines the final class"
        String source = '''
            import groovy.transform.CompileStatic
            import grails.compiler.beans.GrailsBeans
            import org.springframework.beans.factory.annotation.Value
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @CompileStatic
            @AutoConfiguration
            class CompileStaticMembersFixture {
                def beans = {
                    field('suffix', String).annotate(Value, value: '${greeting.suffix:!!!}')

                    method('yell', String) { String input ->
                        input.toUpperCase() + (suffix ?: '')
                    }

                    bean('yelledGreeting', String) {
                        yell('hello')
                    }
                }
            }
        '''

        when:
        Class<?> fixture = compile(source)
        def instance = fixture.getDeclaredConstructor().newInstance()
        def suffixField = fixture.getDeclaredField('suffix')
        suffixField.accessible = true
        suffixField.set(instance, '!!!')

        then:
        instance.yelledGreeting() == 'HELLO!!!'
    }

    def "field(...) and method(...) resolve correctly on the Plugin-subclass sibling under @CompileStatic"() {
        given:
        String source = '''
            import groovy.transform.CompileStatic
            import grails.compiler.beans.GrailsBeans
            import grails.plugins.Plugin
            import org.springframework.beans.factory.annotation.Value
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @CompileStatic
            @AutoConfiguration
            class CompileStaticMembersPluginFixture extends Plugin {
                String version = '1.0'

                def beans = {
                    field('suffix', String).annotate(Value, value: '${greeting.suffix:!!!}')

                    method('yell', String) { String input ->
                        input.toUpperCase() + (suffix ?: '')
                    }

                    bean('yelledGreeting', String) {
                        yell('hello')
                    }
                }
            }
        '''

        when:
        Class<?> pluginClass = compile(source)
        Class<?> autoConfigClass = new GroovyClassLoader(pluginClass.classLoader)
                .loadClass('CompileStaticMembersPluginFixtureAutoConfiguration')
        def instance = autoConfigClass.getDeclaredConstructor().newInstance()
        def suffixField = autoConfigClass.getDeclaredField('suffix')
        suffixField.accessible = true
        suffixField.set(instance, '!!!')

        then:
        instance.yelledGreeting() == 'HELLO!!!'
    }

    def "a realistic i18n-plugin-shaped conversion compiles and behaves correctly"() {
        given: "field(...)/method(...)/bean(...) together, modelled on the real conversion now " +
                "live in org.grails.plugins.i18n.I18nGrailsPlugin - injected config fields feeding a " +
                "strategy-selecting helper method and a multi-field-dependent bean, each bean backing " +
                "off an existing same-named bean the way the real plugin does. LocaleResolver stands " +
                "in for org.springframework.web.servlet.LocaleResolver (spring-webmvc isn't a " +
                "dependency of this module) but the DSL usage is identical either way"
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import grails.plugins.Plugin
            import org.springframework.boot.autoconfigure.AutoConfiguration
            import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
            import org.springframework.boot.autoconfigure.condition.SearchStrategy
            import org.springframework.boot.autoconfigure.context.MessageSourceAutoConfiguration
            import org.springframework.context.support.ReloadableResourceBundleMessageSource

            interface LocaleResolver {
                String describe()
            }

            class CookieLocaleResolver implements LocaleResolver {
                String describe() { 'cookie' }
            }

            class SessionLocaleResolver implements LocaleResolver {
                String describe() { 'session' }
            }

            @GrailsBeans
            @AutoConfiguration(before = MessageSourceAutoConfiguration)
            class I18nStyleGrailsPlugin extends Plugin {

                String version = "1.0"

                def beans = {
                    field("encoding", String).value("grails.views.gsp.encoding", "UTF-8")
                    field("localeResolverType", String).value("grails.i18n.locale.resolver", "session")

                    method("buildLocaleResolver", LocaleResolver) {
                        localeResolverType?.toLowerCase() == "cookie" ? new CookieLocaleResolver() : new SessionLocaleResolver()
                    }

                    bean(LocaleResolver).conditionalOnMissingBeanName(search: SearchStrategy.CURRENT) {
                        buildLocaleResolver()
                    }

                    method("buildMessageSource", ReloadableResourceBundleMessageSource) {
                        def source = new ReloadableResourceBundleMessageSource(basename: "WEB-INF/grails-app/i18n/messages")
                        source.defaultEncoding = encoding
                        source
                    }

                    bean("messageSource", ReloadableResourceBundleMessageSource)
                            .conditionalOnMissingBeanName(search: SearchStrategy.CURRENT) {
                        buildMessageSource()
                    }
                }

                @Override
                void doWithApplicationContext() {
                }
            }
        '''

        when: "the *GrailsPlugin suffix derives the sibling name, exactly as the real conversion relies on"
        Class<?> pluginClass = compile(source)
        Class<?> autoConfigClass = new GroovyClassLoader(pluginClass.classLoader).loadClass('I18nStyleAutoConfiguration')
        def instance = autoConfigClass.getDeclaredConstructor().newInstance()
        def localeResolverTypeField = autoConfigClass.getDeclaredField('localeResolverType')
        localeResolverTypeField.accessible = true
        localeResolverTypeField.set(instance, 'cookie')
        def encodingField = autoConfigClass.getDeclaredField('encoding')
        encodingField.accessible = true
        encodingField.set(instance, 'ISO-8859-1')

        then: "the strategy-selecting helper, driven by an injected field, picks the right resolver"
        instance.localeResolver().describe() == 'cookie'

        and: "the second bean, built from a different helper reading a different field, also works"
        instance.messageSource().defaultEncoding == 'ISO-8859-1'

        and: "every bean backs off an existing same-named bean, matching the real plugin's semantics"
        autoConfigClass.getDeclaredMethod('localeResolver')
                .getAnnotation(ConditionalOnMissingBean).search() == SearchStrategy.CURRENT
    }

    def "compiles under @GrailsCompileStatic on a Plugin subclass"() {
        given:
        String source = '''
            import grails.compiler.GrailsCompileStatic
            import grails.compiler.beans.GrailsBeans
            import grails.plugins.Plugin
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @GrailsCompileStatic
            @AutoConfiguration
            class GrailsCompileStaticPluginFixture extends Plugin {
                String version = '1.0'

                def beans = {
                    bean('greeting', String) {
                        'hello'
                    }
                }
            }
        '''

        when:
        Class<?> pluginClass = compile(source)
        Class<?> autoConfigClass = new GroovyClassLoader(pluginClass.classLoader).loadClass('GrailsCompileStaticPluginFixtureAutoConfiguration')

        then:
        autoConfigClass.getDeclaredConstructor().newInstance().greeting() == 'hello'
    }

    private static final String PLUGIN_FIXTURE_TEMPLATE = '''
        import grails.compiler.beans.GrailsBeans
        import grails.plugins.Plugin
        import org.springframework.boot.autoconfigure.AutoConfiguration
        %s

        @GrailsBeans
        %s
        @AutoConfiguration
        class DispatchFixturePlugin extends Plugin {
            String version = '1.0'

            def beans = {
                bean('greeting', String) {
                    'hello'.toUpperCase()
                }
            }
        }
    '''

    private static final String STANDALONE_FIXTURE_TEMPLATE = '''
        import grails.compiler.beans.GrailsBeans
        import org.springframework.boot.autoconfigure.AutoConfiguration
        %s

        @GrailsBeans
        %s
        @AutoConfiguration
        class DispatchFixtureStandalone {
            def beans = {
                bean('greeting', String) {
                    'hello'.toUpperCase()
                }
            }
        }
    '''

    private File compileToRealClassFiles(String template, String staticAnnotation, String fileName, String subDir) {
        String annotationImport = staticAnnotation == 'GrailsCompileStatic' ?
                'import grails.compiler.GrailsCompileStatic' : 'import groovy.transform.CompileStatic'
        String source = String.format(template,
                staticAnnotation ? annotationImport : '',
                staticAnnotation ? "@${staticAnnotation}" : '')
        File destDir = new File(tempDir, subDir)
        CompilerConfiguration config = new CompilerConfiguration()
        config.targetDirectory = destDir
        CompilationUnit unit = new CompilationUnit(config, null, new GroovyClassLoader(getClass().classLoader))
        unit.addSource(fileName, source)
        unit.compile(Phases.OUTPUT)
        destDir
    }

    private boolean usesInvokeDynamic(File classFile) {
        String javap = "${System.getProperty('java.home')}/bin/javap"
        Process process = [javap, '-c', '-p', classFile.absolutePath].execute()
        process.waitFor()
        process.text.contains('invokedynamic')
    }

    def "the standalone form's @Bean methods are statically dispatched when @CompileStatic is present"() {
        given: "the same DSL compiled with and without @CompileStatic on the annotated class"
        File staticDir = compileToRealClassFiles(STANDALONE_FIXTURE_TEMPLATE, 'CompileStatic', 'Static.groovy', 'standalone-static')
        File dynamicDir = compileToRealClassFiles(STANDALONE_FIXTURE_TEMPLATE, null, 'Dynamic.groovy', 'standalone-dynamic')

        expect: "the generated greeting() method is compiled the same way as everything else on the class"
        !usesInvokeDynamic(new File(staticDir, 'DispatchFixtureStandalone.class'))
        usesInvokeDynamic(new File(dynamicDir, 'DispatchFixtureStandalone.class'))
    }

    def "the Plugin-subclass sibling's @Bean methods are statically dispatched when @CompileStatic is present"() {
        given: "the same DSL compiled with and without @CompileStatic on the Plugin subclass"
        File staticDir = compileToRealClassFiles(PLUGIN_FIXTURE_TEMPLATE, 'CompileStatic', 'StaticPlugin.groovy', 'plugin-static')
        File dynamicDir = compileToRealClassFiles(PLUGIN_FIXTURE_TEMPLATE, null, 'DynamicPlugin.groovy', 'plugin-dynamic')

        expect: "the transform explicitly applies static compilation after generating the sibling"
        !usesInvokeDynamic(new File(staticDir, 'DispatchFixturePluginAutoConfiguration.class'))
        usesInvokeDynamic(new File(dynamicDir, 'DispatchFixturePluginAutoConfiguration.class'))
    }

    def "the Plugin-subclass sibling's @Bean methods are statically dispatched under @GrailsCompileStatic"() {
        given:
        File staticDir = compileToRealClassFiles(
                PLUGIN_FIXTURE_TEMPLATE, 'GrailsCompileStatic', 'GrailsStaticPlugin.groovy', 'plugin-grails-static')

        expect:
        !usesInvokeDynamic(new File(staticDir, 'DispatchFixturePluginAutoConfiguration.class'))
    }


    def "an empty beans block on a plugin generates no sibling and leaves the plugin's annotations alone"() {
        given: "a plugin whose beans block declares nothing"
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import grails.plugins.Plugin
            import org.springframework.boot.autoconfigure.AutoConfiguration
            import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication

            @GrailsBeans
            @AutoConfiguration
            @ConditionalOnWebApplication
            class EmptyBeansGrailsPlugin extends Plugin {
                def beans = { }
            }
        '''

        when: "it compiles without error"
        Class<?> plugin = compile(source)

        then: "the plugin keeps the annotations that would otherwise have been moved onto a sibling"
        plugin.annotations*.annotationType()*.simpleName.containsAll(['AutoConfiguration', 'ConditionalOnWebApplication'])

        and: "the DSL scaffolding is still stripped"
        !plugin.declaredFields*.name.contains('beans')

        when: "the sibling a non-empty block would have generated is looked up"
        plugin.classLoader.loadClass('EmptyBeansAutoConfiguration')

        then: "it was never generated, so nothing bean-less is registered as an auto-configuration"
        thrown(ClassNotFoundException)
    }

    def "bean(name, Type, Implementation) declares the interface and constructs the implementation"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class ImplementationBeans {
                def beans = {
                    bean('greeter', Greeter, EnglishGreeter)
                }
            }

            interface Greeter { String greet() }
            class EnglishGreeter implements Greeter { String greet() { 'hello' } }
        '''

        when:
        Class<?> beans = compile(source)
        def method = beans.getDeclaredMethod('greeter')

        then: "the declared type is what consumers inject"
        method.returnType.simpleName == 'Greeter'
        method.getAnnotation(Bean).value() == ['greeter'] as String[]

        and: "the body constructs the implementation"
        method.invoke(beans.getDeclaredConstructor().newInstance()).greet() == 'hello'
    }

    def "bean(Type, Implementation) with no name derives the name from the declared type, not the implementation"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class DerivedNameImplementationBeans {
                def beans = {
                    bean(Greeter, EnglishGreeter)
                }
            }

            interface Greeter { String greet() }
            class EnglishGreeter implements Greeter { String greet() { 'hello' } }
        '''

        when:
        Class<?> beans = compile(source)

        then: "the bean is named after the contract it satisfies"
        beans.getDeclaredMethod('greeter').getAnnotation(Bean).value() == ['greeter'] as String[]
    }

    def "bean(name, Type, Implementation) takes its constructor arguments from the closure parameters"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class InjectedImplementationBeans {
                def beans = {
                    bean('suffix', String) { '!' }
                    bean('greeter', Greeter, EnglishGreeter) { String suffix -> }
                }
            }

            interface Greeter { String greet() }
            class EnglishGreeter implements Greeter {
                private final String suffix
                EnglishGreeter(String suffix) { this.suffix = suffix }
                String greet() { 'hello' + suffix }
            }
        '''

        when:
        Class<?> beans = compile(source)
        def method = beans.getDeclaredMethod('greeter', String)

        then: "the parameter is the injection point and the constructor argument"
        method.returnType.simpleName == 'Greeter'
        method.invoke(beans.getDeclaredConstructor().newInstance(), '!').greet() == 'hello!'
    }

    def "bean(name, Type, Implementation) chains with the qualifiers"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration
            import org.springframework.core.annotation.Order

            @GrailsBeans
            @AutoConfiguration
            class QualifiedImplementationBeans {
                def beans = {
                    bean('greeter', Greeter, EnglishGreeter).primary().lazy().annotate(Order, value: 3)
                }
            }

            interface Greeter { String greet() }
            class EnglishGreeter implements Greeter { String greet() { 'hello' } }
        '''

        when:
        def method = compile(source).getDeclaredMethod('greeter')

        then:
        method.isAnnotationPresent(Primary)
        method.isAnnotationPresent(Lazy)
        method.getAnnotation(Order).value() == 3
    }

    def "rejects a factory closure body alongside an implementation type, which would answer the same question twice"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class ContradictoryImplementationBeans {
                def beans = {
                    bean('greeter', Greeter, EnglishGreeter) { new FrenchGreeter() }
                }
            }

            interface Greeter { String greet() }
            class EnglishGreeter implements Greeter { String greet() { 'hello' } }
            class FrenchGreeter implements Greeter { String greet() { 'bonjour' } }
        '''

        when:
        compile(source)

        then:
        MultipleCompilationErrorsException e = thrown(MultipleCompilationErrorsException)
        e.message.contains('already declares what to construct')
    }

    def "rejects an implementation that is not a subtype of the declared type"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class UnrelatedImplementationBeans {
                def beans = {
                    bean('greeter', Greeter, Stranger)
                }
            }

            interface Greeter { String greet() }
            class Stranger { }
        '''

        when:
        compile(source)

        then:
        MultipleCompilationErrorsException e = thrown(MultipleCompilationErrorsException)
        e.message.contains('Stranger is not a Greeter')
    }

    def "rejects an abstract implementation type"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class AbstractImplementationBeans {
                def beans = {
                    bean('greeter', Greeter, AbstractGreeter)
                }
            }

            interface Greeter { String greet() }
            abstract class AbstractGreeter implements Greeter { }
        '''

        when:
        compile(source)

        then:
        MultipleCompilationErrorsException e = thrown(MultipleCompilationErrorsException)
        e.message.contains('cannot be the implementation')
    }

    def "the bodyless-interface error points at the implementation form"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class BodylessInterfaceBeans {
                def beans = {
                    bean('greeter', Greeter)
                }
            }

            interface Greeter { String greet() }
        '''

        when:
        compile(source)

        then:
        MultipleCompilationErrorsException e = thrown(MultipleCompilationErrorsException)
        e.message.contains('name the implementation: bean(Greeter, SomeImplementation)')
    }

    def "field(...) and method(...) do not take an implementation type"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class FieldImplementationBeans {
                def beans = {
                    field('greeter', Greeter, EnglishGreeter)
                }
            }

            interface Greeter { String greet() }
            class EnglishGreeter implements Greeter { String greet() { 'hello' } }
        '''

        when:
        compile(source)

        then:
        MultipleCompilationErrorsException e = thrown(MultipleCompilationErrorsException)
        e.message.contains('field(...) requires a type, optionally preceded by a name')
    }

    def "the implementation type settles the declared type's type arguments without restating them"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class InferredFromImplementationBeans {
                def beans = {
                    bean('auditor', Auditor, LongAuditor)
                }
            }

            interface Auditor<T> { T current() }
            class LongAuditor implements Auditor<Long> { Long current() { 1L } }
        '''

        when:
        def method = compile(source).getDeclaredMethod('auditor')

        then: "the generic signature Spring resolves against is there, unstated"
        method.genericReturnType.toString().contains('Auditor<java.lang.Long>')
    }

    def "a factory body that is just a construction settles them too"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class InferredFromBodyBeans {
                def beans = {
                    bean('auditor', Auditor) { new LongAuditor() }
                }
            }

            interface Auditor<T> { T current() }
            class LongAuditor implements Auditor<Long> { Long current() { 1L } }
        '''

        when:
        def method = compile(source).getDeclaredMethod('auditor')

        then:
        method.genericReturnType.toString().contains('Auditor<java.lang.Long>')
    }

    def "an explicit typeArguments still wins over what the construction would prove"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class ExplicitOverInferredBeans {
                def beans = {
                    bean('numbers', List).typeArguments(Number) { new ArrayList<Number>() }
                }
            }
        '''

        when:
        def method = compile(source).getDeclaredMethod('numbers')

        then:
        method.genericReturnType.toString().contains('List<java.lang.Number>')
    }

    def "infers nothing when the construction proves nothing: #description"() {
        given:
        String source = """
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class NotInferredBeans {
                def beans = {
                    bean('holder', Holder) { $body }
                }
            }

            interface Holder<T> { T get() }
            class GenericBox<T> implements Holder<T> { T value; T get() { value } }
            class StringBox implements Holder<String> { String get() { 'x' } }
        """

        when:
        def method = compile(source).getDeclaredMethod('holder')

        then: "the raw type stands, exactly as it did before inference existed"
        method.genericReturnType.toString() == 'interface Holder'

        where:
        description                           | body
        'the construction is raw'             | 'new GenericBox()'
        'the body is not a bare construction' | 'def b = new StringBox(); b'
    }

    def "a parameterized construction proves the declared type's arguments as well as a bound implementation does"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class InferredFromParameterizedConstructionBeans {
                def beans = {
                    bean('holder', Holder) { new GenericBox<String>() }
                }
            }

            interface Holder<T> { T get() }
            class GenericBox<T> implements Holder<T> { T value; T get() { value } }
        '''

        when:
        def method = compile(source).getDeclaredMethod('holder')

        then:
        method.genericReturnType.toString().contains('Holder<java.lang.String>')
    }

    def "a bodyless bean on a generic type is left raw, since it proves only its own placeholders"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class BodylessGenericBeans {
                def beans = {
                    bean('names', ArrayList)
                }
            }
        '''

        when:
        def method = compile(source).getDeclaredMethod('names')

        then:
        method.returnType == ArrayList
        method.genericReturnType.toString() == 'class java.util.ArrayList'
    }

    def "annotate(Bean, ...) merges into the synthesized @Bean, reaching its own attributes"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration
            import org.springframework.context.annotation.Bean

            @GrailsBeans
            @AutoConfiguration
            class BeanAttributeBeans {
                def beans = {
                    bean('sharedClient', String).annotate(Bean, destroyMethod: '') { 'client' }
                    bean('managed', String).annotate(Bean, initMethod: 'trim', autowireCandidate: false) { 'managed' }
                }
            }
        '''

        when:
        Class<?> beans = compile(source)

        then: "the name bean(...) states survives the merge"
        beans.getDeclaredMethod('sharedClient').getAnnotation(Bean).value() == ['sharedClient'] as String[]

        and: "and the attribute that was previously unreachable is set"
        beans.getDeclaredMethod('sharedClient').getAnnotation(Bean).destroyMethod() == ''

        and:
        beans.getDeclaredMethod('managed').getAnnotation(Bean).initMethod() == 'trim'
        !beans.getDeclaredMethod('managed').getAnnotation(Bean).autowireCandidate()
    }

    def "rejects setting the bean's name through annotate(Bean, #attribute:), which bean(...) already states"() {
        given:
        String source = """
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration
            import org.springframework.context.annotation.Bean

            @GrailsBeans
            @AutoConfiguration
            class RenamedBeans {
                def beans = {
                    bean('greeting', String).annotate(Bean, $attribute: 'other') { 'hello' }
                }
            }
        """

        when:
        compile(source)

        then:
        MultipleCompilationErrorsException e = thrown(MultipleCompilationErrorsException)
        e.message.contains('would state it twice')

        where:
        attribute << ['value', 'name']
    }

    def "rejects a bare annotate(Bean), which adds nothing"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration
            import org.springframework.context.annotation.Bean

            @GrailsBeans
            @AutoConfiguration
            class BareBeanAnnotationBeans {
                def beans = {
                    bean('greeting', String).annotate(Bean) { 'hello' }
                }
            }
        '''

        when:
        compile(source)

        then:
        MultipleCompilationErrorsException e = thrown(MultipleCompilationErrorsException)
        e.message.contains('adds nothing')
    }

    def "rejects setting the same @Bean attribute twice"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration
            import org.springframework.context.annotation.Bean

            @GrailsBeans
            @AutoConfiguration
            class DuplicateBeanAttributeBeans {
                def beans = {
                    bean('greeting', String).annotate(Bean, initMethod: 'a').annotate(Bean, initMethod: 'b') { 'hello' }
                }
            }
        '''

        when:
        compile(source)

        then:
        MultipleCompilationErrorsException e = thrown(MultipleCompilationErrorsException)
        e.message.contains('is already set here')
    }

    def "rejects annotate(Bean, ...) on a method(...) helper, which Spring never reads as a bean"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration
            import org.springframework.context.annotation.Bean

            @GrailsBeans
            @AutoConfiguration
            class BeanOnHelperBeans {
                def beans = {
                    method('helper', String).annotate(Bean, initMethod: 'start') { 'hello' }
                }
            }
        '''

        when:
        compile(source)

        then:
        MultipleCompilationErrorsException e = thrown(MultipleCompilationErrorsException)
        e.message.contains('applies to bean(...) declarations')
    }

    def "rejects a #description bean declared without staticMethod()"() {
        given:
        String source = """
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration
            import $type

            @GrailsBeans
            @AutoConfiguration
            class NonStaticPostProcessorBeans {
                def beans = {
                    bean('postProcessor', ${type.tokenize('.').last()}) { $body }
                }
            }
        """

        when:
        compile(source)

        then:
        MultipleCompilationErrorsException e = thrown(MultipleCompilationErrorsException)
        e.message.contains("a $description bean must be declared .staticMethod()")

        where:
        description                | type                                                             | body
        'BeanFactoryPostProcessor' | 'org.springframework.beans.factory.config.BeanFactoryPostProcessor' | '{ factory -> } as BeanFactoryPostProcessor'
        'BeanPostProcessor'        | 'org.springframework.beans.factory.config.BeanPostProcessor'        | 'new BeanPostProcessor() { }'
    }

    def "accepts a post-processor bean declared staticMethod()"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration
            import org.springframework.beans.factory.config.BeanFactoryPostProcessor

            @GrailsBeans
            @AutoConfiguration
            class StaticPostProcessorBeans {
                def beans = {
                    bean('postProcessor', BeanFactoryPostProcessor).staticMethod() {
                        { factory -> } as BeanFactoryPostProcessor
                    }
                }
            }
        '''

        when:
        def method = compile(source).getDeclaredMethod('postProcessor')

        then:
        Modifier.isStatic(method.modifiers)
    }

    def "rejects a subtype of a post-processor too, not just the interface itself"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration
            import org.springframework.beans.factory.config.BeanFactoryPostProcessor
            import org.springframework.beans.factory.config.ConfigurableListableBeanFactory

            @GrailsBeans
            @AutoConfiguration
            class SubtypePostProcessorBeans {
                def beans = {
                    bean('postProcessor', MarkPrimary)
                }
            }

            class MarkPrimary implements BeanFactoryPostProcessor {
                void postProcessBeanFactory(ConfigurableListableBeanFactory factory) { }
            }
        '''

        when:
        compile(source)

        then:
        MultipleCompilationErrorsException e = thrown(MultipleCompilationErrorsException)
        e.message.contains('must be declared .staticMethod()')
    }

    def "leaves an ordinary bean alone"() {
        given:
        Class<?> beans = compile()

        expect: "nothing in the standard fixture is a post-processor, so nothing is forced static"
        !Modifier.isStatic(beans.getDeclaredMethod('greeting').modifiers)
    }

    def "conditionalOnGrailsEnv(...) compiles to @ConditionalOnGrailsEnv, not a grails.env property condition"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class EnvironmentBeans {
                def beans = {
                    bean('devOnly', String).conditionalOnGrailsEnv('development') { 'dev' }
                    bean('nonProduction', String).conditionalOnGrailsEnv('development', 'test') { 'not prod' }
                }
            }
        '''

        when:
        Class<?> beans = compile(source)

        then:
        beans.getDeclaredMethod('devOnly').getAnnotation(ConditionalOnGrailsEnv).value() == ['development'] as String[]
        beans.getDeclaredMethod('nonProduction').getAnnotation(ConditionalOnGrailsEnv).value() == ['development', 'test'] as String[]

        and: "it is a real Spring condition, so Spring evaluates it like any other"
        ConditionalOnGrailsEnv.isAnnotationPresent(Conditional)
        ConditionalOnGrailsEnv.getAnnotation(Conditional).value() == [OnGrailsEnvCondition] as Class[]
    }

    def "the condition matches the environment Grails reports, including one nobody set as a property"() {
        given: "an environment inferred by Grails, with no grails.env property anywhere"
        def context = new AnnotationConfigApplicationContext()
        context.environment.propertySources.addFirst(new MapPropertySource('empty', [:]))
        def condition = new OnGrailsEnvCondition()
        def metadata = Mock(AnnotatedTypeMetadata)
        metadata.getAnnotationAttributes(ConditionalOnGrailsEnv.name) >> [value: names as String[]]
        def conditionContext = Mock(ConditionContext)
        conditionContext.getClassLoader() >> getClass().classLoader
        conditionContext.getEnvironment() >> context.environment

        expect:
        condition.matches(conditionContext, metadata) == matches

        cleanup:
        context.close()

        where:
        names                       | matches
        [Environment.current.name]  | true
        ['no-such-environment']     | false
        ['no-such-environment', Environment.current.name] | true
    }

    def "rejects conditionalOnGrailsEnv(...) #description"() {
        given:
        String source = """
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class BadEnvironmentBeans {
                def beans = {
                    bean('greeting', String).conditionalOnGrailsEnv($arguments) { 'hello' }
                }
            }
        """

        when:
        compile(source)

        then:
        MultipleCompilationErrorsException e = thrown(MultipleCompilationErrorsException)
        e.message.contains(message)

        where:
        description                  | arguments   | message
        'with no environment'        | ''          | 'requires at least one environment name'
        'with a blank environment'   | "'  '"      | 'non-blank environment names'
        'with something not a String'| 'String'    | 'non-blank environment names'
    }

    def "conditionalOnGrailsEnv(...) tells two same-named beans apart, as any other condition does"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class PerEnvironmentBeans {
                def beans = {
                    bean('store', String).conditionalOnGrailsEnv('development') { 'in-memory' }
                    bean('store', String).conditionalOnGrailsEnv('production') { 'redis' }
                }
            }
        '''

        when: "both declare the same bean name under mutually exclusive conditions"
        Class<?> beans = compile(source)

        then: "the shared-name check accepts them, and both methods carry their own condition"
        beans.declaredMethods.count { it.isAnnotationPresent(ConditionalOnGrailsEnv) } == 2
    }

    def "conditionalOnBean tells two same-named beans apart, as any other condition does"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class PerBeanConditionBeans {
                def beans = {
                    bean('sender', String).conditionalOnBean(Integer) { 'with transport' }
                    bean('sender', String).conditionalOnBean(Long) { 'with other transport' }
                }
            }
        '''

        when: "both declare one bean name under mutually exclusive conditions"
        Class<?> beans = compile(source)

        then: "the shared-name check accepts them"
        beans.declaredMethods.count { it.isAnnotationPresent(ConditionalOnBean) } == 2
    }

    def "the shared-name error points at a first-class condition qualifier, not only the escape hatch"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class UnconditionedDuplicateBeans {
                def beans = {
                    bean('store', String) { 'one' }
                    bean('store', String) { 'two' }
                }
            }
        '''

        when:
        compile(source)

        then:
        MultipleCompilationErrorsException e = thrown(MultipleCompilationErrorsException)
        e.message.contains(".conditionalOnProperty(...)")
        e.message.contains('.conditionalOnGrailsEnv(...)')
    }

    def "conditionalOnProperty takes property names positionally and attributes by name"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class PropertyConditionBeans {
                def beans = {
                    bean('offlineOnly', String).conditionalOnProperty('app.offline', havingValue: 'true') { 'offline' }
                    bean('onlineOnly', String).conditionalOnProperty('app.offline', havingValue: 'false', matchIfMissing: true) { 'online' }
                    bean('bothSet', String).conditionalOnProperty('a.one', 'a.two') { 'both' }
                    bean('prefixed', String).conditionalOnProperty(prefix: 'app', name: 'offline', havingValue: 'false') { 'prefixed' }
                }
            }
        '''

        when:
        Class<?> beans = compile(source)

        then:
        beans.getDeclaredMethod('offlineOnly').getAnnotation(ConditionalOnProperty).name() == ['app.offline'] as String[]
        beans.getDeclaredMethod('offlineOnly').getAnnotation(ConditionalOnProperty).havingValue() == 'true'

        and: "matchIfMissing rides along as an ordinary attribute"
        beans.getDeclaredMethod('onlineOnly').getAnnotation(ConditionalOnProperty).matchIfMissing()

        and: "several names are all required, as the annotation defines"
        beans.getDeclaredMethod('bothSet').getAnnotation(ConditionalOnProperty).name() == ['a.one', 'a.two'] as String[]

        and: "the fully-named form still works, prefix included"
        beans.getDeclaredMethod('prefixed').getAnnotation(ConditionalOnProperty).prefix() == 'app'
        beans.getDeclaredMethod('prefixed').getAnnotation(ConditionalOnProperty).name() == ['offline'] as String[]
    }

    def "rejects conditionalOnProperty #description"() {
        given:
        String source = """
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class BadPropertyConditionBeans {
                def beans = {
                    bean('greeting', String).conditionalOnProperty($arguments) { 'hello' }
                }
            }
        """

        when:
        compile(source)

        then:
        MultipleCompilationErrorsException e = thrown(MultipleCompilationErrorsException)
        e.message.contains(message)

        where:
        description                              | arguments                 | message
        'naming no property at all'              | ''                        | 'needs at least one property name'
        'with only attributes, no name'          | "havingValue: 'true'"     | 'needs at least one property name'
        'naming properties both ways'            | "'a.one', name: 'a.two'"  | 'both positionally and via'
        'naming properties both ways via value:' | "'a.one', value: 'a.two'" | 'both positionally and via'
        'with a blank name'                      | "'  '"                    | 'non-blank property names'
        'with a name that is not a String'       | 'String'                  | 'non-blank property names'
    }

    def "conditionalOnProperty tells two same-named beans apart"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class PerPropertyBeans {
                def beans = {
                    bean('store', String).conditionalOnProperty('app.offline', havingValue: 'true') { 'memory' }
                    bean('store', String).conditionalOnProperty('app.offline', havingValue: 'false') { 'redis' }
                }
            }
        '''

        when:
        Class<?> beans = compile(source)

        then:
        beans.declaredMethods.count { it.isAnnotationPresent(ConditionalOnProperty) } == 2
    }

    def "conditionalOnExpression carries the expression through verbatim, placeholders and all"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class ExpressionConditionBeans {
                def beans = {
                    bean('fallbackTransport', String)
                            .conditionalOnExpression('!${aws.ses.enabled:false} or ${app.offline:false}') { 'logging' }
                }
            }
        '''

        when:
        def method = compile(source).getDeclaredMethod('fallbackTransport')

        then: "the placeholder syntax is Spring's, and must reach the annotation untouched"
        method.getAnnotation(ConditionalOnExpression).value() ==
                '!${aws.ses.enabled:false} or ${app.offline:false}'
    }

    def "rejects conditionalOnExpression #description"() {
        given:
        String source = """
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class BadExpressionConditionBeans {
                def beans = {
                    bean('greeting', String).conditionalOnExpression($arguments) { 'hello' }
                }
            }
        """

        when:
        compile(source)

        then:
        MultipleCompilationErrorsException e = thrown(MultipleCompilationErrorsException)
        e.message.contains('exactly one non-blank String expression')

        where:
        description             | arguments
        'with no expression'    | ''
        'with a blank one'      | "'   '"
        'with more than one'    | "'a', 'b'"
        'with something else'   | 'String'
    }

    def "conditionalOnExpression tells two same-named beans apart"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class PerExpressionBeans {
                def beans = {
                    bean('transport', String).conditionalOnExpression('${app.offline:false}') { 'logging' }
                    bean('transport', String).conditionalOnExpression('!${app.offline:false}') { 'ses' }
                }
            }
        '''

        when:
        Class<?> beans = compile(source)

        then:
        beans.declaredMethods.count { it.isAnnotationPresent(ConditionalOnExpression) } == 2
    }

    def "annotate(Scope, ...) completes the @Scope that .scope(...) attached"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration
            import org.springframework.context.annotation.Scope
            import org.springframework.context.annotation.ScopedProxyMode

            @GrailsBeans
            @AutoConfiguration
            class ScopedBeans {
                def beans = {
                    bean('cart', String).scope('session').annotate(Scope, proxyMode: ScopedProxyMode.TARGET_CLASS) { 'cart' }
                }
            }
        '''

        when:
        def method = compile(source).getDeclaredMethod('cart')

        then: "the scope the qualifier set survives"
        method.getAnnotation(Scope).value() == 'session'

        and: "and proxyMode, which no qualifier sets and a scoped bean needs, is reachable"
        method.getAnnotation(Scope).proxyMode() == ScopedProxyMode.TARGET_CLASS
    }

    def "rejects restating through annotate(...) what the qualifier already set"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration
            import org.springframework.context.annotation.Scope

            @GrailsBeans
            @AutoConfiguration
            class RestatedScopeBeans {
                def beans = {
                    bean('cart', String).scope('session').annotate(Scope, value: 'request') { 'cart' }
                }
            }
        '''

        when:
        compile(source)

        then:
        MultipleCompilationErrorsException e = thrown(MultipleCompilationErrorsException)
        e.message.contains('@Scope\'s "value" is already set here')
    }

    def "annotate(...) is still once per annotation type when it attached the annotation itself"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration
            import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty

            @GrailsBeans
            @AutoConfiguration
            class TwiceAnnotatedBeans {
                def beans = {
                    bean('x', String).annotate(ConditionalOnProperty, name: 'a')
                            .annotate(ConditionalOnProperty, havingValue: 'b') { 'x' }
                }
            }
        '''

        when: "the two calls set different attributes, so a merge would have accepted them"
        compile(source)

        then: "but .annotate(...) already takes every attribute at once, so twice is a mistake"
        MultipleCompilationErrorsException e = thrown(MultipleCompilationErrorsException)
        e.message.contains('already attached')
    }

    def "aliases(...) gives a bean additional names, the canonical one staying first"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration
            import org.springframework.context.annotation.AnnotationConfigApplicationContext

            @GrailsBeans
            @AutoConfiguration
            class AliasedBeans {
                def beans = {
                    bean('recipientResolver', String).aliases('legacyResolver', 'oldResolver') { 'resolver' }
                }
            }
        '''

        when:
        Class<?> beans = compile(source)
        def method = beans.getDeclaredMethod('recipientResolver')

        then: "Spring reads the first as the bean name and the rest as aliases"
        method.getAnnotation(Bean).value() == ['recipientResolver', 'legacyResolver', 'oldResolver'] as String[]
    }

    def "an aliased bean really is resolvable under every name"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.context.annotation.Configuration

            @GrailsBeans
            @Configuration
            class AliasResolutionBeans {
                def beans = {
                    bean('canonical', String).aliases('legacy') { 'one instance' }
                }
            }
        '''
        Class<?> config = compile(source)
        def context = new AnnotationConfigApplicationContext()
        context.register(config)
        context.refresh()

        expect: "both names reach it, and reach the same singleton"
        context.getBean('canonical') == 'one instance'
        context.getBean('legacy') == 'one instance'
        context.getBean('canonical').is(context.getBean('legacy'))

        cleanup:
        context.close()
    }

    def "rejects aliases(...) #description"() {
        given:
        String source = """
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class BadAliasBeans {
                def beans = {
                    bean('greeting', String).aliases($arguments) { 'hello' }
                }
            }
        """

        when:
        compile(source)

        then:
        MultipleCompilationErrorsException e = thrown(MultipleCompilationErrorsException)
        e.message.contains(message)

        where:
        description                        | arguments               | message
        'with no name'                     | ''                      | 'requires at least one additional name'
        'with a blank name'                | "'  '"                  | 'non-blank names'
        'with something that is not a name'| 'String'                | 'non-blank names'
        "with the bean's own name"         | "'greeting'"            | 'own name, so it is not an alias'
        'with the same alias twice'        | "'other', 'other'"      | 'given as an alias twice'
    }

    def "aliases(...) is not valid on a field(...) or method(...)"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class AliasedHelperBeans {
                def beans = {
                    method('helper', String).aliases('other') { 'hello' }
                }
            }
        '''

        when:
        compile(source)

        then:
        thrown(MultipleCompilationErrorsException)
    }

    def "scope(...) takes the annotation's other attributes by name"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration
            import org.springframework.context.annotation.ScopedProxyMode

            @GrailsBeans
            @AutoConfiguration
            class ScopeAttributeBeans {
                def beans = {
                    bean('cart', String).scope('session', proxyMode: ScopedProxyMode.TARGET_CLASS) { 'cart' }
                    bean('plain', String).scope('prototype') { 'plain' }
                    bean('named', String).scope(scopeName: 'request') { 'named' }
                }
            }
        '''

        when:
        Class<?> beans = compile(source)

        then: "one call now says what a scoped bean needs to work"
        beans.getDeclaredMethod('cart').getAnnotation(Scope).value() == 'session'
        beans.getDeclaredMethod('cart').getAnnotation(Scope).proxyMode() == ScopedProxyMode.TARGET_CLASS

        and: "the bare positional form is unchanged"
        beans.getDeclaredMethod('plain').getAnnotation(Scope).value() == 'prototype'
        beans.getDeclaredMethod('plain').getAnnotation(Scope).proxyMode() == ScopedProxyMode.DEFAULT

        and: "and the scope may be named instead, as the annotation allows"
        beans.getDeclaredMethod('named').getAnnotation(Scope).scopeName() == 'request'
    }

    def "rejects scope(...) #description"() {
        given:
        String source = """
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration
            import org.springframework.context.annotation.ScopedProxyMode

            @GrailsBeans
            @AutoConfiguration
            class BadScopeBeans {
                def beans = {
                    bean('greeting', String).scope($arguments) { 'hello' }
                }
            }
        """

        when:
        compile(source)

        then:
        MultipleCompilationErrorsException e = thrown(MultipleCompilationErrorsException)
        e.message.contains(message)

        where:
        description                       | arguments                              | message
        'naming no scope at all'          | ''                                     | 'needs a scope name'
        'with only attributes, no scope'  | 'proxyMode: ScopedProxyMode.TARGET_CLASS' | 'needs a scope name'
        'naming the scope both ways'      | "'session', value: 'request'"          | 'both positionally and via'
        'naming the scope both ways via scopeName:' | "'session', scopeName: 'request'" | 'both positionally and via'
        'with more than one scope name'   | "'session', 'request'"                 | 'takes one scope name'
        'with an empty scope name'        | "''"                                   | 'non-empty String scope name'
    }

    def "a bean body may construct an anonymous inner class"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class AnonymousInnerBeans {
                def beans = {
                    bean('greeter', Greeter) {
                        new Greeter() {
                            String greet() { 'hello' }
                        }
                    }
                }
            }

            interface Greeter { String greet() }
        '''

        when:
        Class<?> beans = compile(source)
        def instance = beans.getDeclaredConstructor().newInstance()

        then: "the lifted body still constructs it against the generated method, not the closure"
        instance.greeter().greet() == 'hello'
    }

    def "an anonymous subclass in a bean body keeps working, overrides and all"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class AnonymousSubclassBeans {
                def beans = {
                    bean('greeter', Greeter) {
                        new Greeter() {
                            @Override
                            String greet() { 'overridden ' + super.greet() }
                        }
                    }
                }
            }

            class Greeter { String greet() { 'base' } }
        '''

        when:
        Class<?> beans = compile(source)
        def instance = beans.getDeclaredConstructor().newInstance()

        then:
        instance.greeter().greet() == 'overridden base'
    }

    def "a method(...) helper body may construct an anonymous inner class too"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class AnonymousInHelperBeans {
                def beans = {
                    method('build', Greeter) {
                        new Greeter() {
                            String greet() { 'from helper' }
                        }
                    }

                    bean('greeting', String) { build().greet() }
                }
            }

            interface Greeter { String greet() }
        '''

        when:
        Class<?> beans = compile(source)

        then:
        beans.getDeclaredConstructor().newInstance().greeting() == 'from helper'
    }

    def "rejects an anonymous inner class in a staticMethod() bean, which has no enclosing instance"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration
            import org.springframework.beans.factory.config.BeanFactoryPostProcessor
            import org.springframework.beans.factory.config.ConfigurableListableBeanFactory

            @GrailsBeans
            @AutoConfiguration
            class StaticAnonymousBeans {
                def beans = {
                    bean('pp', BeanFactoryPostProcessor).staticMethod() {
                        new BeanFactoryPostProcessor() {
                            void postProcessBeanFactory(ConfigurableListableBeanFactory f) { }
                        }
                    }
                }
            }
        '''

        when:
        compile(source)

        then:
        MultipleCompilationErrorsException e = thrown(MultipleCompilationErrorsException)
        e.message.contains('needs an enclosing instance the static method has not got')
    }

    def "a bean body may coerce a closure to a functional interface under @CompileStatic"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import groovy.transform.CompileStatic
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @CompileStatic
            @AutoConfiguration
            class SamCoercionBeans {
                def beans = {
                    bean('greeter', Greeter).typeArguments(String) {
                        BaseGreeter delegate = new BaseGreeter()
                        return { String name -> delegate.greet(name).toUpperCase() } as Greeter<String>
                    }
                }
            }

            interface Greeter<T> { String greet(T name) }
            class BaseGreeter { String greet(String name) { "hello $name" } }
        '''

        when:
        Class<?> beans = compile(source)
        def greeter = beans.getDeclaredConstructor().newInstance().greeter()

        then: "no anonymous inner class is involved, so nothing needs re-homing"
        greeter.greet('world') == 'HELLO WORLD'

        and: "and the declared type still carries its type argument"
        beans.getDeclaredMethod('greeter').genericReturnType.toString().contains('Greeter<java.lang.String>')
    }

    def "Groovy itself statically compiles an anonymous inner class in an ordinary method"() {
        given: "no beans DSL anywhere - this is the control for the lifted-body case"
        String source = '''
            import groovy.transform.CompileStatic

            @CompileStatic
            class PlainStaticHost {
                Greeter make() {
                    new Greeter() {
                        String greet() { 'hello' }
                    }
                }
            }

            interface Greeter { String greet() }
        '''

        when:
        Class<?> host = compile(source)

        then: "it compiles and runs, so @CompileStatic is not the limitation"
        host.getDeclaredConstructor().newInstance().make().greet() == 'hello'
    }

    def "an anonymous inner class works under @CompileStatic too"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import groovy.transform.CompileStatic
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @CompileStatic
            @AutoConfiguration
            class StaticAnonymousInnerBeans {
                def beans = {
                    bean('greeter', Greeter) {
                        new Greeter() {
                            String greet() { 'hello' }
                        }
                    }
                }
            }

            interface Greeter { String greet() }
        '''

        when:
        Class<?> beans = compile(source)

        then:
        beans.getDeclaredConstructor().newInstance().greeter().greet() == 'hello'
    }

    def "an empty beans block on a plain configuration class is a no-op"() {
        given:
        String source = '''
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            class EmptyBeansConfiguration {
                def beans = { }
            }
        '''

        when:
        Class<?> configuration = compile(source)

        then: "it compiles, contributes no @Bean methods, and keeps its own annotations"
        configuration.declaredMethods.every { !it.isAnnotationPresent(Bean) }
        configuration.annotations*.annotationType()*.simpleName.contains('AutoConfiguration')
        !configuration.declaredFields*.name.contains('beans')
    }
}
