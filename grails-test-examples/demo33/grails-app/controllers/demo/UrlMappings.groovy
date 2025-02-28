package demo

class UrlMappings {

    static mappings = {
        "/foo"(controller: "test", action: "fooGet", method: "GET")
        "/foo"(controller: "test", action: "fooPost", method: "POST")
        "/bar"(controller: "test", action: "bar")

        "/$controller/$action?/$id?(.$format)?"{
            constraints {
                // apply constraints here
            }
        }

        "/"(view:"/index")
        "500"(view:'/error')
        "404"(view:'/notFound')
    }
}
