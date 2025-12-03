package grails.gorm.specs.multitenancy

import grails.gorm.multitenancy.CurrentTenant
import grails.gorm.services.Service
import grails.gorm.transactions.Transactional

@CurrentTenant
@Service(User)
@Transactional
abstract class UserService {

    List<User> findAllByDepartment(String departmentName) {
        Department department = Department.findByName(departmentName)
        if (department) {
            return User.executeQuery('from User u where u.department = :department', [department: department],[:])
        }
        return []
    }

    abstract User save(User user)

    abstract Number count()
}
