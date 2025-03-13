package grails.test.mixin.hibernate

import grails.gorm.annotation.Entity
import grails.test.hibernate.HibernateSpec

/**
 * Created by graemerocher on 15/07/2016.
 */
class HibernateSpecSpec extends HibernateSpec {

    void setup() {
        if (!Book.countByTitle("The Stand")) {
            new Book(title: "The Stand").save(flush:true)
        }
    }

    void "test hibernate spec"() {
        expect:
        hibernateDatastore.connectionSources.defaultConnectionSource.settings.dataSource.dbCreate == 'create-drop'
        hibernateDatastore.connectionSources.defaultConnectionSource.settings.dataSource.logSql == true
        Book.count() == 1
        !new Book().validate()
        !new Book(title: "").validate()
        hibernateSession != null
        sessionFactory != null
    }

    void "test hibernate spec with domain constraint inheritance"() {
        given:

        def player = new Player(sport: "Football", name: "Cantona", age: 50)
        player.validate()

        expect:
        !new Player().validate()
        !new Player(sport:"Football").validate()
        !new Player(sport:"Football", name: "Cantona").validate()
        !new Player(sport:"Football", name: "Cantona", age:70).validate()
        new Player(sport:"Football", name: "Cantona", age:50).validate()
    }

    void "Configuration defaults are correct"() {
        expect: "Default from application.yml"
        hibernateDatastore.failOnError == false
        and: "Default"
        hibernateDatastore.defaultFlushModeName == "COMMIT"
    }

    List<Class> getDomainClasses() { [Person, Player, Book] }
}

@Entity
class Person {
    String name
    Integer age
    String phone
    static constraints = {
        age min: 18, max: 65
        name blank: false
        phone nullable: true
    }
}
@Entity
class Player extends Person {
    String sport
    String height
    static constraints = {
        sport blank: false
        height nullable: true
    }
}

@Entity
class Book {
    String title

    static constraints = {
        title validator: { val ->
            val.asBoolean()
        }
    }
}
