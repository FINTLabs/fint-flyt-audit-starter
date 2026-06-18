package no.novari.flyt.audit.authorization

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.util.UUID

@RestClientTest
@EnableConfigurationProperties(AuthorizationProperties::class)
@TestPropertySource(
    properties = [
        "novari.flyt.audit.authorization.base-url=http://auth-service",
        "novari.flyt.audit.authorization.client-registration-id=authorization-service",
    ],
)
class AuthorizationClientTest {
    @TestConfiguration
    class Config {
        @Bean("authorizationRestClient")
        fun authorizationRestClient(
            builder: RestClient.Builder,
            properties: AuthorizationProperties,
        ): RestClient = builder.baseUrl("${properties.baseUrl}/api/intern-klient/authorization/users").build()

        @Bean
        fun authorizationClient(
            @Qualifier("authorizationRestClient") restClient: RestClient,
        ): AuthorizationClient = RestClientAuthorizationClient(restClient)
    }

    @Autowired
    lateinit var client: AuthorizationClient

    @Autowired
    lateinit var server: MockRestServiceServer

    @Autowired
    lateinit var mapper: ObjectMapper

    @Test
    fun `findByObjectIdentifier returnerer bruker ved 200`() {
        val oid = UUID.randomUUID()
        val dto = AuthorizedUserDto(objectIdentifier = oid, name = "Ola Nordmann")

        server
            .expect(requestTo("http://auth-service/api/intern-klient/authorization/users/$oid"))
            .andRespond(withSuccess(mapper.writeValueAsString(dto), MediaType.APPLICATION_JSON))

        val result = client.findByObjectIdentifier(oid)

        assertThat(result).isEqualTo(dto)
    }

    @Test
    fun `findByObjectIdentifier returnerer null ved 404`() {
        val oid = UUID.randomUUID()

        server
            .expect(requestTo("http://auth-service/api/intern-klient/authorization/users/$oid"))
            .andRespond(withResourceNotFound())

        val result = client.findByObjectIdentifier(oid)

        assertThat(result).isNull()
    }

    @Test
    fun `lookupUsers returnerer liste ved batch-oppslag`() {
        val oid1 = UUID.randomUUID()
        val oid2 = UUID.randomUUID()
        val dtos =
            listOf(
                AuthorizedUserDto(objectIdentifier = oid1, name = "Ola Nordmann"),
                AuthorizedUserDto(objectIdentifier = oid2, name = "Kari Nordmann"),
            )

        server
            .expect(requestTo("http://auth-service/api/intern-klient/authorization/users/actions/lookup"))
            .andRespond(withSuccess(mapper.writeValueAsString(dtos), MediaType.APPLICATION_JSON))

        val result = client.lookupUsers(listOf(oid1, oid2))

        assertThat(result).containsExactlyInAnyOrderElementsOf(dtos)
    }

    @Test
    fun `lookupUsers returnerer tom liste ved tom inndata`() {
        val result = client.lookupUsers(emptyList())

        assertThat(result).isEmpty()
        server.verify()
    }
}
