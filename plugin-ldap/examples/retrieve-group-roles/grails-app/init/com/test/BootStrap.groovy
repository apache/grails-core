package com.test

import groovy.transform.CompileStatic

@CompileStatic
class BootStrap {
	UserService userService
	RoleService roleService
	UserRoleService userRoleService
	def init = {
		User gauss = userService.save('gauss', 'n/a')
		Role roleSuperUser = roleService.save('ROLE_SUPERUSER')
		userRoleService.save(gauss, roleSuperUser)
	}
}
