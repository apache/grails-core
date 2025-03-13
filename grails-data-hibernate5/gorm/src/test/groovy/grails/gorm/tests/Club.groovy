package grails.gorm.tests

import grails.gorm.hibernate.HibernateEntity
import grails.gorm.annotation.Entity

@Entity
class Club implements HibernateEntity<Club> {
    String name

    @Override
    String toString() {
        name
    }
}
