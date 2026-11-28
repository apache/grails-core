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

import grails.plugins.*
import org.springframework.beans.factory.BeanRegistrar
import org.springframework.beans.factory.BeanRegistry
import org.springframework.core.env.Environment

import grails.plugins.metadata.GrailsPluginDescriptor

@GrailsPluginDescriptor(after = ['springSecurityCore'])
class LoadafterGrailsPlugin extends Plugin {

    // Exercises the beanRegistrar() API end-to-end using the concise closure-coercion form. Registrar
    // beans register ahead of auto-configuration, so the app's @ConditionalOnMissingBean default for
    // registrarProbe must defer, and so must its default for earlyPluginProbe, which the post-processor
    // the registrar registers contributes as a plain bean definition before the configuration classes
    // are read. PluginBeansBeforeAutoConfigurationSpec asserts the plugin's values win.
    @Override
    BeanRegistrar beanRegistrar() {
        { BeanRegistry registry, Environment environment ->
            registry.registerBean('registrarProbe', String) { spec ->
                spec.supplier { context -> 'from-plugin-beanRegistrar' }
            }
            registry.registerBean('earlyPluginProbePostProcessor', EarlyPluginProbePostProcessor) { spec ->
                spec.infrastructure()
            }
        } as BeanRegistrar
    }

}
