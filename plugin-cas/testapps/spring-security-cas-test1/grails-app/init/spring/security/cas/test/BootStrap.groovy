package spring.security.cas.test

import com.test.Role
import com.test.User
import com.test.UserRole
import groovy.transform.CompileStatic

@CompileStatic
class BootStrap {

    def init = { servletContext ->
        Role roleAdmin
        Role roleUser
        User user
        User admin
        Role.withTransaction {
            roleAdmin = new Role('ROLE_ADMIN').save()
            roleUser = new Role('ROLE_USER').save()
        }


        User.withTransaction {
            user = new User('user', 'user').save()
            admin = new User('admin', 'admin').save()
        }

        UserRole.withTransaction {
            UserRole.create user, roleUser
            UserRole.create admin, roleUser
            UserRole.create admin, roleAdmin, true
        }
    }

    def destroy = {
    }
}
