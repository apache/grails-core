package org.demo.spock

import java.nio.charset.StandardCharsets

class UploadController {

    static allowedMethods = [index: 'GET', store: 'POST']

    def index() {}

    def store() {
        def myFile = request.getFile('myFile')
        def text = 'No file uploaded'
        if (myFile) {
            try (InputStream inputStream = myFile.getInputStream()) {
                byte[] bytes = inputStream.readAllBytes()
                text = new String(bytes, StandardCharsets.UTF_8)
            }
        }
        render(view: 'store', model: [text: text])
    }
}