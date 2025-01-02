package org.demo.spock.pages

import geb.Page
import geb.module.FileInput

class UploadPage extends Page {

    static url = '/upload'
    static at = { title == 'Upload Test' }

    static content = {
        fileInput { $('input', name: 'myFile').module(FileInput) }
        submitBtn { $('input', type: 'submit') }
    }
}