package dniel.forwardauth.domain

import java.net.URI
import java.net.URLEncoder

class OriginUrl(val protocol: String, val host: String, val uri: String) {

    override fun toString(): String {
        return "$protocol://$host$uri".toLowerCase()
    }

    fun startsWith(url: String): Boolean = this.toString().startsWith(url, ignoreCase = true)

    fun uri(): URI = URI.create(URLEncoder.encode(this.toString(), "UTF-8"))

}
