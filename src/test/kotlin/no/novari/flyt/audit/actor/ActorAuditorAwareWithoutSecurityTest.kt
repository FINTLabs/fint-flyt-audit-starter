package no.novari.flyt.audit.actor

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URLClassLoader
import java.util.Optional

/** Spring Security is compileOnly, so an app without it must still get an actor for scheduled and Kafka work. */
class ActorAuditorAwareWithoutSecurityTest {
    @Test
    fun `gives Actor System without Spring Security on the classpath`() {
        val classpath =
            System
                .getProperty("java.class.path")
                .split(File.pathSeparator)
                .filterNot { "spring-security" in File(it).name }
                .map { File(it).toURI().toURL() }
        URLClassLoader(classpath.toTypedArray(), ClassLoader.getPlatformClassLoader()).use { loader ->
            assertThat(loader.getResource("org/springframework/security/core/context/SecurityContextHolder.class"))
                .isNull()
            val auditorAware = loader.loadClass(ActorAuditorAware::class.java.name).getConstructor().newInstance()
            val actor = auditorAware.javaClass.getMethod("getCurrentAuditor").invoke(auditorAware) as Optional<*>

            assertThat(actor.get().javaClass.name).isEqualTo(Actor.System::class.java.name)
        }
    }
}
