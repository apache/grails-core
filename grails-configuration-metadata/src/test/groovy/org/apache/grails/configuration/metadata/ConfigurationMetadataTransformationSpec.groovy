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
package org.apache.grails.configuration.metadata

import groovy.json.JsonSlurper
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.ConstructorNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.stmt.EmptyStatement
import org.codehaus.groovy.classgen.GeneratorContext
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.control.customizers.CompilationCustomizer
import org.codehaus.groovy.control.messages.WarningMessage
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty
import spock.lang.Specification
import spock.lang.Unroll

import java.lang.reflect.Field
import java.lang.reflect.Modifier

class ConfigurationMetadataTransformationSpec extends Specification {

    private static final int SYNTHETIC = 0x00001000

    def "global transform emits actual properties and nested groups with only constant defaults"() {
        given:
        GroovyClassLoader loader = new GroovyClassLoader(getClass().classLoader)

        when:
        Class<?> configuration = loader.parseClass('''
            package example

            import org.springframework.boot.context.properties.ConfigurationProperties

            @ConfigurationProperties('sample.service')
            class SampleConfiguration {
                String displayName = 'Grails'
                int port = 8080
                List<String> labels = ['one']
                final String immutableValue
                Nested nested = new Nested()
                String dynamicValue = UUID.randomUUID().toString()
                private String internalSecret = 'secret'

                SampleConfiguration(String immutableValue) {
                    this.immutableValue = immutableValue
                }

                static class Nested {
                    boolean enabled = true
                }
            }
        ''')
        Field payloadField = configuration.getDeclaredField(ConfigurationMetadataTransformation.PAYLOAD_FIELD)
        payloadField.accessible = true
        Map payload = new JsonSlurper().parseText(payloadField.get(null) as String) as Map

        then:
        Modifier.isPrivate(payloadField.modifiers)
        Modifier.isStatic(payloadField.modifiers)
        Modifier.isFinal(payloadField.modifiers)
        payloadField.synthetic
        payload.prefix == 'sample.service'
        payload.sourceType == 'example.SampleConfiguration'
        payload.get('groups') == [[
                name: 'sample.service.nested',
                sourceType: 'example.SampleConfiguration',
                type: 'example.SampleConfiguration$Nested'
        ]]
        payload.get('properties')*.name == [
                'sample.service.displayName',
                'sample.service.dynamicValue',
                'sample.service.immutableValue',
                'sample.service.labels',
                'sample.service.nested.enabled',
                'sample.service.port'
        ]
        !payload.get('properties')*.name.contains('sample.service.internalSecret')
        payload.get('properties').find { it.name == 'sample.service.displayName' }.defaultValue == 'Grails'
        payload.get('properties').find { it.name == 'sample.service.port' }.defaultValue == 8080
        payload.get('properties').find { it.name == 'sample.service.nested.enabled' }.defaultValue
        payload.get('properties').find { it.name == 'sample.service.labels' }.type == 'java.util.List<java.lang.String>'
        !payload.get('properties').find { it.name == 'sample.service.dynamicValue' }.containsKey('defaultValue')
        !payload.get('properties').find { it.name == 'sample.service.immutableValue' }.containsKey('defaultValue')
        !payload.get('properties').find { it.name == 'sample.service.labels' }.containsKey('defaultValue')

        cleanup:
        loader.close()
    }

    def "global transform rejects a user property that collides with its metadata payload"() {
        given:
        GroovyClassLoader loader = new GroovyClassLoader(getClass().classLoader)

        when:
        loader.parseClass('''
            import org.springframework.boot.context.properties.ConfigurationProperties

            @ConfigurationProperties('sample')
            class CollidingConfiguration {
                String __grailsConfigurationMetadata
            }
        ''')

        then:
        MultipleCompilationErrorsException error = thrown()
        error.message.contains("reserved field '__grailsConfigurationMetadata'")

        cleanup:
        loader.close()
    }

    def "global transform excludes delegated and framework setters while collecting inherited setters"() {
        given:
        GroovyClassLoader loader = new GroovyClassLoader(getClass().classLoader)

        when:
        loader.parseClass('''
            package example

            import groovy.lang.Delegate
            import org.springframework.boot.context.properties.ConfigurationProperties

            trait ConfigurationTrait {
                void setFromTrait(String fromTrait) {
                }
            }

            interface ConfigurationContract {
                void setFromInterface(String fromInterface)
            }

            class ConfigurationParent {
                void setFromParent(String fromParent) {
                }
            }

            @ConfigurationProperties('sample.exclusions')
            abstract class ExclusionConfiguration extends ConfigurationParent implements ConfigurationTrait, ConfigurationContract {
                @Delegate URI endpoint
                String included

                void setGrailsApplication(Object grailsApplication) {
                }

                void setMetaClass(MetaClass metaClass) {
                }
            }
        ''')
        Map payload = payloadFor(loader.loadClass('example.ExclusionConfiguration'))

        then:
        payload.get('properties')*.name == [
                'sample.exclusions.fromInterface',
                'sample.exclusions.fromParent',
                'sample.exclusions.fromTrait',
                'sample.exclusions.included'
        ]
        !payload.get('properties')*.name.any { it in [
                'sample.exclusions.endpoint',
                'sample.exclusions.grailsApplication',
                'sample.exclusions.metaClass'
        ] }

        cleanup:
        loader.close()
    }

    @Unroll
    def "global transform warns about #visibility #delegateType fields before Delegate composition"() {
        given:
        List<WarningMessage> warnings = []
        CompilerConfiguration compilerConfiguration = new CompilerConfiguration()
        compilerConfiguration.addCompilationCustomizers(new CompilationCustomizer(CompilePhase.CANONICALIZATION) {
            @Override
            void call(SourceUnit source, GeneratorContext context, ClassNode classNode) {
                warnings.addAll(source.errorCollector.warnings.findAll { it instanceof WarningMessage } as List<WarningMessage>)
            }
        })
        GroovyClassLoader loader = new GroovyClassLoader(getClass().classLoader, compilerConfiguration)
        if (delegateType == 'groovy.transform.Delegate') {
            loader.parseClass('''
                package groovy.transform

                @interface Delegate {
                }
            ''')
        }

        when:
        Class<?> configuration = loader.parseClass("""
            package example

            import ${delegateType}
            import org.springframework.boot.context.properties.ConfigurationProperties

            @ConfigurationProperties('sample.delegate')
            class DelegatingConfiguration {
                ${visibility}@Delegate URI endpoint
            }
        """)
        Map payload = payloadFor(configuration)

        then:
        WarningMessage warning = warnings.find { it.message.contains('DelegatingConfiguration.endpoint') &&
                it.message.contains('SEMANTIC_ANALYSIS') &&
                it.message.contains('additional-spring-configuration-metadata.json') }
        warning
        warning.context.startLine == 9
        !payload.get('properties')*.name.contains('sample.delegate.endpoint')

        cleanup:
        loader.close()

        where:
        delegateType                     | visibility
        'groovy.lang.Delegate'           | ''
        'groovy.transform.Delegate'      | ''
        'groovy.lang.Delegate'           | 'private '
        'groovy.transform.Delegate'      | 'private '
        'groovy.lang.Delegate'           | 'protected '
        'groovy.transform.Delegate'      | 'protected '
    }

    def "global transform only recurses into inner and explicitly nested configuration properties"() {
        given:
        GroovyClassLoader loader = new GroovyClassLoader(getClass().classLoader)

        when:
        loader.parseClass('''
            package example

            import org.springframework.boot.context.properties.ConfigurationProperties
            import org.springframework.boot.context.properties.NestedConfigurationProperty

            class ConfigurationParent {
                void setInherited(String inherited) {
                }
            }

            class ForeignBean {
                String leaked
            }

            class ExplicitNested {
                String explicitValue
            }

            @ConfigurationProperties('sample.nesting')
            class InferenceConfiguration extends ConfigurationParent {
                Inner inner = new Inner()
                @NestedConfigurationProperty ExplicitNested explicitNested = new ExplicitNested()
                ForeignBean foreignBean = new ForeignBean()

                static class Inner {
                    String value
                    Inner child
                }
            }
        ''')
        Map payload = payloadFor(loader.loadClass('example.InferenceConfiguration'))

        then:
        payload.get('groups')*.name == [
                'sample.nesting.explicitNested',
                'sample.nesting.inner'
        ]
        payload.get('properties')*.name == [
                'sample.nesting.explicitNested.explicitValue',
                'sample.nesting.foreignBean',
                'sample.nesting.inherited',
                'sample.nesting.inner.child',
                'sample.nesting.inner.value'
        ]
        !payload.get('properties')*.name.any { it.startsWith('sample.nesting.foreignBean.') }

        cleanup:
        loader.close()
    }

    def "global transform recognizes inherited nested configuration properties annotated on JavaBean getters"() {
        given:
        GroovyClassLoader loader = new GroovyClassLoader(getClass().classLoader)

        when:
        loader.parseClass('''
            package example

            import org.springframework.boot.context.properties.ConfigurationProperties
            import org.springframework.boot.context.properties.NestedConfigurationProperty

            class AccessorNested {
                String value
            }

            class AccessorParent {
                @NestedConfigurationProperty
                AccessorNested getNested() {
                    null
                }

                void setNested(AccessorNested nested) {
                }
            }

            @ConfigurationProperties('sample.accessor')
            class AccessorConfiguration extends AccessorParent {
            }
        ''')
        Map payload = payloadFor(loader.loadClass('example.AccessorConfiguration'))

        then:
        payload.get('groups') == [[
                name: 'sample.accessor.nested',
                sourceType: 'example.AccessorConfiguration',
                type: 'example.AccessorNested'
        ]]
        payload.get('properties')*.name == ['sample.accessor.nested.value']

        cleanup:
        loader.close()
    }

    def "global transform merges compatible nested annotations from field-backed and getter-only JavaBean accessors"() {
        given:
        GroovyClassLoader loader = new GroovyClassLoader(getClass().classLoader)

        when:
        loader.parseClass('''
            package example

            import org.springframework.boot.context.properties.ConfigurationProperties
            import org.springframework.boot.context.properties.NestedConfigurationProperty

            class AccessorNested {
                String value
            }

            @ConfigurationProperties('sample.accessor')
            class AccessorConfiguration {
                AccessorNested fieldBacked = new AccessorNested()
                private final AccessorNested getterOnly = new AccessorNested()

                @NestedConfigurationProperty
                AccessorNested getFieldBacked() {
                    fieldBacked
                }

                @NestedConfigurationProperty
                AccessorNested getGetterOnly() {
                    getterOnly
                }

                @NestedConfigurationProperty
                AccessorNested getIncompatible() {
                    null
                }

                void setIncompatible(String incompatible) {
                }
            }
        ''')
        Map payload = payloadFor(loader.loadClass('example.AccessorConfiguration'))

        then:
        payload.get('groups')*.name == [
                'sample.accessor.fieldBacked',
                'sample.accessor.getterOnly'
        ]
        payload.get('properties')*.name == [
                'sample.accessor.fieldBacked.value',
                'sample.accessor.getterOnly.value',
                'sample.accessor.incompatible'
        ]
        !payload.get('properties')*.name.any { it.startsWith('sample.accessor.incompatible.') }

        cleanup:
        loader.close()
    }

    def "global transform selects only supported constructor binding candidates"() {
        given:
        GroovyClassLoader loader = new GroovyClassLoader(getClass().classLoader)

        when:
        loader.parseClass('''
            package example

            import org.springframework.boot.context.properties.ConfigurationProperties
            import org.springframework.boot.context.properties.bind.ConstructorBinding

            @ConfigurationProperties('sample.annotated')
            class AnnotatedConstructorConfiguration {
                final String annotated
                final String unannotated

                AnnotatedConstructorConfiguration(String unannotated) {
                    this.annotated = null
                    this.unannotated = unannotated
                }

                @ConstructorBinding
                AnnotatedConstructorConfiguration(String annotated, int ignored) {
                    this.annotated = annotated
                    this.unannotated = null
                }
            }

            @ConfigurationProperties('sample.fallback')
            class FallbackConstructorConfiguration {
                final String fallback

                FallbackConstructorConfiguration(String fallback) {
                    this.fallback = fallback
                }
            }

            @ConfigurationProperties('sample.filtered')
            class FilteredConstructorConfiguration {
                final String selected
                final String privateValue

                private FilteredConstructorConfiguration(String privateValue) {
                    this.selected = null
                    this.privateValue = privateValue
                }

                FilteredConstructorConfiguration(String selected, int ignored = 0) {
                    this.selected = selected
                    this.privateValue = null
                }
            }

            class ExternalOptions {
                String host
            }

            enum Mode {
                DEFAULT
            }

            @ConfigurationProperties('sample.options')
            class ConstructorOptionsConfiguration {
                final ExternalOptions options
                final String text
                final int port
                final Mode mode
                final Class target
                final List<String> labels
                final Map<String, String> values

                ConstructorOptionsConfiguration(ExternalOptions options, String text, int port, Mode mode,
                                                Class target, List<String> labels, Map<String, String> values) {
                    this.options = options
                    this.text = text
                    this.port = port
                    this.mode = mode
                    this.target = target
                    this.labels = labels
                    this.values = values
                }
            }

        ''')
        Map annotated = payloadFor(loader.loadClass('example.AnnotatedConstructorConfiguration'))
        Map fallback = payloadFor(loader.loadClass('example.FallbackConstructorConfiguration'))
        Class<?> filteredClass = loader.loadClass('example.FilteredConstructorConfiguration')
        Map filtered = payloadFor(filteredClass)
        Map options = payloadFor(loader.loadClass('example.ConstructorOptionsConfiguration'))

        then:
        annotated.get('properties')*.name == ['sample.annotated.annotated']
        fallback.get('properties')*.name == ['sample.fallback.fallback']
        filteredClass.declaredConstructors.any { Modifier.isPrivate(it.modifiers) }
        filtered.get('properties')*.name == ['sample.filtered.selected']
        !filtered.get('properties')*.name.contains('sample.filtered.privateValue')
        options.get('groups')*.name == ['sample.options.options']
        options.get('properties')*.name == [
                'sample.options.labels',
                'sample.options.mode',
                'sample.options.options.host',
                'sample.options.port',
                'sample.options.target',
                'sample.options.text',
                'sample.options.values'
        ]

        cleanup:
        loader.close()
    }

    def "global transform ignores synthetic constructors added before semantic analysis"() {
        given:
        CompilerConfiguration compilerConfiguration = new CompilerConfiguration()
        compilerConfiguration.addCompilationCustomizers(new CompilationCustomizer(CompilePhase.CONVERSION) {
            @Override
            void call(SourceUnit source, GeneratorContext context, ClassNode classNode) {
                if (classNode.name == 'example.SyntheticConstructorConfiguration') {
                    ConstructorNode constructor = new ConstructorNode(
                            Modifier.PUBLIC | SYNTHETIC,
                            [new Parameter(ClassHelper.STRING_TYPE, 'syntheticValue')] as Parameter[],
                            ClassNode.EMPTY_ARRAY,
                            EmptyStatement.INSTANCE)
                    constructor.synthetic = true
                    classNode.addConstructor(constructor)
                }
            }
        })
        GroovyClassLoader loader = new GroovyClassLoader(getClass().classLoader, compilerConfiguration)

        when:
        Class<?> configuration = loader.parseClass('''
            package example

            import org.springframework.boot.context.properties.ConfigurationProperties

            @ConfigurationProperties('sample.synthetic')
            class SyntheticConstructorConfiguration {
                final String syntheticValue = null
            }
        ''')
        Map payload = payloadFor(configuration)

        then:
        configuration.declaredConstructors.size() == 1
        configuration.declaredConstructors[0].synthetic
        configuration.declaredConstructors[0].parameterCount == 1
        payload.get('properties') == []

        cleanup:
        loader.close()
    }

    def "global transform defers a non-ConstantExpression prefix to bytecode scanning"() {
        given:
        GroovyClassLoader loader = new GroovyClassLoader(getClass().classLoader)

        when:
        loader.parseClass('''
            package example

            import org.springframework.boot.context.properties.ConfigurationProperties

            @ConfigurationProperties(prefix = (String) 'sample.deferred')
            class DynamicPrefixConfiguration {
                String value
            }
        ''')
        Class<?> configuration = loader.loadClass('example.DynamicPrefixConfiguration')

        then:
        configuration.getAnnotation(ConfigurationProperties).prefix() == 'sample.deferred'
        !configuration.declaredFields*.name.contains(ConfigurationMetadataTransformation.PAYLOAD_FIELD)

        cleanup:
        loader.close()
    }

    def "global transform skips annotated interfaces"() {
        given:
        GroovyClassLoader loader = new GroovyClassLoader(getClass().classLoader)

        when:
        loader.parseClass('''
            package example

            import org.springframework.boot.context.properties.ConfigurationProperties

            @ConfigurationProperties('sample.interface')
            interface IgnoredConfiguration {
                void setValue(String value)
            }
        ''')
        Class<?> ignored = loader.loadClass('example.IgnoredConfiguration')

        then:
        !ignored.declaredFields*.name.contains(ConfigurationMetadataTransformation.PAYLOAD_FIELD)

        cleanup:
        loader.close()
    }

    def "global transform renders generic types and JSON-escapes constant defaults"() {
        given:
        GroovyClassLoader loader = new GroovyClassLoader(getClass().classLoader)
        String expected = 'quote" slash\\ newline\n control\u0001'

        when:
        loader.parseClass('''
            package example

            import org.springframework.boot.context.properties.ConfigurationProperties

            @ConfigurationProperties('sample.types')
            class TypeConfiguration<T> {
                String[] names
                List<? extends Number> extendsNumbers
                List<? super Integer> superNumbers
                List<?> unknowns
                List<T> values
                String escaped = 'quote" slash\\\\ newline\\n control\\u0001'
            }
        ''')
        Map payload = payloadFor(loader.loadClass('example.TypeConfiguration'))

        then:
        payload.get('properties').find { it.name == 'sample.types.names' }.type == 'java.lang.String[]'
        payload.get('properties').find { it.name == 'sample.types.extendsNumbers' }.type ==
                'java.util.List<? extends java.lang.Number>'
        payload.get('properties').find { it.name == 'sample.types.superNumbers' }.type ==
                'java.util.List<? super java.lang.Integer>'
        payload.get('properties').find { it.name == 'sample.types.unknowns' }.type == 'java.util.List<?>'
        payload.get('properties').find { it.name == 'sample.types.values' }.type == 'java.util.List<T>'
        payload.get('properties').find { it.name == 'sample.types.escaped' }.defaultValue == expected

        cleanup:
        loader.close()
    }

    def "global transform emits unprefixed names when configuration properties has no prefix"() {
        given:
        GroovyClassLoader loader = new GroovyClassLoader(getClass().classLoader)

        when:
        Class<?> configuration = loader.parseClass('''
            package example

            import org.springframework.boot.context.properties.ConfigurationProperties

            @ConfigurationProperties
            class UnprefixedConfiguration {
                String value
            }
        ''')
        Map payload = payloadFor(configuration)

        then:
        payload.prefix == ''
        payload.get('properties')*.name == ['value']

        cleanup:
        loader.close()
    }

    private static Map payloadFor(Class<?> configuration) {
        Field payloadField = configuration.getDeclaredField(ConfigurationMetadataTransformation.PAYLOAD_FIELD)
        payloadField.accessible = true
        new JsonSlurper().parseText(payloadField.get(null) as String) as Map
    }
}
