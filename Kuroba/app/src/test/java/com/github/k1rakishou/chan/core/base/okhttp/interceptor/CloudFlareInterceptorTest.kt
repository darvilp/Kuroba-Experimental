package com.github.k1rakishou.chan.core.base.okhttp.interceptor

import com.github.k1rakishou.chan.core.manager.FirewallBypassManager
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.SiteRequestModifier
import com.github.k1rakishou.chan.core.site.SiteResolver
import com.github.k1rakishou.common.FirewallDetectedException
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.whenever
import okhttp3.ConnectionPool
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.mockito.Mockito.mock

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class CloudFlareInterceptorTest {
  private val siteResolver = mock(SiteResolver::class.java)
  private val bypassManager = mock(FirewallBypassManager::class.java)
  private val interceptor = CloudFlareInterceptor(siteResolver, bypassManager)

  @Test
  fun `ordinary error responses remain readable`() {
    for (code in listOf(403, 503)) {
      for (text in listOf("Permission denied", "", "<", "x".repeat(24 * 1024) + "tail")) {
        val body = TrackingBody(text)
        client(body, code).newCall(request()).execute().use { response ->
          assertFalse(body.closed)
          assertEquals(text, response.body.string())
        }
        assertTrue(body.closed)
      }
    }
  }

  @Test
  fun `body detection spans reads but respects preview limit`() {
    val body = TrackingBody("x".repeat(9000) + "<title>Just a moment</title>")
    assertThrows(FirewallDetectedException::class.java) {
      client(body).newCall(request()).execute().close()
    }
    assertTrue(body.closed)

    val text = "x".repeat(24 * 1024) + "<title>Just a moment</title>"
    val beyondLimit = TrackingBody(text)
    client(beyondLimit).newCall(request()).execute().use { response ->
      assertFalse(beyondLimit.closed)
      assertEquals(text, response.body.string())
    }
  }

  @Test
  fun `detected challenges close before failed bypass`() {
    for (serverHeader in listOf(false, true)) {
      val body = TrackingBody("<title>Just a moment</title>")
      configureBypass {
        assertTrue(body.closed)
        false
      }
      assertThrows(FirewallDetectedException::class.java) {
        client(body, serverHeader = serverHeader).newCall(request()).execute().close()
      }
      assertTrue(body.closed)
    }
  }

  @Test
  fun `ineligible requests close rejected responses`() {
    val body = TrackingBody("challenge")
    assertThrows(FirewallDetectedException::class.java) {
      client(body, serverHeader = true).newCall(request().newBuilder().head().build()).execute().close()
    }
    assertTrue(body.closed)
  }

  @Test
  fun `successful bypass closes challenge and returns open retry response`() {
    for (retryCode in listOf(200, 403)) {
      val first = TrackingBody("challenge")
      val second = TrackingBody("retry")
      var bypassCount = 0
      configureBypass {
        assertTrue(first.closed)
        bypassCount++
        true
      }
      var requests = 0
      val client = OkHttpClient.Builder().addInterceptor(interceptor).addInterceptor { chain ->
        requests++
        if (requests == 1) {
          response(chain.request(), first, 403, true)
        } else {
          response(chain.request(), second, retryCode, retryCode == 403)
        }
      }.build()

      if (retryCode == 200) {
        client.newCall(request()).execute().use { result ->
          assertFalse(second.closed)
          assertEquals("retry", result.body.string())
        }
      } else {
        assertThrows(FirewallDetectedException::class.java) { client.newCall(request()).execute().close() }
      }
      assertEquals(2, requests)
      assertEquals(1, bypassCount)
      assertTrue(first.closed)
      assertTrue(second.closed)
    }
  }

  @Test
  fun `terminal challenge releases real HTTP connection`() {
    MockWebServer().use { server ->
      server.enqueue(MockResponse().setResponseCode(403).setHeader("Server", "cloudflare").setBody("challenge"))
      server.start()
      val pool = ConnectionPool()
      var received: Response? = null
      val client = OkHttpClient.Builder().connectionPool(pool).addInterceptor(interceptor)
        .addNetworkInterceptor { chain -> chain.proceed(chain.request()).also { received = it } }
        .build()
      try {
        assertThrows(FirewallDetectedException::class.java) {
          client.newCall(Request.Builder().url(server.url("/")).build()).execute().close()
        }
        assertEquals(pool.connectionCount(), pool.idleConnectionCount())
      } finally {
        received?.close()
        pool.evictAll()
      }
    }
  }

  private fun configureBypass(result: () -> Boolean) {
    val site = mock(Site::class.java)
    whenever(site.requestModifier).thenReturn(mock(SiteRequestModifier::class.java))
    whenever(siteResolver.isInitialized()).thenReturn(true)
    whenever(siteResolver.findSiteForUrl(any())).thenReturn(site)
    doAnswer { invocation ->
      invocation.getArgument<(Boolean) -> Unit>(2).invoke(result())
      null
    }.whenever(bypassManager).onFirewallDetected(any(), any(), any())
  }

  private fun request(): Request = Request.Builder().url("https://example.com/").build()

  private fun client(body: ResponseBody, code: Int = 403, serverHeader: Boolean = false): OkHttpClient {
    return OkHttpClient.Builder().addInterceptor(interceptor).addInterceptor { chain ->
      response(chain.request(), body, code, serverHeader)
    }.build()
  }

  private fun response(request: Request, body: ResponseBody, code: Int, serverHeader: Boolean): Response {
    return Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("test")
      .body(body).apply { if (serverHeader) header("Server", "cloudflare") }.build()
  }

  private class TrackingBody(text: String) : ResponseBody() {
    var closed = false
    private val data = object : ForwardingSource(Buffer().writeUtf8(text)) {
      override fun read(sink: Buffer, byteCount: Long): Long = super.read(sink, minOf(byteCount, 17L))
      override fun close() {
        closed = true
        super.close()
      }
    }.buffer()

    override fun contentType(): MediaType? = null
    override fun contentLength(): Long = -1
    override fun source(): BufferedSource = data
  }
}
