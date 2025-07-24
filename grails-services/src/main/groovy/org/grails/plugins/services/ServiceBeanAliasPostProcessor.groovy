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
package org.grails.plugins.services

import groovy.transform.CompileStatic

import org.springframework.beans.BeansException
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.beans.factory.config.BeanFactoryPostProcessor
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.beans.factory.support.GenericBeanDefinition
import org.springframework.core.AliasRegistry

import grails.artefact.Artefact
import grails.util.GrailsNameUtils
import org.grails.core.artefact.ServiceArtefactHandler

/**
 * Registers bean aliases for service beans provided by plugins.
 *
 * If a plugin named ReportingPlugin provides a service artifact named
 * PrintingService, the corresponding bean name will be reportingPrintingService.
 * This post processor will create a printingService alias which points to the
 * reportingPrintingService bean as long as there is not another bean in the
 * context named printingService.
 *
 * @since 2.3
 * @author Jeff Scott Brown
 */
@CompileStatic
class ServiceBeanAliasPostProcessor implements BeanFactoryPostProcessor {

    @Override
    void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        def beanNames = beanFactory.beanDefinitionNames
        Map<String, List<String>> aliasNamesToListOfBeanNames = [:].withDefault { key -> [] }
        for (String beanName : beanNames) {
            if (beanName.endsWith(ServiceArtefactHandler.TYPE)) {
                BeanDefinition beanDefinition = beanFactory.getBeanDefinition(beanName)
                if (beanDefinition instanceof GenericBeanDefinition) {
                    GenericBeanDefinition genericBeanDefinition = (GenericBeanDefinition) beanDefinition
                    if(genericBeanDefinition.hasBeanClass()) {

                        Class beanClass = genericBeanDefinition.beanClass
                        Artefact artefactAnn = beanClass?.getAnnotation(Artefact)
                        if (artefactAnn != null && artefactAnn.value() == ServiceArtefactHandler.TYPE) {
                            String serviceClassName = beanClass.name
                            if (serviceClassName) {
                                String potentialAliasName = GrailsNameUtils.getPropertyName(beanClass.simpleName)
                                // if the alias name does not conflict with another bean name,
                                // add it to the Map for consideration
                                if (!beanNames.contains(potentialAliasName)) {
                                    def aliasExists = false
                                    if (beanFactory instanceof AliasRegistry) {
                                        aliasExists = ((AliasRegistry) beanFactory).isAlias(potentialAliasName)
                                    }
                                    if (!aliasExists) {
                                        aliasNamesToListOfBeanNames[potentialAliasName] << beanName
                                    }
                                }
                            }
                        }
                    }


                }
            }
        }
        if(!aliasNamesToListOfBeanNames.isEmpty()) {
            aliasNamesToListOfBeanNames.each { String aliasName, List<String> listOfBeanNames ->
                // only register the alias if their is only
                // one candidate target bean for the alias
                if (listOfBeanNames.size() == 1) {
                    beanFactory.registerAlias listOfBeanNames[0], aliasName
                }
            }
        }
    }
}
