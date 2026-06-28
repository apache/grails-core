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

/**
 * Verifies that an explicit {@code save()} marks the instance dirty so it is persisted even when no
 * tracked property has changed — i.e. a previously-saved, now-detached/clean instance can be
 * re-saved and re-inserted. Regression guard for the dropped {@code markDirty()} in
 * {@link org.grails.datastore.gorm.GormInstanceApi#save}, which made re-saving a clean instance a
 * no-op (observed downstream as empty association {@code <select>} option lists in grails-fields).
 */
class ExplicitSaveRepersistsSpec extends GrailsDataTckSpec<GrailsDataCoreTckManager> {

    void setupSpec() {
        manager.registerDomainClasses(ResaveWidget)
    }

    void "explicit save() re-persists a previously saved, now-cleared clean instance"() {
        given: "a saved and flushed instance (clean after flush)"
        ResaveWidget widget = new ResaveWidget(name: "a").save(flush: true)
        Serializable id = widget.id

        and: "its row is removed from the store, leaving 'widget' as a clean detached instance"
        ResaveWidget.get(id).delete(flush: true)
        manager.session.clear()

        expect: "the store is empty and the instance reports no pending changes"
        ResaveWidget.count() == 0
        !widget.isDirty()

        when: "the same clean instance is saved again"
        widget.save(flush: true)

        then: "the explicit save marks it dirty so it is re-inserted"
        ResaveWidget.count() == 1
        ResaveWidget.get(id)?.name == "a"
    }
}

@Entity
class ResaveWidget {

    String name
}
