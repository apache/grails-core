import org.pac4j.oauth.client.FacebookClient
import org.pac4j.oauth.client.Google2Client
import org.pac4j.oauth.client.TwitterClient

grails {
    plugin {
        springsecurity {

            useSecurityEventListener = true

            filterChain {
                chainMap = [
                        [pattern: '/api/**',       filters: 'JOINED_FILTERS,-anonymousAuthenticationFilter,-exceptionTranslationFilter,-authenticationProcessingFilter,-securityContextPersistenceFilter,-rememberMeAuthenticationFilter'],
                        [pattern: '/secured/**',   filters: 'JOINED_FILTERS,-anonymousAuthenticationFilter,-exceptionTranslationFilter,-authenticationProcessingFilter,-securityContextPersistenceFilter,-rememberMeAuthenticationFilter'],
                        [pattern: '/anonymous/**', filters: 'anonymousAuthenticationFilter,restTokenValidationFilter,restExceptionTranslationFilter,filterInvocationInterceptor'],
                        [pattern: '/**',           filters: 'JOINED_FILTERS,-restTokenValidationFilter,-restExceptionTranslationFilter']
                ]
            }
            rest {
                token {
                    validation {
                        enableAnonymousAccess = true
                        useBearerToken = false
                    }
                }

                oauth {
                    frontendCallbackUrl = {String tokenValue -> "http://example.org#token=${tokenValue}" }

                    google {
                        client = Google2Client
                        key = 'TODO'
                        secret = 'TODO'
                        scope = Google2Client.Google2Scope.EMAIL_AND_PROFILE
                        defaultRoles = ['ROLE_USER', 'ROLE_GOOGLE']
                    }

                    facebook {
                        client = FacebookClient
                        key = 'TODO'
                        secret = 'TODO'

                        //https://developers.facebook.com/docs/reference/login/
                        scope = 'public_profile,email'
                        fields = 'id,name,first_name,middle_name,last_name,link,gender,email,birthday'
                        defaultRoles = ['ROLE_USER', 'ROLE_FACEBOOK']
                    }

                    twitter {
                        client = TwitterClient
                        key = 'TODO'
                        secret = 'TODO'
                        defaultRoles = ['ROLE_USER', 'ROLE_TWITTER']
                    }
                }
            }
        }
    }
}

