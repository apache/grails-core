/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package grails.testing.spring

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

import org.springframework.boot.test.context.TestConfiguration

/**
 * Marker annotation for Spring test configuration classes that should be auto-imported into
 * Integration tests by {@code IntegrationTestAstTransformation}.
 *
 * @since 7.1
 */
@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.TYPE])
@TestConfiguration(proxyBeanMethods = false)
@interface IntegrationTestAutoConfiguration {

    /**
     * Fully-qualified class names that must be present on the classpath before this
     * configuration is auto-imported.
     */
    String[] requiredClasses() default []

    /**
     * Fully-qualified annotation class names that, when present on the target test class,
     * will prevent auto-import of this configuration.
     */
    String[] skipIfAnnotatedWith() default []
}

