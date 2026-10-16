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
import org.hibernate.MappingException
import org.hibernate.boot.spi.SecondPass

import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty

@CompileStatic
@SuppressWarnings('PMD.NonSerializableClass')
class ListSecondPass implements SecondPass, GrailsSecondPass, Serializable {

    private static final long serialVersionUID = -3024674993774205193L

    protected final HibernateToManyProperty property
    private final org.hibernate.mapping.Collection collection
    private final ListSecondPassBinder listSecondPassBinder

    ListSecondPass(ListSecondPassBinder listSecondPassBinder, HibernateToManyProperty property) {
        this(listSecondPassBinder, property, null)
    }

    /**
     * @param collection the collection this pass binds. The same property can be bound more than once, for instance
     * when its owner is an entity that is also embedded in another, and each binding has its own collection.
     */
    ListSecondPass(ListSecondPassBinder listSecondPassBinder, HibernateToManyProperty property, org.hibernate.mapping.Collection collection) {
        this.listSecondPassBinder = listSecondPassBinder
        this.property = property
        this.collection = collection
    }

    @Override
    void doSecondPass(Map persistentClasses) throws MappingException {
        if (collection != null) {
            property.hibernateCollection = collection
        }
        listSecondPassBinder.bindListSecondPass(property)
        createCollectionKeys(property.collection)
    }

}
