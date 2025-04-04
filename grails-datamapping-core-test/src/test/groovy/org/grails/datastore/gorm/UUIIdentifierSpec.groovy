package org.grails.datastore.gorm

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity

class UUIIdentifierSpec extends GormDatastoreSpec {

    void "Test that a UUID identifier is correctly generated"() {
        when:"A domain with a UUID is saved"
            def dm = new DocumentModel(name: "My Doc").save()

        then:"The UUID is correctly generated"
            dm != null
            dm.id != null
            DocumentModel.count() == 1

        when:"Another entity is saved"
            new DocumentModel(name: "Another").save()
        then:"There are 2"
            DocumentModel.count() == 2
    }

    @Override
    List getDomainClasses() {
        [DocumentModel]
    }
}

@Entity
class DocumentModel  {
    static final SCORE = 40

    String id // UUID , for replications / optimization
    String name
    String description = ''

    Date dateCreated
    Date lastUpdated

    long estimatedScore = 0
    long score = 0

    Map<String, Object> parameters = new HashMap<String, Object>()

    static mapping = {
        id generator:'uuid'
        name index: 'idx_doc_name'
        description size:0..300, nullable:true
    }

    static constraints = {
        estimatedScore min: 0l
        score min: 0l
        name blank: false, unique: 'workspace'
    }
}
