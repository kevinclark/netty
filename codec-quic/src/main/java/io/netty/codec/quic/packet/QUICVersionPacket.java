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
import io.netty.codec.quic.packet.QUICVersionPacket.Payload;

import java.util.List;

public class QUICVersionPacket extends QUICLongHeaderPacket<Payload> {
    public static class Payload implements QUICLongHeaderPacket.ToByteBuf {
        final List<QUICVersion> supportedVersions;

        Payload(final List<QUICVersion> supportedVersions) {
            this.supportedVersions = supportedVersions;
        }

        @Override
        public ByteBuf toByteBuf() {
            final ByteBuf payload = Unpooled.buffer(supportedVersions.size() * 4);
            for (QUICVersion version : supportedVersions) {
                payload.writeInt(version.value);
            }
            return payload;
        }
    }

    public static QUICVersionPacket from(final ByteBuf destConnId, final ByteBuf sourceConnId,
                                         final List<QUICVersion> supportedVersions) {
        return new QUICVersionPacket(destConnId, sourceConnId, new Payload(supportedVersions));
    }

    private QUICVersionPacket(final ByteBuf destConnId, final ByteBuf sourceConnId, final Payload payload) {
        super((byte) (0x8 | 0x4) /* Draft 29 says just Header Form MUST be set, but SHOULD also set 0x4 */,
              QUICVersion.NEGOTIATING,
              destConnId, sourceConnId,
              payload);
    }
}
