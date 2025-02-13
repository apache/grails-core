package com.test

import groovy.transform.CompileStatic

@CompileStatic
class BootStrap {
	UserService userService
	RoleService roleService
	UserRoleService userRoleService

	def init = {
		userService.save('person1', 'n/a')
		userService.save('person2', 'n/a')
		User person3 = userService.save('person3', 'n/a')
		Role roleSuperUser = roleService.save('ROLE_SUPERUSER')
		userRoleService.save(person3, roleSuperUser)
	}
}
