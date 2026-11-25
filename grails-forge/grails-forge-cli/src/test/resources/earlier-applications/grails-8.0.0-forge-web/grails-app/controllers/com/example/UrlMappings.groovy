package com.example

class UrlMappings {
    static mappings = {
        "/$namespace/$controller/$action?/$id?(.$format)?" {}
        "/$controller/$action?/$id?(.$format)?"{
            constraints {
                // apply constraints here
            }
        }

        "/"(view:"/index")
        // Browsers request /favicon.ico for pages that declare no icon of their own
        "/favicon.ico"(redirect: [uri: '/assets/favicon.ico', permanent: true])
        "500"(view:'/error')
        "404"(view:'/notFound')

    }
}
