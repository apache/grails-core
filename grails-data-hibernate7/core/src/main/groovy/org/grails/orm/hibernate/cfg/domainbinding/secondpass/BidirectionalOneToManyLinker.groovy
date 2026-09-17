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
package org.grails.orm.hibernate.cfg.domainbinding.secondpass

import groovy.transform.CompileStatic
import org.hibernate.mapping.Collection
import org.hibernate.mapping.Column
import org.hibernate.mapping.DependantValue
import org.hibernate.mapping.PersistentClass

import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty
import org.grails.orm.hibernate.cfg.domainbinding.util.GrailsPropertyResolver

/** Links bidirectional one-to-many associations by copying columns. */
@CompileStatic
class BidirectionalOneToManyLinker {

    private final GrailsPropertyResolver grailsPropertyResolver

    /** Creates a new {@link BidirectionalOneToManyLinker} instance. */
    BidirectionalOneToManyLinker(GrailsPropertyResolver grailsPropertyResolver) {
        this.grailsPropertyResolver = grailsPropertyResolver
    }

    /** Link. */
    void link(
            Collection collection,
            PersistentClass associatedClass,
            DependantValue key,
            HibernatePersistentProperty otherSide) {
        collection.inverse = true

        for (Column column : grailsPropertyResolver.getProperty(associatedClass, otherSide.name).value.columns) {
            Column mappingColumn = new Column()
            mappingColumn.name = column.name
            mappingColumn.length = column.length
            mappingColumn.nullable = otherSide.isNullable()
            mappingColumn.sqlType = column.sqlType

            mappingColumn.value = key
            key.addColumn(mappingColumn)
            key.table.addColumn(mappingColumn)
        }
        key.sortProperties()
    }

}
