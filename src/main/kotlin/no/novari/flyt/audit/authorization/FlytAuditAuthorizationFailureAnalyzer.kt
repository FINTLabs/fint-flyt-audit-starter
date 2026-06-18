package no.novari.flyt.audit.authorization

import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.beans.factory.UnsatisfiedDependencyException
import org.springframework.boot.diagnostics.AbstractFailureAnalyzer
import org.springframework.boot.diagnostics.FailureAnalysis

class FlytAuditAuthorizationFailureAnalyzer : AbstractFailureAnalyzer<UnsatisfiedDependencyException>() {
    override fun analyze(
        rootFailure: Throwable,
        cause: UnsatisfiedDependencyException,
    ): FailureAnalysis? {
        val missingBean = findMissingBean(cause) ?: return null
        if (!missingBean.contains("ClientRegistrationRepository") &&
            !missingBean.contains("OAuth2AuthorizedClientService")
        ) {
            return null
        }

        return FailureAnalysis(
            """
            fint-flyt-audit-starter krever OAuth2 client credentials for å kalle
            fint-flyt-authorization-service (navn-oppslag for endringslogg), men
            spring.security.oauth2.client.registration.$DEFAULT_REGISTRATION_ID
            er ikke konfigurert eller spring-boot-starter-oauth2-client mangler.

            Merk: '$DEFAULT_REGISTRATION_ID' er standard registrerings-ID. Bruker du
            et annet navn, sett novari.flyt.audit.authorization.client-registration-id
            til samme verdi i application.yaml.
            """.trimIndent(),
            """
            1. Legg til spring-boot-starter-oauth2-client som avhengighet i tjenesten.
            2. Legg til NamOAuthClientApplicationResource i kustomize/base/ og map
               nøkkelen i flais.yaml:

                   # kustomize/base/oauth2-authorization-client.yaml
                   apiVersion: fintlabs.no/v1alpha1
                   kind: NamOAuthClientApplicationResource
                   metadata:
                     name: fint-flyt-authorization-oauth2-client
                   spec:
                     grantTypes:
                       - client_credentials

                   # flais.yaml (env-seksjon)
                   - name: fint.flyt.authorization.sso.client-id
                     valueFrom:
                       secretKeyRef:
                         name: fint-flyt-authorization-oauth2-client
                         key: fint.sso.client-id
                   - name: fint.flyt.authorization.sso.client-secret
                     valueFrom:
                       secretKeyRef:
                         name: fint-flyt-authorization-oauth2-client
                         key: fint.sso.client-secret

            3. Legg til application-flyt-authorization-client.yaml med:

                   spring:
                     security:
                       oauth2:
                         client:
                           registration:
                             $DEFAULT_REGISTRATION_ID:
                               authorization-grant-type: client_credentials
                               client-id: ${"$"}{fint.flyt.authorization.sso.client-id}
                               client-secret: ${"$"}{fint.flyt.authorization.sso.client-secret}
                               provider: fint-idp

               og aktiver profilen via spring.profiles.include i application.yaml.
            """.trimIndent(),
            cause,
        )
    }

    private fun findMissingBean(cause: UnsatisfiedDependencyException): String? {
        var current: Throwable? = cause
        while (current != null) {
            if (current is NoSuchBeanDefinitionException) {
                return current.beanType?.simpleName ?: current.beanName
            }
            current = current.cause
        }
        return null
    }

    companion object {
        private const val DEFAULT_REGISTRATION_ID = "authorization-service"
    }
}
