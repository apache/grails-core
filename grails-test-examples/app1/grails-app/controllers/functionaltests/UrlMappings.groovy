package functionaltests

class UrlMappings {

    static mappings = {
        "/viewBooks"(redirect: '/book/index')
        "/$controller/$action?/$id?(.$format)?"{
            constraints {
                // apply constraints here
            }
        }

        "/alphaDemo"(controller: 'demo', action: 'doit', namespace: 'alpha')
        "/betaDemo"(controller: 'demo', action: 'doit', namespace: 'beta')

        // See RedirectWithAndWithoutParamsFunctionalSpec.groovy
        "/old-with-uri"(redirect: [uri: '/new-url'])
        "/old-with-uri-2"(redirect: [uri: '/new-url', keepParamsWhenRedirect: false])
        "/old-with-controller-action"(redirect: [controller: 'baz', action: 'newUrl'])
        "/old-with-controller-action-2"(redirect: [controller: 'baz', action: 'newUrl', keepParamsWhenRedirect: false])
        "/old-uri-with-params"(redirect: [uri: '/new-url', keepParamsWhenRedirect: true])
        "/old-controller-action-with-params"(redirect: [controller: 'baz', action: 'newUrl', keepParamsWhenRedirect: true])
        "/new-url"(controller: 'baz', 'action': 'newUrl')

        "/forward/$param1"(controller: 'forwarding', action: 'two')

        "/"(view:"/index")
        "500"(view:'/error')
        "404"(controller:"errors", action:"notFound")
        "500"(controller:"errors", action:'customErrorHandler', exception:CustomException)
    }
}
