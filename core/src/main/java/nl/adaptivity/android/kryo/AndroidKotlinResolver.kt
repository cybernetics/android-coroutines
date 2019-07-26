package nl.adaptivity.android.kryo

import android.accounts.AccountManager
import android.app.Activity
import android.app.Fragment
import android.content.Context
import android.support.v4.app.FragmentActivity
import com.esotericsoftware.kryo.Registration
import com.esotericsoftware.kryo.serializers.FieldSerializer
import com.esotericsoftware.kryo.util.DefaultClassResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.android.HandlerDispatcher
import nl.adaptivity.android.coroutines.contexts.AndroidContext
import nl.adaptivity.android.coroutines.contexts.FragmentContext
import nl.adaptivity.android.kryo.serializers.*
import java.lang.ref.Reference
import kotlin.coroutines.CoroutineContext

open class AndroidKotlinResolver(protected val context: Context?) : DefaultClassResolver() {

    override fun getRegistration(type: Class<*>): Registration? {
        val c = context
        val superReg = super.getRegistration(type)
        return when {
            superReg!=null -> superReg
            type.superclass==null -> superReg
            // For now this is actually unique, but this is not very stable.
            HandlerDispatcher::class.java.isAssignableFrom(type) ->
                register(Registration(type, kryo.pseudoObjectSerializer(Dispatchers.Main), NAME))
            AndroidContext.Key::class.java == type ->
                register(Registration(type, kryo.pseudoObjectSerializer(AndroidContext.Key), NAME))
            FragmentContext.Key::class.java == type ->
                register(Registration(type, kryo.pseudoObjectSerializer(FragmentContext.Key), NAME))
            "nl.adaptivity.android.coroutinesCompat.AppcompatFragmentContext\$Key" == type.name -> {
                register(Registration(type, kryo.pseudoObjectSerializer(APPCOMPATFRAGMENTCONTEXT_KEY), NAME))
            }
            c!=null && c.javaClass == type ->
                register(Registration(type, ContextSerializer(context), NAME))
            Thread::class.java.isAssignableFrom(type) ->
                throw IllegalArgumentException("Serializing threads is never valid")
            Context::class.java.isAssignableFrom(type.superclass) ->
                register(Registration(type, ContextSerializer(context), NAME))
            context is Activity && Fragment::class.java.isAssignableFrom(type.superclass) ->
                register(Registration(type, FragmentSerializer(context), NAME))
            context is FragmentActivity && android.support.v4.app.Fragment::class.java.isAssignableFrom(type.superclass) ->
                register(Registration(type, SupportFragmentSerializer(context), NAME))
            Reference::class.java.isAssignableFrom(type) ->
                register(Registration(type, ReferenceSerializer(kryo, type.asSubclass(Reference::class.java)), NAME))
            Function::class.java.isAssignableFrom(type.superclass) ->
                register(Registration(type, FieldSerializer<Any>(kryo, type).apply { setIgnoreSyntheticFields(false) }, NAME))
            AccountManager::class.java.isAssignableFrom(type) ->
                register(Registration(type, AccountManagerSerializer(kryo, type, c), NAME))
            type.superclass?.name=="kotlin.coroutines.jvm.internal.ContinuationImpl" ->
                register(Registration(type, ContinuationImplSerializer(kryo, type), NAME))
            type.superclass?.name=="kotlin.coroutines.experimental.jvm.internal.CoroutineImpl" ->
                register(Registration(type, CoroutineImplSerializer(kryo, type), NAME))
// Requires the reflection library
//            type.kotlin.isCompanion -> register(Registration(type, kryo.pseudoObjectSerializer(type.kotlin.objectInstance), NAME))
            type.isKObject -> register(Registration(type, ObjectSerializer(kryo, type), NAME))
            else -> null
        }
    }

    companion object {
        const val TAG = "AndroidKotlinResolver"
        const val NAME = DefaultClassResolver.NAME.toInt()
        val APPCOMPATFRAGMENTCONTEXT_CLASS = try {
            Class.forName("nl.adaptivity.android.coroutinesCompat.AppcompatFragmentContext")
        } catch (e: ClassNotFoundException) { null }
        val APPCOMPATFRAGMENTCONTEXT_KEY: CoroutineContext.Key<*>? = APPCOMPATFRAGMENTCONTEXT_CLASS?.let { cl ->
            val inst = cl.getDeclaredField("Key")
            inst.get(null) as CoroutineContext.Key<*>
        }
    }
}