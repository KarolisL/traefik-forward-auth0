package dniel.forwardauth.infrastructure.endpoints

import dniel.forwardauth.AuthProperties
import dniel.forwardauth.domain.SigninException
import dniel.forwardauth.domain.State
import dniel.forwardauth.domain.UnauthorizedException
import dniel.forwardauth.domain.service.TokenService
import dniel.forwardauth.infrastructure.auth0.Auth0Service
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import javax.ws.rs.*
import javax.ws.rs.core.*


/**
 * OAuth2 Callback Endpoint where ForwardAuth receives user information from Auth0
 * after the user has Authenticated with Auth0 and authorized to share user information.
 */
@Path("signin")
@Component
class SigninEndpoint(val properties: AuthProperties, val auth0Client: Auth0Service, val verifyTokenService: TokenService) {
    private val LOGGER = LoggerFactory.getLogger(this.javaClass)
    private val DOMAIN = properties.domain

    /**
     * Callback Endpoint.
     * Handle Callback from Auth0 and perform sign in logic and error handling.
     *
     * @param error if something has failed while authentication at Auth0 it will send the error to us.
     * @param errorDescription a longer description of the error.
     * @param headers http headers to dump if logging is set to TRACE level for debugging.
     * @param code is the exchange code for tokens.
     * @param forwardedHost is the host header set by Traefik when it forwards a requests.
     * @param nonceCookie the secret nonce to prevent CORS set in browser cookie.
     * @param state Is state sent while authenticating and should contain the same nonce as the noonce cookie + some more.
     *
     * TODO rename the endpoint to callback instead of signin to match OAuth2 better to avoid confusion.
     *
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    fun callback(@Context headers: HttpHeaders,
                 @QueryParam("code") code: String?,
                 @QueryParam("error_description") errorDescription: String?,
                 @QueryParam("error") error: String?,
                 @QueryParam("state") state: String,
                 @HeaderParam("x-forwarded-host") forwardedHost: String,
                 @CookieParam("AUTH_NONCE") nonceCookie: Cookie): Response {
        if (LOGGER.isTraceEnabled) {
            printHeaders(headers)
        }
        return when {
            error.isNullOrEmpty() && code.isNullOrEmpty() -> throw SigninException("Missing field. One of the fields 'code' or 'error' must be filled out.")
            error == "unauthorized" -> throw UnauthorizedException(error, errorDescription)
            !error.isNullOrEmpty() -> throw SigninException(error, errorDescription)
            !code.isNullOrEmpty() -> performSignin(code, forwardedHost, state, nonceCookie)
            else -> throw SigninException("Illegal State", "Shouldn't be possible to end in this state.")
        }
    }

    /**
     * Verify Signin and set user session cookies.
     * @param code is the exchange code for tokens.
     * @param forwardedHost is the host header set by Traefik when it forwards a requests
     * @param nonceCookie the secret nonce to prevent CORS set in browser cookie.
     * @param state Is state sent while authenticating and should contain the same nonce as the noonce cookie + some more.
     *
     * TODO should extract this method into an application service like I have aready done with AuthorizationCommandHandler
     * so that its easier to write unit tests, separating the code from http/rest technical code into pure application logic.
     */
    private fun performSignin(code: String, forwardedHost: String, state: String, nonceCookie: Cookie): Response {
        LOGGER.debug("SignIn with code=$code")
        val app = properties.findApplicationOrDefault(forwardedHost)
        val audience = app.audience
        val tokenCookieDomain = app.tokenCookieDomain

        // TODO move into NonceService and add proper errorhandling if nnonce check fails.
        val decodedState = State.decode(state)
        val receivedNonce = decodedState.nonce.value
        val sentNonce = nonceCookie.value
        if (receivedNonce != sentNonce) {
            LOGGER.error("SignInFailedNonce received=$receivedNonce sent=$sentNonce")
        }

        val response = auth0Client.authorizationCodeExchange(code, app.clientId, app.clientSecret, app.redirectUri)
        val accessToken = response.get("access_token") as String
        val idToken = response.get("id_token") as String

        if (shouldVerifyAccessToken(app)) {
            verifyTokenService.verify(accessToken, audience, DOMAIN)
        }
        val accessTokenCookie = NewCookie("ACCESS_TOKEN", accessToken, "/", tokenCookieDomain, null, -1, false, true)
        val jwtCookie = NewCookie("JWT_TOKEN", idToken, "/", tokenCookieDomain, null, -1, false, true)
        val nonceCookie = NewCookie("AUTH_NONCE", "deleted", "/", tokenCookieDomain, null, 0, false, true)

        LOGGER.info("SignInSuccessful, redirect to originUrl originUrl=${decodedState.originUrl}")
        return Response
                .temporaryRedirect(decodedState.originUrl.uri())
                .cookie(jwtCookie)
                .cookie(accessTokenCookie)
                .cookie(nonceCookie)
                .build()
    }

    private fun shouldVerifyAccessToken(app: AuthProperties.Application): Boolean = !app.audience.equals("${DOMAIN}userinfo")

    private fun printHeaders(headers: HttpHeaders) {
        for (requestHeader in headers.requestHeaders) {
            LOGGER.trace("Header ${requestHeader.key} = ${requestHeader.value}")
        }
    }
}