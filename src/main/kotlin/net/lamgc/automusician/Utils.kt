package net.lamgc.automusician

import io.ktor.http.*
import org.apache.hc.client5.http.classic.methods.HttpGet
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder
import org.apache.hc.core5.http.HttpEntity
import org.apache.hc.core5.http.HttpHost
import org.apache.hc.core5.http.HttpResponse
import org.apache.hc.core5.http.io.entity.EntityUtils
import org.apache.hc.core5.http.io.entity.StringEntity
import java.io.IOException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class HashcodeSet<T>(private val map: MutableMap<Int, T>, override val size: Int = map.size) : MutableSet<T> {

    override fun add(element: T): Boolean {
        map[getHash(element)] = element
        return true
    }

    override fun addAll(elements: Collection<T>): Boolean {
        for (element in elements) {
            add(element)
        }
        return true
    }

    override fun clear() = map.clear()

    override fun iterator() = map.values.iterator()

    override fun remove(element: T) = map.remove(element.hashCode(), element)

    override fun removeAll(elements: Collection<T>) = map.values.removeAll(elements.toSet())

    override fun retainAll(elements: Collection<T>) = map.values.retainAll(elements.toSet())

    override fun contains(element: T) = map.values.contains(element)

    override fun containsAll(elements: Collection<T>) = map.values.containsAll(elements)

    override fun isEmpty() = map.isEmpty()

    fun getByHash(hash: Int): T {
        if (!map.contains(hash)) {
            throw NoSuchElementException(hash.toString())
        }
        return map[hash]!!
    }

    companion object {
        fun getHash(element: Any?): Int = Objects.hash(element)
    }

}

object HttpUtils {

    private val client = HttpClientBuilder.create()
        .disableCookieManagement()
        .disableAuthCaching()
        .setDefaultRequestConfig(RequestConfig.custom()
            .setConnectTimeout(15, TimeUnit.SECONDS)
            .setResponseTimeout(45, TimeUnit.SECONDS)
            .setProxy(if (Const.config.httpProxy.enable)
                Const.config.httpProxy.let { HttpHost(it.host, it.port) } else null)
            .build())
        .setProxy(if (Const.config.httpProxy.enable)
            Const.config.httpProxy.let { HttpHost(it.host, it.port) } else null)
        .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/95.0.4638.69 Safari/537.36")
        .build()

    fun <R> get(
        url: String,
        cookie: String? = null,
        action: (success: Boolean, response: HttpResponse?, content: String?, cause: Throwable?) -> R
    ): R {

        val request = HttpGet(url)

        if (cookie != null) {
            request.setHeader(HttpHeaders.Cookie, cookie)
        }

        return try {
            val response = client.execute(request)
            action(true, response, EntityUtils.toString(response.entity) ?: "", null)
        } catch (e: IOException) {
            action(false, null, null, e)
        }

    }

    fun <R> post(
        url: String, body: String,
        requestEntity: HttpEntity? = null,
        cookie: String?,
        action: (success: Boolean, response: HttpResponse?, content: String?, cause: Throwable?) -> R
    ): R {
        val request = HttpPost(url)
        if (requestEntity != null) {
            request.entity = requestEntity
        } else if (body.isNotEmpty()) {
            request.entity = StringEntity(body)
        }

        if (cookie != null) {
            request.setHeader(HttpHeaders.Cookie, cookie)
        }

        return try {
            val response = client.execute(request)
            action(true, response, EntityUtils.toString(response.entity) ?: "", null)
        } catch (e: IOException) {
            action(false, null, null, e)
        }
    }

}

fun HttpResponse.notError(): Boolean {
    return this.code !in 400..599
}

/**
 * 多值 Map.
 *
 * 通过 Value 为 List, 简化多值存储的操作.
 * 注意: 本 Map 不支持符号操作, 即使是插入值也不允许 `map[key].add()`, 只能使用 put 方法插入多值.
 */
class MultiValueMap<K, V> : MutableMap<K, MutableList<V>> {

    private val map = ConcurrentHashMap<K, MutableList<V>>()

    fun isEmpty(key: K) = getValuesByKey(key, false)?.isEmpty() ?: true

    fun containsValue(key: K, value: V): Boolean = getValuesByKey(key)?.contains(value) ?: false

    fun clear(key: K) {
        getValuesByKey(key, false)?.clear()
    }

    fun put(key: K, value: V) {
        getValuesByKey(key, true)!!.add(value)
    }

    override fun get(key: K): MutableList<V> = getValuesByKey(key) ?: Collections.emptyList()

    override fun containsKey(key: K): Boolean = map.containsKey(key)

    override fun containsValue(value: MutableList<V>): Boolean = map.containsValue(value)

    override fun isEmpty(): Boolean = map.isEmpty()

    override fun clear() = map.clear()

    override fun put(key: K, value: MutableList<V>): MutableList<V>? = map.put(key!!, value)

    override fun putAll(from: Map<out K, MutableList<V>>) = map.putAll(from)

    override fun remove(key: K): MutableList<V>? = map.remove(key!!)

    private fun getValuesByKey(key: K, create: Boolean = false): MutableList<V>? {
        return if (!map.containsKey(key)) {
            if (create) {
                val newList = LinkedList<V>()
                map[key] = newList
                newList
            } else {
                null
            }
        } else {
            map[key]!!
        }
    }

    override val size: Int
        get() = map.size
    override val entries: MutableSet<MutableMap.MutableEntry<K, MutableList<V>>>
        get() = map.entries
    override val keys: MutableSet<K>
        get() = map.keys
    override val values: MutableCollection<MutableList<V>>
        get() = map.values

}

fun Random.nextString(length: Int = nextInt(32)): String {
    val buffer = StringBuilder()
    for (i in 1..length) {
        val char = when (val num = nextInt(62)) {
            in 0..25 -> {
                'A' + (num % 26)
            }
            in 26..51 -> {
                'a' + (num % 26)
            }
            else -> {
                '0' + (num % 10)
            }
        }
        buffer.append(char)
    }
    return buffer.toString()
}

fun <T> List<T>.randomElement(): T {
    return this[Random.nextInt(this.size)]
}
