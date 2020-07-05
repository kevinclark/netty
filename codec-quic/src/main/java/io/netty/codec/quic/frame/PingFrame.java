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
import io.netty.buffer.Unpooled;
import io.netty.codec.quic.util.QUICByteBufs;

// A singleton - PingFrames have no data but type.
public class PingFrame extends Frame {
    public static final long TYPE = 0x1;
    private static PingFrame INSTANCE = new PingFrame(); // No reason to have more

    public static PingFrame create() {
        return INSTANCE;
    }

    private PingFrame() {};

    @Override
    public ByteBuf toByteBuf() {
        ByteBuf buf = Unpooled.buffer(1);
        QUICByteBufs.writeVariableLengthNumber(buf, TYPE); // Type
        return buf;
    }

    static PingFrame parseFrom(ByteBuf buf) {
        long type = QUICByteBufs.readVariableLengthNumber(buf);

        return parseFromWithType(type, buf);
    }

    // This interface is a little silly in this version, but I want it
    // consistent across the frames
    static PingFrame parseFromWithType(long type, ByteBuf buf) {
        assert type == TYPE;

        return INSTANCE;
    }
}
