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
import org.springframework.beans.factory.support.RootBeanDefinition
import org.springframework.context.aot.ApplicationContextAotGenerator
import org.springframework.context.support.GenericApplicationContext
import org.springframework.javapoet.ClassName
import spock.lang.Specification

/**
 * Covers the code generated for each bean definition marking the definition, naming the type whose members
 * that code injects. What the marks are for is covered by {@code GeneratedArtifactsInjectionSpec}, which runs
 * the generated code.
 */
class GeneratedInjectionBeanRegistrationAotProcessorSpec extends Specification {

    GenericApplicationContext context = new GenericApplicationContext()

    void cleanup() {
        context.close()
    }

    /** The generated source for the context. */
    private String generatedSource() {
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

    void 'a definition is marked with the class of its bean'() {
        given:
            context.registerBeanDefinition('subject', new RootBeanDefinition(ProductFixture))

        expect:
            generatedSource().contains(markedAs(ProductFixture))
    }

    void 'the definition of a factory method product is marked with the type the method declares'() {
        given:
            context.registerBeanDefinition('factory', new RootBeanDefinition(FactoryFixture))
            context.registerBeanDefinition('product', new RootBeanDefinition().tap {
                factoryBeanName = 'factory'
                factoryMethodName = 'product'
            })

        when:
            String generated = generatedSource()

        then: 'that type is the one the generated code injects through, not the class of what the method returns'
            generated.contains(markedAs(ProductType))
            !generated.contains(markedAs(ProductFixture))
    }

    void 'an inner bean is marked too'() {
        given:
            context.registerBeanDefinition('outer', new RootBeanDefinition(OuterFixture).tap {
                propertyValues.add('nested', new RootBeanDefinition(ProductFixture))
            })

        when:
            String generated = generatedSource()

        then: 'it is created by code generated for it, which injects it'
            generated.contains(markedAs(OuterFixture))
            generated.contains(markedAs(ProductFixture))
    }

    private static String markedAs(Class<?> declaredType) {
        "GeneratedBeanDefinitions.markGenerated(beanDefinition, \"${declaredType.name}\")"
    }

    static interface ProductType {
    }

    static class ProductFixture implements ProductType {
    }

    static class FactoryFixture {

        ProductType product() {
            new ProductFixture()
        }
    }

    static class OuterFixture {

        ProductFixture nested
    }
}
