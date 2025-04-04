package grails.gorm.tests.multitenancy

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import grails.gorm.multitenancy.CurrentTenant
import grails.gorm.services.Service
import grails.gorm.transactions.Rollback
import grails.gorm.transactions.Transactional
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver
import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.dialect.H2Dialect
import spock.lang.AutoCleanup
import spock.lang.Ignore
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by puneetbehl on 21/03/2018.
 */
class MultiTenancyBidirectionalManyToManySpec extends Specification {

    final Map config = [
            "grails.gorm.multiTenancy.mode":MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR,
            "grails.gorm.multiTenancy.tenantResolverClass":SystemPropertyTenantResolver.name,
            'dataSource.url':"jdbc:h2:mem:grailsDB;LOCK_TIMEOUT=10000",
            'dataSource.dialect': H2Dialect.name,
            'dataSource.formatSql': 'true',
            'hibernate.flush.mode': 'COMMIT',
            'hibernate.cache.queries': 'true',
            'hibernate.hbm2ddl.auto': 'create',
    ]

    @Shared DepartmentService departmentService
    @Shared UserService userService

    @Shared @AutoCleanup HibernateDatastore datastore


    void setup() {
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "oci")
        datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver(config), getClass().getPackage() )
        departmentService = datastore.getService(DepartmentService)
        userService = datastore.getService(UserService)
    }

    @Rollback
    @Issue("https://github.com/grails/gorm-hibernate5/issues/58")
    void "test hasMany and 'in' query with multi-tenancy" () {
        given:
        createSomeUsers()

        when:
        List<User> users = userService.findAllByDepartment("Grails")

        then:
        users.size() == 4
    }

    Number createSomeUsers() {
        Department department = departmentService.save("Grails")
        department.addToUsers(username: "John Doe").save()
        department.addToUsers(username: "Hanna William").save()
        department.addToUsers(username: "Mark").save()
        department.addToUsers(username: "Karl").save()
        department.save(flush: true)
        department.users.size()
    }

}

@Entity
class User implements MultiTenant<User> {
    String username
    String tenantId

    static belongsTo = [Department]
    static hasMany = [departments: Department]

    static mapping = {
        table '`user`'
    }
}

@Entity
class Department implements MultiTenant<Department> {
    String name
    String tenantId

    static hasMany = [users: User]
}

@CurrentTenant
@Service(Department)
@Transactional
abstract class DepartmentService {

    UserService userService

    abstract Department save(String name)

    abstract Department save(Department department)

    List<Department> findAllByUser(String username) {
        User user = User.findByUsername(username)
        Department.executeQuery('from Department d where :user in elements(d.users)', [user: user])
    }

    abstract Number count()

}

@CurrentTenant
@Service(User)
@Transactional
abstract class UserService {

    List<User> findAllByDepartment(String departmentName) {
        Department department = Department.findByName(departmentName)
        User.executeQuery('from User u where :department in elements(u.departments)', [department: department])
    }

    abstract User save(User user)

    abstract Number count()
}




