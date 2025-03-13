package org.grails.datastore.gorm.mongo

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity

class EmbeddedWithIdSpecifiedSpec extends GormDatastoreSpec {

    void "Test that id is saved of embedded entity if specified"() {
         when:"A domain model with an embedded id specified"
            def sc = new SystemCustomer(name: "Bob", singleKpi:new MultiLevelKpi(id: "bar", name: "bar1", type: 'goods'))
            sc.kpis << new MultiLevelKpi(id: "foo", name: "foo1", type: "stuff")
            sc.save flush:true
            session.clear()
            sc = SystemCustomer.get(sc.id)

         then:"The id is saved too"
            sc != null
            sc.kpis.size() == 1
            sc.kpis[0].id == "foo"
            sc.kpis[0].name == "foo1"
            sc.kpis[0].type == "stuff"
            sc.singleKpi != null
            sc.singleKpi.id == 'bar'
            sc.singleKpi.name == 'bar1'
    }

    @Override
    List getDomainClasses() {
        [SystemCustomer, PreorderTreeNode, MultiLevelKpi]
    }
}

@Entity
class PreorderTreeNode {
    String id
    Integer left = 1
    Integer right = 2
}

@Entity
class SystemCustomer {
    String id
    List kpis = []
    static hasMany = [kpis:MultiLevelKpi]
    static embedded = ['kpis', 'singleKpi']

    String name
    MultiLevelKpi singleKpi
    String toString() { name }
}

@Entity
class MultiLevelKpi extends PreorderTreeNode {
    String name
    String type
}
