package dniel.forwardauth.domain.service

import dniel.forwardauth.domain.Nonce
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.*

@Component
class NonceService {
    private val LOGGER = LoggerFactory.getLogger(this.javaClass)

    fun generate(): Nonce {
        return Nonce(UUID.randomUUID().toString().replace("-", ""))
    }

}