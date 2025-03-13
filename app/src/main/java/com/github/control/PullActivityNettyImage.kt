package com.github.control

import android.accessibilityservice.AccessibilityService
import android.graphics.BitmapFactory
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Bundle
import android.view.MotionEvent
import android.view.SurfaceHolder
import androidx.appcompat.app.AppCompatActivity
import com.github.control.databinding.ActivityPullTcpBinding
import io.netty.bootstrap.Bootstrap
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.LengthFieldBasedFrameDecoder
import io.netty.handler.codec.LengthFieldPrepender
import io.netty.handler.codec.bytes.ByteArrayDecoder
import io.netty.handler.codec.bytes.ByteArrayEncoder
import kotlinx.coroutines.channels.Channel


class PullActivityNettyImage : AppCompatActivity() {
    private val context = this
    private lateinit var binding: ActivityPullTcpBinding

    private val eventLoopGroup = NioEventLoopGroup()
    private val eventLoopGroup2 = NioEventLoopGroup()
    private val inetHost by lazy { intent.getStringExtra("host") }


    private lateinit var decoder: MediaCodec

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPullTcpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.SurfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                val mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 1280, 800)
                decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
                    .apply {
                        configure(mediaFormat, holder.surface, null, 0)
                        start()
                    }
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
            }
        })

        initService()
    }

    private val eventChannel = Channel<MotionEvent>(Channel.BUFFERED)
    private val actionChannel = Channel<Int>(Channel.BUFFERED)

    override fun onStart() {
        super.onStart()
        binding.SurfaceView.setOnTouchListener { _, event ->
            eventChannel.trySend(event)
            true
        }

        binding.back.setOnClickListener {
            actionChannel.trySend(AccessibilityService.GLOBAL_ACTION_BACK)
        }
        binding.home.setOnClickListener {
            actionChannel.trySend(AccessibilityService.GLOBAL_ACTION_HOME)
        }
        binding.recents.setOnClickListener {
            actionChannel.trySend(AccessibilityService.GLOBAL_ACTION_RECENTS)
        }
    }


    private fun initService() {
        Bootstrap()
            .group(eventLoopGroup)
            .channel(NioSocketChannel::class.java)
            .handler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(channel: SocketChannel) {
                    channel.pipeline()
                        .addLast(LengthFieldBasedFrameDecoder(Int.MAX_VALUE, 0, 4, 0, 4))
                        .addLast(LengthFieldPrepender(4))
                        .addLast(ByteArrayDecoder())
                        .addLast(ByteArrayEncoder())
                        .addLast(object : SimpleChannelInboundHandler<ByteArray>() {

                            override fun channelActive(ctx: ChannelHandlerContext) {
                            }

                            //信道不活跃消息
                            override fun channelInactive(ctx: ChannelHandlerContext?) {
                            }



                            //信道读消息
                            override fun channelRead0(ctx: ChannelHandlerContext, data: ByteArray) {
                                val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
                                val canvas = binding.SurfaceView.holder.lockCanvas()
                                canvas.drawBitmap(bitmap, 0f, 0f, null)
                                binding.SurfaceView.holder.unlockCanvasAndPost(canvas)
                            }
                        })
                }
            })
            .connect(inetHost, 40003)
            .apply {
                addListener { future ->
                    if (future.isSuccess) {
                        println("Server started on port 8888");
                    } else {
                        println("Failed to start server");
                        future.cause()
                            .printStackTrace();
                    }
                }
            }
            .channel()//
            .closeFuture()
            .apply {
                addListener {
                    eventLoopGroup.shutdownGracefully()
                }
            }
    }



    override fun onDestroy() {
        super.onDestroy()
        eventLoopGroup.shutdownGracefully()
        eventLoopGroup2.shutdownGracefully()
    }
}