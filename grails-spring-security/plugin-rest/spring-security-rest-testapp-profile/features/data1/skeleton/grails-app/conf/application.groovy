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

                    storage {
                        gorm {
                            tokenDomainClassName = 'gorm.AccessToken'
                        }
                    }
                }

                oauth {
                    frontendCallbackUrl = {String tokenValue -> "http://example.org#token=${tokenValue}" }

                    google {
                        client = Google2Client
                        key = '1093785205845-hl3jv0rd8jfohkn55jchgmnpvdpsnal4.apps.googleusercontent.com'
                        secret = 'sWXY3VMm4wKAGoRZg8r3ftZc'
                        scope = Google2Client.Google2Scope.EMAIL_AND_PROFILE
                        defaultRoles = ['ROLE_USER', 'ROLE_GOOGLE']
                    }

                    facebook {
                        client = FacebookClient
                        key = '585495051532332'
                        secret = 'f6bfaff8c66a3fd7b1e9ec4c986fda8b'

                        //https://developers.facebook.com/docs/reference/login/
                        scope = 'public_profile,email'
                        fields = 'id,name,first_name,middle_name,last_name,link,gender,email,birthday'
                        defaultRoles = ['ROLE_USER', 'ROLE_FACEBOOK']
                    }

                    twitter {
                        client = TwitterClient
                        key = 'A2hwgEMfNIp7OF2f05Gqw'
                        secret = 'BUpumhJGeNskn53Ssr3QQuesKg8lOIEWaLO4pCdgeTw'
                        defaultRoles = ['ROLE_USER', 'ROLE_TWITTER']
                    }
                }
            }
        }
    }
}

