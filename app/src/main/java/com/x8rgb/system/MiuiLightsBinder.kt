package com.x8rgb.system

import android.os.IBinder
import android.os.Parcel
import android.util.Log
import com.x8rgb.util.DiagLog

private const val TAG          = "MiuiLights"
private const val DESCRIPTOR   = "miui.lights.ILightsManager"
private const val TX_SET_COLOR = 2

object MiuiLightsBinder {
    var callerPackage: String = "com.x8rgb"

    private fun service(): IBinder? = try {
        val m = Class.forName("android.os.ServiceManager")
            .getDeclaredMethod("getService", String::class.java)
        m.isAccessible = true
        (m.invoke(null, DESCRIPTOR) as? IBinder)?.takeIf { it.isBinderAlive }
    } catch (t: Throwable) {
        null
    }

    fun setColor(rgb: Int): Boolean = setColorRaw(rgb, 3, 0)

    /**
     * Same transaction as setColor but with the two unknown trailing ints exposed,
     * so DebugTestRunner "probe" can find out whether they select a ring / segment.
     */
    fun setColorRaw(rgb: Int, arg3: Int, arg4: Int, code: Int = TX_SET_COLOR): Boolean {
        val b = service() ?: return false
        val data  = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(DESCRIPTOR)
            data.writeInt(rgb and 0xFFFFFF)
            data.writeString(callerPackage)
            data.writeInt(arg3)
            data.writeInt(arg4)
            val ok = b.transact(code, data, reply, 0)
            if (ok) reply.readException()
            ok
        } catch (t: Throwable) {
            DiagLog.log("binder", "raw arg3=$arg3 arg4=$arg4 -> ${t.javaClass.simpleName}: ${t.message}")
            false
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    /** Debug helper: send an arbitrary payload (Int / String args) and describe what the service said. */
    fun rawTransact(code: Int, args: List<Any>): String {
        val b = service() ?: return "no service"
        val data  = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(DESCRIPTOR)
            for (a in args) when (a) {
                is Int    -> data.writeInt(a)
                is String -> data.writeString(a)
            }
            if (!b.transact(code, data, reply, 0)) return "transact=false (no such method)"
            reply.readException()
            "ok reply=${reply.dataAvail()}b"
        } catch (t: Throwable) {
            "${t.javaClass.simpleName}: ${t.message}"
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    fun off(): Boolean = setColor(0)
}