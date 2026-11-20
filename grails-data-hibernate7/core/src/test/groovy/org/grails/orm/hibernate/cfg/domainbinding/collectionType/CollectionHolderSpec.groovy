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

package org.grails.orm.hibernate.cfg.domainbinding.collectionType

import grails.gorm.tests.HibernateGormDatastoreSpec
import spock.lang.Subject
import spock.lang.Unroll

class CollectionHolderSpec extends HibernateGormDatastoreSpec {

    @Subject
    CollectionHolder holder

    def setup() {
        holder = new CollectionHolder(getGrailsDomainBinder().getMetadataBuildingContext())
    }

    @Unroll
    def "should return correct collection type for #collectionClass"() {
        expect:
        holder.get(collectionClass)?.getClass() == expectedType

        where:
        collectionClass | expectedType
        Set             | SetCollectionType
        SortedSet       | SetCollectionType
        List            | ListCollectionType
        Collection      | BagCollectionType
        Map             | MapCollectionType
    }

    def "should return null for unsupported type"() {
        expect:
        holder.get(String) == null
    }
}
