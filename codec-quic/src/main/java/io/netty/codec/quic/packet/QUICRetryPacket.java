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
import io.netty.codec.quic.QUICVersion;
import io.netty.codec.quic.packet.QUICRetryPacket.Payload;
import io.netty.codec.quic.util.ToByteBuf;

public class QUICRetryPacket extends QUICLongHeaderPacket<Payload> {
    public static class Payload implements ToByteBuf {
        public final ByteBuf token;
        public final byte[] integrityTag;

        public Payload(final ByteBuf token, final byte[] integrityTag) {
            this.token = token.retainedDuplicate();
            this.integrityTag = integrityTag;
        }

        @Override
        public ByteBuf toByteBuf() {
            return Unpooled.wrappedBuffer(this.token,
                                          Unpooled.wrappedBuffer(this.integrityTag));
        }
    }

    public QUICRetryPacket(final QUICVersion version,
                           final ByteBuf destConnId, final ByteBuf sourceConnId,
                           final Payload payload) {
        super(PacketType.Retry, (byte)0x0 /* Unused */, version,
              destConnId, sourceConnId, payload);
    }
}
