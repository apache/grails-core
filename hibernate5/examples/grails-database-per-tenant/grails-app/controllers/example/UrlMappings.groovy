package example

/**
 * Created by graemerocher on 21/07/2016.
 */
class UrlMappings {

    static mappings = {
        "/$controller/$action?/$id?(.$format)?"{
            constraints {
                // apply constraints here
            }
        }

        "/book/tenant/moreBooks"(controller:"book", action:"selectTenant") {
            tenantId = "moreBooks"
        }

        "/book/tenant/evenMoreBooks"(controller:"book", action:"selectTenant") {
            tenantId = "evenMoreBooks"
        }

        "/"(view:'/index')
    }
}
