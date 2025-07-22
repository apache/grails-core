/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.grails.common.compiler

import groovy.transform.CompileStatic

/**
 * Helper class to store the transformation order for groovy based AST transformations
 */
@CompileStatic
interface GroovyTransformOrder {
    static final int HIGHEST_PRIORITY = Integer.MAX_VALUE
    static final int HIGHEST_STARTING_PRIORITY = HIGHEST_PRIORITY - 1000000
    static final int DECREMENT_PRIORITY = -5
    static final int LOWEST_PRIORITY = Integer.MIN_VALUE

    //
    // Conversion Orders
    //

    /**
     * Adds global imports
     */
    static final int GLOBAL_IMPORT_ORDER = HIGHEST_STARTING_PRIORITY

    //
    // Semantic Analysis Orders
    //

    /**
     * Allows specifying the format for a field when binding
     */
    static final int BINDING_FORMAT_ORDER = HIGHEST_STARTING_PRIORITY

    /**
     * Transforms a JPA entity into a GORM entity
     */
    static final int GLOBAL_JPA_ORDER = BINDING_FORMAT_ORDER + DECREMENT_PRIORITY

    /**
     * Implements a Data Service
     */
    static final int DATA_SERVICE_ORDER = GLOBAL_JPA_ORDER + DECREMENT_PRIORITY

    /**
     * Adds line numbers to GSPs
     */
    static final int GSP_LINE_ORDER = DATA_SERVICE_ORDER + DECREMENT_PRIORITY

    /**
     * Enhances view scripts with Trait behavior
     */
    static final int VIEWS_ORDER = GSP_LINE_ORDER + DECREMENT_PRIORITY

    /**
     * Enhances gson view scripts with Trait behavior
     */
    static final int VIEWS_GSON_ORDER = VIEWS_ORDER + DECREMENT_PRIORITY

    /**
     * Enhances view scripts with Trait behavior
     */
    static final int VIEWS_MARKUP_ORDER = VIEWS_GSON_ORDER + DECREMENT_PRIORITY

    /**
     * Implements Cacheable
     */
    static final int CACHEABLE_ORDER = VIEWS_MARKUP_ORDER + DECREMENT_PRIORITY

    //
    // Canonicalization Orders
    //
    static final int INTEGRATION_ORDER = HIGHEST_STARTING_PRIORITY

    /**
     * Transforms where queries into DetachedCriteria
     */
    static final int WHERE_ORDER = INTEGRATION_ORDER + DECREMENT_PRIORITY

    /**
     * Transforms groovy finders into DetachedCriteria
     */
    static final int FINDER_ORDER = WHERE_ORDER + DECREMENT_PRIORITY

    /**
     * Grails allows applying transforms by artefact type, this transformation is what implements that
     */
    static final int GLOBAL_GRAILS_TRANSFORM_ORDER = FINDER_ORDER + DECREMENT_PRIORITY

    /**
     * Similar to Groovy's @Delegate AST transform but instead assumes the first
     * argument to every method is 'this'.
     */
    static final int API_DELEGATE_ORDER = GLOBAL_GRAILS_TRANSFORM_ORDER + DECREMENT_PRIORITY

    /**
     * Changes methods in a file to return a promise instead of a value
     */
    static final int DELEGATE_ASYNC_ORDER = API_DELEGATE_ORDER + DECREMENT_PRIORITY

    /**
     * Changes methods in a file to return a promise instead of a value
     */
    static final int GORM_ASYNC_ORDER = DELEGATE_ASYNC_ORDER + DECREMENT_PRIORITY

    /**
     * Adds methods from one class onto another
     */
    static final int MIXIN_ORDER = GORM_ASYNC_ORDER + DECREMENT_PRIORITY

    /**
     * Transform that finds any `@Enhance` annotation on traits to automatically add this trait to that artefact type
     */
    static final int ENHANCES_ORDER = MIXIN_ORDER + DECREMENT_PRIORITY

    /**
     * Used to apply transformers to classes not located in Grails
     * directory structure, i.e. @Artefact('Controller')
     */
    static final int ARTIFACT_TYPE_ORDER = ENHANCES_ORDER + DECREMENT_PRIORITY

    /**
     * Enables dirty tracking to occur at the domain class level instead of at the ORM level
     */
    static final int DIRTY_CHECK_ORDER = ARTIFACT_TYPE_ORDER + DECREMENT_PRIORITY

    /**
     * Makes all GormEntities be a JPA entity
     */
    static final int JPA_GORM_ENTITY_ORDER = DIRTY_CHECK_ORDER + DECREMENT_PRIORITY

    /**
     * getter/setter transforms for hibernate entities
     */
    static final int HIBERNATE5_ORDER = JPA_GORM_ENTITY_ORDER + DECREMENT_PRIORITY

    /**
     * Transforms a given class to a GORM Entity
     */
    static final int GORM_ENTITY_ORDER = HIBERNATE5_ORDER + DECREMENT_PRIORITY

    /**
     * Adds basic fields like id, version, toString, and associations
     * Adds the DomainClassArtefactType
     */
    static final int ENTITY_ORDER = GORM_ENTITY_ORDER + DECREMENT_PRIORITY

    /**
     * Several of the gorm related transforms iterate not at the priority level but on the node level, this class
     * performs those iterations
     */
    static final int GORM_TRANSFORMS_ORDER = ENTITY_ORDER + DECREMENT_PRIORITY

    /**
     * Implements Publisher
     */
    static final int PUBLISHER_ORDER = GORM_TRANSFORMS_ORDER + DECREMENT_PRIORITY

    /**
     * Implements the Transaction, and Readonly transforms
     */
    static final int TRANSACTIONAL_ORDER = PUBLISHER_ORDER + DECREMENT_PRIORITY

    /**
     * Implements Rollback
     */
    static final int ROLLBACK_ORDER = TRANSACTIONAL_ORDER + DECREMENT_PRIORITY

    /**
     * Implements AnnotatedSubscriber
     */
    static final int SUBSCRIBER_ORDER = ROLLBACK_ORDER + DECREMENT_PRIORITY

    /**
     * Exposes a domain class as a restful resource
     */
    static final int RESOURCE_ORDER = SUBSCRIBER_ORDER + DECREMENT_PRIORITY

    /**
     * Implements CachePut
     */
    static final int CACHE_PUT_ORDER = RESOURCE_ORDER + DECREMENT_PRIORITY

    /**
     * Implements CacheEvict
     */
    static final int CACHE_EVICT_ORDER = CACHE_PUT_ORDER + DECREMENT_PRIORITY

    /**
     * Implements WithoutTenant, WithTenant, and CurrentTenant transforms
     */
    static final int TENANT_ORDER = CACHE_EVICT_ORDER + DECREMENT_PRIORITY

    /**
     * Allows adding link() support to any class
     */
    static final int LINK_ORDER = TENANT_ORDER + DECREMENT_PRIORITY

    /**
     * Transforms a method to non-block IO
     */
    static final int RX_SCHEDULER_ORDER = LINK_ORDER + DECREMENT_PRIORITY
}
