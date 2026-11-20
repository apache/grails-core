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

import grails.gorm.DetachedCriteria
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.query.Query.Criterion
import org.grails.datastore.mapping.query.api.AssociationCriteria

/**
 * Criterion related to an association
 *
 * @author Graeme Rocher
 * @since 1.0
 */
class DetachedAssociationCriteria<T> extends DetachedCriteria<T> implements Criterion, AssociationCriteria {

    final Association association
    final String associationPath

    /**
     * The path as it was written when it starts with an alias declared earlier, as {@code c2.related}, which is
     * {@link #getAssociationPath()} {@code children.related}. It names the alias the join continues from, which the
     * association path alone does not when the association has several aliases. {@code null} when the path does not
     * start with an alias.
     */
    String pathFromAlias

    DetachedAssociationCriteria(Class<T> targetClass, Association association) {
        this(targetClass, association, null)
    }

    DetachedAssociationCriteria(Class targetClass, Association association, String alias) {
        super(targetClass, alias)
        this.association = association
        this.associationPath = association.getName()
    }

    DetachedAssociationCriteria(Class targetClass, Association association, String associationPath, String alias) {
        super(targetClass, alias)
        this.association = association
        this.associationPath = associationPath
    }
}
