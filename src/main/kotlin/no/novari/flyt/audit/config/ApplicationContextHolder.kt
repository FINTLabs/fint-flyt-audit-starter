package no.novari.flyt.audit.config

import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware

class ApplicationContextHolder : ApplicationContextAware {
    override fun setApplicationContext(applicationContext: ApplicationContext) {
        context = applicationContext
    }

    companion object {
        private lateinit var context: ApplicationContext

        fun getContext(): ApplicationContext = context
    }
}
