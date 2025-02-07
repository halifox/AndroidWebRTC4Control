package com.github.control

import android.view.MotionEvent
import com.github.control.scrcpy.Injector
import com.github.control.scrcpy.Position
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class EventSocketHandler(
    private val inputStream: DataInputStream,
    private val outputStream: DataOutputStream,
    private val injector: Injector? = null,
    private val singleWriterExecutor: ExecutorService = Executors.newSingleThreadExecutor(),
) : Closeable {
    companion object {
        private const val TYPE_MOTION_EVENT = 2
    }

    constructor(
        socket: Socket,
        injector: Injector? = null,
    ) : this(
        DataInputStream(socket.inputStream),
        DataOutputStream(socket.outputStream),
        injector,
    )


    override fun close() {
        inputStream.close()
        outputStream.close()
    }

    private fun recv() {
        val type = inputStream.readInt()
        when (type) {
            TYPE_MOTION_EVENT -> readMotionEvent()
            else              -> {}
        }
    }

    fun loopRecv() {
        try {
            while (true) {
                recv()
            }
        } catch (e: IOException) {
            // 处理一般I/O异常
        } catch (e: InterruptedIOException) {
            // 处理线程中断异常
        } catch (e: EOFException) {
            // 处理文件末尾异常
        } catch (e: Exception) {
            // 捕获其他未预见的异常
        } finally {
            try {
                inputStream.close()  // 确保关闭流
            } catch (e: IOException) {
                // 捕获关闭流时的异常
                e.printStackTrace()
            }
        }
    }


    fun readMotionEvent() {
        //val type = inputStream.readInt()//TYPE_MOTION_EVENT
        val action = inputStream.readInt()
        val pointerId = inputStream.readInt()
        val x = inputStream.readInt()
        val y = inputStream.readInt()
        val screenWidth = inputStream.readInt()
        val screenHeight = inputStream.readInt()
        val pressure = inputStream.readFloat()
        val actionButton = inputStream.readInt()
        val buttons = inputStream.readInt()
        val position = Position(x, y, screenWidth, screenHeight)
        injector?.injectTouch(action, pointerId, position, pressure, actionButton, buttons)
    }


    fun writeMotionEvent(event: MotionEvent) {
        singleWriterExecutor.execute {
            outputStream.writeInt(TYPE_MOTION_EVENT)
            outputStream.writeInt(event.action)
            outputStream.writeInt(event.getPointerId(event.actionIndex))
            outputStream.writeInt(event.getX(event.actionIndex).toInt())
            outputStream.writeInt(event.getY(event.actionIndex).toInt())
            outputStream.writeInt(Injector.displayMetrics.widthPixels)
            outputStream.writeInt(Injector.displayMetrics.heightPixels)
            outputStream.writeFloat(event.pressure)
            outputStream.writeInt(event.actionButton)
            outputStream.writeInt(event.buttonState)
            outputStream.flush()
        }
    }


}