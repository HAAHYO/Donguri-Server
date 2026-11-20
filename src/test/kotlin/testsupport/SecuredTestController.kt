package testsupport

import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class SecuredTestController {
    @GetMapping("/test/authenticated")
    fun authenticated() = "ok"

    @PreAuthorize("hasRole('FDS_ADMIN')")
    @GetMapping("/test/fds-admin")
    fun fdsAdmin() = "ok"

    @GetMapping("/test/whoami")
    fun whoami(authentication: Authentication): String = authentication.name

    @GetMapping("/test/boom")
    fun boom(): String = throw IllegalStateException("internal-detail-should-not-leak")
}
