grails {
	plugin {
		springsecurity {
			authority.className = 'com.test.Role'
         cas {
            loginUri         = '/login'
            serverUrlPrefix  = 'http://localhost:9090/cas'
            proxyReceptorUrl = '/secure/receptor'
            serviceUrl       = 'http://localhost:${server.port}/login/cas'
            proxyCallbackUrl = 'http://localhost:${server.port}/secure/receptor'
         }
			controllerAnnotations.staticRules = [
				[pattern: '/',               access: 'permitAll'],
				[pattern: '/error',          access: 'permitAll'],
				[pattern: '/index',          access: 'permitAll'],
				[pattern: '/index.gsp',      access: 'permitAll'],
				[pattern: '/shutdown',       access: 'permitAll'],
				[pattern: '/assets/**',      access: 'permitAll'],
				[pattern: '/**/js/**',       access: 'permitAll'],
				[pattern: '/**/css/**',      access: 'permitAll'],
				[pattern: '/**/images/**',   access: 'permitAll'],
				[pattern: '/**/favicon.ico', access: 'permitAll']
			]
         fii.rejectPublicInvocations = false
			filterChain.chainMap = [
				[pattern: '/assets/**',      filters: 'none'],
				[pattern: '/**/js/**',       filters: 'none'],
				[pattern: '/**/css/**',      filters: 'none'],
				[pattern: '/**/images/**',   filters: 'none'],
				[pattern: '/**/favicon.ico', filters: 'none'],
				[pattern: '/**',             filters: 'JOINED_FILTERS']
			]
         logout.postOnly = false
         rejectIfNoRule = false
			userLookup {
				userDomainClassName = 'com.test.User'
				authorityJoinClassName = 'com.test.UserRole'
			}
		}
	}
}
