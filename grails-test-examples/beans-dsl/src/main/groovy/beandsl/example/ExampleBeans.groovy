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
package beandsl.example

import org.springframework.boot.autoconfigure.AutoConfiguration

import grails.compiler.beans.GrailsBeans

/**
 * Spike: a {@code doWithSpring}-style closure DSL that {@link GrailsBeans} compiles into real
 * {@code @Bean} factory methods at compile time, so this class ships as a plain Spring Boot
 * {@code @AutoConfiguration} with no closures surviving into the compiled bytecode.
 */
@GrailsBeans
@AutoConfiguration
class ExampleBeans {

    def beans = {
        bean('greeter', Greeter) {
            new Greeter('hello from GrailsBeans')
        }

        bean('fancyGreeter', FancyGreeter).conditionalOnMissingBean(FancyGreeter)

        bean('loudGreeter', LoudGreeter) { Greeter greeter ->
        }
    }

}
