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

package issue11767.app

import issue11767.plugin.PluginGroovyMicronautBean
import issue11767.plugin.PluginGroovySpringBean
import issue11767.plugin.PluginJavaMicronautBean
import org.springframework.beans.factory.annotation.Autowired

class AppController {

    PluginGroovySpringBean pluginGroovySpringBean
    @Autowired PluginGroovyMicronautBean pluginGroovyMicronautConfigBean
    @Autowired PluginJavaMicronautBean pluginJavaMicronautConfigBean
    @Autowired AppGroovyMicronautBean appGroovyMicronautConfigBean

    def index() {
        render """
            <pre>
                Plugin Groovy Spring Bean - my.value1: ${pluginGroovySpringBean.value1}
                Plugin Groovy Spring Bean - my.value2: ${pluginGroovySpringBean.value2}
                <br>
                Plugin Groovy Micronaut Bean - my.value1: ${pluginGroovyMicronautConfigBean.value1}
                Plugin Groovy Micronaut Bean - my.value2: ${pluginGroovyMicronautConfigBean.value2}
                <br>
                Plugin Java Micronaut Bean - my.value1: ${pluginJavaMicronautConfigBean.value1}
                Plugin Java Micronaut Bean - my.value2: ${pluginJavaMicronautConfigBean.value2}
                <br>
                App Groovy Micronaut Bean - my.value1: ${appGroovyMicronautConfigBean.value1}
                App Groovy Micronaut Bean - my.value2: ${appGroovyMicronautConfigBean.value2}
            </pre>
        """.stripIndent()
    }
}