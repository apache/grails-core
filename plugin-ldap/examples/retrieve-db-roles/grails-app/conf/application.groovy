grails {
	plugin {
		springsecurity {
			authority {
				className = 'com.test.Role'
			}
			controllerAnnotations.staticRules = [
				[pattern: '/',               access: 'permitAll'],
				[pattern: '/logoff',         access: 'permitAll'],
				[pattern: '/error',          access: 'permitAll'],
				[pattern: '/index',          access: 'permitAll'],
				[pattern: '/index.gsp',      access: 'permitAll'],
				[pattern: '/shutdown',       access: 'permitAll'],
				[pattern: '/assets/**',      access: 'permitAll'],
				[pattern: '/secure/users',   access: 'permitAll'],
				[pattern: '/**/js/**',       access: 'permitAll'],
				[pattern: '/**/css/**',      access: 'permitAll'],
				[pattern: '/**/images/**',   access: 'permitAll'],
				[pattern: '/**/favicon.ico', access: 'permitAll']
			]
			password.algorithm = 'SHA-256'
			rememberMe {
				persistent = true
				persistentToken.domainClassName = 'com.test.PersistentLogin'
			}
			userLookup {
				userDomainClassName = 'com.test.User'
				authorityJoinClassName = 'com.test.UserRole'
			}

			// http://www.forumsys.com/tutorials/integration-how-to/ldap/online-ldap-test-server/
			ldap {
				context {
					managerDn = 'cn=read-only-admin,dc=example,dc=com'
					managerPassword = 'password'
					server = 'ldap://ldap.forumsys.com:389/' //'ldap://[ip]:[port]/'
				}
				authorities {
					ignorePartialResultException = true
					retrieveGroupRoles = false
					retrieveDatabaseRoles = true
					defaultRole = 'ROLE_USER'
				}
				search {
					base = 'dc=example,dc=com'
				}
			}
		}
	}
}