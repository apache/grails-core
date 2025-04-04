package org.grails.datastore.gorm.mongo

import grails.gorm.annotation.Entity
import grails.gorm.tests.GormDatastoreSpec
import org.grails.datastore.mapping.proxy.EntityProxy
import spock.lang.Issue

/**
 * Created by graemerocher on 14/10/16.
 */
class CustomIdProxySpec extends GormDatastoreSpec{

    @Issue('https://github.com/grails/grails-data-mapping/issues/813')
    void "Test custom id with proxies"() {
        when:
        CustomIdCompany c = new CustomIdCompany([ slug:'mycompany' ]).insert()
        CustomIdTeam t = new CustomIdTeam([ slug: 'myteam', company: c ]).insert(flush: true)
        session.clear()
        t = CustomIdTeam.findBySlug('myteam')

        then:
        t.company instanceof EntityProxy
        !t.company.isInitialized()
        t.company.slug == 'mycompany'
    }

    @Override
    List getDomainClasses() {
        [CustomIdCompany, CustomIdTeam]
    }
}
@Entity
class CustomIdCompany {
    String slug
    static mapping = {
        id name: 'slug'
    }
}
@Entity
class CustomIdTeam {
    String slug
    CustomIdCompany company
    static mapping = {
        id name: 'slug'
    }
}