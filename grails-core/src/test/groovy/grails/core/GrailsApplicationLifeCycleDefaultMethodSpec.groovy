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
package grails.core

import spock.lang.Specification

/**
 * Guards the Groovy interface default {@link GrailsApplicationLifeCycle#beanRegistrar()} against
 * the Groovy 6 classic-callsite regression where a default method referenced a missing {@code $1}
 * CallSite holder and broke app boot under {@code -PgrailsIndy=false}.
 */
class GrailsApplicationLifeCycleDefaultMethodSpec extends Specification {

    void 'interface default beanRegistrar returns null without requiring an override'() {
        given:
        GrailsApplicationLifeCycle lifeCycle = new GrailsApplicationLifeCycle() {
            @Override
            Closure doWithSpring() {
                return { -> }
            }

            @Override
            void doWithDynamicMethods() {
            }

            @Override
            void doWithApplicationContext() {
            }

            @Override
            void onConfigChange(Map<String, Object> event) {
            }

            @Override
            void onStartup(Map<String, Object> event) {
            }

            @Override
            void onShutdown(Map<String, Object> event) {
            }
        }

        expect:
        lifeCycle.beanRegistrar() == null
    }

    void 'adapter also returns null from beanRegistrar'() {
        expect:
        new GrailsApplicationLifeCycleAdapter().beanRegistrar() == null
    }
}
