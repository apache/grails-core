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
package org.grails.forge

import groovy.transform.CompileStatic
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.FilterType

/**
 * Registers the generator beans: every Spring component under {@code org.grails.forge}, which is the
 * feature catalogue, the project generator, the context factory and the feature validators.
 *
 * <p>The packages of the two Forge applications are left out, because each application wires its own
 * package, and so is the {@link ForgeGrailsPlugin} descriptor, which Spring Boot imports as an
 * auto-configuration and must not register a second time as a scanned component.</p>
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan(
        basePackages = 'org.grails.forge',
        excludeFilters = [
                @ComponentScan.Filter(
                        type = FilterType.REGEX,
                        pattern = 'org\\.grails\\.forge\\.(web|cli|api)\\..*'
                ),
                @ComponentScan.Filter(
                        type = FilterType.CUSTOM,
                        classes = AutoConfigurationExcludeFilter
                )
        ]
)
@CompileStatic
class ForgeCoreConfiguration { }
