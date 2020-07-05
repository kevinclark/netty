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

package io.netty.codec.quic.frame;

import io.netty.buffer.ByteBuf;
import org.junit.Test;

import static org.junit.Assert.*;

public class PingFrameTest {
    @Test
    public void toByteBuffer() {
        final ByteBuf buf = PingFrame.create().toByteBuf();
        assertEquals(0x1, buf.readByte()); // Just the type (variable encoded is still just 1 byte)
    }

    @Test
    public void parseFrom() {
        final PingFrame frame = PingFrame.create();
        final ByteBuf buf = frame.toByteBuf();

        assertEquals(frame, PingFrame.parseFrom(buf));
        assertFalse(buf.isReadable());
    }
}