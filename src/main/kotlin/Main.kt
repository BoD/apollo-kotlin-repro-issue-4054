import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.api.DefaultUpload
import com.apollographql.apollo3.api.content
import com.apollographql.apollo3.api.http.HttpBody
import com.apollographql.apollo3.api.http.HttpRequest
import com.apollographql.apollo3.api.http.HttpResponse
import com.apollographql.apollo3.network.http.HttpInterceptor
import com.apollographql.apollo3.network.http.HttpInterceptorChain
import com.apollographql.apollo3.network.okHttpClient
import com.example.CreateProjectMutation
import okhttp3.OkHttpClient
import okio.Buffer
import okio.BufferedSink
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

private const val USE_PROXY = true


/**
 * An interceptor that provides a Content-Length on the body if not already present.
 */
class ProvideContentLengthHttpInterceptor : HttpInterceptor {
    override suspend fun intercept(request: HttpRequest, chain: HttpInterceptorChain): HttpResponse {
        if (request.body == null || request.body!!.contentLength != -1L) {
            return chain.proceed(request)
        }
        val body = request.body!!
        val buffer = Buffer()
        // XXX The whole body is written in memory! This could potentially be huge, so this is not a good
        // practice. This is a temporary workaround for issue #4054.
        body.writeTo(buffer)
        val contentLength = buffer.size
        return chain.proceed(
            request.newBuilder().body(object : HttpBody {
                override val contentType = body.contentType
                override val contentLength = contentLength
                override fun writeTo(bufferedSink: BufferedSink) {
                    bufferedSink.writeAll(buffer)
                }
            }).build()
        )
    }
}

suspend fun main() {
    val apolloClient = ApolloClient.Builder()
        .serverUrl("https://api.sandbox.wevidit.com/graphql")
        .apply {
            if (USE_PROXY) okHttpClient(
                OkHttpClient.Builder()
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress("localhost", 8888)))
                    .ignoreAllSSLErrors()
                    .build()
            )
        }
        .addHttpHeader("Authorization",
            "JWT <REPLACE WITH TOKEN>")
        .addHttpInterceptor(ProvideContentLengthHttpInterceptor())
        .build()

    val thumbnail = DefaultUpload.Builder()
        .content(File("thumbnail.jpg"))
        .contentType("image/jpeg")
        .fileName("thumbnail.jpg")
        .build()

    val video = DefaultUpload.Builder()
        .content(File("video.mp4"))
        .contentType("video/mp4")
        .fileName("video.mp4")
        .build()

    val response = apolloClient.mutation(CreateProjectMutation(thumbnail = thumbnail, video = video)).execute()
    println(response.toFormattedString())
}

