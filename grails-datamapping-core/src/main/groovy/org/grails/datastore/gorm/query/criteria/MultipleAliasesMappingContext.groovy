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
package org.grails.datastore.gorm.query.criteria

import groovy.transform.CompileStatic

/**
 * Implemented by the {@link org.grails.datastore.mapping.model.MappingContext} of a datastore that can join one
 * association under several aliases. A {@link AbstractDetachedCriteria} keeps every alias that
 * {@code createAlias} declares for an association path as an entry of its own when the datastore says so. Without
 * it a second alias of a path takes the place of the first, which is what a datastore that ignores aliases, or
 * joins an association once, has always seen.
 *
 * @since 8.0.1
 */
@CompileStatic
interface MultipleAliasesMappingContext {

    /**
     * @return whether the datastore joins an association once for each alias that is declared for it
     */
    boolean isMultipleAliasesPerAssociationSupported()
}
