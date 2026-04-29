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
package org.grails.web.config.http;

/**
 * Constants for filter ordering in Grails applications.
 * <p>
 * These constants were previously obtained from Spring Boot's {@code SecurityProperties}
 * class, but were removed in Spring Boot 4.0. They are now defined here for use by
 * Grails filter configuration.
 *
 * @since 8.0
 */
public final class GrailsFilterOrder {

    private GrailsFilterOrder() {
        // Utility class
    }

    /**
     * Default order of Spring Security's Filter in the servlet container (i.e. amongst
     * other filters registered with the container). There is no connection between this
     * and the {@code @Order} on a {@code SecurityFilterChain}.
     * <p>
     * The value {@code -100} matches what was previously defined in Spring Boot's
     * {@code SecurityProperties.DEFAULT_FILTER_ORDER} (computed as
     * {@code OrderedFilter.REQUEST_WRAPPER_FILTER_MAX_ORDER - 100}) before it was
     * removed in Spring Boot 4.0.
     */
    public static final int DEFAULT_FILTER_ORDER = -100;

}
