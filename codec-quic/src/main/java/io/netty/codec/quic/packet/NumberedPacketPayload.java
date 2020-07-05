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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.codec.quic.util.QUICByteBufs;
import io.netty.codec.quic.util.ToByteBuf;

public class NumberedPacketPayload implements ToByteBuf {
    public final PacketNumber number;
    public final ByteBuf payload;

    public NumberedPacketPayload(final PacketNumber number, final ByteBuf payload) {
        this.number = number;
        this.payload = payload.retainedDuplicate();
    }

    @Override
    public ByteBuf toByteBuf() {
        long length = this.number.bytesNeeded() + this.payload.readableBytes();
        return Unpooled.wrappedBuffer(QUICByteBufs.encodeVariableLengthNumber(length),
                                      this.number.toByteBuf(),
                                      this.payload);
    }
}
