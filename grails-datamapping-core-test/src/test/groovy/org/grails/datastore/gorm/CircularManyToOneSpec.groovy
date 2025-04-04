package org.grails.datastore.gorm

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity

class CircularManyToOneSpec extends GormDatastoreSpec {

    void "Test that a circular many-to-one persists correctly"() {
        when:"A self referencing domain model is created"
            TreeNode root = new TreeNode(parent: null, name: "root")
            root.save()

            TreeNode child = new TreeNode(parent: root, name: "child")
            child.save()

            TreeNode grandchild = new TreeNode(parent: child, name:"grandchild")
            grandchild.save(flush:true)

        then:"The associations are configured correctly"
            root.parent == null
            child.parent == root
            grandchild.parent == child

        when:"The model is queried"
            session.clear()
            grandchild = TreeNode.findByName("grandchild")

        then:"It is loaded correctly"
            grandchild.name == "grandchild"
            grandchild.parent.name == 'child'
            grandchild.parent.parent.name == 'root'
            grandchild.parent.parent.parent == null
    }

    @Override
    List getDomainClasses() {
        [TreeNode]
    }
}

@Entity
class TreeNode {
    Long id
    TreeNode parent
    String name
    static constraints = {
        parent nullable:true
    }
}
