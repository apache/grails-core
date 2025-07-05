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
import org.bson.types.ObjectId
import org.grails.datastore.mapping.proxy.EntityProxy
import spock.lang.Issue

/**
 * @author Graeme Rocher
 */
class InheritanceWithSingleEndedAssociationSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {

    void setupSpec() {
        manager.addAllDomainClasses([Node, NodeA, NodeB, NodeC])
    }

    @Issue('GPMONGODB-304')
    void "Test that inheritance works correctly with single ended associations"() {
        given: "An association that uses a parent class type"

        def a = new NodeA(a: 'A')
        def c = new NodeC(c: 'C')
        def b = new NodeB(b: 'B', childNode: a)
        def b2 = new NodeB(b: 'B2', childNode: c)
        a.save(validate: false)
        c.save(validate: false)
        b2.save(validate: false)
        b.save(flush: true, validate: false)
        manager.session.clear()

        when: "The association is queried with the get method"
        def nodeB = NodeB.get(b.id)
        def nodeB2 = NodeB.get(b2.id)

        then: "The correct type is returned for the association"
        nodeB.childNode instanceof EntityProxy
        nodeB.childNode.target instanceof NodeA
        nodeB2.childNode instanceof EntityProxy
        nodeB2.childNode.target instanceof NodeC

        when: "The association is queried with a finder"
        nodeB = NodeB.findById(b.id)
        nodeB2 = NodeB.findById(b2.id)
        then: "The correct type is returned for the association"
        nodeB.childNode.target instanceof NodeA
        nodeB2.childNode.target instanceof NodeC

//        nodeB = NodeB.findByB('B')
//        assertTrue(nodeB.childNode instanceof NodeA) // doesn't work, childNode is a Node
    }
}

@Entity
class Node {

    ObjectId id

    String name

    static constraints = {
    }

    static mapping = {
        version false
//        collection "node"
    }
}

@Entity
class NodeA extends Node {
    String a
}

@Entity
class NodeB extends Node {
    String b
    Node childNode
}

@Entity
class NodeC extends NodeA {
    String c
}