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
package org.grails.datastore.gorm.timestamp;

import java.util.Optional;

/**
 * Interface for components that are aware of the application's current auditor.
 * This will be used to populate @CreatedBy and @LastModifiedBy annotated fields
 * in domain objects.
 *
 * <p>Implementations should be registered as Spring beans. The type parameter
 * should match the type of the auditor field in your domain classes (e.g., User,
 * Long, String, etc.).</p>
 *
 * <p>Example implementation:</p>
 * <pre>{@code
 * @Component
 * public class SpringSecurityAuditorAware implements AuditorAware<String> {
 *     @Override
 *     public Optional<String> getCurrentAuditor() {
 *         return Optional.ofNullable(SecurityContextHolder.getContext())
 *                 .map(SecurityContext::getAuthentication)
 *                 .filter(Authentication::isAuthenticated)
 *                 .map(Authentication::getName);
 *     }
 * }
 * }</pre>
 *
 * @param <T> the type of the auditor (e.g., User, Long, String)
 * @author Scott Murphy Heiberg
 * @since 7.1
 */
public interface AuditorAware<T> {

    /**
     * Returns the current auditor of the application.
     *
     * @return the current auditor, or {@link Optional#empty()} if no auditor is available
     */
    Optional<T> getCurrentAuditor();
}
