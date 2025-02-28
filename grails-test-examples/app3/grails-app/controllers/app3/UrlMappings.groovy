package app3

class UrlMappings {

    static mappings = {
        "/$controller/$action?/$id?(.$format)?"{

        }

        "/"(view:"/index")
        "500"(view:'/error')
        "404"(view:'/notFound')
    }
}
