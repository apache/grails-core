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
package grails.compiler

import groovy.transform.AnnotationCollector
import groovy.transform.CompileStatic

/**
 *
 * @since 2.4
 *
 */
@AnnotationCollector
@CompileStatic(extensions = [
        'org.grails.compiler.CriteriaTypeCheckingExtension',
        'org.grails.compiler.DomainMappingTypeCheckingExtension',
        'org.grails.compiler.DynamicFinderTypeCheckingExtension',
        'org.grails.compiler.HttpServletRequestTypeCheckingExtension',
        'org.grails.compiler.NamedQueryTypeCheckingExtension',
        'org.grails.compiler.RelationshipManagementMethodTypeCheckingExtension',
        'org.grails.compiler.ValidateableTypeCheckingExtension',
        'org.grails.compiler.WhereQueryTypeCheckingExtension',
        // Catch-all: must run last so it can defer to any extension above that has
        // already resolved an unrecognised call (see TagLibraryInvokerTypeCheckingExtension).
        'org.grails.compiler.TagLibraryInvokerTypeCheckingExtension',
])
@interface GrailsCompileStatic {}
