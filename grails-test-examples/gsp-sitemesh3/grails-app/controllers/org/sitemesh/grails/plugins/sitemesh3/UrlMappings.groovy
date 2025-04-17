package org.sitemesh.grails.plugins.sitemesh3

class UrlMappings {
    static mappings = {
        "/$controller/$action?/$id?(.$format)?"{}
        "/"(view:"/index")
        "/index(.$format)?"(view:"/index")
        "500"(view:'/error')
// The following works when the exception originates from a view, but not from a controller.
//        "500"(controller: 'demo', action:'error500')
        "404"(view:'/notFound')
    }
}
