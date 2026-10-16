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
package org.grails.orm.hibernate.cfg.domainbinding.binder

import groovy.transform.CompileStatic
import org.hibernate.boot.spi.MetadataBuildingContext
import org.hibernate.mapping.OneToOne
import org.hibernate.mapping.PersistentClass
import org.hibernate.mapping.Table

import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateOneToOneProperty

@CompileStatic
class OneToOneBinder {

    private final MetadataBuildingContext metadataBuildingContext
    private final SimpleValueBinder simpleValueBinder

    OneToOneBinder(MetadataBuildingContext metadataBuildingContext, SimpleValueBinder simpleValueBinder) {
        this.metadataBuildingContext = metadataBuildingContext
        this.simpleValueBinder = simpleValueBinder
    }

    OneToOne bindOneToOne(final HibernateOneToOneProperty property, String path) {
        Table table = property.table
        PersistentClass owner = property.hibernateOwner.persistentClass
        OneToOne oneToOne = new OneToOne(metadataBuildingContext, table, owner)

        oneToOne.constrained = property.isHibernateConstrained()
        oneToOne.foreignKeyType = property.hibernateForeignKeyDirection
        oneToOne.alternateUniqueKey = true
        oneToOne.fetchMode = property.hibernateFetchMode
        oneToOne.referencedEntityName = property.hibernateReferencedEntityName
        oneToOne.propertyName = property.name
        oneToOne.referenceToPrimaryKey = false

        if (property.needsSimpleValueBinding()) {
            simpleValueBinder.bindSimpleValue(property, null, oneToOne, path)
        }
        else {
            oneToOne.referencedPropertyName = property.hibernateReferencedPropertyName
        }
        return oneToOne
    }

}
