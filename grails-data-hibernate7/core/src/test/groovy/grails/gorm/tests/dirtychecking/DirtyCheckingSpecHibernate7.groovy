package grails.gorm.specs.dirtychecking
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
import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec

class DirtyCheckingSpecHibernate7 extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.addAllDomainClasses([DirtyCheckingTestBookHibernate7])
    }

    void "When marking whole class dirty, then derived and transient properties are still not dirty"() {
        when:
        DirtyCheckingTestBookHibernate7 book = new DirtyCheckingTestBookHibernate7()
        book.title = "Test"
        and: "mark class as not dirty - to clear previous dirty tracking"
        book.trackChanges()

        then:
        !book.hasChanged()

        when: "Mark whole class as dirty"
        book.markDirty()

        then: "whole class is dirty"
        book.hasChanged()

        and: "The formula and transient properties are not dirty"
        !book.hasChanged('formulaProperty')
        !book.hasChanged('transientProperty')

        and: "Other properties are"
        book.hasChanged('id')
        book.hasChanged('title')

    }

    void "Test that dirty tracking doesn't apply on Entity's transient properties"() {
        when:
        DirtyCheckingTestBookHibernate7 book = new DirtyCheckingTestBookHibernate7()
        book.title = "Test"
        and: "mark class as not dirty, clear previous dirty tracking"
        book.trackChanges()

        then:
        !book.hasChanged()

        when: "update transient property"
        book.transientProperty = "new transient value"

        then: "class is not dirty"
        !book.hasChanged()

        and: "transient properties are not dirty"
        !book.hasChanged('transientProperty')
    }
}

@Entity
class DirtyCheckingTestBookHibernate7 implements Serializable {

    Long id
    String title

    String formulaProperty

    String transientProperty

    static mapping = {
        formulaProperty(formula: 'name || \' (formula)\'')
    }

    static transients = ['transientProperty']
}

@Entity
class DirtyCheckingTestAuthorHibernate7 implements Serializable {
    Long id
    String name
    Integer age

    @Override
    boolean equals(Object o) {
        if (this.is(o)) return true
        if (o == null || getClass() != o.class) return false

        DirtyCheckingTestAuthorHibernate7 that = (DirtyCheckingTestAuthorHibernate7) o

        if (id != null ? !id.equals(that.id) : that.id != null) return false
        return true
    }

    @Override
    int hashCode() {
        return id != null ? id.hashCode() : 0
    }
}
