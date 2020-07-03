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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class QUICInitialPacket extends QUICLongHeaderPacket<QUICInitialPacket.Payload> {
    public static class Payload implements QUICLongHeaderPacket.ToByteBuf {
        public final ByteBuf token;
        public final QUICPacketNumber number;
        public final ByteBuf packetPayload;

        public Payload(final ByteBuf token, final QUICPacketNumber number, final ByteBuf packetPayload) {
            this.token = token;
            this.number = number;
            this.packetPayload = packetPayload;
        }

        @Override
        public ByteBuf toByteBuf() {
            long length = this.number.bytesNeeded() + this.packetPayload.readableBytes();

            return Unpooled.wrappedBuffer(Unpooled.copyInt(this.token.readableBytes()),
                                          this.token,
                                          /* Length of remaining data (packet number plus payload) */
                                          QUICByteBufs.encodeVariableLengthNumber(length),
                                          this.number.toByteBuf(),
                                          this.packetPayload);
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
