class UrlMappings {

    static mappings = {
        "/$controller/$action?/$id?(.$format)?"{
            constraints {
                // apply constraints here
            }
        }

        "/admin/$controller/$action?/$id?(.$format)?"{
            namespace = "admin"
        }

        "/"(view:"/index")
        "500"(view:'/error')
    }
}
