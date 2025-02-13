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
//			ldap {
//				authorities {
//					groupSearchBase = 'ou=groups,dc=d1,dc=example,dc=com'
//					groupSearchFilter = 'uniquemember={0}'
//					retrieveDatabaseRoles = true
//				}
//				context {
//					managerDn = 'uid=admin,ou=system'
//					managerPassword = 'secret'
//					server = 'ldap://localhost:10389'
//				}
//				rememberMe {
//					detailsManager {
//						groupRoleAttributeName = 'cn'
//						groupSearchBase = 'ou=groups,dc=d1,dc=example,dc=com'
//					}
//					usernameMapper {
//						userDnBase = 'dc=d1,dc=example,dc=com'
//						usernameAttribute = 'cn'
//					}
//				}
//				search {
//					base = 'dc=d1,dc=example,dc=com'
//					filter = '(uid={0})'
//				}
//				useRememberMe = true
//			}
		}
	}
}
//
//ldapServers {
//	d1 {
//		base = 'dc=d1,dc=example,dc=com'
//		port = 10389
//		indexed = ['objectClass', 'uid', 'mail', 'userPassword', 'description']
//	}
//}