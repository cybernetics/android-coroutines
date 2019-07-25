package nl.adaptivity.android.test

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import kotlinx.coroutines.*
import kotlinx.coroutines.test.withTestContext
import nl.adaptivity.android.kryo.kryoAndroid
import org.junit.Test

import org.junit.Assert.*
import org.objenesis.strategy.StdInstantiatorStrategy
import java.io.ByteArrayOutputStream
import kotlin.coroutines.Continuation
import kotlin.coroutines.suspendCoroutine
import kotlin.coroutines.resume

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see [Testing documentation](http://d.android.com/tools/testing)
 */
class PlainCoroutineTestAndroid {
    private suspend fun foo(): String {
        yield()
        return "2"
    }

    @Test
    fun testClosureSerialization() = runBlocking {
        val def = async {
            //        val kryo = Kryo().apply { instantiatorStrategy = Kryo.DefaultInstantiatorStrategy(StdInstantiatorStrategy()) }
            val kryo = kryoAndroid

            var coroutine: Continuation<Unit>? = null

            // Blocking scope cannot be serialized, as it holds a thread reference.
            GlobalScope.async(start = CoroutineStart.UNDISPATCHED) {
                val s = "Hello"
                suspendCoroutine<Unit> { cont -> coroutine = cont }
                s
            }


            val baos = ByteArrayOutputStream()

            Output(baos).use { output ->
                kryo.writeClassAndObject(output, coroutine)
            }

            val serialized = baos.toByteArray()
//            val deserializedCoroutine = coroutine!!
            val deserializedCoroutine = kryo.readClassAndObject(Input(serialized)) as Continuation<Unit>
            val resultField = deserializedCoroutine::class.java.getDeclaredField("result").apply { isAccessible=true }
            resultField.set(deserializedCoroutine, kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED)

            deserializedCoroutine.resume(Unit) // is not guaranteed to run here

            val deferred = deserializedCoroutine.context[Job] as Deferred<String>

            val result = runBlocking {
                System.out.println("5")
                deferred.await().apply {
                    System.out.println("6")
                }
            }
            System.out.println("5")

            assertEquals("Hello", result)

            coroutineContext[Job]!!.cancelAndJoin()
        }
        if (def.isActive) {
            delay(1000)
            if (def.isActive) {
                def.cancel(CancellationException("timeout"))
            }
        }
    }
}