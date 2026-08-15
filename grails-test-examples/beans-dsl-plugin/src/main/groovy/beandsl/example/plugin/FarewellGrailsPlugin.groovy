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

import org.springframework.boot.autoconfigure.AutoConfiguration

import grails.compiler.beans.GrailsBeans
import grails.plugins.Plugin

/**
 * Demonstrates {@code @GrailsBeans} applied directly to a {@code *GrailsPlugin.groovy}-style
 * class. The {@code beans} block below compiles onto a generated sibling
 * {@code FarewellAutoConfiguration} class rather than onto this one - a
 * {@code Plugin} subclass is instantiated by {@code DefaultGrailsPlugin} via plain reflection,
 * never as a Spring bean, so it cannot itself carry {@code @Bean} methods. Everything else about
 * this class - the {@code Plugin} lifecycle hooks, {@code version}, etc. - works exactly as it
 * would without {@code @GrailsBeans}.
 */
@GrailsBeans
@AutoConfiguration
class FarewellGrailsPlugin extends Plugin {

    String version = '1.0'

    def beans = {
        bean('farewell', Farewell) {
            new Farewell('goodbye from a Plugin')
        }
    }

}
