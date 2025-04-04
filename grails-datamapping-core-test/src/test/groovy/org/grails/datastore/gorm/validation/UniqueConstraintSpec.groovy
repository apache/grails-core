package org.grails.datastore.gorm.validation

import grails.gorm.annotation.Entity
import grails.gorm.transactions.Transactional
import org.grails.datastore.gorm.validation.constraints.MappingContextAwareConstraintFactory
import org.grails.datastore.gorm.validation.constraints.builtin.UniqueConstraint
import org.grails.datastore.gorm.validation.constraints.registry.ConstraintRegistry
import org.grails.datastore.gorm.validation.constraints.registry.DefaultValidatorRegistry
import org.grails.datastore.mapping.simple.SimpleMapDatastore
import org.springframework.context.support.StaticMessageSource
import spock.lang.AutoCleanup
import spock.lang.Specification

@Transactional
class UniqueConstraintSpec extends Specification {

    @AutoCleanup SimpleMapDatastore datastore = new SimpleMapDatastore(
            Channel,
            DefaultChannel,
            ListChannel,
            OtherListChannel,
            Organization
    )

    def setup() {
        ConstraintRegistry constraintRegistry = new DefaultValidatorRegistry(
                datastore.mappingContext,
                datastore.connectionSources.defaultConnectionSource.settings
        )

        def messageSource = new StaticMessageSource()
        constraintRegistry.addConstraintFactory(
                new MappingContextAwareConstraintFactory(UniqueConstraint.class, messageSource, datastore.mappingContext)
        )

        datastore.mappingContext.setValidatorRegistry(constraintRegistry)
    }

    void 'unique constraint works with parent/child'() {
        given: 'an existing channel'
        def testOrg = new Organization(name: 'Test 1')
        testOrg.defaultChannel.organization = testOrg
        testOrg.save(failOnError: true, flush: true)
        def defaultChannel1 = testOrg.defaultChannel

        when: 'a new channel with the same name is created'
        def defaultChannel2 = new DefaultChannel(name: defaultChannel1.name, organization: testOrg)

        then:
        !defaultChannel2.validate()
        defaultChannel2.hasErrors()
        defaultChannel2.errors.getFieldError('name').code == 'unique'

    }

    void "test unique constraint checks parent field"() {

        setup:
        Organization organization = new Organization(name: "Test Org")
        organization.defaultChannel.organization = organization
        organization.addToChannels(name: "Foo")
        organization.addToChannels(name: "Bar")
        organization.save(flush: true, failOnError: true)
        datastore.currentSession.clear()

        when: "we change the channel name to an existing channel name in the organization"
        Channel channel = Channel.findByName("Bar")
        channel.name = "Foo"

        then:
        !channel.validate()
        channel.hasErrors()
        channel.errors.getFieldError('name').code == 'unique'

        cleanup:
        Channel.deleteAll()
        Organization.deleteAll(organization)
    }

    void 'unique constraint works with parent/child/child'() {
        given: 'an existing channel'
        def testOrg = new Organization(name: 'Test 1')
        testOrg.defaultChannel.organization = testOrg
        testOrg.save(failOnError: true, flush: true)
        def defaultChannel1 = testOrg.defaultChannel

        when: 'a new channel with the same name is created'
        def defaultChannel2 = new OtherListChannel(name: defaultChannel1.name, organization: testOrg)

        then:
        !defaultChannel2.validate()
        defaultChannel2.hasErrors()
        defaultChannel2.errors.getFieldError('name').code == 'unique'

    }

    void "unique constraint works in sibling classes"() {
        given: 'an existing channel'
        def testOrg = new Organization(name: 'Test 1')
        testOrg.defaultChannel.organization = testOrg
        testOrg.save(failOnError: true, flush: true)

        and: 'a channel is added'
        def alphaChannel = new ListChannel(name: 'Alpha', organization: testOrg).save(failOnError: true, flush: true)

        when: 'a new channel with the same name is created'
        def alphaChannel2 = new ListChannel(name: alphaChannel.name, organization: testOrg)

        then:
        !alphaChannel2.validate()
        alphaChannel2.hasErrors()
        alphaChannel2.errors.getFieldError('name').code == 'unique'

        when: 'the channel belongs to another org'
        alphaChannel2.clearErrors()
        def testOrg2 = new Organization(name: 'Test 2')
        testOrg2.defaultChannel.organization = testOrg2
        testOrg2.save(failOnError: true, flush: true)
        alphaChannel2.organization = testOrg2

        then:
        alphaChannel2.validate()
    }

    void 'unique constraint works with hasOne'() {
        given: 'an existing channel'
        def testOrg = new Organization(
                name: 'Test 1'
        )
        testOrg.defaultChannel.organization = testOrg
        testOrg.save(failOnError: true, flush: true)

        when: 'a new org is created'
        def testOrg2 = new Organization(
                name: 'Test 2'
        )
        testOrg2.defaultChannel.organization = testOrg2

        then: 'that org is also valid'
        testOrg2.save(failOnError: true, flush: true)
    }
}


@Entity
class Channel {

    String name
    static belongsTo = [organization: Organization]

    static constraints = {
        name size: 1..80, unique: 'organization'
    }
}

@Entity
class DefaultChannel extends Channel {

    static constraints = {
    }

    def beforeValidate() {
        if (!name) {
            name = 'Default'
        }
    }
}

@Entity
class ListChannel extends Channel {

    static constraints = {
    }
}


@Entity
class OtherListChannel extends ListChannel {

    static constraints = {
    }
}

@Entity
class Organization {
    String name

    DefaultChannel defaultChannel = new DefaultChannel()

    static hasOne = [defaultChannel: DefaultChannel]
    static hasMany = [channels: Channel]
}