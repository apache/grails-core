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
package org.grails.spring.beans.factory

import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.beans.factory.support.RootBeanDefinition
import spock.lang.Specification

class GenericBeanFactoryAccessorSpec extends Specification {

    DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory()
    GenericBeanFactoryAccessor accessor = new GenericBeanFactoryAccessor(beanFactory)

    void 'getBeanFactory returns the wrapped factory'() {
        expect:
        accessor.getBeanFactory().is(beanFactory)
    }

    void 'getBean by name returns the registered bean'() {
        given:
        beanFactory.registerSingleton('greeting', 'hello')

        expect:
        accessor.getBean('greeting') == 'hello'
        accessor.getBean('greeting', String) == 'hello'
    }

    void 'getBeansOfType returns matching beans keyed by name'() {
        given:
        beanFactory.registerSingleton('a', 'foo')
        beanFactory.registerSingleton('b', 42)

        expect:
        accessor.getBeansOfType(String) == [a: 'foo']
    }

    void 'getBeansWithAnnotation finds annotated bean classes by their class annotation'() {
        given:
        RootBeanDefinition definition = new RootBeanDefinition(Annotated)
        beanFactory.registerBeanDefinition('annotatedBean', definition)
        beanFactory.registerSingleton('plainBean', 'not annotated')

        expect:
        accessor.getBeansWithAnnotation(MarkerAnnotation).keySet() == ['annotatedBean'] as Set
    }

    void 'findAnnotationOnBean finds the annotation from the merged bean definition class when not on the exposed instance'() {
        given:
        RootBeanDefinition definition = new RootBeanDefinition(Annotated)
        beanFactory.registerBeanDefinition('annotatedBean', definition)

        expect:
        accessor.findAnnotationOnBean('annotatedBean', MarkerAnnotation) != null
    }

    void 'findAnnotationOnBean returns null when no matching annotation exists'() {
        given:
        beanFactory.registerSingleton('plainBean', 'not annotated')

        expect:
        accessor.findAnnotationOnBean('plainBean', MarkerAnnotation) == null
    }

}

@Retention(RetentionPolicy.RUNTIME)
@interface MarkerAnnotation {

}

@MarkerAnnotation
class Annotated {

}
