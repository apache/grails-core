package issue11102

class TestController {

    def get1() {
        render "GET1"
    }

    def get2() {
        forward action: 'get1'
    }
}
