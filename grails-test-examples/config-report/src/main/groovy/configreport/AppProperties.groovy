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
package configreport

import groovy.transform.CompileStatic
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/**
 * A Spring Boot {@code @ConfigurationProperties} bean that binds to
 * the {@code myapp.typed} prefix.
 *
 * <p>Properties for this bean are defined in {@code application.yml}
 * and verified in the ConfigReportCommand integration test.
 */
@CompileStatic
@Validated
@ConfigurationProperties(prefix = 'myapp.typed')
class AppProperties {

    /**
     * The display name of the application.
     */
    String name = 'Default App'

    /**
     * The maximum number of items per page.
     */
    Integer pageSize = 25

    /**
     * Whether debug mode is active.
     */
    Boolean debugEnabled = false

}
