grails {
    plugin {
        springsecurity {

            filterChain {
                chainMap = [
                    [pattern: '/api/**',       filters: 'JOINED_FILTERS,-anonymousAuthenticationFilter,-exceptionTranslationFilter,-authenticationProcessingFilter,-securityContextPersistenceFilter'],
                    [pattern: '/secured/**',   filters: 'JOINED_FILTERS,-anonymousAuthenticationFilter,-exceptionTranslationFilter,-authenticationProcessingFilter,-securityContextPersistenceFilter'],
                    [pattern: '/anonymous/**', filters: 'anonymousAuthenticationFilter,restTokenValidationFilter,restExceptionTranslationFilter,filterInvocationInterceptor'],
                    [pattern: '/**',           filters: 'JOINED_FILTERS,-restTokenValidationFilter,-restExceptionTranslationFilter']
                ]
            }

            rest {
                token {
                    validation {
                        enableAnonymousAccess = true
                        useBearerToken = true
                    }

                    storage {
                        jwt {
                            useEncryptedJwt = true

                            privateKeyPath = "grails-app/conf/private_key.der"
                            publicKeyPath = "grails-app/conf/public_key.der"

                            expiration = 5
                        }
                    }
                }
            }
        }
    }
}

