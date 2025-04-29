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
package org.grails.datastore.gorm

import grails.persistence.Entity
import org.apache.grails.data.simple.core.GrailsDataCoreTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec

class CircularManyToOneSpec extends GrailsDataTckSpec<GrailsDataCoreTckManager> {
    void setupSpec() {
        manager.domainClasses.addAll([TreeNode])
    }

    void "Test that a circular many-to-one persists correctly"() {
        when: "A self referencing domain model is created"
        TreeNode root = new TreeNode(parent: null, name: "root")
        root.save()

        TreeNode child = new TreeNode(parent: root, name: "child")
        child.save()

        TreeNode grandchild = new TreeNode(parent: child, name: "grandchild")
        grandchild.save(flush: true)

        then: "The associations are configured correctly"
        root.parent == null
        child.parent == root
        grandchild.parent == child

        when: "The model is queried"
        manager.session.clear()
        grandchild = TreeNode.findByName("grandchild")

        then: "It is loaded correctly"
        grandchild.name == "grandchild"
        grandchild.parent.name == 'child'
        grandchild.parent.parent.name == 'root'
        grandchild.parent.parent.parent == null
    }
}

@Entity
class TreeNode {
    Long id
    TreeNode parent
    String name
    static constraints = {
        parent nullable: true
    }
}
