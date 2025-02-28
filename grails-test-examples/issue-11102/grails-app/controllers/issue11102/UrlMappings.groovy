package issue11102

class UrlMappings {

    static mappings = {

        name testMapping: '/get1'(controller: 'test') {
            action = [GET: 'get1']
        }

        name testMapping2: '/get2'(controller: 'test') {
            action = [GET: 'get2']
        }

        "/"(view:"/index")
        "500"(view:'/error')
        "404"(view:'/notFound')
    }
}
