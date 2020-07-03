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

package io.netty.codec.quic;

import com.google.common.base.Charsets;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import static org.junit.Assert.*;

public class QUICZeroRTTPacketTest {
    @Test
    public void payloadToByteBuf() {
        final ByteBuf payload = Unpooled.wrappedBuffer("payload".getBytes(Charsets.UTF_8));
        final ByteBuf buf = new QUICZeroRTTPacket.Payload(new QUICPacketNumber(5), payload).toByteBuf();

        assertEquals(1 + payload.readableBytes(), QUICByteBufs.readVariableLengthNumber(buf));
        assertEquals(5, buf.readByte());
        assertEquals(payload.slice(), buf.readBytes(payload.readableBytes()));
    }

}