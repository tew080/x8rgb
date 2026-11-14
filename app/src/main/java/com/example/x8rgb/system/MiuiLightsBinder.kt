package com.x8rgb.system

import android.os.IBinder
import android.os.Parcel
import android.util.Log
import com.x8rgb.util.DiagLog

private const val TAG          = "MiuiLights"
private const val DESCRIPTOR   = "miui.lights.ILightsManager"
private const val TX_SET_COLOR = 2   // setColorCommon; decompiled from ZenithOrbitRGB z0()

/**
 * Direct binder access to HyperOS ring lights.
 * No root/Shizuku: service is looked up via ServiceManager reflection from a normal app.
 *
 * Parcel layout mirrors the observed call: interface token, color (0xRRGGBB),
 * caller package, type=3, flags=0.
 */
object MiuiLightsBinder {

    /** Original app hardcodes "com.android.camera"; test own package first. */
    var callerPackage: String = "com.x8rgb"

    private fun service(): IBinder? = try {
        val m = Class.forName("android.os.ServiceManager")
            .getDeclaredMethod("getService", String::class.java)
        m.isAccessible = true
        (m.invoke(null, DESCRIPTOR) as? IBinder)?.takeIf { it.isBinderAlive }
    } catch (t: Throwable) {
        Log.w(TAG, "ServiceManager lookup failed", t)
        DiagLog.log("binder", "lookup failed: ${t.javaClass.simpleName}")
        null
    }

    /** rgb = 0xRRGGBB. Returns true if the binder accepted the call. */
    fun setColor(rgb: Int): Boolean {
        val b = service() ?: run {
            DiagLog.log("binder", "service unavailable")
            return false
        }
        val data  = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(DESCRIPTOR)
            data.writeInt(rgb and 0xFFFFFF)
            data.writeString(callerPackage)
            data.writeInt(3)
            data.writeInt(0)
            val ok = b.transact(TX_SET_COLOR, data, reply, 0)
            if (!ok) DiagLog.log("binder", "transact returned false")
            if (ok) reply.readException()
            ok
        } catch (t: Throwable) {
            Log.e(TAG, "setColor failed", t)
            DiagLog.log("binder", "exception ${t.javaClass.simpleName}: ${t.message}")
            false
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    fun off(): Boolean = setColor(0)
}
