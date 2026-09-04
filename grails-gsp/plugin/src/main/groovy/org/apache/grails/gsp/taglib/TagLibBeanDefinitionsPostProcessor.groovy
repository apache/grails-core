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
package org.apache.grails.gsp.taglib

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.beans.factory.support.AbstractBeanDefinition
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor
import org.springframework.beans.factory.support.GenericBeanDefinition
import org.springframework.core.PriorityOrdered

import grails.core.GrailsApplication
import grails.core.gsp.GrailsTagLibClass
import org.grails.core.artefact.gsp.TagLibArtefactHandler

/**
 * Registers a bean definition for every tag library artefact, replacing the registration the GSP
 * plugin previously performed through the {@code doWithSpring()} bean DSL.
 *
 * <p>Registering them there meant registering them again on every start, over whatever was already
 * there. That is harmless on a running JVM, but an ahead-of-time image has already generated a
 * definition for each tag library, carrying the field and method injection the generator worked out
 * at build time. Replacing it discarded that injection, leaving a tag library holding null where it
 * expected a collaborator -- and by-name autowiring did not stand in for it, because the
 * collaborators are fields rather than properties. Contributing the definitions from here leaves the
 * generated ones alone.</p>
 *
 * <p>The artefacts are read from the application rather than named individually, so a tag library
 * belongs to whoever declared it: the application, another plugin, or one supplied through
 * {@code providedArtefacts}. An existing definition for the same name wins untouched, which
 * preserves the ability to override a tag library -- including one deliberately declared without
 * by-name autowiring, which cannot be told apart from a generated one. What a generated definition
 * needs is carried into it while it is generated, by the processor that reads the autowire mode.</p>
 *
 * @since 8.0
 */
@Slf4j
@CompileStatic
class TagLibBeanDefinitionsPostProcessor implements BeanDefinitionRegistryPostProcessor, PriorityOrdered {

    private final GrailsApplication grailsApplication

    TagLibBeanDefinitionsPostProcessor(GrailsApplication grailsApplication) {
        this.grailsApplication = grailsApplication
    }

    @Override
    void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
        for (Object each : grailsApplication.getArtefacts(TagLibArtefactHandler.TYPE)) {
            GrailsTagLibClass artefact = (GrailsTagLibClass) each
            String beanName = artefact.fullName
            if (!artefact.available) {
                continue
            }
            if (registry.containsBeanDefinition(beanName)) {
                continue
            }
            GenericBeanDefinition definition = new GenericBeanDefinition(
                    beanClass: artefact.clazz,
                    lazyInit: true,
                    autowireMode: AbstractBeanDefinition.AUTOWIRE_BY_NAME
            )
            registry.registerBeanDefinition(beanName, definition)
            log.debug('Registered tag library {}', beanName)
        }
    }

    @Override
    void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
    }

    @Override
    int getOrder() {
        HIGHEST_PRECEDENCE
    }
}
