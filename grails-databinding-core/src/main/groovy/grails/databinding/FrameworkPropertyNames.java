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
package grails.databinding;

import java.util.Set;

/**
 * Property names managed by the language runtime or Grails rather than ordinary request data.
 * <p>
 * Intrinsic runtime properties are never request-bindable. Grails-managed domain properties
 * are excluded from generated allowlists by default, but may still bind and be cleared when an
 * application explicitly opts them in (for example {@code bindable: true}); intrinsic runtime
 * properties remain protected.
 */
public final class FrameworkPropertyNames {

    /**
     * Language / MetaClass properties that must never be bound from request data.
     */
    public static final Set<String> INTRINSIC_RUNTIME_PROPERTIES = Set.of(
            "class", "classLoader", "protectionDomain", "metaClass", "metaPropertyValues", "properties");

    /**
     * Grails domain lifecycle properties excluded from default binding allowlists but eligible
     * for {@code clearMissing} when explicitly included.
     */
    public static final Set<String> GRAILS_MANAGED_PROPERTIES = Set.of(
            "errors", "id", "version", "dateCreated", "lastUpdated");

    /**
     * Union of {@link #INTRINSIC_RUNTIME_PROPERTIES} and {@link #GRAILS_MANAGED_PROPERTIES}.
     */
    public static final Set<String> FRAMEWORK_MANAGED_PROPERTIES = Set.of(
            "class", "classLoader", "protectionDomain", "metaClass", "metaPropertyValues", "properties",
            "errors", "id", "version", "dateCreated", "lastUpdated");

    private FrameworkPropertyNames() {
    }
}
