package grails.gorm.specs.multitenancy

import grails.gorm.multitenancy.CurrentTenant
import grails.gorm.services.Service
import grails.gorm.transactions.Transactional

@CurrentTenant
@Service(Department)
@Transactional
abstract class DepartmentService {

    UserService userService

    abstract Department save(String name)

    abstract Department save(Department department)

    List<Department> findAllByUser(String username) {
        User user = User.findByUsername(username)
        Department.executeQuery('from Department d where :user in elements(d.users)', [user: user],[:])
    }

    abstract Number count()

}
