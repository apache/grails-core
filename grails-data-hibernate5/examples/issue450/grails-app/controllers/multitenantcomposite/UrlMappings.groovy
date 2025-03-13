package multitenantcomposite

class UrlMappings {

    static mappings = {
        "/$controller/$action?/$id?(.$format)?"{
            constraints {
                // apply constraints here
            }
        }

        "/"(controller: 'book')
        "500"(view:'/error')
        "404"(view:'/notFound')
    }
}