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
package org.grails.orm.hibernate.cfg.domainbinding.jpa

import groovy.transform.CompileStatic

/**
 * The multi-tenant filter the domain binder adds to the class of a multi-tenant entity: the name of the global filter
 * (and of its only parameter), the condition that compares the parameter with the tenant column, and the Java type of
 * the parameter, which the binder takes from the type of the tenant id property.
 *
 * <p>The filter is enabled per session by GORM, never by Hibernate, so it carries no auto-enable and no default
 * condition. {@link GrailsDomainGenerator} writes it as {@code @Filter} on the entity and as one {@code @FilterDef}.</p>
 *
 * @since 9.0
 */
@CompileStatic
record TenantFacets(
    String filterName,
    String condition,
    Class<?> parameterType) {
}
