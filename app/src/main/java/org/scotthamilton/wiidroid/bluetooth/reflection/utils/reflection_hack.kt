package org.scotthamilton.wiidroid.bluetooth.reflection.utils

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.net.LocalSocket
import android.os.ParcelFileDescriptor
import android.os.ParcelUuid
import android.os.RemoteException
import android.util.Log
import androidx.annotation.RequiresPermission
import java.io.FileDescriptor
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.lang.IllegalArgumentException
import java.lang.reflect.Field
import kotlin.reflect.KProperty


@RequiresPermission(value="android.permission.BLUETOOTH_PRIVILEGED")
fun bluetoothHidHostConnect(device: BluetoothDevice, host: Any): Boolean {
    try {
        Class.forName("android.bluetooth.BluetoothHidHost")
    } catch (e: Exception) {
        Log.d("BtReflectionUtils","can't get find class BluetoothHidHost, $e")
        null
    }?.let { cls ->
        try {
            cls.getMethod("connect", BluetoothDevice::class.java)
        } catch (e: Exception) {
            Log.d("BtReflectionUtils","can't get method BluetoothHidHost::connect, $e")
            null
        }?.let { m ->
            try {
                m.invoke(host, device)
                return true
            } catch (e: Exception) {
                Log.d("BtReflectionUtils","can't invoke BluetoothHidHost::connect, $e")
            }
        }
    }
    return false
}

private open class FieldDelegate<C, T>(
    private val cls: Class<C>,
    private val fieldName: String,
    private val obj: C) {
    companion object {
        private const val TAG = "FieldDelegate"
    }
    protected val field: Field = cls.getField(fieldName)
    operator fun getValue(thisRef: T?, property: KProperty<*>): T =
        try {
            field.get(obj) as? T ?: throw IllegalAccessError("field was null")
        } catch (e: Exception) {
            Log.d(TAG, "error, can't get field value of ${cls.name}::$fieldName, $e")
            throw(e)
        }
}

private class MutableFieldDelegate<C, T>(
    private val cls: Class<C>,
    private val fieldName: String,
    private val obj: C) : FieldDelegate<C, T>(cls, fieldName, obj) {
    companion object {
        private const val TAG = "FieldDelegate"
    }
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) =
        try {
            field.set(obj, value)
        } catch (e: Exception) {
            Log.d(TAG, "error, can't set field value of ${cls.name}::$fieldName to $value, $e")
        }
}

private fun <C, T> Any.invokeMethod(
    cls: Class<C>, methodName: String,
    args: List<Any?>,
    errorMsg: String?,
    vararg parameterTypes: Class<*>): T {
    val m = cls.getMethod(methodName, *parameterTypes)
    return m.invoke(this, args.toTypedArray()) as? T
        ?: throw IllegalArgumentException(
            errorMsg ?: "failed to call ${cls.name}::$methodName")
}

private fun <C, T> Any.invokeMethod(
    cls: Class<C>, methodName: String,
    args: List<Any?>,
    vararg parameterTypes: Class<*>): T =
    invokeMethod(cls, methodName, args, null, *parameterTypes)

private fun <C, T> Class<C>.invokeStaticMethod(
    methodName: String,
    args: List<Any?>,
    vararg parameterTypes: Class<*>): T {
    val m = getMethod(methodName, *parameterTypes)
    return m.invoke(null, args.toTypedArray()) as? T
        ?: throw IllegalArgumentException("failed to call ${name}::$methodName")
}

private enum class SocketState {
    INIT, CONNECTED, LISTENING, CLOSED
}

private val IBluetooth = Class.forName("android.bluetooth.IBluetooth")
private val IBluetoothSocketManager =
    Class.forName("android.bluetooth.IBluetoothSocketManager")

@SuppressLint("SoonBlockedPrivateApi")
fun BluetoothSocket.reflectionChannelConnect(channel: Int) {
    val mDevice: BluetoothDevice by FieldDelegate(
        BluetoothSocket::class.java,
        "mDevice",
        this)
    var mSocketState: SocketState by MutableFieldDelegate(
        BluetoothSocket::class.java,
        "mSocketState",
        this)
    var mPfd: ParcelFileDescriptor by MutableFieldDelegate(
        BluetoothSocket::class.java,
        "mPfd",
        this)
    val mType: Int by FieldDelegate(
        BluetoothSocket::class.java,
        "mType",
        this)
    val mUuid: ParcelUuid by FieldDelegate(
        BluetoothSocket::class.java,
        "mUuid",
        this)
    var mPort: Int by MutableFieldDelegate(
        BluetoothSocket::class.java,
        "mPort",
        this)
    var mSocket: LocalSocket by MutableFieldDelegate(
        BluetoothSocket::class.java,
        "mSocket",
        this)
    var mSocketIS: InputStream by MutableFieldDelegate(
        BluetoothSocket::class.java,
        "mSocketIS",
        this)
    var mSocketOS: OutputStream by MutableFieldDelegate(
        BluetoothSocket::class.java,
        "mSocketOS",
        this)

    try {
        if (mSocketState == SocketState.CLOSED) throw IOException("socket closed");
//      val bluetoothProxy: IBluetooth? =
//          BluetoothAdapter.getDefaultAdapter(). getBluetoothService(null);
        val bluetoothProxy: Any =
            BluetoothAdapter.getDefaultAdapter().invokeMethod(
                BluetoothAdapter::class.java,
                "getBluetoothService",
                listOf(null),
                "Bluetooth is off",
                Class.forName("android.bluetooth.IBluetoothManagerCallback")
            )
//        mPfd = bluetoothProxy.getSocketManager().connectSocket(mDevice, mType,
//            mUuid, mPort, getSecurityFlags());
        val socketManager: Any = bluetoothProxy.invokeMethod(
            IBluetooth,
            "getSocketManager",
            listOf()
        )
        mPfd = socketManager.invokeMethod(
            IBluetoothSocketManager,
            "connectSocket",
            listOf(mDevice, mType, mUuid, mPort, 0), // TODO: implement security flags
            BluetoothDevice::class.java,
        )
        synchronized (this) {
            Log.d("BluetoothSocket",
                "connect(), SocketState: $mSocketState, mPfd: $mPfd"
            );
            if (mSocketState == SocketState.CLOSED) throw IOException("socket closed");
            if (mPfd == null) throw IOException("bt socket connect failed");
            val fd = mPfd.fileDescriptor;
//            mSocket =  LocalSocket.createConnectedLocalSocket(fd);
            mSocket = LocalSocket::class.java.invokeStaticMethod(
                "createConnectedLocalSocket",
                listOf(fd),
                FileDescriptor::class.java
            ) ?: throw IllegalArgumentException(
                "failed to call LocalSocket::createConnectedLocalSocket")
            mSocketIS = mSocket.inputStream;
            mSocketOS = mSocket.outputStream;
        }
//        val channel = readInt(mSocketIS);
//        val channel: Int = this.invokeMethod(
//            BluetoothSocket::class.java,
//            "readInt",
//            listOf(mSocketIS),
//            InputStream::class.java
//        ) ?: throw IllegalArgumentException("failed to call BluetoothSocket::readInt")
//        if (channel <= 0) {
//            throw IOException("bt socket connect failed");
//        }
        mPort = channel;
//        waitSocketSignal(mSocketIS);
        this.invokeMethod<BluetoothSocket, String>(
            BluetoothSocket::class.java,
            "waitSocketSignal",
            listOf(mSocketIS),
            InputStream::class.java
        )
        synchronized (this) {
            if (mSocketState == SocketState.CLOSED) {
                throw IOException("bt socket closed");
            }
            mSocketState = SocketState.CONNECTED;
        }
    } catch (e: RemoteException) {
        Log.e("BluetoothSocket", Log.getStackTraceString(Throwable()));
        throw IOException("unable to send RPC: " + e.message);
    }
}
