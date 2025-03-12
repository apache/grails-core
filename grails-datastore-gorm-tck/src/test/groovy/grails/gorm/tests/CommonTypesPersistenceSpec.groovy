package grails.gorm.tests

import grails.persistence.Entity

/**
 * @author graemerocher
 */
class CommonTypesPersistenceSpec extends GormDatastoreSpec {

    def testPersistBasicTypes() {
        given:
            def now = new Date()
            def cal = new GregorianCalendar()
            def ct = new CommonTypes(
                l: 10L,
                b: 10 as byte,
                s: 10 as short,
                bool: true,
                i: 10,
                url: new URL("http://google.com"),
                date: now,
                c: cal,
                bd: 1.0,
                bi: 10 as BigInteger,
                d: 1.0 as Double,
                f: 1.0 as Float,
                tz: TimeZone.getTimeZone("GMT"),
                loc: Locale.UK,
                cur: Currency.getInstance("USD"),
                ba: 'hello'.bytes
            )

        when:
            ct.save(flush:true)
            ct.discard()
            ct = CommonTypes.get(ct.id)

        then:
            ct
            10L == ct.l
            (10 as byte) == ct.b
            (10 as short) == ct.s
            true == ct.bool
            10 == ct.i
            new URL("http://google.com") == ct.url
            now.time == ct.date.time
            cal == ct.c
            1.0 == ct.bd
            10 as BigInteger == ct.bi
            (1.0 as Double) == ct.d
            (1.0 as Float) == ct.f
            TimeZone.getTimeZone("GMT") == ct.tz
            Locale.UK == ct.loc
            Currency.getInstance("USD") == ct.cur
            'hello'.bytes == ct.ba
    }
}

@Entity
class CommonTypes implements Serializable {
    Long id
    Long version
    Long l
    Byte b
    Short s
    Boolean bool
    Integer i
    URL url
    Date date
    Calendar c
    BigDecimal bd
    BigInteger bi
    Double d
    Float f
    TimeZone tz
    Locale loc
    Currency cur
    byte[] ba
}
