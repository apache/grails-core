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

/**
 * The application class: the lifecycle hooks it shares with plugins ({@link GrailsApplicationLifeCycle}),
 * and {@link #doWithSpring()}, which only an application has.
 *
 * @author Graeme Rocher
 * @since 3.0.10
 */
trait GrailsApplicationClass implements GrailsApplicationLifeCycle {

    /**
     * Beans of the application in the bean builder DSL, registered after the plugins' beans and the
     * auto-configurations, together with those of {@code grails-app/conf/spring/resources.groovy}, so
     * they replace a bean of the same name. A plugin has no such hook: it registers its beans with
     * {@link #beanRegistrar()} or a {@code beans} block.
     *
     * @return A closure that defines beans to be registered by Spring, or {@code null} for none
     * @deprecated since 8.0 in favour of {@link #beanRegistrar()}. The underlying bean builder DSL
     * remains available but is no longer actively supported and will not receive fixes for new
     * issues.
     */
    @Deprecated(since = '8.0')
    abstract Closure doWithSpring()
}
