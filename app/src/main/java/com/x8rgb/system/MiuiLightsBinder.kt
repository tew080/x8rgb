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

    fun setColor(rgb: Int): Boolean {
        val b = service() ?: return false
        val data  = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(DESCRIPTOR)
            data.writeInt(rgb and 0xFFFFFF)
            data.writeString(callerPackage)
            data.writeInt(3)
            data.writeInt(0)
            val ok = b.transact(TX_SET_COLOR, data, reply, 0)
            if (ok) reply.readException()
            ok
        } catch (t: Throwable) {
            false
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    fun off(): Boolean = setColor(0)
}
