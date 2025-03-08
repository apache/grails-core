package grails.gorm.tests

import grails.gorm.transactions.Rollback
import org.grails.orm.hibernate.HibernateDatastore
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.AutoCleanup
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by graemerocher on 17/11/16.
 */
@Rollback
class SqlQuerySpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore datastore = new HibernateDatastore(Club)
    @Shared PlatformTransactionManager transactionManager = datastore.getTransactionManager()

    void "test simple query returns a single result"() {
        given:
        setupTestData()

        when:"Some test data is saved"
        String name = "Arsenal"
        Club c = Club.findWithSql("select * from club c where c.name = $name")

        then:"The results are correct"
        c != null
        c.name == name

    }

    void "test simple sql query"() {

        given:
        setupTestData()

        when:"Some test data is saved"
        List<Club> results = Club.findAllWithSql("select * from club c order by c.name")

        then:"The results are correct"
        results.size() == 3
        results[0] instanceof Club
        results[0].name == 'Arsenal'
    }

    void "test sql query with gstring parameters"() {
        given:
        setupTestData()

        when:"Some test data is saved"
        String p = "%l%"
        List<Club> results = Club.findAllWithSql("select * from club c where c.name like $p order by c.name")

        then:"The results are correct"
        results.size() == 2
        results[0] instanceof Club
        results[0].name == 'Arsenal'
    }

    void "test escape HQL in findAll with gstring"() {
        given:
        setupTestData()

        when:"A query is used that embeds a GString with a value that should be encoded for the query to succeed"
        String p = "%l%"
        List<Club> results = Club.findAll("from Club c where c.name like $p order by c.name")

        then:"The results are correct"
        results.size() == 2
        results[0] instanceof Club
        results[0].name == 'Arsenal'

        when:"A query that passes arguments is used"
        results = Club.findAll("from Club c where c.name like $p and c.name like :test order by c.name", [test:'%e%'])

        then:"The results are correct"
        results.size() == 2
        results[0] instanceof Club
        results[0].name == 'Arsenal'
    }

    void "test escape HQL in executeQuery with gstring"() {
        given:
        setupTestData()

        when:"A query is used that embeds a GString with a value that should be encoded for the query to succeed"
        String p = "%l%"
        List<Club> results = Club.executeQuery("from Club c where c.name like $p order by c.name")

        then:"The results are correct"
        results.size() == 2
        results[0] instanceof Club
        results[0].name == 'Arsenal'

        when:"A query that passes arguments is used"
        results = Club.executeQuery("from Club c where c.name like $p and c.name like :test order by c.name", [test:'%e%'])

        then:"The results are correct"
        results.size() == 2
        results[0] instanceof Club
        results[0].name == 'Arsenal'
    }

    void "test escape HQL in find with gstring"() {
        given:
        setupTestData()

        when:"A query is used that embeds a GString with a value that should be encoded for the query to succeed"
        String p = "%chester%"
        Club c = Club.find("from Club c where c.name like $p order by c.name")

        then:"The results are correct"
        c != null
        c.name == 'Manchester United'

        when:"A query that passes arguments is used"
        c = Club.find("from Club c where c.name like $p and c.name like :test order by c.name", [test:'%e%'])

        then:"The results are correct"
        c != null
        c.name == 'Manchester United'
    }

    protected void setupTestData() {
        new Club(name: "Barcelona").save()
        new Club(name: "Arsenal").save()
        new Club(name: "Manchester United").save(flush: true)
    }
}
