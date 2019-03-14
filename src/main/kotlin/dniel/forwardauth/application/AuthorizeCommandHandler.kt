package dniel.forwardauth.application

import com.auth0.jwt.interfaces.Claim
import dniel.forwardauth.AuthProperties
import dniel.forwardauth.AuthProperties.Application
import dniel.forwardauth.domain.*
import dniel.forwardauth.domain.service.NonceService
import dniel.forwardauth.domain.service.TokenService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.net.URI

@Component
class AuthorizeCommandHandler(val properties: AuthProperties,
                              val verifyTokenService: TokenService,
                              val nonceService: NonceService) :
        CommandHandler<AuthorizeCommandHandler.AuthorizeCommand, AuthorizeCommandHandler.AuthorizeResult> {

    companion object {
        val LOGGER = LoggerFactory.getLogger(this.javaClass)
    }

    private val AUTHORIZE_URL = properties.authorizeUrl
    private val DOMAIN = properties.domain

    /**
     * this is the parameter object for the handler to pass inn all
     * needed parameters by the handler.
     */
    public data class AuthorizeCommand(val accessToken: String?,
                                       val idToken: String?,
                                       val protocol: String,
                                       val host: String,
                                       val uri: String,
                                       val method: String
    ) : Command

    /**
     * The result from the Authorization, all return values.
     */
    public data class AuthorizeResult(var cookieDomain: String? = null,
                                      var isAuthenticated: Boolean = false,
                                      var isRestrictedUrl: Boolean = true,
                                      var redirectUrl: URI? = null,
                                      var nonce: Nonce? = null,
                                      var userinfo: Map<String, String> = emptyMap()
    ) : CommandResult

    override fun handle(command: AuthorizeCommand): AuthorizeResult {
        LOGGER.debug("AuthorizeCommand start")
        return with(AuthorizeResult()) {
            val app = properties.findApplicationOrDefault(command.host)
            val originUrl = OriginUrl(command.protocol, command.host, command.uri)
            LOGGER.debug("Authorize request=${originUrl} to app=${app.name}")

            nonce = nonceService.generate()
            val state = State.create(originUrl, nonce!!)

            redirectUrl = AuthorizeUrl(AUTHORIZE_URL, app, state).toURI()
            cookieDomain = app.tokenCookieDomain
            isAuthenticated = verifyTokens(command, app, this)
            isRestrictedUrl = isRestrictedUrl(command.method, originUrl, app)

            this
        }.also {
            LOGGER.debug("AuthorizeCommand finished")
        }
    }

    private fun verifyTokens(params: AuthorizeCommand, app: Application, commandResult: AuthorizeResult): Boolean {
        try {
            return verifyIdToken(params, app, commandResult) && verifyAccessToken(params, app, commandResult)
        } catch (e: Exception) {
            LOGGER.warn("VerifyTokensFailed ${e.message}", e)
            return false
        }
    }

    private fun verifyAccessToken(params: AuthorizeCommand, app: Application, commandResult: AuthorizeResult): Boolean {
        if (hasAccessToken(params)) {
            if (shouldVerifyAccessToken(app)) {
                return verifyToken(params.accessToken!!, app.audience, DOMAIN) != null
            } else {
                LOGGER.debug("Skip Verification of opaque Access Token.")
                return true
            }
        } else {
            return false
        }
    }

    private fun verifyIdToken(params: AuthorizeCommand, app: Application, commandResult: AuthorizeResult): Boolean {
        if (hasIdToken(params)) {
            commandResult.userinfo = getUserinfoFromToken(app, verifyToken(params.idToken!!, app.clientId, DOMAIN)!!)
            return verifyToken(params.idToken!!, app.clientId, DOMAIN) != null
        } else {
            return false
        }
    }

    private fun verifyToken(token: String, expectedAudience: String, domain: String): Token? = verifyTokenService.verify(token, expectedAudience, domain)

    private fun hasAccessToken(params: AuthorizeCommand): Boolean = !params.accessToken.isNullOrEmpty()

    private fun hasIdToken(params: AuthorizeCommand): Boolean = !params.idToken.isNullOrEmpty()

    private fun shouldVerifyAccessToken(app: Application): Boolean = !app.audience.equals("${DOMAIN}userinfo", ignoreCase = true)

    private fun isRestrictedUrl(method: String, originUrl: OriginUrl, app: Application): Boolean {
        return !originUrl.startsWith(app.redirectUri) && app.restrictedMethods.any() { t -> t.equals(method, true) }
    }

    private fun getUserinfoFromToken(app: Application, token: Token): Map<String, String> {
        return token.value.claims
                .filterKeys { app.claims.contains(it) }
                .mapValues { getClaimValue(it.value) }
                .filterValues { it != null } as Map<String, String>
    }

    private fun getClaimValue(claim: Claim): String? {
        return when {
            claim.asArray(String::class.java) != null -> claim.asArray(String::class.java).joinToString()
            claim.asBoolean() != null -> claim.asBoolean().toString()
            claim.asString() != null -> claim.asString().toString()
            claim.asLong() != null -> claim.asLong().toString()
            else -> null
        }
    }
}