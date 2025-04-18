package grails.test.app

class CreditCardPayment extends Payment {

    String cardNumber

    static constraints = {
    }

    static graphql = true
}
