package grails.test.app

import org.grails.gorm.graphql.plugin.testing.GraphQLSpec
import grails.testing.mixin.integration.Integration
import org.grails.web.json.JSONArray
import org.grails.web.json.JSONObject
import spock.lang.Specification
import spock.lang.Stepwise

@Integration
@Stepwise
class PaymentIntegrationSpec extends Specification implements GraphQLSpec {

    void "test payments can not be created because the class is abstract"() {
        when:
        def resp = graphQL.graphql("""
            mutation {
              paymentCreate(payment: {amount: 5}) {
                id
              }
            }
        """)

        Map result = resp.body()

        then:
        result.errors.size() == 1
        result.errors[0].message == "Validation error (FieldUndefined@[paymentCreate]) : Field 'paymentCreate' in type 'Mutation' is undefined"
    }

    void "test creating a credit card payment"() {
        when:
        def resp = graphQL.graphql("""
            mutation {
              creditCardPaymentCreate(creditCardPayment: {
                cardNumber: "1234 5678 9012 3456",
                amount: 56.73
              }) {
                id
                amount
                cardNumber
                errors {
                  field
                  message
                }
              }
            }
        """)
        Map obj = resp.body().data.creditCardPaymentCreate

        then:
        obj.id
        obj.cardNumber == '1234 5678 9012 3456'
        obj.amount == new BigDecimal('56.73')
        obj.errors.empty
    }

    void "test querying a single credit card payment"() {
        when:
        def resp = graphQL.graphql("""
            {
              creditCardPayment(id: 1) {
                id
                amount
                cardNumber
              }
            }
        """)
        Map obj = resp.body().data.creditCardPayment

        then:
        obj.id
        obj.cardNumber == '1234 5678 9012 3456'
        obj.amount == new BigDecimal('56.73')

        when:
        resp = graphQL.graphql("""
            {
              payment(id: 1) {
                id
                amount
                ... on CreditCardPayment {
                    cardNumber
                }
              }
            }
        """)
        Map json = resp.body()
        obj = json.data.payment

        then:
        obj.id
        obj.amount == new BigDecimal('56.73')
        obj.cardNumber == '1234 5678 9012 3456'

        when: 'You attempt to query something not available in the parent class, but available in the subclass'
        resp = graphQL.graphql("""
            {
              payment(id: 1) {
                id
                amount
                cardNumber
              }
            }
        """)
        obj = resp.body()

        then: 'An error is returned'
        obj.data == null
        obj.errors.size() == 1
        obj.errors[0].message == "Validation error (FieldUndefined@[payment/cardNumber]) : Field 'cardNumber' in type 'Payment' is undefined"
    }

    void "test querying a list of credit card payments"() {
        given:
        graphQL.graphql("""
            mutation {
              creditCardPaymentCreate(creditCardPayment: {
                cardNumber: "xxx yyy zzz aaa",
                amount: 34.43
              }) {
                id
              }
            }
        """)

        when:
        def resp = graphQL.graphql("""
            {
              creditCardPaymentList {
                id
                amount
                cardNumber
              }
            }
        """)
        List obj = resp.body().data.creditCardPaymentList

        then:
        obj.size() == 2
        obj.find { it.id == 1 }.cardNumber == '1234 5678 9012 3456'
        obj.find { it.id == 2 }.cardNumber == 'xxx yyy zzz aaa'

        when:
        resp = graphQL.graphql("""
            {
              paymentList {
                id
                amount
              }
            }
        """)
        obj = resp.body().data.paymentList

        then:
        obj.size() == 2
        obj.find { it.id == 1 }.amount == new BigDecimal('56.73')
        obj.find { it.id == 2 }.amount == new BigDecimal('34.43')
    }

    void "test updating a credit card payment"() {
        when:
        def resp = graphQL.graphql("""
            mutation {
                creditCardPaymentUpdate(id: 1, creditCardPayment: {
                    cardNumber: "foo",
                    amount: 1
                }) {
                    id
                    cardNumber
                    amount
                }
            }
        """)
        Map obj = resp.body().data.creditCardPaymentUpdate

        then:
        obj.id == 1
        obj.cardNumber == 'foo'
        obj.amount == new BigDecimal('1')

        when: 'A subclass property is provided in the parent class update'
        resp = graphQL.graphql("""
            mutation {
                paymentUpdate(id: 1, payment: {
                    cardNumber: "foo",
                    amount: 1
                }) {
                    id
                    amount
                }
            }
        """)
        obj = resp.body()

        then: 'An error is thrown'
        obj.data == null
        obj.errors.size() == 1
        obj.errors[0].message.startsWith('Validation error (WrongType@[paymentUpdate])')
        obj.errors[0].message.endsWith("contains a field not in 'PaymentUpdate': 'cardNumber'")

        when:
        resp = graphQL.graphql("""
            mutation {
                paymentUpdate(id: 1, payment: {
                    amount: 2
                }) {
                    id
                    amount
                }
            }
        """)
        obj = resp.body().data.paymentUpdate

        then:
        obj.amount == new BigDecimal('2')
    }

    void "test deleting a credit card payment"() {
        when:
        def resp = graphQL.graphql("""
            mutation {
                creditCardPaymentDelete(id: 1) {
                    success
                }
            }
        """)
        Map obj = resp.body().data.creditCardPaymentDelete

        then:
        obj.success

        when:
        resp = graphQL.graphql("""
            mutation {
                paymentDelete(id: 2) {
                    success                  
                }
            }
        """)
        obj = resp.body().data.paymentDelete

        then:
        obj.success
    }

}
