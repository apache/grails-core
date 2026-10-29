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

import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy

import org.springframework.aot.AotDetector
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.beans.factory.support.RootBeanDefinition
import org.springframework.context.annotation.AnnotationConfigUtils
import spock.lang.Specification

import org.apache.grails.core.aot.GeneratedBeanDefinitions

/**
 * Covers the processors that read the injection annotations being restored when running on
 * artifacts generated ahead of time.
 *
 * <p>The generator resolves the annotations itself for a bean whose implementation it can see, but a
 * bean contributed as an interface built by a supplier hides that implementation, and the annotated
 * members on it are then injected by nobody.</p>
 */
class GrailsApplicationPostProcessorAnnotationConfigSpec extends Specification {

    private static final String AOT_KEY = 'spring.aot.enabled'

    BeanDefinitionRegistry registry = new DefaultListableBeanFactory()

    /** Restores the property, so the environment other specs observe is unchanged. */
    private void withGeneratedArtifacts(boolean enabled, Closure body) {
        String previous = System.getProperty(AOT_KEY)
        try {
            enabled ? System.setProperty(AOT_KEY, 'true') : System.clearProperty(AOT_KEY)
            body.call()
        }
        finally {
            previous == null ? System.clearProperty(AOT_KEY) : System.setProperty(AOT_KEY, previous)
        }
    }

    private void register() {
        GrailsApplicationPostProcessor.registerAnnotationConfigProcessorsForGeneratedArtifacts(registry)
    }

    void 'the injection processors are registered when running on generated artifacts'() {
        when:
            withGeneratedArtifacts(true) { register() }

        then:
            registry.containsBeanDefinition(AnnotationConfigUtils.AUTOWIRED_ANNOTATION_PROCESSOR_BEAN_NAME)
            registry.containsBeanDefinition(AnnotationConfigUtils.COMMON_ANNOTATION_PROCESSOR_BEAN_NAME)
    }

    void 'the injection processors are created without reflection'() {
        when:
            withGeneratedArtifacts(true) { register() }

        then: 'they are registered while running on generated artifacts, which includes a native image'
            [AnnotationConfigUtils.AUTOWIRED_ANNOTATION_PROCESSOR_BEAN_NAME, AnnotationConfigUtils.COMMON_ANNOTATION_PROCESSOR_BEAN_NAME].every {
                ((RootBeanDefinition) registry.getBeanDefinition(it)).instanceSupplier != null
            }
    }

    void 'the processor that reads configuration classes is not'() {
        when:
            withGeneratedArtifacts(true) { register() }

        then: 'reading them again in a context whose configuration is already generated makes a ' +
                'second definition for beans the generated code has contributed'
            !registry.containsBeanDefinition(AnnotationConfigUtils.CONFIGURATION_ANNOTATION_PROCESSOR_BEAN_NAME)
    }

    void 'nothing is registered without generated artifacts'() {
        when:
            withGeneratedArtifacts(false) { register() }

        then:
            !registry.containsBeanDefinition(AnnotationConfigUtils.AUTOWIRED_ANNOTATION_PROCESSOR_BEAN_NAME)
            !registry.containsBeanDefinition(AnnotationConfigUtils.COMMON_ANNOTATION_PROCESSOR_BEAN_NAME)
    }

    void 'a context that already has them keeps what it has'() {
        given:
            RootBeanDefinition existing = new RootBeanDefinition(String)
            registry.registerBeanDefinition(
                    AnnotationConfigUtils.AUTOWIRED_ANNOTATION_PROCESSOR_BEAN_NAME, existing)

        when:
            withGeneratedArtifacts(true) { register() }

        then:
            registry.getBeanDefinition(
                    AnnotationConfigUtils.AUTOWIRED_ANNOTATION_PROCESSOR_BEAN_NAME).is(existing)
    }

    void 'the lifecycle methods the generated code recorded from annotations are left to the common processor'() {
        given: 'a generated definition recording an annotated method and one named by the definition itself, ' +
                'and holding an inner generated bean that records the same'
            RootBeanDefinition generated = generatedDefinition()
            RootBeanDefinition inner = generatedDefinition()
            generated.propertyValues.add('nested', [inner])
            registry.registerBeanDefinition('generated', generated)

        and: 'one declared any other way, recording the same'
            RootBeanDefinition declared = new RootBeanDefinition(LifecycleFixture).tap {
                initMethodNames = ['initialize', 'start'] as String[]
                destroyMethodNames = ['shutdown', 'stop'] as String[]
            }
            registry.registerBeanDefinition('declared', declared)

        when:
            withGeneratedArtifacts(true) { register() }

        then: 'the processor calls the annotated methods for every instance, and the container the others'
            generated.initMethodNames == ['start']
            generated.destroyMethodNames == ['stop']
            inner.initMethodNames == ['start']
            inner.destroyMethodNames == ['stop']

        and:
            declared.initMethodNames == ['initialize', 'start']
            declared.destroyMethodNames == ['shutdown', 'stop']
    }

    void 'the lifecycle methods are left where they are without generated artifacts'() {
        given:
            RootBeanDefinition generated = generatedDefinition()
            registry.registerBeanDefinition('generated', generated)

        when:
            withGeneratedArtifacts(false) { register() }

        then:
            generated.initMethodNames == ['initialize', 'start']
            generated.destroyMethodNames == ['shutdown', 'stop']
    }

    /** A definition as the generated code contributes one for a {@link LifecycleFixture}. */
    private static RootBeanDefinition generatedDefinition() {
        new RootBeanDefinition(LifecycleFixture).tap {
            initMethodNames = ['initialize', 'start'] as String[]
            destroyMethodNames = ['shutdown', 'stop'] as String[]
            instanceSupplier = { new LifecycleFixture() }
            GeneratedBeanDefinitions.markGenerated(it, LifecycleFixture.name)
        }
    }

    static class LifecycleFixture {

        @PostConstruct
        void initialize() {
        }

        void start() {
        }

        @PreDestroy
        void shutdown() {
        }

        void stop() {
        }
    }
}
