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
package org.grails.orm.hibernate.cfg.domainbinding.util;

import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToOneProperty;

// each property may consist of one or many columns (due to composite ids) so in order to get the
// number of columns required for a column key we have to perform the calculation here
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class ForeignKeyColumnCountCalculator {
    public int calculateForeignKeyColumnCount(GrailsHibernatePersistentEntity refDomainClass, String[] propertyNames) {
        int expectedForeignKeyColumnLength = 0;
        for (String propertyName : propertyNames) {
            HibernatePersistentProperty referencedProperty = refDomainClass.getHibernatePropertyByName(propertyName);
            if (referencedProperty instanceof HibernateToOneProperty toOne) {
                PersistentProperty[] compositeIdentity =
                        toOne.getAssociatedEntity().getCompositeIdentity();
                if (compositeIdentity != null) {
                    expectedForeignKeyColumnLength += compositeIdentity.length;
                } else {
                    expectedForeignKeyColumnLength++;
                }
            } else {
                expectedForeignKeyColumnLength++;
            }
        }
        return expectedForeignKeyColumnLength;
    }
}
