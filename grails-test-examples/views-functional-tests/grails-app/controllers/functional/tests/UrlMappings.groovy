package functional.tests

class UrlMappings {

    static mappings = {
        "/$controller/$action?/$id?(.$format)?"{
            constraints {
                // apply constraints here
            }
        }

        "/"(view:"/index")
        "500"(view:'/error')
        "401"(controller: 'error', action: 'unauthorized')
        "404"(view:'/notFound')

        "/books"(resources:"book")
        "/books/listExcludes"(controller: "book", action: "listExcludes")
        "/books/listExcludesRespond"(controller: "book", action: "listExcludesRespond")
        "/books/listCallsTmpl"(controller: "book", action: "listCallsTmpl")
        "/books/listCallsTmplVar"(controller: "book", action: "listCallsTmplVar")
        "/books/listCallsTmplExtraData"(controller: "book", action: "listCallsTmplExtraData")
        "/books/showWithParams/$id"(controller: "book", action: "showWithParams")
        "/books/non-standard-template"(controller:"book", action:"nonStandardTemplate")
        "/teams"(resources:"team")
        "/products"(resources:"product")
        "/teams/deep/$id"(controller: "team", action:"deep")
        "/teams/hal/$id"(controller: "team", action:"hal")
        "/authors"(resources:"author")
        "/api/book/$action?"(controller: 'book', namespace: 'api')
        "/person-inheritance"(controller: 'personInheritance', action: 'index')
        "/person-inheritance/npe"(controller: 'personInheritance', action: 'npe')
    }
}
