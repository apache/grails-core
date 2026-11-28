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

package loadafter

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor
import org.springframework.beans.factory.support.GenericBeanDefinition
import org.springframework.core.Ordered
import org.springframework.core.PriorityOrdered

/**
 * Registers {@code earlyPluginProbe} as a plain bean definition, the way a plugin registers a definition
 * the {@code BeanRegistry} API cannot express. As a {@link PriorityOrdered} post-processor it runs before
 * the configuration classes are read, so a {@code @ConditionalOnMissingBean} default backs off.
 */
class EarlyPluginProbePostProcessor implements BeanDefinitionRegistryPostProcessor, PriorityOrdered {

    @Override
    void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
        def definition = new GenericBeanDefinition(beanClass: String)
        definition.constructorArgumentValues.addGenericArgumentValue('from-plugin-postProcessor')
        registry.registerBeanDefinition('earlyPluginProbe', definition)
    }

    @Override
    void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
    }

    @Override
    int getOrder() {
        Ordered.HIGHEST_PRECEDENCE
    }
}
