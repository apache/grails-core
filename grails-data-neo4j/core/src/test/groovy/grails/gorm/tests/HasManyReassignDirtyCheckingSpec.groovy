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
package grails.gorm.tests

import grails.gorm.annotation.Entity
import grails.neo4j.Neo4jEntity
import org.apache.grails.data.neo4j.core.Neo4jGormDatastoreSpec

/**
 * Reassigning a hasMany must leave the raw collection for Neo4jEntityPersister to wrap in its
 * own relationship-aware type on save. DirtyCheckingSupport.rewrap only re-wraps values whose
 * current wrapper is one of the plugin's exact generic classes — a store-specific subclass
 * (Neo4jList et al.) is left alone, because a generic replacement makes
 * createDirtyCheckableAwareCollection take its "already dirty-checkable" branch and in-place
 * removals then stop deleting relationships (adds still MERGE, so only removals go missing).
 */
class HasManyReassignDirtyCheckingSpec extends Neo4jGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(ReassignPlaylist, ReassignSong)
    }

    void "in-place removal after a hasMany reassignment still deletes the relationship"() {
        given:
        ReassignSong a = new ReassignSong(title: 'a').save(flush: true)
        ReassignSong b = new ReassignSong(title: 'b').save(flush: true)
        ReassignSong c = new ReassignSong(title: 'c').save(flush: true)
        ReassignPlaylist playlist = new ReassignPlaylist(name: 'mix').save(flush: true, validate: false)
        manager.session.clear()

        when: 'the association is reassigned wholesale, saved, then mutated in place and saved again'
        playlist = ReassignPlaylist.get(playlist.id)
        playlist.trackChanges()
        a = ReassignSong.get(a.id)
        playlist.songs = [a, ReassignSong.get(b.id), ReassignSong.get(c.id)]
        playlist.save(flush: true)
        playlist.songs.remove(a)
        playlist.save(flush: true)
        manager.session.clear()
        playlist = ReassignPlaylist.get(playlist.id)

        then: 'the removed relationship is gone after reload'
        playlist.songs.size() == 2
        playlist.songs*.title as Set == ['b', 'c'] as Set
    }
}

@Entity
class ReassignPlaylist implements Neo4jEntity<ReassignPlaylist> {
    String name
    List<ReassignSong> songs
    static hasMany = [songs: ReassignSong]
    static constraints = {
        songs nullable: true
    }
}

@Entity
class ReassignSong implements Neo4jEntity<ReassignSong> {
    String title
}
