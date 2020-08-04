/*
 * Copyright 2020 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.codec.quic.packet;

import com.google.common.base.Charsets;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.codec.quic.packet.InitialPacket.Payload;
import io.netty.codec.quic.util.QUICByteBufs;
import org.junit.Test;

import static org.junit.Assert.*;

public class InitialPacketTest {
    @Test
    public void payloadToByteBuf() {
        final ByteBuf tok = Unpooled.wrappedBuffer("token".getBytes(Charsets.UTF_8));
        final ByteBuf payloadBuf = Unpooled.wrappedBuffer("It's a payload!".getBytes(Charsets.UTF_8));

        final ByteBuf buf = new Payload(tok, new PacketNumber(1 << 25), payloadBuf).toByteBuf();
        assertEquals(5, buf.readInt()); // Tok length
        assertEquals(tok.slice(), buf.readBytes(5)); // Then tok
        // Then remaining length, which is the packet number length plus the length of payloadBuf
        assertEquals(4 + payloadBuf.readableBytes(), (long)QUICByteBufs.readVariableLengthNumber(buf).get());
        assertEquals(1 << 25, buf.readInt());
        assertEquals(payloadBuf, buf.slice());
    }
}