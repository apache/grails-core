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

package grails.gorm.tests.belongsto

import grails.gorm.annotation.Entity
import org.apache.grails.data.hibernate5.core.GrailsDataHibernate5TckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.hibernate.Hibernate
import spock.lang.Issue

/**
 * {@code save()} on the owning side of a bidirectional one-to-one sets the back-reference of a new child
 * whose back-reference is still null, for both a foreign key in the owner and a foreign key in the child.
 */
@Issue('https://github.com/apache/grails-core/issues/16740')
class OneToOneBackReferenceSpec extends GrailsDataTckSpec<GrailsDataHibernate5TckManager> {

    void setupSpec() {
        manager.registerDomainClasses(
                BackRefAuthor, BackRefProfile, BackRefExtendedProfile,
                BackRefOwner, BackRefCard,
                BackRefKeeper, BackRefBadge)
    }

    void 'saving a new owner sets the back-reference of its new one-to-one child'() {
        given:
        BackRefProfile profile = new BackRefProfile(bio: 'Writes about GORM')
        BackRefAuthor author = new BackRefAuthor(name: 'Jane', profile: profile)

        when:
        author.save(flush: true)

        then:
        !author.hasErrors()
        profile.author.is(author)
        BackRefAuthor.count() == 1
        BackRefProfile.count() == 1

        when:
        manager.session.clear()
        BackRefProfile loaded = BackRefProfile.get(profile.id)

        then:
        loaded.author.id == author.id
    }

    void 'saving a persisted owner sets the back-reference of a child assigned later'() {
        given:
        BackRefAuthor author = new BackRefAuthor(name: 'Jane').save(flush: true)
        BackRefProfile profile = new BackRefProfile(bio: 'Writes about GORM')

        when:
        author.profile = profile
        author.save(flush: true)

        then:
        !author.hasErrors()
        profile.author.is(author)
        BackRefProfile.count() == 1
    }

    void 'saving the owner leaves an already set back-reference alone'() {
        given:
        BackRefAuthor other = new BackRefAuthor(name: 'Other').save(flush: true)
        BackRefProfile profile = new BackRefProfile(bio: 'Writes about GORM', author: other)
        BackRefAuthor author = new BackRefAuthor(name: 'Jane', profile: profile)

        when:
        author.save(flush: true)

        then:
        !author.hasErrors()
        profile.author.is(other)
        BackRefProfile.count() == 1
    }

    void 'saving the owner sets the back-reference on a child that is a subclass of the association type'() {
        given:
        BackRefExtendedProfile profile = new BackRefExtendedProfile(bio: 'Writes about GORM', website: 'https://example.com')
        BackRefAuthor author = new BackRefAuthor(name: 'Jane', profile: profile)

        when:
        author.save(flush: true)

        then:
        !author.hasErrors()
        profile.author.is(author)
        BackRefExtendedProfile.count() == 1
    }

    void 'saving the owner does not initialize a child that is an uninitialized proxy'() {
        given:
        BackRefProfile profile = new BackRefProfile(bio: 'Writes about GORM').save(flush: true)
        manager.session.clear()
        BackRefAuthor author = new BackRefAuthor(name: 'Jane', profile: BackRefProfile.proxy(profile.id))

        expect:
        !Hibernate.isInitialized(author.profile)

        when:
        author.save(flush: true)

        then:
        !author.hasErrors()
        !Hibernate.isInitialized(author.profile)
        BackRefAuthor.count() == 1

        when:
        manager.session.clear()
        BackRefProfile loaded = BackRefProfile.get(profile.id)

        then:
        loaded.author.id == author.id
    }

    void 'saving the owner sets the back-reference on the entity behind an initialized child proxy'() {
        given:
        BackRefProfile profile = new BackRefProfile(bio: 'Writes about GORM').save(flush: true)
        manager.session.clear()
        BackRefProfile proxy = BackRefProfile.proxy(profile.id)
        proxy.bio
        BackRefAuthor author = new BackRefAuthor(name: 'Jane', profile: proxy)

        expect:
        Hibernate.isInitialized(proxy)

        when:
        author.save(flush: true)

        then:
        !author.hasErrors()
        proxy.author.is(author)
        Hibernate.unproxy(proxy).author.is(author)
    }

    void 'saving the owner leaves a back-reference that is an uninitialized proxy alone'() {
        given:
        BackRefAuthor other = new BackRefAuthor(name: 'Other').save(flush: true)
        manager.session.clear()
        BackRefAuthor otherProxy = BackRefAuthor.proxy(other.id)
        BackRefProfile profile = new BackRefProfile(bio: 'Writes about GORM', author: otherProxy)
        BackRefAuthor author = new BackRefAuthor(name: 'Jane', profile: profile)

        expect:
        !Hibernate.isInitialized(otherProxy)

        when:
        author.save(flush: true)

        then:
        !author.hasErrors()
        profile.author.is(otherProxy)
        !Hibernate.isInitialized(otherProxy)
        BackRefProfile.count() == 1
    }

    void 'saving a new hasOne owner inserts a child whose foreign key comes from the back-reference'() {
        given:
        BackRefCard card = new BackRefCard(label: 'Library card')
        BackRefOwner owner = new BackRefOwner(name: 'Jane', card: card)

        when:
        owner.save(flush: true)

        then:
        !owner.hasErrors()
        card.owner.is(owner)
        BackRefOwner.count() == 1
        BackRefCard.count() == 1

        when:
        manager.session.clear()
        BackRefCard loaded = BackRefCard.get(card.id)

        then:
        loaded.owner.id == owner.id
    }

    void 'saving a persisted hasOne owner inserts a child assigned later'() {
        given:
        BackRefOwner owner = new BackRefOwner(name: 'Jane').save(flush: true)
        BackRefCard card = new BackRefCard(label: 'Library card')

        when:
        owner.card = card
        owner.save(flush: true)

        then:
        !owner.hasErrors()
        card.owner.is(owner)
        BackRefCard.count() == 1
    }

    void 'a non-nullable back-reference is set before the child is validated'() {
        given:
        BackRefBadge badge = new BackRefBadge(code: 'A-1')
        BackRefKeeper keeper = new BackRefKeeper(name: 'Jane', badge: badge)

        when:
        keeper.save(flush: true, failOnError: true)

        then:
        !keeper.hasErrors()
        badge.keeper.is(keeper)
        BackRefBadge.count() == 1
    }
}

@Entity
class BackRefAuthor {

    String name
    BackRefProfile profile

    static constraints = {
        profile nullable: true
    }
}

@Entity
class BackRefProfile {

    String bio

    static belongsTo = [author: BackRefAuthor]

    static constraints = {
        author nullable: true
    }
}

@Entity
class BackRefExtendedProfile extends BackRefProfile {

    String website
}

@Entity
class BackRefOwner {

    String name

    static hasOne = [card: BackRefCard]

    static constraints = {
        card nullable: true
    }
}

@Entity
class BackRefCard {

    String label

    static belongsTo = [owner: BackRefOwner]

    static constraints = {
        owner nullable: true
    }
}

@Entity
class BackRefKeeper {

    String name

    static hasOne = [badge: BackRefBadge]

    static constraints = {
        badge nullable: true
    }
}

@Entity
class BackRefBadge {

    String code
    BackRefKeeper keeper

    static constraints = {
        keeper nullable: false
    }
}
