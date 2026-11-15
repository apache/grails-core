/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package grails.gorm.tests

import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity
import spock.lang.Issue

import jakarta.persistence.criteria.JoinType

/**
 * Criteria shapes reported against Hibernate: an order inside an association block that must not change the join
 * type the criteria asked for, and the documented ways to ask whether a collection has a member in a where query.
 */
class ReportedCriteriaShapesHibernateSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(ShapeBook, ShapeAuthor, ShapeNode)
    }

    @Issue('https://github.com/apache/grails-core/issues/14543')
    void "an order in an association block keeps the left join the criteria asked for"() {
        given:
        ShapeAuthor writer = new ShapeAuthor(name: 'a').save(failOnError: true)
        new ShapeBook(title: 'with', author: writer).save(failOnError: true)
        new ShapeBook(title: 'without').save(flush: true, failOnError: true)
        manager.session.clear()

        when:
        List<String> titles = new DetachedCriteria(ShapeBook).build {
            author {
                order('name')
            }
            join('author', JoinType.LEFT)
        }.list()*.title.sort()

        then: 'the book without an author is still there'
        titles == ['with', 'without']
    }

    @Issue('https://github.com/apache/grails-core/issues/14530')
    void "a where query on a self-referencing hasMany asks for a collection member with a block or a size, as the guide documents"() {
        given:
        ShapeNode kid = new ShapeNode(label: 'kid').save(failOnError: true)
        ShapeNode top = new ShapeNode(label: 'top', children: [kid]).save(failOnError: true)
        ShapeNode alone = new ShapeNode(label: 'alone').save(flush: true, failOnError: true)
        manager.session.clear()
        Long kidId = kid.id
        Long topId = top.id
        Long aloneId = alone.id

        when: 'the queries are built in a when block, where the where transformation applies'
        List<String> byLabel = ShapeNode.where { children { label == 'kid' } }.list()*.label
        List<String> byMissingLabel = ShapeNode.where { children { label == 'top' } }.list()*.label
        List<String> byId = ShapeNode.where { children { id == kidId } }.list()*.label
        List<String> byIdOfNonMember = ShapeNode.where { children { id == aloneId } }.list()*.label
        List<String> byIdOfOwner = ShapeNode.where { children { id == topId } }.list()*.label
        List<String> bySize = ShapeNode.where { children.size() == 1 }.list()*.label

        then:
        byLabel == ['top']
        byMissingLabel.isEmpty()
        byId == ['top']
        byIdOfNonMember.isEmpty()
        byIdOfOwner.isEmpty()
        bySize == ['top']
    }
}

@Entity
class ShapeAuthor {
    String name
}

@Entity
class ShapeBook {
    String title
    ShapeAuthor author

    static constraints = {
        author nullable: true
    }
}

@Entity
class ShapeNode {
    String label
    static hasMany = [children: ShapeNode]
}
