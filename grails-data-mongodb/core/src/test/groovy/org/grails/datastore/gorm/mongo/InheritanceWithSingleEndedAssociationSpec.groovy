package org.grails.datastore.gorm.mongo

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity
import org.bson.types.ObjectId
import org.grails.datastore.mapping.proxy.EntityProxy
import spock.lang.Issue

/**
 * @author Graeme Rocher
 */
class InheritanceWithSingleEndedAssociationSpec extends GormDatastoreSpec {

    @Issue('GPMONGODB-304')
    void "Test that inheritance works correctly with single ended associations"() {
        given:"An association that uses a parent class type"

        def a = new NodeA(a: 'A')
        def c = new NodeC(c: 'C')
        def b = new NodeB(b: 'B', childNode: a)
        def b2 = new NodeB(b: 'B2', childNode: c)
        a.save(validate: false)
        c.save(validate: false)
        b2.save(validate: false)
        b.save(flush:true, validate: false)
        session.clear()

        when:"The association is queried with the get method"
        def nodeB = NodeB.get(b.id)
        def nodeB2 = NodeB.get(b2.id)

        then:"The correct type is returned for the association"
        nodeB.childNode instanceof EntityProxy
        nodeB.childNode.target instanceof NodeA
        nodeB2.childNode instanceof EntityProxy
        nodeB2.childNode.target instanceof NodeC

        when:"The association is queried with a finder"
        nodeB = NodeB.findById(b.id)
        nodeB2 = NodeB.findById(b2.id)
        then:"The correct type is returned for the association"
        nodeB.childNode.target instanceof NodeA
        nodeB2.childNode.target instanceof NodeC

//        nodeB = NodeB.findByB('B')
//        assertTrue(nodeB.childNode instanceof NodeA) // doesn't work, childNode is a Node


    }

    @Override
    List getDomainClasses() {
        [Node, NodeA, NodeB, NodeC]
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