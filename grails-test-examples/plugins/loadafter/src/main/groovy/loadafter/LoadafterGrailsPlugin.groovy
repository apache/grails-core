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

class LoadafterGrailsPlugin extends Plugin {

    def title = "Load After" // Headline display name of the plugin
    def author = "Your name"
    def authorEmail = ""
    def description = '''\
Brief summary/description of the plugin.
'''
    def profiles = ['web']

    def loadAfter = ['springSecurityCore']

    // Exercises the retimed plugin lifecycle end-to-end (see app3 PluginBeansBeforeAutoConfigurationSpec):
    // doWithSpring beans register ahead of auto-configuration through real plugin discovery, so the
    // app's @ConditionalOnMissingBean default for this name must defer.
    @Override
    Closure doWithSpring() {
        { ->
            earlyPluginProbe(String, 'from-plugin-doWithSpring')
        }
    }

    // Exercises the new beanRegistrar() API end-to-end using the concise closure-coercion form.
    // Registrar beans also register ahead of auto-configuration, so the app's @ConditionalOnMissingBean
    // default for this name must defer — PluginBeansBeforeAutoConfigurationSpec asserts the plugin's value wins.
    @Override
    BeanRegistrar beanRegistrar() {
        { BeanRegistry registry, Environment environment ->
            registry.registerBean('registrarProbe', String) { spec ->
                spec.supplier { context -> 'from-plugin-beanRegistrar' }
            }
        } as BeanRegistrar
    }

}
