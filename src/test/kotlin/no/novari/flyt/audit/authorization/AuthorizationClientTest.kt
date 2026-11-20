package no.novari.flyt.audit.authorization

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import tools.jackson.databind.ObjectMapper
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
    fun `findByObjectIdentifier returns the user on 200`() {
        val oid = UUID.randomUUID()
        val dto = AuthorizedUserDto(objectIdentifier = oid, name = "Ola Nordmann")

        server
            .expect(requestTo("http://auth-service/api/intern-klient/authorization/users/$oid"))
            .andRespond(withSuccess(mapper.writeValueAsString(dto), MediaType.APPLICATION_JSON))

        val result = client.findByObjectIdentifier(oid)

        assertThat(result).isEqualTo(dto)
    }

    @Test
    fun `findByObjectIdentifier returns null on 404`() {
        val oid = UUID.randomUUID()

        server
            .expect(requestTo("http://auth-service/api/intern-klient/authorization/users/$oid"))
            .andRespond(withResourceNotFound())

        val result = client.findByObjectIdentifier(oid)

        assertThat(result).isNull()
    }

    @Test
    fun `findByObjectIdentifier rethrows HTTP errors other than 404`() {
        val oid = UUID.randomUUID()

        server
            .expect(requestTo("http://auth-service/api/intern-klient/authorization/users/$oid"))
            .andRespond(withServerError())

        assertThatThrownBy { client.findByObjectIdentifier(oid) }
            .isInstanceOf(RestClientResponseException::class.java)
    }

    @Test
    fun `lookupUsers returns a list for a batch lookup`() {
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
    fun `lookupUsers returns an empty list for empty input`() {
        val result = client.lookupUsers(emptyList())

        assertThat(result).isEmpty()
        server.verify()
    }
}
