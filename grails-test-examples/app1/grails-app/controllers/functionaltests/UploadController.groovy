package functionaltests

import org.springframework.web.multipart.MultipartFile

class UploadController {

    def index() {
        render view:"index"
    }

    def upload() {
        def contents = new String( request.getFile("myFile").bytes, 'UTF-8' )

        render text: "<p>$contents</p>", contentType:"text/html"
    }

    def upload2() {
        def myFile = params.myFile

        assert myFile instanceof MultipartFile

        render text: "<p>ok</p>", contentType:"text/html"
    }
}
