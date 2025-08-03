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
package org.grails.forge.feature.spring;

import jakarta.inject.Singleton;
import org.grails.forge.application.ApplicationType;
import org.grails.forge.application.generator.GeneratorContext;

import java.util.Map;

@Singleton
public class SpringBootVirtualThreads implements SpringThreadingFeature {

    @Override
    public String getName() {
        return "spring-boot-virtual-threads";
    }

    @Override
    public String getTitle() {
        return "Spring Boot Virtual Threads";
    }

    @Override
    public String getDescription() {
        return "Enables Virtual Threads in Spring Boot for JDK 24+.";
    }

    @Override
    public boolean supports(ApplicationType applicationType) {
        return true;
    }

    @Override
    public void apply(GeneratorContext generatorContext) {
        Map<String, Object> config = generatorContext.getConfiguration();

        // Enable by default only for JDK 24+
        config.put("spring.threads.virtual.enabled", generatorContext.getJdkVersion().majorVersion() >= 24);
    }

    @Override
    public boolean isVisible() {
        return true;
    }

    @Override
    public String getDocumentation() {
        return "https://docs.spring.io/spring-boot/reference/features/spring-application.html#features.spring-application.virtual-threads";
    }
}
