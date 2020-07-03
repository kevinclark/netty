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
import io.netty.codec.quic.QUICVersion;
import io.netty.codec.quic.packet.QUICInitialPacket.Payload;

public class QUICInitialPacket extends QUICLongHeaderPacket<Payload> {
    public static class Payload extends QUICNumberedPacketPayload {
        public final ByteBuf token;

        public Payload(final ByteBuf token, final QUICPacketNumber number, final ByteBuf packetPayload) {
            super(number, packetPayload);
            this.token = token;
        }

        @Override
        public ByteBuf toByteBuf() {
            return Unpooled.wrappedBuffer(Unpooled.copyInt(this.token.readableBytes()),
                                          this.token,
                                          super.toByteBuf());
        }
    }

    public QUICInitialPacket(final QUICVersion version,
                             final ByteBuf destConnId, final ByteBuf sourceConnId,
                             final Payload payload) {
        super(PacketType.Initial,
              /* Reserved Bits (2), Packet Number Length (2)*/
              payload.number.encodedLength, version,
              destConnId, sourceConnId, payload);
    }
}
