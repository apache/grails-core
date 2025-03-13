package grails.gorm.tests

import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import org.grails.orm.hibernate.HibernateDatastore
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by graemerocher on 26/01/2017.
 */
class CompositeIdWithManyToOneAndSequenceSpec extends Specification {

    @AutoCleanup @Shared HibernateDatastore datastore = new HibernateDatastore(Tooth, ToothDisease)
    @Shared PlatformTransactionManager transactionManager = datastore.transactionManager

    @Rollback
    @Issue('https://github.com/grails/grails-data-mapping/issues/835')
    void "Test composite id many to one and sequence"() {

        when:"a many to one association is created"
        ToothDisease td = new ToothDisease(nrVersion: 1).save()
        new Tooth(toothDisease: td).save(flush:true)

        then:"The object was saved"
        Tooth.count() == 1
        Tooth.list().first().toothDisease != null
    }

}


@Entity
class Tooth {
    Integer id
    ToothDisease toothDisease
    static mapping = {
        table name: 'AK_TOOTH'
        id generator: 'sequence', params: [sequence: 'SEQ_AK_TOOTH']
        toothDisease {
            column name: 'FK_AK_TOOTH_ID'
            column name: 'FK_AK_TOOTH_NR_VERSION'
        }
    }
}

@Entity
class ToothDisease implements Serializable {
    Integer idColumn
    Integer nrVersion
    static mapping = {
        table name: 'AK_TOOTH_DISEASE'
        idColumn column: 'ID', generator: 'sequence', params: [sequence: 'SEQ_AK_TOOTH_DISEASE']
        nrVersion column: 'NR_VERSION'
        id composite: ['idColumn', 'nrVersion']
    }
}