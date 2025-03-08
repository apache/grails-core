package functional.tests

import org.grails.orm.hibernate.HibernateDatastore

class BootStrap {

    HibernateDatastore hibernateDatastore

    def init = {
        assert hibernateDatastore.connectionSources.defaultConnectionSource.settings.hibernate.getConfigClass() == CustomHibernateMappingContextConfiguration
        Product.withTransaction {
            new Product(name: "MacBook", price: "1200.01").save()
        }
    }

    def destroy = {
    }
}
