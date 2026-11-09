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
import spock.lang.Issue

@Issue('https://github.com/apache/grails-core/issues/14354')
class MergeDetachedDirtyFieldsSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(MergeDirtyData)
    }

    void "merge writes the changed fields of a detached instance"() {
        given:
        MergeDirtyData data = new MergeDirtyData(name: 'a', number: 1).save(flush: true)
        session.clear()

        when: 'the detached instance is changed and merged'
        data.name = 'dirty'
        data.number = 2
        MergeDirtyData merged = data.merge(flush: true)
        session.clear()
        MergeDirtyData reloaded = MergeDirtyData.get(data.id)

        then: 'the merged instance and the stored row carry the changes'
        merged.name == 'dirty'
        reloaded.name == 'dirty'
        reloaded.number == 2
    }

    void "merge writes an instance that reports a change without naming the properties"() {
        given:
        MergeDirtyData data = new MergeDirtyData(name: 'a', number: 1).save(flush: true)
        session.clear()

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
        session.clear()

        then: 'the row carries its values'
        merged.name == 'changed'
        MergeDirtyData.get(data.id).name == 'changed'
        MergeDirtyData.get(data.id).number == 2

        where:
        form << ['new', 'serialized', 'marked']
    }

    void "merge of an instance the session already manages keeps its changes"() {
        given:
        MergeDirtyData data = new MergeDirtyData(name: 'a', number: 1).save(flush: true)

        when: 'the instance is still attached to the session'
        data.name = 'managed'
        MergeDirtyData merged = data.merge(flush: true)
        session.clear()

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
