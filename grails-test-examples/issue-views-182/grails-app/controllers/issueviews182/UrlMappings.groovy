package issueviews182

class UrlMappings {

    static mappings = {
        delete "/$controller/$id(.$format)?"(action:"delete")
        get "/$controller(.$format)?"(action:"index")
        get "/$controller/$id(.$format)?"(action:"show")
        post "/$controller(.$format)?"(action:"save")
        put "/$controller/$id(.$format)?"(action:"update")
        patch "/$controller/$id(.$format)?"(action:"patch")

        "/"(controller: 'application', action:'index')
        "/customError"(controller: 'error', action: 'customError')
        "500"(view: '/errors/customError', exception: IllegalStateException)
        "500"(view: '/error')
        "404"(view: '/notFound')
    }
}
