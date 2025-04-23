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

import grails.gorm.tests.GormDatastoreSpec
import grails.mongodb.MongoEntity
import grails.persistence.Entity
import org.bson.Document

/**
 * @author Noam Y. Tenne
 */
class BrokenManyToManyAssociationSpec extends GormDatastoreSpec {

    def 'Perform a cascading delete on a broken many-to-many relationship'() {
        given:'An owning entity with 2 owned entities'
        ReferencingEntity referencing = new ReferencingEntity()
        referencing = referencing.save(flush: true)
        referencing.addToReferencedEntities(new ReferencedEntity().save())
        referencing.addToReferencedEntities(new ReferencedEntity().save())

        referencing.save(flush: true)
        session.clear()

        when:'Low-level deleting 1 owned entity to simulate a broken relationship'
        ReferencedEntity.collection.deleteOne(new Document('_id': ReferencedEntity.find{}.id))
        session.clear()
        referencing = ReferencingEntity.find{}

        then:'Expect to still find 2 owned entities, but 1 of them is null (because the reference is broken)'
        referencing.referencedEntities.size() == 2
        referencing.referencedEntities.any { it == null }

        and:
        when:'Deleting the owning entity, thus invoking a cascading delete'
        referencing.delete(flush: true)
        session.clear()

        then:'Expect all the entities to be removed with no error'
        ReferencedEntity.count == 0
        ReferencingEntity.count == 0
    }

    @Override
    List getDomainClasses() {
        [ReferencingEntity, ReferencedEntity]
    }
}

@Entity
class ReferencingEntity implements MongoEntity<ReferencingEntity> {
    String id
    Set<ReferencedEntity> referencedEntities
    static hasMany = [referencedEntities: ReferencedEntity]
}

@Entity
class ReferencedEntity implements MongoEntity<ReferencedEntity> {
    String id
    static belongsTo = ReferencingEntity
    Set<ReferencingEntity> referencingEntities
    static hasMany = [referencingEntities: ReferencingEntity]
}
