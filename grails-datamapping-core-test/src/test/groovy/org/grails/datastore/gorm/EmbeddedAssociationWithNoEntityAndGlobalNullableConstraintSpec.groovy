package org.grails.datastore.gorm

import grails.gorm.transactions.Transactional
import grails.persistence.Entity
import org.grails.datastore.gorm.validation.constraints.registry.DefaultValidatorRegistry
import org.grails.datastore.mapping.config.Settings
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.simple.SimpleMapDatastore
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

class EmbeddedAssociationWithNoEntityAndGlobalNullableConstraintSpec extends Specification {

    @Shared @AutoCleanup SimpleMapDatastore datastore = new SimpleMapDatastore(
        DatastoreUtils.createPropertyResolver((Settings.SETTING_DEFAULT_CONSTRAINTS): { '*'(nullable: true) }),
        [],
        User2
    )

    @Transactional
    @Issue('https://github.com/grails/grails-core/issues/10867')
    void "global constraints are applied to embedded properties defined as POGO"() {
        given: 'a user with only a few properties set'
        def user = new User2()
        user.username = 'admin'
        user.address.city = 'Madrid'

        def context = datastore.mappingContext
        context.setValidatorRegistry(new DefaultValidatorRegistry(context, datastore.getConnectionSources().getDefaultConnectionSource().settings))

        when: 'validating the user'
        user.validate()

        then: 'it has no errors because global constraints are applied'
        !user.hasErrors()
    }
}

@Entity
class User2 {
    Long id
    String username
    String password
    Address2 address = new Address2()

    static embedded = ['address']

    static mapping = {
    }
}

class Address2 {
    Long id
    String city
    String postCode
}
