package grails.gorm.tck

import grails.persistence.Entity

@Entity
class PersonEvent implements Serializable {
    Long id
    Long version
    String name
    Date dateCreated
    Date lastUpdated

    def personService

    static STORE_INITIAL = [
            beforeDelete: 0, afterDelete: 0,
            beforeUpdate: 0, afterUpdate: 0,
            beforeInsert: 0, afterInsert: 0,
            beforeLoad:   0, afterLoad:   0]

    static STORE = [:] + STORE_INITIAL

    static void resetStore() {
        STORE = [:] + STORE_INITIAL
    }

    def beforeDelete() {
        if (name == "DontDelete") {
            return false
        }
        STORE.beforeDelete++
    }

    void afterDelete() {
        STORE.afterDelete++
    }

    def beforeUpdate() {
        if (name == "Bad") {
            return false
        }
        STORE.beforeUpdate++
    }

    void afterUpdate() {
        STORE.afterUpdate++
    }

    def beforeInsert() {
        if (name == "Bad") {
            return false
        }
        STORE.beforeInsert++
    }

    void afterInsert() {
        STORE.afterInsert++
    }

    void beforeLoad() {
        STORE.beforeLoad++
    }

    void afterLoad() {
        STORE.afterLoad++
    }
}

