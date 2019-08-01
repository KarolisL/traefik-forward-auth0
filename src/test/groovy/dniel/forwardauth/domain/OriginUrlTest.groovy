package dniel.forwardauth.domain


import spock.lang.Specification
import spock.lang.Unroll

import static org.hamcrest.Matchers.is
import static spock.util.matcher.HamcrestSupport.that

class OriginUrlTest extends Specification {
    @Unroll
    def "should tostring should not alter the original url in any way."() {
        given: "an requested origin url"
        def originUrl = new OriginUrl(protocol, host, uri)

        when: "we concatenate url to string"
        def result = originUrl.toString()

        then: "we get a valid urlencoded string"
        that(result, is(encodedUrl))

        where:
        protocol | host                             | uri                   | encodedUrl
        "HTTPS"  | "www.example.test"               | "/test"               | "https://www.example.test/test"
        "HttPs"  | "www.example.test"               | "/test"               | "https://www.example.test/test"
        "https"  | "www.example.test"               | "/"                   | "https://www.example.test/"
        "https"  | "www.example.test"               | "/#hello"             | "https://www.example.test/#hello"
        "https"  | "www.example.test"               | "/#hello"             | "https://www.example.test/#hello"
        "https"  | "www.example.test"               | "/?hello?skjd?lskdj^" | "https://www.example.test/?hello?skjd?lskdj^"
        "https"  | "www.example.test"               | "/?hello={sdfasd}"    | "https://www.example.test/?hello={sdfasd}"
        "https"  | "www.example.test"               | "/?"                  | "https://www.example.test/?"
        "https"  | "www.example.test"               | "/?hello={asdasd}"    | "https://www.example.test/?hello={asdasd}"
        "https"  | "user:password@www.example.test" | "/?hello={asdasd}"    | "https://user:password@www.example.test/?hello={asdasd}"
    }
}
