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
package org.grails.web.binding

import grails.gorm.dirty.checking.DirtyCheck
import grails.persistence.Entity
import groovy.transform.CompileStatic
import org.grails.validation.ConstraintEvalUtils
import spock.lang.Issue
import spock.lang.Specification

class DefaultASTDatabindingHelperDomainClassSpecialPropertiesSpec extends
        Specification {

    def setup() {
        ConstraintEvalUtils.clearDefaultConstraints()
    }

    def cleanup() {
        ConstraintEvalUtils.clearDefaultConstraints()
    }

    @Issue('GRAILS-11173')
    void 'Test binding to special properties in a domain class'() {
        when:
        Date now = new Date()
        SomeDomainClass obj = new SomeDomainClass(dateCreated: now, lastUpdated: now)
        
        then:
        obj.dateCreated == null
        obj.lastUpdated == null
    }
    
    @Issue('GRAILS-11173')
    void 'Test binding to special properties in a domain class with explicit bindable rules'() {
        when:
        def now = new Date()
        def obj = new SomeDomainClassWithExplicitBindableRules(dateCreated: now, lastUpdated: now)
        
        then:
        obj.dateCreated == now
        obj.lastUpdated == now
    }

    @Issue('https://github.com/apache/grails-core/issues/15681')
    void 'Test id and version are not bound when a domain class extends a @DirtyCheck abstract base class'() {
        when: 'a domain class that extends a @DirtyCheck abstract base is bound with id and version'
        def obj = new MyDirtyCheckedDomain(id: 99L, version: 7L, name: 'Grace')

        then: 'the regular property is bound but id and version are not'
        obj.name == 'Grace'
        obj.id == null
        obj.version == null
    }

    @Issue('https://github.com/apache/grails-core/issues/15681')
    void 'Test dateCreated and lastUpdated declared on a @DirtyCheck abstract base are not bound in the domain subclass'() {
        when: 'a domain subclass inheriting timestamp properties from a @DirtyCheck base is bound'
        def now = new Date()
        def obj = new DomainWithInheritedTimestamps(id: 1L, version: 2L, name: 'Alan', title: 'Mathematician', dateCreated: now, lastUpdated: now)

        then: 'regular inherited and declared properties are bound'
        obj.name == 'Alan'
        obj.title == 'Mathematician'

        and: 'the default-excluded special properties are not bound regardless of where they are declared'
        obj.id == null
        obj.version == null
        obj.dateCreated == null
        obj.lastUpdated == null
    }

    @Issue('https://github.com/apache/grails-core/issues/15681')
    void 'Test a regular property declared on a @DirtyCheck abstract base is still bound in the domain subclass'() {
        when: 'only regular properties are bound'
        def obj = new DomainWithInheritedTimestamps(name: 'Grace', title: 'Admiral')

        then: 'all regular properties are bound and the special properties remain null'
        obj.name == 'Grace'
        obj.title == 'Admiral'
        obj.id == null
        obj.version == null
    }

    @Issue('https://github.com/apache/grails-core/issues/15681')
    void 'Test explicit bindable:true on inherited special properties re-enables binding without affecting id and version'() {
        when: 'a domain subclass declares an explicit bindable:true constraint for inherited timestamp properties'
        def now = new Date()
        def obj = new DomainWithInheritedBindableTimestamps(id: 5L, version: 3L, name: 'Edsger', title: 'Scientist', dateCreated: now, lastUpdated: now)

        then: 'the explicitly bindable timestamps are bound'
        obj.dateCreated == now
        obj.lastUpdated == now

        and: 'regular properties are bound'
        obj.name == 'Edsger'
        obj.title == 'Scientist'

        and: 'id and version remain excluded as there is no explicit override for them'
        obj.id == null
        obj.version == null
    }

    @Issue('https://github.com/apache/grails-core/issues/15795')
    void 'Test explicit bindable:true on a special property declared in a @DirtyCheck abstract base is inherited by the domain subclass'() {
        when: 'a domain subclass inherits a bindable:true id constraint from its abstract base and declares no constraint of its own'
        def obj = new DomainInheritingBindableId(id: 11L, version: 4L, name: 'Ada', title: 'Countess')

        then: 'the explicitly bindable id is bound through inheritance'
        obj.id == 11L

        and: 'regular properties are bound'
        obj.name == 'Ada'
        obj.title == 'Countess'

        and: 'version, which was not made explicitly bindable, remains excluded'
        obj.version == null
    }

    @Issue('https://github.com/apache/grails-core/issues/15795')
    void 'Test a bindable:false override in the domain subclass wins over an inherited bindable:true special property constraint'() {
        when: 'a domain subclass overrides the inherited bindable:true id constraint with bindable:false'
        def obj = new DomainOverridingBindableIdToFalse(id: 22L, version: 6L, name: 'Linus', title: 'Engineer')

        then: 'regular properties are bound'
        obj.name == 'Linus'
        obj.title == 'Engineer'

        and: 'the subclass override prevents id from being bound and version stays excluded'
        obj.id == null
        obj.version == null
    }

    @Issue('https://github.com/apache/grails-core/issues/15795')
    void 'Test an explicit bindable:true special property constraint propagates through an intermediate plain abstract level'() {
        when: 'a domain class extends a plain abstract class which extends a @DirtyCheck base declaring id bindable:true'
        def obj = new DomainInheritingBindableIdThroughIntermediate(id: 33L, version: 8L, grandparentName: 'g', parentName: 'p', childName: 'c')

        then: 'the explicitly bindable id is bound even though the constraint is declared two levels up'
        obj.id == 33L

        and: 'all regular properties throughout the hierarchy are bound'
        obj.grandparentName == 'g'
        obj.parentName == 'p'
        obj.childName == 'c'

        and: 'version, which was not made explicitly bindable, remains excluded'
        obj.version == null
    }

    @Issue('https://github.com/apache/grails-core/issues/15681')
    void 'Test id and version are not bound across multiple levels of abstract inheritance'() {
        when: 'a domain class extends a plain abstract class which extends a @DirtyCheck abstract base'
        def obj = new DomainExtendingMultiLevelHierarchy(id: 42L, version: 9L, grandparentName: 'g', parentName: 'p', childName: 'c')

        then: 'all regular properties throughout the hierarchy are bound'
        obj.grandparentName == 'g'
        obj.parentName == 'p'
        obj.childName == 'c'

        and: 'id and version are not bound even though they are injected several levels up the hierarchy'
        obj.id == null
        obj.version == null
    }
}
        
@Entity
class SomeDomainClass {
    Date dateCreated
    Date lastUpdated
}

@Entity
class SomeDomainClassWithExplicitBindableRules {
    Date dateCreated
    Date lastUpdated

    static constraints = {
        dateCreated bindable: true
        lastUpdated bindable: true
    }
}

@DirtyCheck
@CompileStatic
abstract class AbstractDirtyCheckedBase {
    String name
}

@Entity
class MyDirtyCheckedDomain extends AbstractDirtyCheckedBase {
    static constraints = {
        name nullable: false
    }
}

@DirtyCheck
@CompileStatic
abstract class AbstractDirtyCheckedBaseWithTimestamps {
    String name
    Date dateCreated
    Date lastUpdated
}

@Entity
class DomainWithInheritedTimestamps extends AbstractDirtyCheckedBaseWithTimestamps {
    String title
}

@Entity
class DomainWithInheritedBindableTimestamps extends AbstractDirtyCheckedBaseWithTimestamps {
    String title

    static constraints = {
        dateCreated bindable: true
        lastUpdated bindable: true
    }
}

@DirtyCheck
abstract class AbstractDirtyCheckedBaseWithBindableId {
    String name

    static constraints = {
        id bindable: true
    }
}

@Entity
class DomainInheritingBindableId extends AbstractDirtyCheckedBaseWithBindableId {
    String title
}

@Entity
class DomainOverridingBindableIdToFalse extends AbstractDirtyCheckedBaseWithBindableId {
    String title

    static constraints = {
        id bindable: false
    }
}

@DirtyCheck
abstract class AbstractDirtyCheckedGrandparentWithBindableId {
    String grandparentName

    static constraints = {
        id bindable: true
    }
}

@CompileStatic
abstract class AbstractPlainParentOfBindableId extends AbstractDirtyCheckedGrandparentWithBindableId {
    String parentName
}

@Entity
class DomainInheritingBindableIdThroughIntermediate extends AbstractPlainParentOfBindableId {
    String childName
}

@DirtyCheck
@CompileStatic
abstract class AbstractDirtyCheckedGrandparent {
    String grandparentName
}

@CompileStatic
abstract class AbstractPlainParent extends AbstractDirtyCheckedGrandparent {
    String parentName
}

@Entity
class DomainExtendingMultiLevelHierarchy extends AbstractPlainParent {
    String childName
}
