package dniel.forwardauth.domain

import javax.ws.rs.WebApplicationException
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

class UnauthorizedException : WebApplicationException {
    constructor(error: String, description: String? = "unknown") : super(
            Response.status(Response.Status.FORBIDDEN)
                    .entity("${error}: ${description}")
                    .type(MediaType.APPLICATION_JSON)
                    .build())
}