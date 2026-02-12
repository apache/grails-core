package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.hibernate.mapping.Column
import org.hibernate.mapping.BasicValue
import org.hibernate.mapping.Table
import spock.lang.Subject

import org.grails.orm.hibernate.cfg.domainbinding.util.SimpleValueColumnFetcher

class SimpleValueColumnFetcherSpec extends HibernateGormDatastoreSpec {

    @Subject
    SimpleValueColumnFetcher fetcher = new SimpleValueColumnFetcher()

    def "should return first column when present"() {
        given:
        def table = new Table("test")
        def simpleValue = new BasicValue(getGrailsDomainBinder().getMetadataBuildingContext(), table)
        def column1 = new Column("col1")
        def column2 = new Column("col2")
        simpleValue.addColumn(column1)
        simpleValue.addColumn(column2)

        when:
        def result = fetcher.getColumnForSimpleValue(simpleValue)

        then:
        result == column1
    }

    def "should return null when columns are empty"() {
        given:
        def table = new Table("test")
        def simpleValue = new BasicValue(getGrailsDomainBinder().getMetadataBuildingContext(), table)

        when:
        def result = fetcher.getColumnForSimpleValue(simpleValue)

        then:
        result == null
    }
}
