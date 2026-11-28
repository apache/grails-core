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
import org.apache.grails.data.hibernate5.core.GrailsDataHibernate5TckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import spock.lang.Issue

@Issue('https://github.com/apache/grails-core/issues/14354')
class MergeDetachedDirtyFieldsSpec extends GrailsDataTckSpec<GrailsDataHibernate5TckManager> {

    void setupSpec() {
        manager.domainClasses.addAll([MergeDirtyData, MergePrimitiveIdData, MergeAssignedStringId, MergeAssignedLongId])
    }

    void "merge writes the changed fields of a detached instance"() {
        given:
        MergeDirtyData data = new MergeDirtyData(name: 'a', number: 1).save(flush: true)
        manager.session.clear()

        when: 'the detached instance is changed and merged'
        data.name = 'dirty'
        data.number = 2
        MergeDirtyData merged = data.merge(flush: true)
        manager.session.clear()
        MergeDirtyData reloaded = MergeDirtyData.get(data.id)

        then: 'the merged instance and the stored row carry the changes'
        merged.name == 'dirty'
        reloaded.name == 'dirty'
        reloaded.number == 2
    }

    void "merge writes an instance that reports a change without naming the properties"() {
        given:
        MergeDirtyData data = new MergeDirtyData(name: 'a', number: 1).save(flush: true)
        manager.session.clear()

        when: 'the instance carries the id and version of the row, is made by hand, restored from a serialized copy, or marked dirty as a whole'
        MergeDirtyData instance = new MergeDirtyData(name: 'changed', number: 2)
        instance.id = data.id
        instance.version = data.version
        if (form == 'serialized') {
            instance = serialized(instance)
        } else if (form == 'marked') {
            instance.markDirty()
        }
        MergeDirtyData merged = instance.merge(flush: true)
        manager.session.clear()

        then: 'the row carries its values'
        merged.name == 'changed'
        MergeDirtyData.get(data.id).name == 'changed'
        MergeDirtyData.get(data.id).number == 2

        where:
        form << ['new', 'serialized', 'marked']
    }

    void "merge of a new instance inserts it once"() {
        when: 'a new instance is merged'
        MergeDirtyData merged = new MergeDirtyData(name: 'new', number: 1).merge(flush: true)

        then: 'the insert is not followed by an update'
        merged.version == 0
        MergeDirtyData.count() == 1
    }

    void "merge of a new instance that tracks a named change is not followed by an update"() {
        given: 'a new instance whose property is set after construction, so the change is tracked by name'
        MergeDirtyData data = new MergeDirtyData()
        data.name = 'tracked'
        data.number = 1

        when:
        MergeDirtyData merged = data.merge(flush: true)

        then: 'the insert is the only write'
        merged.version == 0
        MergeDirtyData.count() == 1
    }

    void "merge of a new instance with a primitive id is not followed by an update"() {
        when: 'the id of a new instance is the primitive default 0, not null'
        MergePrimitiveIdData merged = new MergePrimitiveIdData(name: 'new').merge(flush: true)

        then: 'it is inserted once'
        merged.version == 0
        MergePrimitiveIdData.count() == 1
    }

    @Issue('https://github.com/apache/grails-core/issues/17045')
    void "merge of a new instance with an assigned id is inserted once, as save is"() {
        when: 'a new instance with an assigned id is merged, and another is saved'
        MergeAssignedStringId merged = new MergeAssignedStringId(id: 'merged', title: 'x').merge(flush: true)
        MergeAssignedStringId saved = new MergeAssignedStringId(id: 'saved', title: 'x').save(flush: true, failOnError: true)

        then: 'the insert is not followed by an update'
        merged.version == 0
        saved.version == 0
        MergeAssignedStringId.count() == 2
    }

    @Issue('https://github.com/apache/grails-core/issues/17045')
    void "merge of a new instance with an assigned numeric id is inserted once"() {
        when:
        MergeAssignedLongId merged = new MergeAssignedLongId(id: 42L, title: 'x').merge(flush: true)

        then:
        merged.version == 0
        MergeAssignedLongId.count() == 1
    }

    @Issue('https://github.com/apache/grails-core/issues/17045')
    void "an instance inserted by merge with an assigned id is updated by a later change"() {
        given:
        MergeAssignedStringId merged = new MergeAssignedStringId(id: 'later', title: 'x').merge(flush: true)

        when: 'it is changed and saved'
        merged.title = 'changed'
        merged.save(flush: true, failOnError: true)
        manager.session.clear()

        then: 'the change is written once, so the version is 1'
        MergeAssignedStringId.get('later').title == 'changed'
        MergeAssignedStringId.get('later').version == 1
    }

    @Issue('https://github.com/apache/grails-core/issues/17045')
    void "merge of a detached instance with an assigned id still writes its changes"() {
        given:
        new MergeAssignedStringId(id: 'detached', title: 'a').save(flush: true, failOnError: true)
        manager.session.clear()

        when:
        MergeAssignedStringId detached = new MergeAssignedStringId(id: 'detached', title: 'b')
        detached.version = 0L
        detached.merge(flush: true)
        manager.session.clear()

        then:
        MergeAssignedStringId.get('detached').title == 'b'
    }

    void "merge of an instance the session already manages keeps its changes"() {
        given:
        MergeDirtyData data = new MergeDirtyData(name: 'a', number: 1).save(flush: true)

        when: 'the instance is still attached to the session'
        data.name = 'managed'
        MergeDirtyData merged = data.merge(flush: true)
        manager.session.clear()

        then: 'the merge returns the same instance and the row carries the change'
        merged.is(data)
        MergeDirtyData.get(data.id).name == 'managed'
    }

    private static MergeDirtyData serialized(MergeDirtyData data) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream()
        new ObjectOutputStream(bytes).withCloseable { ObjectOutputStream out -> out.writeObject(data) }
        new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())).withCloseable { ObjectInputStream input ->
            (MergeDirtyData) input.readObject()
        }
    }
}

@Entity
class MergeDirtyData implements Serializable {
    String name
    Integer number
}

@Entity
class MergePrimitiveIdData implements Serializable {
    long id
    String name
}

@Entity
class MergeAssignedStringId {
    String id
    Long version
    String title

    static mapping = {
        id generator: 'assigned'
    }
}

@Entity
class MergeAssignedLongId {
    Long id
    Long version
    String title

    static mapping = {
        id generator: 'assigned'
    }
}
