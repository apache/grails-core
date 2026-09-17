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
import org.hibernate.FetchMode
import org.hibernate.mapping.ManyToOne

import org.grails.orm.hibernate.cfg.PropertyConfig
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateAssociation

@CompileStatic
class ManyToOneValuesBinder {

    void bindManyToOneValues(HibernateAssociation property, ManyToOne manyToOne) {
        PropertyConfig config = property.hibernateMappedForm

        FetchMode fetchMode = config.fetchMode != null ? config.fetchMode : FetchMode.DEFAULT
        manyToOne.fetchMode = fetchMode

        manyToOne.lazy = property.isLazy()

        manyToOne.ignoreNotFound = config.ignoreNotFound

        // set referenced entity
        manyToOne.referencedEntityName = property.associatedEntity.name
    }

}
