/* Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.grails.web.config.http;

import org.springframework.boot.autoconfigure.security.SecurityProperties;

/**
 * Stores the default order numbers of all Grails filters for use in configuration.
 * These filters are run prior to the Spring Security Filter Chain which is at DEFAULT_FILTER_ORDER
 * @since 7.0
 */
public enum GrailsFilters {

    FIRST,
    ASSET_PIPELINE_FILTER,
    CHARACTER_ENCODING_FILTER,
    HIDDEN_HTTP_METHOD_FILTER,
    SITEMESH_FILTER,
    GRAILS_WEB_REQUEST_FILTER,
    LAST(SecurityProperties.DEFAULT_FILTER_ORDER - 10);

    private static final int INTERVAL = 10;
    private final int order;

    GrailsFilters() {
        this.order = SecurityProperties.DEFAULT_FILTER_ORDER - 100 + ordinal() * INTERVAL;
    }
    GrailsFilters(int order) {
        this.order = order;
    }

    public int getOrder() {
        return this.order;
    }

}