package com.dutyventures.dutyeyes.data

import android.annotation.SuppressLint
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import java.net.URI
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.cert.CertificateException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager


class OnvifManager(
    private val user: String,
    private val password: String,
    private val ip: String,
    private val firstProfile: Boolean
) {

    private fun getProfilesBody(
        authData: OnvifAuthData
    ) =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?> <s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:tds=\"http://www.onvif.org/ver10/device/wsdl\" xmlns:tt=\"http://www.onvif.org/ver10/schema\" xmlns:trt=\"http://www.onvif.org/ver10/media/wsdl\"> <s:Header> <Security s:mustUnderstand=\"1\" xmlns=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd\"> <UsernameToken> <Username>${authData.username}</Username> <Password Type=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordDigest\">${authData.passwordEncrypted}</Password> <Nonce EncodingType=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary\">${authData.nonceEncrypted}</Nonce> <Created xmlns=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd\">${authData.date}</Created> </UsernameToken> </Security> </s:Header> <s:Body> <trt:GetProfiles/> </s:Body> </s:Envelope>"

    private fun getStreamUri(
        authData: OnvifAuthData,
        profileToken: String?
    ) =
//        "<?xml version=\"1.0\" encoding=\"UTF-8\"?> <s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:tds=\"http://www.onvif.org/ver10/device/wsdl\" xmlns:tt=\"http://www.onvif.org/ver10/schema\" xmlns:trt=\"http://www.onvif.org/ver10/media/wsdl\"> <s:Header> <Security s:mustUnderstand=\"1\" xmlns=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd\"> <UsernameToken> <Username>${authData.username}</Username> <Password Type=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordDigest\">${authData.passwordEncrypted}</Password> <Nonce EncodingType=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary\">${authData.nonceEncrypted}</Nonce> <Created xmlns=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd\">${authData.date}</Created> </UsernameToken> </Security> </s:Header> <s:Body> <trt:GetStreamUri> <trt:StreamSetup> <tt:Stream>RTP-Unicast</tt:Stream> <tt:Transport> <tt:Protocol>UDP</tt:Protocol> </Transport> </trt:StreamSetup> <trt:ProfileToken>$profileToken</trt:ProfileToken> </trt:GetStreamUri> </s:Body> </s:Envelope>"
    "<?xml version=\"1.0\" encoding=\"UTF-8\"?> <s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:tds=\"http://www.onvif.org/ver10/device/wsdl\" xmlns:tt=\"http://www.onvif.org/ver10/schema\" xmlns:trt=\"http://www.onvif.org/ver10/media/wsdl\"> <s:Header> <Security s:mustUnderstand=\"1\" xmlns=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd\"> <UsernameToken> <Username>${authData.username}</Username> <Password Type=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordDigest\">${authData.passwordEncrypted}</Password> <Nonce EncodingType=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary\">${authData.nonceEncrypted}</Nonce> <Created xmlns=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd\">${authData.date}</Created> </UsernameToken> </Security> </s:Header> <s:Body> <trt:GetStreamUri> <trt:ProfileToken>$profileToken</trt:ProfileToken> </trt:GetStreamUri> </s:Body> </s:Envelope>"

    private val network = OnvifNetwork()

    companion object {
        private val noString = "()()nostring()()"
    }

    suspend fun getStreamUrlV2(): NetworkResponse {

        val auth = OnvifAuth(password = password, username = user)

        val profileBody = getProfilesBody(authData = auth.getData())
        val profileToken = network.requestGetProfile(ip, profileBody, firstProfile)
        val streamUriBody = getStreamUri(
            authData = auth.getData(),
            profileToken = profileToken
        )
        val streamResponse = network.requestGetStream(ip, streamUriBody)!!
        val streamUrl = streamResponse.substringAfter("<tt:Uri>", noString)
            .substringBefore("</tt:Uri>", noString)

        return if (streamUrl != noString) {
            try {
                val host = URI(streamUrl).host
                val newUrl = streamUrl.replace(host, URI(ip).host)
                NetworkResponse.Success(newUrl)
            } catch (e: Exception) {
                Log.e(
                    "Error",
                    "Error replacing IP. From ONVIF: $streamUrl actual IP: $ip ${e.stackTraceToString()}",
                    e
                )
                NetworkResponse.Error("Error replacing IP. From ONVIF: $streamUrl actual IP: $ip ${e.stackTraceToString()}")
            }
        } else {
            NetworkResponse.Error("Can't find the token, in result: $streamResponse")
        }
    }


    suspend fun getStreamUrl(): NetworkResponse {
        val auth = OnvifAuth(password = password, username = user)
        val noString = "[&no_string&]"

        val profileBody = getProfilesBody(authData = auth.getData())
        val profileResponse = network.createSoapRequest(ip, profileBody)
        if (profileResponse !is NetworkResponse.Success) {
            return profileResponse
        }
        val profileToken = if (firstProfile) {
            profileResponse.result.substringAfter(" token=\"", noString)
                .substringBefore("\">", noString)
        } else {
            val removeFirstPart = profileResponse.result.substringAfter("</trt:Profiles>")
            removeFirstPart.substringAfter(" token=\"", noString)
                .substringBefore("\">", noString)
        }
        if (profileToken == noString) {
            return NetworkResponse.Error("Can't find token in response ${profileResponse.result}")
        }
        val streamUriBody = getStreamUri(
            authData = auth.getData(),
            profileToken = profileToken
        )

        val streamUriResponse = network.createSoapRequest(ip, streamUriBody)
        if (streamUriResponse !is NetworkResponse.Success) {
            return streamUriResponse
        }
        val streamUrl =
            streamUriResponse.result.substringAfter("<tt:Uri>", noString)
                .substringBefore("</tt:Uri>", noString)

        return if (streamUrl != noString) {
            try {
                val host = URI(streamUrl).host
                val newUrl = streamUrl.replace(host, URI(ip).host)
                NetworkResponse.Success(newUrl)
            } catch (e: Exception) {
                Log.e(
                    "Error",
                    "Error replacing IP. From ONVIF: $streamUrl actual IP: $ip ${e.stackTraceToString()}",
                    e
                )
                NetworkResponse.Error("Error replacing IP. From ONVIF: $streamUrl actual IP: $ip ${e.stackTraceToString()}")
            }
        } else {
            NetworkResponse.Error("Can't find the token, in result: ${streamUriResponse.result}")
        }
    }
}

data class OnvifAuthData(
    val nonceEncrypted: String?,
    val passwordEncrypted: String?,
    val date: String?,
    val username: String?
)

class OnvifAuth(private val password: String?, private val username: String?) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-d'T'HH:mm:ss'Z'", Locale.getDefault())

    fun getData(): OnvifAuthData {
        val nonce = (Math.random() * 10000).toInt().toString()
        val date = getUTCTime()
        return OnvifAuthData(
            nonceEncrypted = Base64.encodeToString(
                nonce.toByteArray(),
                Base64.DEFAULT
            ),
            passwordEncrypted = encryptPassword(nonce = nonce, date = date),
            date = date,
            username = username
        )
    }

    private fun getUTCTime(): String {
        val calendar = GregorianCalendar(TimeZone.getTimeZone("UTC"))
        return dateFormat.format(calendar.time)
    }

    private fun encryptPassword(nonce: String?, date: String?): String {
        val beforeEncryption = nonce + date + password
        val encryptedRaw: ByteArray = try {
            sha1(beforeEncryption)
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
            return ""
        }
        return Base64.encodeToString(encryptedRaw, Base64.DEFAULT)
    }

    @Throws(NoSuchAlgorithmException::class)
    private fun sha1(text: String): ByteArray {
        val sha1: MessageDigest = MessageDigest.getInstance("SHA1")
        sha1.reset()
        sha1.update(text.toByteArray())
        return sha1.digest()
    }
}


sealed class NetworkResponse {
    class Success(val result: String) : NetworkResponse()
    class Error(val errorMessage: String) : NetworkResponse()
}

class OnvifNetwork {

    private fun createClient(): OkHttpClient {
        val trustAllCerts = arrayOf<TrustManager>(@SuppressLint("CustomX509TrustManager")
        object : X509TrustManager {
            @Throws(CertificateException::class)
            override fun checkClientTrusted(
                chain: Array<java.security.cert.X509Certificate>,
                authType: String
            ) {
            }

            @Throws(CertificateException::class)
            override fun checkServerTrusted(
                chain: Array<java.security.cert.X509Certificate>,
                authType: String
            ) {
            }

            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> {
                return arrayOf()
            }
        })

        // Install the all-trusting trust manager
        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, java.security.SecureRandom())
        // Create an ssl socket factory with our all-trusting manager
        val sslSocketFactory = sslContext.socketFactory

        val builder = OkHttpClient.Builder()
        builder.sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
        // builder.hostnameVerifier { _, _ -> true }
        builder.hostnameVerifier { _, _ -> true }
        return builder
            .connectTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .build()

    }

    @Suppress("BlockingMethodInNonBlockingContext", "BlockingMethodInNonBlockingContext")
    suspend fun requestGetStream(ip: String, bodyString: String): String? {
        val client: OkHttpClient = OkHttpClient().newBuilder()
            .build()
        val mediaType: MediaType? = MediaType.parse("application/soap+xml")
        val body: RequestBody = RequestBody.create(
            mediaType,
            bodyString
        )
        val request: Request = Request.Builder()
            .url("${ip}/onvif/device_service")
            .method("POST", body)
            .addHeader("Content-Type", "application/soap+xml")
            .build()
        val response: Response = client.newCall(request).execute()
        return response.body()?.string()
    }

    @Suppress("BlockingMethodInNonBlockingContext", "BlockingMethodInNonBlockingContext")
    suspend fun requestGetProfile(ip: String, bodyString: String, firstProfile: Boolean): String? {
        val client: OkHttpClient = OkHttpClient().newBuilder()
            .build()
        val mediaType: MediaType? = MediaType.parse("application/soap+xml")
        val body: RequestBody = RequestBody.create(
            mediaType,
            bodyString
        )
        val request: Request = Request.Builder()
            .url("${ip}/onvif/device_service")
            .method("POST", body)
            .addHeader("Content-Type", "application/soap+xml")
            .build()
        val response: Response = client.newCall(request).execute()
        val responseString = response.body()?.string()!!

        val noString = "()()nostring()()"
        val profileToken = if (firstProfile) {
            responseString.substringAfter(" token=\"", noString)
                .substringBefore("\">", noString)
        } else {
            val removeFirstPart = responseString.substringAfter("</trt:Profiles>")
            removeFirstPart.substringAfter(" token=\"", noString)
                .substringBefore("\">", noString)
        }
        if (profileToken == noString) {
            throw Exception("Can't find token in response ${profileToken}")
        }
        return profileToken
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun createSoapRequest(ip: String, bodyText: String): NetworkResponse {
        return withContext(Dispatchers.IO) {
            val mediaType = MediaType.parse("application/soap+xml")
            val body = RequestBody.create(
                mediaType,
                bodyText
            )
            val request = Request.Builder()
                .url("$ip/onvif/device_service")
                .method("POST", body)
                .addHeader("Content-Type", "application/soap+xml")
                .build()
            try {
                val response = createClient().newCall(request).execute()
                NetworkResponse.Success(response.body()?.string()!!).also {
                    response.close()
                }
            } catch (e: Exception) {
                Log.e("Error", "Can't finish request", e)
                NetworkResponse.Error(e.stackTraceToString())
            }
        }
    }
}