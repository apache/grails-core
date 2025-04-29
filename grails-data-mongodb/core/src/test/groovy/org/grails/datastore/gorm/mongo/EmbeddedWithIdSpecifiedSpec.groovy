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
package org.grails.datastore.gorm.mongo

import grails.persistence.Entity
import org.apache.grails.data.mongo.core.GrailsDataMongoTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec

class EmbeddedWithIdSpecifiedSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {

    void setupSpec() {
        manager.domainClasses += [SystemCustomer, PreorderTreeNode, MultiLevelKpi]
    }

    void "Test that id is saved of embedded entity if specified"() {
        when: "A domain model with an embedded id specified"
        def sc = new SystemCustomer(name: "Bob", singleKpi: new MultiLevelKpi(id: "bar", name: "bar1", type: 'goods'))
        sc.kpis << new MultiLevelKpi(id: "foo", name: "foo1", type: "stuff")
        sc.save flush: true
        manager.session.clear()
        sc = SystemCustomer.get(sc.id)

        then: "The id is saved too"
        sc != null
        sc.kpis.size() == 1
        sc.kpis[0].id == "foo"
        sc.kpis[0].name == "foo1"
        sc.kpis[0].type == "stuff"
        sc.singleKpi != null
        sc.singleKpi.id == 'bar'
        sc.singleKpi.name == 'bar1'
    }
}

@Entity
class PreorderTreeNode {
    String id
    Integer left = 1
    Integer right = 2
}

@Entity
class SystemCustomer {
    String id
    List kpis = []
    static hasMany = [kpis: MultiLevelKpi]
    static embedded = ['kpis', 'singleKpi']

    String name
    MultiLevelKpi singleKpi

    String toString() { name }
}

@Entity
class MultiLevelKpi extends PreorderTreeNode {
    String name
    String type
}
