package no.novari.flyt.audit.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class FlytAuditAutoConfigurationTest {
    private val contextRunner =
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FlytAuditAutoConfiguration::class.java))

    @Test
    fun `auto-configuration registreres og kan lastes`() {
        contextRunner.run { context ->
            assertThat(context).hasSingleBean(FlytAuditAutoConfiguration::class.java)
        }
    }
}
