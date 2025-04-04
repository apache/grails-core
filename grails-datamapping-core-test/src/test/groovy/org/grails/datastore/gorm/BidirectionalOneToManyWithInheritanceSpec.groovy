package org.grails.datastore.gorm

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity

/**
 * @author graemerocher
 */
class BidirectionalOneToManyWithInheritanceSpec extends GormDatastoreSpec {

    void "Test a bidirectional one-to-many association with inheritance"() {

        given:
        def doc = new Documentation()

        doc.addToConfigurationItems(new ChangeRequest())
                .addToConfigurationItems(new Documentation())

        when:
        doc.save(flush:true)
        session.clear()
        doc = Documentation.get(1)

        then:
        doc.configurationItems.size() == 2
    }

    @Override
    List getDomainClasses() {
        [ConfigurationItem, Documentation, ChangeRequest]
    }
}

@Entity
class ConfigurationItem {
    Long id
    Long version
    ConfigurationItem parent

    Set configurationItems

    static hasMany = [configurationItems: ConfigurationItem]
    static mappedBy = [configurationItems: 'parent']
    static belongsTo = [ConfigurationItem]
    static constraints = {
        parent(nullable: true)
    }
}

@Entity
class Documentation extends ConfigurationItem {
    Long id
    Long version
}

@Entity
class ChangeRequest extends ConfigurationItem {
    Long id
    Long version
}