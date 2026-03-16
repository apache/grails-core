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
package org.grails.orm.hibernate.cfg.domainbinding.secondpass;

import java.util.Optional;

import org.hibernate.FetchMode;
import org.hibernate.mapping.ManyToOne;

import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty;

/** Binds inverse values for unidirectional one-to-many associations. */
public class UnidirectionalOneToManyInverseValuesBinder {

    public void bindUnidirectionalOneToManyInverseValues(HibernateToManyProperty property, ManyToOne manyToOne) {
        manyToOne.setIgnoreNotFound(property.getIgnoreNotFound());
        manyToOne.setLazy(!FetchMode.JOIN.equals(property.getFetchMode()));
        Optional.ofNullable(property.getLazy()).ifPresent(manyToOne::setLazy);
        manyToOne.setReferencedEntityName(
                property.getHibernateAssociatedEntity().getName());
    }
}
