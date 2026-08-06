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
package beandsl.example.plugin

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.AutoConfiguration

import grails.compiler.beans.GrailsBeans
import grails.plugins.Plugin

/**
 * Demonstrates {@code field(...)} and {@code method(...)} - the same shape real autoconfigurations
 * like the built-in i18n plugin's need: injected configuration shared across beans, and a private
 * helper factored out of a bean's construction logic. Both compile onto the generated
 * {@code GreetingAutoConfiguration} sibling as ordinary private members, exactly like a
 * hand-written {@code @Configuration} class's own fields and methods.
 */
@GrailsBeans
@AutoConfiguration
class GreetingGrailsPlugin extends Plugin {

    String version = '1.0'

    def beans = {
        field('greetingSuffix', String).annotate(Value, value: '${beandsl.example.greeting-suffix:!}')

        method('buildGreeting', String) { String name ->
            "Hello, ${name}${greetingSuffix}"
        }

        bean('greeting', Greeting) {
            new Greeting(buildGreeting('World'))
        }
    }

}
