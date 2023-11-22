package com.example

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpVersion
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.ReadTimeoutException
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import reactor.core.publisher.Flux
import kotlin.time.Duration.Companion.milliseconds

private const val serverPort = "24444"
private val readTimeout = 500.milliseconds

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HttpclientBugTest : TestPropertyProvider {

    @Inject
    @field:Client("/")
    lateinit var httpClientv1: HttpClient

    @Inject
    @field:Client(
        "/",
        httpVersion = HttpVersion.HTTP_2_0
    )
    lateinit var httpClientv2: HttpClient


    @Test
    fun testClientV1() {
        doTest(httpClientv1)
    }

    @Test
    fun testClientV1Async() {
        runBlocking { doTestWithAsync(httpClientv1) }
    }

    @Test
    fun testClientV1AsyncAndAwaitHelper() {
        runBlocking { doTestWithAsyncAndAwaitSingle(httpClientv1) }
    }

    @Test
    fun testClientV2() {
        doTest(httpClientv2)
    }

    @Test
    fun testClientV2Async() {
        runBlocking { doTestWithAsync(httpClientv1) }
    }

    @Test
    fun testClientV2AsyncAndAwaitHelper() {
        runBlocking { doTestWithAsyncAndAwaitSingle(httpClientv2) }
    }


    private fun doTest(httpClient: HttpClient) {
        val getFirst = httpClient.exchange(HttpRequest.GET<Void>("/first"), String::class.java)
        val getSecond = httpClient.exchange(HttpRequest.GET<Void>("/second"), String::class.java)

        val firstResponse = try {
            Flux.from(getFirst).single().block()?.body()
        } catch (e: ReadTimeoutException) {
            null
        }
        val secondResponse = try {
            Flux.from(getSecond).single().block()?.body()
        } catch (e: ReadTimeoutException) {
            null
        }

        Assertions.assertNull(firstResponse)
        Assertions.assertEquals(secondResponse, "secondone")
    }

    private suspend fun doTestWithAsync(httpClient: HttpClient) {
        val getFirst = httpClient.exchange(HttpRequest.GET<Void>("/first"), String::class.java)
        val getSecond = httpClient.exchange(HttpRequest.GET<Void>("/second"), String::class.java)

        supervisorScope {
            val firstResponse = async {
                try {
                    Flux.from(getFirst).single().block()?.body()
                } catch (e: ReadTimeoutException) {
                    println("firstGet timeout")
                    null
                }
            }
            val secondResponse = async {
                try {
                    Flux.from(getSecond).single().block()?.body()
                } catch (e: ReadTimeoutException) {
                    println("secondGet timeout")
                    null
                }
            }

            Assertions.assertNull(firstResponse.await())
            Assertions.assertEquals(secondResponse.await(), "secondone")
        }
    }

    private suspend fun doTestWithAsyncAndAwaitSingle(httpClient: HttpClient) {
        val getFirst = httpClient.exchange(HttpRequest.GET<Void>("/first"), String::class.java)
        val getSecond = httpClient.exchange(HttpRequest.GET<Void>("/second"), String::class.java)

        supervisorScope {
            val firstResponse = async {
                try {
                    getFirst.awaitSingle().body()
                } catch (e: ReadTimeoutException) {
                    println("firstGet timeout")
                    null
                }
            }
            val secondResponse = async {
                try {
                    getSecond.awaitSingle().body()
                } catch (e: ReadTimeoutException) {
                    println("secondGet timeout")
                    null
                }
            }

            Assertions.assertNull(firstResponse.await())
            Assertions.assertEquals(secondResponse.await(), "secondone")
        }
    }


    @Controller
    class TestController {

        @Get("/first")
        fun first(): String {
            Thread.sleep(readTimeout.plus(200.milliseconds).inWholeMilliseconds)
            return "firstone"
        }

        @Get("/second")
        fun second(): String {
            return "secondone"
        }
    }

    override fun getProperties(): Map<String, String> =
        mapOf(
            "micronaut.server.port" to serverPort,
            "micronaut.server.context-path" to "/",
            "micronaut.server.http-version" to "2.0",
            "micronaut.http.client.read-timeout" to readTimeout.toString()
        )
}
