package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import org.grails.orm.hibernate.HibernateDatastore
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * Tests the persistence behavior of various one-to-many cascade settings in GORM.
 * This spec uses a dedicated set of domain classes to ensure complete test isolation.
 */
class CascadeBehaviorPersisterSpec extends Specification {

    @AutoCleanup @Shared HibernateDatastore datastore = new HibernateDatastore(
            // Unidirectional
            ChildPersister,
            Owner_Default_Uni_P, Owner_All_Uni_P, Owner_SaveUpdate_Uni_P, Owner_Merge_Uni_P, Owner_Delete_Uni_P,
            Owner_Lock_Uni_P, Owner_Replicate_Uni_P, Owner_Evict_Uni_P, Owner_Persist_Uni_P,

            // Bidirectional
            Child_BT_Default_P,
            Child_BT_All_P,
            Child_BT_SaveUpdate_P, Child_BT_Merge_P, Child_BT_Delete_P,
            Child_BT_Lock_P, Child_BT_Replicate_P, Child_BT_Evict_P, Child_BT_Persist_P,
            Owner_Default_Bi_P,
            Owner_All_Bi_P,
            Owner_SaveUpdate_Bi_P, Owner_Merge_Bi_P, Owner_Delete_Bi_P,
            Owner_Lock_Bi_P, Owner_Replicate_Bi_P, Owner_Evict_Bi_P, Owner_Persist_Bi_P,

            // Orphan Removal
            Child_Orphan_P, Owner_Orphan_P
    )
    @Shared PlatformTransactionManager transactionManager = datastore.transactionManager

    // --- Unidirectional `hasMany` Persistence Tests ---

    @Rollback
    void "test unidirectional default cascade persists child"() {
        when: "A new owner is saved after adding a child"
        def owner = new Owner_Default_Uni_P(name: "Owner")
        owner.addToChildren(new ChildPersister(title: "Child"))
        owner.save(flush: true)

        then: "The owner is saved without errors and both owner and child exist"
        !owner.errors.hasErrors()
        Owner_Default_Uni_P.count() == 1
        ChildPersister.count() == 1
    }

    @Rollback
    void "test unidirectional 'all' cascade persists child"() {
        when: "A new owner is saved after adding a child"
        def owner = new Owner_All_Uni_P(name: "Owner")
        owner.addToChildren(new ChildPersister(title: "Child"))
        owner.save(flush: true)

        then: "The owner is saved without errors and both owner and child exist"
        !owner.errors.hasErrors()
        Owner_All_Uni_P.count() == 1
        ChildPersister.count() == 1
    }

    @Rollback
    void "test unidirectional 'save-update' cascade persists child"() {
        when: "A new owner is saved after adding a child"
        def owner = new Owner_SaveUpdate_Uni_P(name: "Owner")
        owner.addToChildren(new ChildPersister(title: "Child"))
        owner.save(flush: true)

        then: "The owner is saved without errors and both owner and child exist"
        !owner.errors.hasErrors()
        Owner_SaveUpdate_Uni_P.count() == 1
        ChildPersister.count() == 1
    }

    @Rollback
    void "test unidirectional 'persist' cascade persists child"() {
        when: "A new owner is saved after adding a child"
        def owner = new Owner_Persist_Uni_P(name: "Owner")
        owner.addToChildren(new ChildPersister(title: "Child"))
        owner.save(flush: true)

        then: "The owner is saved without errors and both owner and child exist"
        !owner.errors.hasErrors()
        Owner_Persist_Uni_P.count() == 1
        ChildPersister.count() == 1
    }


    // --- Bidirectional `hasMany` Persistence Tests ---

    @Rollback
    void "test bidirectional 'all' cascade persists child"() {
        when: "A new owner is saved after adding a child"
        def owner = new Owner_All_Bi_P(name: "Owner")
        owner.addToChildren(new Child_BT_All_P(title: "Child"))
        owner.save(flush: true)

        then: "The owner is saved without errors and both owner and child exist"
        !owner.errors.hasErrors()
        Owner_All_Bi_P.count() == 1
        Child_BT_All_P.count() == 1
    }

    @Rollback
    void "test bidirectional 'save-update' cascade persists child"() {
        when: "A new owner is saved after adding a child"
        def owner = new Owner_SaveUpdate_Bi_P(name: "Owner")
        owner.addToChildren(new Child_BT_SaveUpdate_P(title: "Child"))
        owner.save(flush: true)

        then: "The owner is saved without errors and both owner and child exist"
        !owner.errors.hasErrors()
        Owner_SaveUpdate_Bi_P.count() == 1
        Child_BT_SaveUpdate_P.count() == 1
    }

    @Rollback
    void "test bidirectional 'persist' cascade persists child"() {
        when: "A new owner is saved after adding a child"
        def owner = new Owner_Persist_Bi_P(name: "Owner")
        owner.addToChildren(new Child_BT_Persist_P(title: "Child"))
        owner.save(flush: true)

        then: "The owner is saved without errors and both owner and child exist"
        !owner.errors.hasErrors()
        Owner_Persist_Bi_P.count() == 1
        Child_BT_Persist_P.count() == 1
    }

    // --- Orphan Removal Persistence Test ---

    @Rollback
    void "test 'all-delete-orphan' cascade persists child"() {
        when: "A new owner is saved after adding a child"
        def owner = new Owner_Orphan_P(name: "Owner")
        owner.addToChildren(new Child_Orphan_P(title: "Child"))
        owner.save(flush: true)

        then: "The owner is saved without errors and both owner and child exist"
        !owner.errors.hasErrors()
        Owner_Orphan_P.count() == 1
        Child_Orphan_P.count() == 1
    }
}

// --- Domain Classes for Unidirectional One-to-Many Tests ---
@Entity
class ChildPersister {
    String title
}

@Entity
class Owner_Default_Uni_P {
    String name
    static hasMany = [children: ChildPersister]
}

@Entity
class Owner_All_Uni_P {
    String name
    static hasMany = [children: ChildPersister]
    static mapping = { children cascade: 'all' }
}

@Entity
class Owner_SaveUpdate_Uni_P {
    String name
    static hasMany = [children: ChildPersister]
    static mapping = { children cascade: 'save-update' }
}

@Entity
class Owner_Merge_Uni_P {
    String name
    static hasMany = [children: ChildPersister]
    static mapping = { children cascade: 'merge' }
}

@Entity
class Owner_Delete_Uni_P {
    String name
    static hasMany = [children: ChildPersister]
    static mapping = { children cascade: 'delete' }
}

@Entity
class Owner_Lock_Uni_P {
    String name
    static hasMany = [children: ChildPersister]
    static mapping = { children cascade: 'lock' }
}

@Entity
class Owner_Replicate_Uni_P {
    String name
    static hasMany = [children: ChildPersister]
    static mapping = { children cascade: 'replicate' }
}

@Entity
class Owner_Evict_Uni_P {
    String name
    static hasMany = [children: ChildPersister]
    static mapping = { children cascade: 'evict' }
}

@Entity
class Owner_Persist_Uni_P {
    String name
    static hasMany = [children: ChildPersister]
    static mapping = { children cascade: 'persist' }
}


// --- Domain Classes for Bidirectional One-to-Many Tests ---
@Entity
class Owner_Default_Bi_P {
    String name
    Set<Child_BT_Default_P> children
    static hasMany = [children: Child_BT_Default_P]
}

@Entity
class Child_BT_Default_P {
    String title
    static belongsTo = [owner: Owner_Default_Bi_P]
}

@Entity
class Owner_All_Bi_P {
    String name
    Set<Child_BT_All_P> children
    static hasMany = [children: Child_BT_All_P]
    static mapping = { children cascade: 'all' }
}

@Entity
class Child_BT_All_P {
    String title
    static belongsTo = [owner: Owner_All_Bi_P]
}

@Entity
class Owner_SaveUpdate_Bi_P {
    String name
    Set<Child_BT_SaveUpdate_P> children
    static hasMany = [children: Child_BT_SaveUpdate_P]
    static mapping = { children cascade: 'save-update' }
}

@Entity
class Child_BT_SaveUpdate_P {
    String title
    static belongsTo = [owner: Owner_SaveUpdate_Bi_P]
}

@Entity
class Owner_Persist_Bi_P {
    String name
    Set<Child_BT_Persist_P> children
    static hasMany = [children: Child_BT_Persist_P]
    static mapping = { children cascade: 'persist' }
}

@Entity
class Child_BT_Persist_P {
    String title
    static belongsTo = [owner: Owner_Persist_Bi_P]
}

// Bidirectional classes for non-persisting cascades need nullable back-references to avoid validation errors
@Entity
class Owner_Merge_Bi_P {
    String name
    Set<Child_BT_Merge_P> children
    static hasMany = [children: Child_BT_Merge_P]
    static mapping = { children cascade: 'merge' }
}

@Entity
class Child_BT_Merge_P {
    String title
    static belongsTo = [owner: Owner_Merge_Bi_P]
    static constraints = { owner nullable: true }
}

@Entity
class Owner_Delete_Bi_P {
    String name
    Set<Child_BT_Delete_P> children
    static hasMany = [children: Child_BT_Delete_P]
    static mapping = { children cascade: 'delete' }
}

@Entity
class Child_BT_Delete_P {
    String title
    static belongsTo = [owner: Owner_Delete_Bi_P]
    static constraints = { owner nullable: true }
}

@Entity
class Owner_Lock_Bi_P {
    String name
    Set<Child_BT_Lock_P> children
    static hasMany = [children: Child_BT_Lock_P]
    static mapping = { children cascade: 'lock' }
}

@Entity
class Child_BT_Lock_P {
    String title
    static belongsTo = [owner: Owner_Lock_Bi_P]
    static constraints = { owner nullable: true }
}

@Entity
class Owner_Replicate_Bi_P {
    String name
    Set<Child_BT_Replicate_P> children
    static hasMany = [children: Child_BT_Replicate_P]
    static mapping = { children cascade: 'replicate' }
}

@Entity
class Child_BT_Replicate_P {
    String title
    static belongsTo = [owner: Owner_Replicate_Bi_P]
    static constraints = { owner nullable: true }
}

@Entity
class Owner_Evict_Bi_P {
    String name
    Set<Child_BT_Evict_P> children
    static hasMany = [children: Child_BT_Evict_P]
    static mapping = { children cascade: 'evict' }
}

@Entity
class Child_BT_Evict_P {
    String title
    static belongsTo = [owner: Owner_Evict_Bi_P]
    static constraints = { owner nullable: true }
}

// --- Domain Classes for Orphan Removal Test ---
@Entity
class Owner_Orphan_P {
    String name
    Set<Child_Orphan_P> children
    static hasMany = [children: Child_Orphan_P]
    static mapping = { children cascade: 'all-delete-orphan' }
}

@Entity
class Child_Orphan_P {
    String title
    static belongsTo = [owner: Owner_Orphan_P]
}