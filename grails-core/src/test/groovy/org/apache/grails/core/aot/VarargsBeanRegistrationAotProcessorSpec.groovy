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
package org.apache.grails.core.aot

import org.springframework.aot.generate.ClassNameGenerator
import org.springframework.aot.generate.DefaultGenerationContext
import org.springframework.aot.generate.GeneratedFiles
import org.springframework.aot.generate.InMemoryGeneratedFiles
import org.springframework.beans.factory.config.RuntimeBeanReference
import org.springframework.beans.factory.support.AbstractBeanDefinition
import org.springframework.beans.factory.support.RootBeanDefinition
import org.springframework.context.aot.ApplicationContextAotGenerator
import org.springframework.context.support.GenericApplicationContext
import org.springframework.javapoet.ClassName
import spock.lang.Specification

/**
 * Covers a variable-argument constructor argument being gathered into the array it feeds.
 *
 * <p>Spring adapts the argument when it builds the bean, but not when it reads the definition to
 * generate code for it: it looks the argument up by the parameter's type, misses a value that is not
 * already the array, and resolves it as a dependency instead -- which for an array type is an empty
 * array. The bean is then built with nothing where its arguments should be, and says so much later.</p>
 *
 * <p>These read the code that is actually generated, because the failure this guards against is one
 * where every bean is still registered and still built.</p>
 */
class VarargsBeanRegistrationAotProcessorSpec extends Specification {

    GenericApplicationContext context = new GenericApplicationContext()

    void cleanup() {
        context.close()
    }

    /** The generated source for a context holding one bean with the given constructor arguments. */
    private String generatedSourceFor(Class<?> beanClass, List<Object> arguments,
                                      int autowireMode = AbstractBeanDefinition.AUTOWIRE_NO) {
        RootBeanDefinition definition = new RootBeanDefinition(beanClass)
        definition.autowireMode = autowireMode
        arguments.each { definition.constructorArgumentValues.addGenericArgumentValue(it) }
        context.registerBeanDefinition('subject', definition)

        InMemoryGeneratedFiles generatedFiles = new InMemoryGeneratedFiles()
        DefaultGenerationContext generationContext = new DefaultGenerationContext(
                new ClassNameGenerator(ClassName.get('com.example', 'Subject')), generatedFiles)
        new ApplicationContextAotGenerator().processAheadOfTime(context, generationContext)
        generationContext.writeGeneratedContent()

        generatedFiles.getGeneratedFiles(GeneratedFiles.Kind.SOURCE)
                .keySet()
                .collect { generatedFiles.getGeneratedFileContent(GeneratedFiles.Kind.SOURCE, it) }
                .join('\n')
    }

    void 'a lone value is gathered into the array its parameter takes'() {
        when:
            String generated = generatedSourceFor(Registration, ['*.gsp'])

        then: 'a bare "*.gsp" does not answer to String[], so the generated lookup would miss it'
            generated.contains('new String[] {"*.gsp"}')
    }

    void 'a collection is gathered into the array of its element type'() {
        when:
            String generated = generatedSourceFor(Mapped, [[String, Integer]])

        then:
            generated.contains('new Class[] {String.class, Integer.class}')
    }

    void 'an argument that is already the array is left as it is'() {
        when:
            String generated = generatedSourceFor(Registration, [['*.gsp', '*.jsp'] as String[]])

        then:
            generated.contains('new String[] {"*.gsp", "*.jsp"}')
    }

    void 'a fixed constructor is untouched'() {
        when:
            String generated = generatedSourceFor(Fixed, ['one', 'two'])

        then:
            generated.contains('addGenericArgumentValue("one")')
            generated.contains('addGenericArgumentValue("two")')
            !generated.contains('new String[]')
    }

    void 'a reference is left for the context to resolve'() {
        given:
            context.registerBeanDefinition('elsewhere', new RootBeanDefinition(String))

        when:
            String generated = generatedSourceFor(Registration, [new RuntimeBeanReference('elsewhere')])

        then: 'what it refers to is not known until the context runs, so it cannot be gathered here'
            generated.contains('RuntimeBeanReference("elsewhere")')
            !generated.contains('new String[]')
    }

    void 'an argument whose elements would need converting is left alone'() {
        when: 'a String where the array takes Class'
            String generated = generatedSourceFor(Mapped, ['java.lang.String'])

        then: 'gathering it would turn an argument that is missed into one that is wrong'
            generated.contains('addGenericArgumentValue("java.lang.String")')
            !generated.contains('new Class[]')
    }

    void 'a bean that is also autowired keeps both contributions'() {
        when: 'two processors decorate the same generated properties, one after the other'
            String generated = generatedSourceFor(Registration, ['*.gsp'],
                    AbstractBeanDefinition.AUTOWIRE_BY_NAME)

        then: 'neither displaces the other'
            generated.contains('new String[] {"*.gsp"}')
            generated.contains("setAutowireMode(${AbstractBeanDefinition.AUTOWIRE_BY_NAME})")
    }

    void 'a bean built with no arguments is untouched'() {
        when:
            String generated = generatedSourceFor(Registration, [])

        then:
            !generated.contains('new String[]')
    }

    static class Registration {

        Registration(String... urlMappings) {
        }
    }

    static class Mapped {

        Mapped(Class... classes) {
        }
    }

    static class Fixed {

        Fixed(String one, String two) {
        }
    }
}
