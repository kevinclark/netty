/*
 * Copyright 2016 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.http2;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpScheme;
import io.netty.handler.codec.http2.Http2Exception.StreamException;
import io.netty.handler.codec.http2.LastInboundHandler.Consumer;
import io.netty.util.AsciiString;
import io.netty.util.AttributeKey;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.netty.util.ReferenceCountUtil.release;
import static io.netty.handler.codec.http2.Http2TestUtil.anyChannelPromise;
import static io.netty.handler.codec.http2.Http2TestUtil.anyHttp2Settings;
import static io.netty.handler.codec.http2.Http2TestUtil.assertEqualsAndRelease;
import static io.netty.handler.codec.http2.Http2TestUtil.bb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link Http2MultiplexCodec}.
 */
public class Http2MultiplexCodecTest {
    private final Http2Headers request = new DefaultHttp2Headers()
            .method(HttpMethod.GET.asciiName()).scheme(HttpScheme.HTTPS.name())
            .authority(new AsciiString("example.org")).path(new AsciiString("/foo"));

    private EmbeddedChannel parentChannel;
    private Http2FrameWriter frameWriter;
    private Http2FrameInboundWriter frameInboundWriter;
    private TestChannelInitializer childChannelInitializer;
    private Http2MultiplexCodec codec;

    private static final int initialRemoteStreamWindow = 1024;

    @Before
    public void setUp() {
        childChannelInitializer = new TestChannelInitializer();
        parentChannel = new EmbeddedChannel();
        frameInboundWriter = new Http2FrameInboundWriter(parentChannel);
        parentChannel.connect(new InetSocketAddress(0));
        frameWriter = Http2TestUtil.mockedFrameWriter();
        codec = new Http2MultiplexCodecBuilder(true, childChannelInitializer).frameWriter(frameWriter).build();
        parentChannel.pipeline().addLast(codec);
        parentChannel.runPendingTasks();
        parentChannel.pipeline().fireChannelActive();

        parentChannel.writeInbound(Http2CodecUtil.connectionPrefaceBuf());

        Http2Settings settings = new Http2Settings().initialWindowSize(initialRemoteStreamWindow);
        frameInboundWriter.writeInboundSettings(settings);

        verify(frameWriter).writeSettingsAck(eqMultiplexCodecCtx(), anyChannelPromise());

        frameInboundWriter.writeInboundSettingsAck();

        Http2SettingsFrame settingsFrame = parentChannel.readInbound();
        assertNotNull(settingsFrame);

        // Handshake
        verify(frameWriter).writeSettings(eqMultiplexCodecCtx(),
                anyHttp2Settings(), anyChannelPromise());
    }

    private ChannelHandlerContext eqMultiplexCodecCtx() {
        return eq(codec.ctx);
    }

    @After
    public void tearDown() throws Exception {
        if (childChannelInitializer.handler instanceof LastInboundHandler) {
            ((LastInboundHandler) childChannelInitializer.handler).finishAndReleaseAll();
        }
        parentChannel.finishAndReleaseAll();
        codec = null;
    }

    // TODO(buchgr): Flush from child channel
    // TODO(buchgr): ChildChannel.childReadComplete()
    // TODO(buchgr): GOAWAY Logic
    // TODO(buchgr): Test ChannelConfig.setMaxMessagesPerRead

    @Test
    public void writeUnknownFrame() {
        Http2StreamChannel childChannel = newOutboundStream(new ChannelHandler() {
            @Override
            public void channelActive(ChannelHandlerContext ctx) {
                ctx.writeAndFlush(new DefaultHttp2HeadersFrame(new DefaultHttp2Headers()));
                ctx.writeAndFlush(new DefaultHttp2UnknownFrame((byte) 99, new Http2Flags()));
                ctx.fireChannelActive();
            }
        });
        assertTrue(childChannel.isActive());

        parentChannel.runPendingTasks();

        verify(frameWriter).writeFrame(eq(codec.ctx), eq((byte) 99), eqStreamId(childChannel), any(Http2Flags.class),
                any(ByteBuf.class), any(ChannelPromise.class));
    }

    private Http2StreamChannel newInboundStream(int streamId, boolean endStream, final ChannelHandler childHandler) {
        return newInboundStream(streamId, endStream, null, childHandler);
    }

    private Http2StreamChannel newInboundStream(int streamId, boolean endStream,
                                                AtomicInteger maxReads, final ChannelHandler childHandler) {
        final AtomicReference<Http2StreamChannel> streamChannelRef = new AtomicReference<>();
        childChannelInitializer.maxReads = maxReads;
        childChannelInitializer.handler = new ChannelHandler() {
            @Override
            public void channelRegistered(ChannelHandlerContext ctx) {
                assertNull(streamChannelRef.get());
                streamChannelRef.set((Http2StreamChannel) ctx.channel());
                ctx.pipeline().addLast(childHandler);
                ctx.fireChannelRegistered();
            }
        };

        frameInboundWriter.writeInboundHeaders(streamId, request, 0, endStream);
        parentChannel.runPendingTasks();
        Http2StreamChannel channel = streamChannelRef.get();
        assertEquals(streamId, channel.stream().id());
        return channel;
    }

    @Test
    public void readUnkownFrame() {
        LastInboundHandler handler = new LastInboundHandler();

        Http2StreamChannel channel = newInboundStream(3, true, handler);
        frameInboundWriter.writeInboundFrame((byte) 99, channel.stream().id(), new Http2Flags(), Unpooled.EMPTY_BUFFER);

        // header frame and unknown frame
        verifyFramesMultiplexedToCorrectChannel(channel, handler, 2);

        Channel childChannel = newOutboundStream(new ChannelHandler() { });
        assertTrue(childChannel.isActive());
    }

    @Test
    public void headerAndDataFramesShouldBeDelivered() {
        LastInboundHandler inboundHandler = new LastInboundHandler();

        Http2StreamChannel channel = newInboundStream(3, false, inboundHandler);
        Http2HeadersFrame headersFrame = new DefaultHttp2HeadersFrame(request).stream(channel.stream());
        Http2DataFrame dataFrame1 = new DefaultHttp2DataFrame(bb("hello")).stream(channel.stream());
        Http2DataFrame dataFrame2 = new DefaultHttp2DataFrame(bb("world")).stream(channel.stream());

        assertTrue(inboundHandler.isChannelActive());
        frameInboundWriter.writeInboundData(channel.stream().id(), bb("hello"), 0, false);
        frameInboundWriter.writeInboundData(channel.stream().id(), bb("world"), 0, false);

        assertEquals(headersFrame, inboundHandler.readInbound());

        assertEqualsAndRelease(dataFrame1, inboundHandler.readInbound());
        assertEqualsAndRelease(dataFrame2, inboundHandler.readInbound());

        assertNull(inboundHandler.readInbound());
    }

    @Test
    public void framesShouldBeMultiplexed() {
        LastInboundHandler handler1 = new LastInboundHandler();
        Http2StreamChannel channel1 = newInboundStream(3, false, handler1);
        LastInboundHandler handler2 = new LastInboundHandler();
        Http2StreamChannel channel2 = newInboundStream(5, false, handler2);
        LastInboundHandler handler3 = new LastInboundHandler();
        Http2StreamChannel channel3 = newInboundStream(11, false, handler3);

        verifyFramesMultiplexedToCorrectChannel(channel1, handler1, 1);
        verifyFramesMultiplexedToCorrectChannel(channel2, handler2, 1);
        verifyFramesMultiplexedToCorrectChannel(channel3, handler3, 1);

        frameInboundWriter.writeInboundData(channel2.stream().id(), bb("hello"), 0, false);
        frameInboundWriter.writeInboundData(channel1.stream().id(), bb("foo"), 0, true);
        frameInboundWriter.writeInboundData(channel2.stream().id(), bb("world"), 0, true);
        frameInboundWriter.writeInboundData(channel3.stream().id(), bb("bar"), 0, true);

        verifyFramesMultiplexedToCorrectChannel(channel1, handler1, 1);
        verifyFramesMultiplexedToCorrectChannel(channel2, handler2, 2);
        verifyFramesMultiplexedToCorrectChannel(channel3, handler3, 1);
    }

    @Test
    public void inboundDataFrameShouldUpdateLocalFlowController() throws Http2Exception {
        Http2LocalFlowController flowController = Mockito.mock(Http2LocalFlowController.class);
        codec.connection().local().flowController(flowController);

        LastInboundHandler handler = new LastInboundHandler();
        final Http2StreamChannel channel = newInboundStream(3, false, handler);

        ByteBuf tenBytes = bb("0123456789");

        frameInboundWriter.writeInboundData(channel.stream().id(), tenBytes, 0, true);

        // Verify we marked the bytes as consumed
        verify(flowController).consumeBytes(argThat(http2Stream -> http2Stream.id() == channel.stream().id()), eq(10));

        // headers and data frame
        verifyFramesMultiplexedToCorrectChannel(channel, handler, 2);
    }

    @Test
    public void unhandledHttp2FramesShouldBePropagated() {
        Http2PingFrame pingFrame = new DefaultHttp2PingFrame(0);
        frameInboundWriter.writeInboundPing(false, 0);
        assertEquals(parentChannel.readInbound(), pingFrame);

        DefaultHttp2GoAwayFrame goAwayFrame = new DefaultHttp2GoAwayFrame(1,
                parentChannel.alloc().buffer().writeLong(8));
        frameInboundWriter.writeInboundGoAway(0, goAwayFrame.errorCode(), goAwayFrame.content().retainedDuplicate());

        Http2GoAwayFrame frame = parentChannel.readInbound();
        assertEqualsAndRelease(frame, goAwayFrame);
    }

    @Test
    public void channelReadShouldRespectAutoRead() {
        LastInboundHandler inboundHandler = new LastInboundHandler();
        Http2StreamChannel childChannel = newInboundStream(3, false, inboundHandler);
        assertTrue(childChannel.config().isAutoRead());
        Http2HeadersFrame headersFrame = inboundHandler.readInbound();
        assertNotNull(headersFrame);

        childChannel.config().setAutoRead(false);

        frameInboundWriter.writeInboundData(childChannel.stream().id(), bb("hello world"), 0, false);
        Http2DataFrame dataFrame0 = inboundHandler.readInbound();
        assertNotNull(dataFrame0);
        release(dataFrame0);

        frameInboundWriter.writeInboundData(childChannel.stream().id(), bb("foo"), 0, false);
        frameInboundWriter.writeInboundData(childChannel.stream().id(), bb("bar"), 0, false);

        assertNull(inboundHandler.readInbound());

        childChannel.config().setAutoRead(true);
        verifyFramesMultiplexedToCorrectChannel(childChannel, inboundHandler, 2);
    }

    @Test
    public void readInChannelReadWithoutAutoRead() {
        useReadWithoutAutoRead(false);
    }

    @Test
    public void readInChannelReadCompleteWithoutAutoRead() {
        useReadWithoutAutoRead(true);
    }

    private void useReadWithoutAutoRead(final boolean readComplete) {
        LastInboundHandler inboundHandler = new LastInboundHandler();
        Http2StreamChannel childChannel = newInboundStream(3, false, inboundHandler);
        assertTrue(childChannel.config().isAutoRead());
        childChannel.config().setAutoRead(false);
        assertFalse(childChannel.config().isAutoRead());

        Http2HeadersFrame headersFrame = inboundHandler.readInbound();
        assertNotNull(headersFrame);

        // Add a handler which will request reads.
        childChannel.pipeline().addFirst(new ChannelHandler() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                ctx.fireChannelRead(msg);
                if (!readComplete) {
                    ctx.read();
                }
            }

            @Override
            public void channelReadComplete(ChannelHandlerContext ctx) {
                ctx.fireChannelReadComplete();
                if (readComplete) {
                    ctx.read();
                }
            }
        });

        frameInboundWriter.writeInboundData(childChannel.stream().id(), bb("hello world"), 0, false);
        frameInboundWriter.writeInboundData(childChannel.stream().id(), bb("foo"), 0, false);
        frameInboundWriter.writeInboundData(childChannel.stream().id(), bb("bar"), 0, false);
        frameInboundWriter.writeInboundData(childChannel.stream().id(), bb("hello world"), 0, false);
        frameInboundWriter.writeInboundData(childChannel.stream().id(), bb("foo"), 0, false);
        frameInboundWriter.writeInboundData(childChannel.stream().id(), bb("bar"), 0, true);

        verifyFramesMultiplexedToCorrectChannel(childChannel, inboundHandler, 6);
    }

    private Http2StreamChannel newOutboundStream(ChannelHandler handler) {
        return new Http2StreamChannelBootstrap(parentChannel).handler(handler)
                .open().syncUninterruptibly().getNow();
    }

    /**
     * A child channel for a HTTP/2 stream in IDLE state (that is no headers sent or received),
     * should not emit a RST_STREAM frame on close, as this is a connection error of type protocol error.
     */
    @Test
    public void idleOutboundStreamShouldNotWriteResetFrameOnClose() {
        LastInboundHandler handler = new LastInboundHandler();

        Channel childChannel = newOutboundStream(handler);
        assertTrue(childChannel.isActive());

        childChannel.close();
        parentChannel.runPendingTasks();

        assertFalse(childChannel.isOpen());
        assertFalse(childChannel.isActive());
        assertNull(parentChannel.readOutbound());
    }

    @Test
    public void outboundStreamShouldWriteResetFrameOnClose_headersSent() {
        ChannelHandler handler = new ChannelHandler() {
            @Override
            public void channelActive(ChannelHandlerContext ctx) {
                ctx.writeAndFlush(new DefaultHttp2HeadersFrame(new DefaultHttp2Headers()));
                ctx.fireChannelActive();
            }
        };

        Http2StreamChannel childChannel = newOutboundStream(handler);
        assertTrue(childChannel.isActive());

        childChannel.close();
        verify(frameWriter).writeRstStream(eqMultiplexCodecCtx(),
                eqStreamId(childChannel), eq(Http2Error.CANCEL.code()), anyChannelPromise());
    }

    @Test
    public void outboundStreamShouldNotWriteResetFrameOnClose_IfStreamDidntExist() {
        when(frameWriter.writeHeaders(eqMultiplexCodecCtx(), anyInt(),
                any(Http2Headers.class), anyInt(), anyShort(), anyBoolean(), anyInt(), anyBoolean(),
                any(ChannelPromise.class))).thenAnswer(new Answer<ChannelFuture>() {

            private boolean headersWritten;
            @Override
            public ChannelFuture answer(InvocationOnMock invocationOnMock) {
                // We want to fail to write the first headers frame. This is what happens if the connection
                // refuses to allocate a new stream due to having received a GOAWAY.
                if (!headersWritten) {
                    headersWritten = true;
                    return ((ChannelPromise) invocationOnMock.getArgument(8)).setFailure(new Exception("boom"));
                }
                return ((ChannelPromise) invocationOnMock.getArgument(8)).setSuccess();
            }
        });

        Http2StreamChannel childChannel = newOutboundStream(new ChannelHandler() {
            @Override
            public void channelActive(ChannelHandlerContext ctx) {
                ctx.writeAndFlush(new DefaultHttp2HeadersFrame(new DefaultHttp2Headers()));
                ctx.fireChannelActive();
            }
        });

        assertFalse(childChannel.isActive());

        childChannel.close();
        parentChannel.runPendingTasks();
        // The channel was never active so we should not generate a RST frame.
        verify(frameWriter, never()).writeRstStream(eqMultiplexCodecCtx(), eqStreamId(childChannel), anyLong(),
                anyChannelPromise());

        assertTrue(parentChannel.outboundMessages().isEmpty());
    }

    @Test
    public void inboundRstStreamFireChannelInactive() {
        LastInboundHandler inboundHandler = new LastInboundHandler();
        Http2StreamChannel channel = newInboundStream(3, false, inboundHandler);
        assertTrue(inboundHandler.isChannelActive());
        frameInboundWriter.writeInboundRstStream(channel.stream().id(), Http2Error.INTERNAL_ERROR.code());

        assertFalse(inboundHandler.isChannelActive());

        // A RST_STREAM frame should NOT be emitted, as we received a RST_STREAM.
        verify(frameWriter, Mockito.never()).writeRstStream(eqMultiplexCodecCtx(), eqStreamId(channel),
                anyLong(), anyChannelPromise());
    }

    @Test(expected = StreamException.class)
    public void streamExceptionTriggersChildChannelExceptionAndClose() throws Exception {
        LastInboundHandler inboundHandler = new LastInboundHandler();
        Http2StreamChannel channel = newInboundStream(3, false, inboundHandler);
        assertTrue(channel.isActive());
        StreamException cause = new StreamException(channel.stream().id(), Http2Error.PROTOCOL_ERROR, "baaam!");
        parentChannel.pipeline().fireExceptionCaught(cause);

        assertFalse(channel.isActive());
        inboundHandler.checkException();
    }

    @Test(expected = ClosedChannelException.class)
    public void streamClosedErrorTranslatedToClosedChannelExceptionOnWrites() throws Throwable {
        LastInboundHandler inboundHandler = new LastInboundHandler();

        final Http2StreamChannel childChannel = newOutboundStream(inboundHandler);
        assertTrue(childChannel.isActive());

        Http2Headers headers = new DefaultHttp2Headers();
        when(frameWriter.writeHeaders(eqMultiplexCodecCtx(), anyInt(),
                eq(headers), anyInt(), anyShort(), anyBoolean(), anyInt(), anyBoolean(),
                any(ChannelPromise.class))).thenAnswer((Answer<ChannelFuture>) invocationOnMock ->
                ((ChannelPromise) invocationOnMock.getArgument(8)).setFailure(
                        new StreamException(childChannel.stream().id(), Http2Error.STREAM_CLOSED, "Stream Closed")));
        ChannelFuture future = childChannel.writeAndFlush(new DefaultHttp2HeadersFrame(new DefaultHttp2Headers()));

        parentChannel.flush();

        assertFalse(childChannel.isActive());
        assertFalse(childChannel.isOpen());

        inboundHandler.checkException();

        try {
            future.syncUninterruptibly();
        } catch (CompletionException e) {
            throw e.getCause();
        }
    }

    @Test
    public void creatingWritingReadingAndClosingOutboundStreamShouldWork() {
        LastInboundHandler inboundHandler = new LastInboundHandler();
        Http2StreamChannel childChannel = newOutboundStream(inboundHandler);
        assertTrue(childChannel.isActive());
        assertTrue(inboundHandler.isChannelActive());

        // Write to the child channel
        Http2Headers headers = new DefaultHttp2Headers().scheme("https").method("GET").path("/foo.txt");
        childChannel.writeAndFlush(new DefaultHttp2HeadersFrame(headers));

        // Read from the child channel
        frameInboundWriter.writeInboundHeaders(childChannel.stream().id(), headers, 0, false);

        Http2HeadersFrame headersFrame = inboundHandler.readInbound();
        assertNotNull(headersFrame);
        assertEquals(headers, headersFrame.headers());

        // Close the child channel.
        childChannel.close();

        parentChannel.runPendingTasks();
        // An active outbound stream should emit a RST_STREAM frame.
        verify(frameWriter).writeRstStream(eqMultiplexCodecCtx(), eqStreamId(childChannel),
                anyLong(), anyChannelPromise());

        assertFalse(childChannel.isOpen());
        assertFalse(childChannel.isActive());
        assertFalse(inboundHandler.isChannelActive());
    }

    // Test failing the promise of the first headers frame of an outbound stream. In practice this error case would most
    // likely happen due to the max concurrent streams limit being hit or the channel running out of stream identifiers.
    //
    @Test(expected = Http2NoMoreStreamIdsException.class)
    public void failedOutboundStreamCreationThrowsAndClosesChannel() throws Throwable {
        LastInboundHandler handler = new LastInboundHandler();
        Http2StreamChannel childChannel = newOutboundStream(handler);
        assertTrue(childChannel.isActive());

        Http2Headers headers = new DefaultHttp2Headers();
        when(frameWriter.writeHeaders(eqMultiplexCodecCtx(), anyInt(),
               eq(headers), anyInt(), anyShort(), anyBoolean(), anyInt(), anyBoolean(),
               any(ChannelPromise.class))).thenAnswer((Answer<ChannelFuture>) invocationOnMock ->
                ((ChannelPromise) invocationOnMock.getArgument(8)).setFailure(
                       new Http2NoMoreStreamIdsException()));

        ChannelFuture future = childChannel.writeAndFlush(new DefaultHttp2HeadersFrame(headers));
        parentChannel.flush();

        assertFalse(childChannel.isActive());
        assertFalse(childChannel.isOpen());

        handler.checkException();

        try {
            future.syncUninterruptibly();
        } catch (CompletionException e) {
            throw e.getCause();
        }
    }

    @Test
    public void channelClosedWhenCloseListenerCompletes() {
        LastInboundHandler inboundHandler = new LastInboundHandler();
        Http2StreamChannel childChannel = newInboundStream(3, false, inboundHandler);

        assertTrue(childChannel.isOpen());
        assertTrue(childChannel.isActive());

        final AtomicBoolean channelOpen = new AtomicBoolean(true);
        final AtomicBoolean channelActive = new AtomicBoolean(true);

        // Create a promise before actually doing the close, because otherwise we would be adding a listener to a future
        // that is already completed because we are using EmbeddedChannel which executes code in the JUnit thread.
        ChannelPromise p = childChannel.newPromise();
        p.addListener((ChannelFutureListener) future -> {
            channelOpen.set(future.channel().isOpen());
            channelActive.set(future.channel().isActive());
        });
        childChannel.close(p).syncUninterruptibly();

        assertFalse(channelOpen.get());
        assertFalse(channelActive.get());
        assertFalse(childChannel.isActive());
    }

    @Test
    public void channelClosedWhenChannelClosePromiseCompletes() {
         LastInboundHandler inboundHandler = new LastInboundHandler();
         Http2StreamChannel childChannel = newInboundStream(3, false, inboundHandler);

         assertTrue(childChannel.isOpen());
         assertTrue(childChannel.isActive());

         final AtomicBoolean channelOpen = new AtomicBoolean(true);
         final AtomicBoolean channelActive = new AtomicBoolean(true);

         childChannel.closeFuture().addListener((ChannelFutureListener) future -> {
             channelOpen.set(future.channel().isOpen());
             channelActive.set(future.channel().isActive());
         });
         childChannel.close().syncUninterruptibly();

         assertFalse(channelOpen.get());
         assertFalse(channelActive.get());
         assertFalse(childChannel.isActive());
    }

    @Test
    public void channelClosedWhenWriteFutureFails() {
        final Queue<ChannelPromise> writePromises = new ArrayDeque<>();

        LastInboundHandler inboundHandler = new LastInboundHandler();
        Http2StreamChannel childChannel = newInboundStream(3, false, inboundHandler);

        assertTrue(childChannel.isOpen());
        assertTrue(childChannel.isActive());

        final AtomicBoolean channelOpen = new AtomicBoolean(true);
        final AtomicBoolean channelActive = new AtomicBoolean(true);

        Http2Headers headers = new DefaultHttp2Headers();
        when(frameWriter.writeHeaders(eqMultiplexCodecCtx(), anyInt(),
                eq(headers), anyInt(), anyShort(), anyBoolean(), anyInt(), anyBoolean(),
                any(ChannelPromise.class))).thenAnswer((Answer<ChannelFuture>) invocationOnMock -> {
                    ChannelPromise promise = invocationOnMock.getArgument(8);
                    writePromises.offer(promise);
                    return promise;
                });

        ChannelFuture f = childChannel.writeAndFlush(new DefaultHttp2HeadersFrame(headers));
        assertFalse(f.isDone());
        f.addListener((ChannelFutureListener) future -> {
            channelOpen.set(future.channel().isOpen());
            channelActive.set(future.channel().isActive());
        });

        ChannelPromise first = writePromises.poll();
        first.setFailure(new ClosedChannelException());
        f.awaitUninterruptibly();

        assertFalse(channelOpen.get());
        assertFalse(channelActive.get());
        assertFalse(childChannel.isActive());
    }

    @Test
    public void channelClosedTwiceMarksPromiseAsSuccessful() {
        LastInboundHandler inboundHandler = new LastInboundHandler();
        Http2StreamChannel childChannel = newInboundStream(3, false, inboundHandler);

        assertTrue(childChannel.isOpen());
        assertTrue(childChannel.isActive());
        childChannel.close().syncUninterruptibly();
        childChannel.close().syncUninterruptibly();

        assertFalse(childChannel.isOpen());
        assertFalse(childChannel.isActive());
    }

    @Test
    public void settingChannelOptsAndAttrs() {
        AttributeKey<String> key = AttributeKey.newInstance("foo");

        Channel childChannel = newOutboundStream(new ChannelHandler() { });
        childChannel.config().setAutoRead(false).setWriteSpinCount(1000);
        childChannel.attr(key).set("bar");
        assertFalse(childChannel.config().isAutoRead());
        assertEquals(1000, childChannel.config().getWriteSpinCount());
        assertEquals("bar", childChannel.attr(key).get());
    }

    @Test
    public void outboundFlowControlWritability() {
        Http2StreamChannel childChannel = newOutboundStream(new ChannelHandler() { });
        assertTrue(childChannel.isActive());

        assertTrue(childChannel.isWritable());
        childChannel.writeAndFlush(new DefaultHttp2HeadersFrame(new DefaultHttp2Headers()));
        parentChannel.flush();

        // Test for initial window size
        assertEquals(initialRemoteStreamWindow, childChannel.config().getWriteBufferHighWaterMark());

        assertTrue(childChannel.isWritable());
        childChannel.write(new DefaultHttp2DataFrame(Unpooled.buffer().writeZero(16 * 1024 * 1024)));
        assertFalse(childChannel.isWritable());
    }

    @Test
    public void writabilityAndFlowControl() {
        LastInboundHandler inboundHandler = new LastInboundHandler();
        Http2StreamChannel childChannel = newInboundStream(3, false, inboundHandler);
        assertEquals("", inboundHandler.writabilityStates());

        assertTrue(childChannel.isWritable());
        // HEADERS frames are not flow controlled, so they should not affect the flow control window.
        childChannel.writeAndFlush(new DefaultHttp2HeadersFrame(new DefaultHttp2Headers()));
        codec.onHttp2StreamWritabilityChanged(codec.ctx, childChannel.stream(), true);

        assertTrue(childChannel.isWritable());
        assertEquals("", inboundHandler.writabilityStates());

        codec.onHttp2StreamWritabilityChanged(codec.ctx, childChannel.stream(), true);
        assertTrue(childChannel.isWritable());
        assertEquals("", inboundHandler.writabilityStates());

        codec.onHttp2StreamWritabilityChanged(codec.ctx, childChannel.stream(), false);
        assertFalse(childChannel.isWritable());
        assertEquals("false", inboundHandler.writabilityStates());

        codec.onHttp2StreamWritabilityChanged(codec.ctx, childChannel.stream(), false);
        assertFalse(childChannel.isWritable());
        assertEquals("false", inboundHandler.writabilityStates());
    }

    @Test
    public void channelClosedWhenInactiveFired() {
        LastInboundHandler inboundHandler = new LastInboundHandler();
        Http2StreamChannel childChannel = newInboundStream(3, false, inboundHandler);

        final AtomicBoolean channelOpen = new AtomicBoolean(false);
        final AtomicBoolean channelActive = new AtomicBoolean(false);
        assertTrue(childChannel.isOpen());
        assertTrue(childChannel.isActive());

        childChannel.pipeline().addLast(new ChannelHandler() {
            @Override
            public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                channelOpen.set(ctx.channel().isOpen());
                channelActive.set(ctx.channel().isActive());

                ctx.fireChannelInactive();
            }
        });

        childChannel.close().syncUninterruptibly();
        assertFalse(channelOpen.get());
        assertFalse(channelActive.get());
    }

    @Test
    public void channelInactiveHappensAfterExceptionCaughtEvents() throws Exception {
        final AtomicInteger count = new AtomicInteger(0);
        final AtomicInteger exceptionCaught = new AtomicInteger(-1);
        final AtomicInteger channelInactive = new AtomicInteger(-1);
        final AtomicInteger channelUnregistered = new AtomicInteger(-1);
        Http2StreamChannel childChannel = newOutboundStream(new ChannelHandler() {

            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                ctx.close();
                throw new Exception("exception");
            }
        });

        childChannel.pipeline().addLast(new ChannelHandler() {

            @Override
            public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                channelInactive.set(count.getAndIncrement());
                ctx.fireChannelInactive();
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                exceptionCaught.set(count.getAndIncrement());
                ctx.fireExceptionCaught(cause);
            }

            @Override
            public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
                channelUnregistered.set(count.getAndIncrement());
                ctx.fireChannelUnregistered();
            }
        });

        childChannel.pipeline().fireUserEventTriggered(new Object());
        parentChannel.runPendingTasks();

        // The events should have happened in this order because the inactive and deregistration events
        // get deferred as they do in the AbstractChannel.
        assertEquals(0, exceptionCaught.get());
        assertEquals(1, channelInactive.get());
        assertEquals(2, channelUnregistered.get());
    }

    @Test
    public void callUnsafeCloseMultipleTimes() {
        LastInboundHandler inboundHandler = new LastInboundHandler();
        Http2StreamChannel childChannel = newInboundStream(3, false, inboundHandler);
        childChannel.unsafe().close(childChannel.voidPromise());

        ChannelPromise promise = childChannel.newPromise();
        childChannel.unsafe().close(promise);
        promise.syncUninterruptibly();
        childChannel.closeFuture().syncUninterruptibly();
    }

    @Test
    public void endOfStreamDoesNotDiscardData() {
        AtomicInteger numReads = new AtomicInteger(1);
        final AtomicBoolean shouldDisableAutoRead = new AtomicBoolean();
        Consumer<ChannelHandlerContext> ctxConsumer = obj -> {
            if (shouldDisableAutoRead.get()) {
                obj.channel().config().setAutoRead(false);
            }
        };
        LastInboundHandler inboundHandler = new LastInboundHandler(ctxConsumer);
        Http2StreamChannel childChannel = newInboundStream(3, false, numReads, inboundHandler);
        childChannel.config().setAutoRead(false);

        Http2DataFrame dataFrame1 = new DefaultHttp2DataFrame(bb("1")).stream(childChannel.stream());
        Http2DataFrame dataFrame2 = new DefaultHttp2DataFrame(bb("2")).stream(childChannel.stream());
        Http2DataFrame dataFrame3 = new DefaultHttp2DataFrame(bb("3")).stream(childChannel.stream());
        Http2DataFrame dataFrame4 = new DefaultHttp2DataFrame(bb("4")).stream(childChannel.stream());

        assertEquals(new DefaultHttp2HeadersFrame(request).stream(childChannel.stream()), inboundHandler.readInbound());

        ChannelHandler readCompleteSupressHandler = new ChannelHandler() {
            @Override
            public void channelReadComplete(ChannelHandlerContext ctx) {
                // We want to simulate the parent channel calling channelRead and delay calling channelReadComplete.
            }
        };

        parentChannel.pipeline().addFirst(readCompleteSupressHandler);
        frameInboundWriter.writeInboundData(childChannel.stream().id(), bb("1"), 0, false);

        assertEqualsAndRelease(dataFrame1, inboundHandler.<Http2DataFrame>readInbound());

        // Deliver frames, and then a stream closed while read is inactive.
        frameInboundWriter.writeInboundData(childChannel.stream().id(), bb("2"), 0, false);
        frameInboundWriter.writeInboundData(childChannel.stream().id(), bb("3"), 0, false);
        frameInboundWriter.writeInboundData(childChannel.stream().id(), bb("4"), 0, false);

        shouldDisableAutoRead.set(true);
        childChannel.config().setAutoRead(true);
        numReads.set(1);

        frameInboundWriter.writeInboundRstStream(childChannel.stream().id(), Http2Error.NO_ERROR.code());

        // Detecting EOS should flush all pending data regardless of read calls.
        assertEqualsAndRelease(dataFrame2, inboundHandler.<Http2DataFrame>readInbound());
        assertEqualsAndRelease(dataFrame3, inboundHandler.<Http2DataFrame>readInbound());
        assertEqualsAndRelease(dataFrame4, inboundHandler.<Http2DataFrame>readInbound());

        Http2ResetFrame resetFrame = inboundHandler.readInbound();
        assertEquals(childChannel.stream(), resetFrame.stream());
        assertEquals(Http2Error.NO_ERROR.code(), resetFrame.errorCode());

        assertNull(inboundHandler.readInbound());

        // Now we want to call channelReadComplete and simulate the end of the read loop.
        parentChannel.pipeline().remove(readCompleteSupressHandler);
        parentChannel.flushInbound();

        childChannel.closeFuture().syncUninterruptibly();
    }

    @Test
    public void childQueueIsDrainedAndNewDataIsDispatchedInParentReadLoopAutoRead() {
        AtomicInteger numReads = new AtomicInteger(1);
        final AtomicInteger channelReadCompleteCount = new AtomicInteger(0);
        final AtomicBoolean shouldDisableAutoRead = new AtomicBoolean();
        Consumer<ChannelHandlerContext> ctxConsumer = obj -> {
            channelReadCompleteCount.incrementAndGet();
            if (shouldDisableAutoRead.get()) {
                obj.channel().config().setAutoRead(false);
            }
        };
        LastInboundHandler inboundHandler = new LastInboundHandler(ctxConsumer);
        Http2StreamChannel childChannel = newInboundStream(3, false, numReads, inboundHandler);
        childChannel.config().setAutoRead(false);

        Http2DataFrame dataFrame1 = new DefaultHttp2DataFrame(bb("1")).stream(childChannel.stream());
        Http2DataFrame dataFrame2 = new DefaultHttp2DataFrame(bb("2")).stream(childChannel.stream());
        Http2DataFrame dataFrame3 = new DefaultHttp2DataFrame(bb("3")).stream(childChannel.stream());
        Http2DataFrame dataFrame4 = new DefaultHttp2DataFrame(bb("4")).stream(childChannel.stream());

        assertEquals(new DefaultHttp2HeadersFrame(request).stream(childChannel.stream()), inboundHandler.readInbound());

        ChannelHandler readCompleteSupressHandler = new ChannelHandler() {
            @Override
            public void channelReadComplete(ChannelHandlerContext ctx) {
                // We want to simulate the parent channel calling channelRead and delay calling channelReadComplete.
            }
        };
        parentChannel.pipeline().addFirst(readCompleteSupressHandler);

        frameInboundWriter.writeInboundData(childChannel.stream().id(), bb("1"), 0, false);

        assertEqualsAndRelease(dataFrame1, inboundHandler.<Http2DataFrame>readInbound());

        // We want one item to be in the queue, and allow the numReads to be larger than 1. This will ensure that
        // when beginRead() is called the child channel is added to the readPending queue of the parent channel.
        frameInboundWriter.writeInboundData(childChannel.stream().id(), bb("2"), 0, false);

        numReads.set(10);
        shouldDisableAutoRead.set(true);
        childChannel.config().setAutoRead(true);

        frameInboundWriter.writeInboundData(childChannel.stream().id(), bb("3"), 0, false);
        frameInboundWriter.writeInboundData(childChannel.stream().id(), bb("4"), 0, false);

        // Detecting EOS should flush all pending data regardless of read calls.
        assertEqualsAndRelease(dataFrame2, inboundHandler.<Http2DataFrame>readInbound());
        assertEqualsAndRelease(dataFrame3, inboundHandler.<Http2DataFrame>readInbound());
        assertEqualsAndRelease(dataFrame4, inboundHandler.<Http2DataFrame>readInbound());

        assertNull(inboundHandler.readInbound());

        // Now we want to call channelReadComplete and simulate the end of the read loop.
        parentChannel.pipeline().remove(readCompleteSupressHandler);
        parentChannel.flushInbound();

        // 3 = 1 for initialization + 1 for read when auto read was off + 1 for when auto read was back on
        assertEquals(3, channelReadCompleteCount.get());
    }

    @Test
    public void childQueueIsDrainedAndNewDataIsDispatchedInParentReadLoopNoAutoRead() {
        final AtomicInteger numReads = new AtomicInteger(1);
        final AtomicInteger channelReadCompleteCount = new AtomicInteger(0);
        final AtomicBoolean shouldDisableAutoRead = new AtomicBoolean();
        Consumer<ChannelHandlerContext> ctxConsumer = obj -> {
            channelReadCompleteCount.incrementAndGet();
            if (shouldDisableAutoRead.get()) {
                obj.channel().config().setAutoRead(false);
            }
        };
        final LastInboundHandler inboundHandler = new LastInboundHandler(ctxConsumer);
        Http2StreamChannel childChannel = newInboundStream(3, false, numReads, inboundHandler);
        childChannel.config().setAutoRead(false);

        Http2DataFrame dataFrame1 = new DefaultHttp2DataFrame(bb("1")).stream(childChannel.stream());
        Http2DataFrame dataFrame2 = new DefaultHttp2DataFrame(bb("2")).stream(childChannel.stream());
        Http2DataFrame dataFrame3 = new DefaultHttp2DataFrame(bb("3")).stream(childChannel.stream());
        Http2DataFrame dataFrame4 = new DefaultHttp2DataFrame(bb("4")).stream(childChannel.stream());

        assertEquals(new DefaultHttp2HeadersFrame(request).stream(childChannel.stream()), inboundHandler.readInbound());

        ChannelHandler readCompleteSupressHandler = new ChannelHandler() {
            @Override
            public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
                // We want to simulate the parent channel calling channelRead and delay calling channelReadComplete.
            }
        };
        parentChannel.pipeline().addFirst(readCompleteSupressHandler);

        frameInboundWriter.writeInboundData(childChannel.stream().id(), bb("1"), 0, false);

        assertEqualsAndRelease(dataFrame1, inboundHandler.readInbound());

        // We want one item to be in the queue, and allow the numReads to be larger than 1. This will ensure that
        // when beginRead() is called the child channel is added to the readPending queue of the parent channel.
        frameInboundWriter.writeInboundData(childChannel.stream().id(), bb("2"), 0, false);

        numReads.set(2);
        childChannel.read();

        assertEqualsAndRelease(dataFrame2, inboundHandler.readInbound());

        assertNull(inboundHandler.readInbound());

        // This is the second item that was read, this should be the last until we call read() again. This should also
        // notify of readComplete().
        frameInboundWriter.writeInboundData(childChannel.stream().id(), bb("3"), 0, false);

        assertEqualsAndRelease(dataFrame3, inboundHandler.readInbound());

        frameInboundWriter.writeInboundData(childChannel.stream().id(), bb("4"), 0, false);
        assertNull(inboundHandler.readInbound());

        childChannel.read();

        assertEqualsAndRelease(dataFrame4, inboundHandler.readInbound());

        assertNull(inboundHandler.readInbound());

        // Now we want to call channelReadComplete and simulate the end of the read loop.
        parentChannel.pipeline().remove(readCompleteSupressHandler);
        parentChannel.flushInbound();

        // 3 = 1 for initialization + 1 for first read of 2 items + 1 for second read of 2 items +
        // 1 for parent channel readComplete
        assertEquals(4, channelReadCompleteCount.get());
    }

    private static void verifyFramesMultiplexedToCorrectChannel(Http2StreamChannel streamChannel,
                                                                LastInboundHandler inboundHandler,
                                                                int numFrames) {
        for (int i = 0; i < numFrames; i++) {
            Http2StreamFrame frame = inboundHandler.readInbound();
            assertNotNull(frame);
            assertEquals(streamChannel.stream(), frame.stream());
            release(frame);
        }
        assertNull(inboundHandler.readInbound());
    }

    private static int eqStreamId(Http2StreamChannel channel) {
        return eq(channel.stream().id());
    }
}
