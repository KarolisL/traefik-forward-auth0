package dniel.forwardauth.domain

import javax.ws.rs.WebApplicationException
import javax.ws.rs.core.Response

class AuthorizationException : WebApplicationException {
    constructor(error: String, description: String) : super("${error}: ${description}", Response.Status.FORBIDDEN)
}