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

import java.util.List;

public class QUICVersionPacket extends QUICLongHeaderPacket {
    public static QUICVersionPacket from(final ByteBuf destConnId, final ByteBuf sourceConnId,
                                         final List<QUICVersion> supportedVersions) {
        final ByteBuf payload = Unpooled.buffer(supportedVersions.size() * 4);
        for (QUICVersion version : supportedVersions) {
            payload.writeInt(version.value);
        }

        return new QUICVersionPacket(destConnId, sourceConnId, payload);
    }

    private QUICVersionPacket(final ByteBuf destConnId, final ByteBuf sourceConnId,
                              final ByteBuf supportedVersions) {
        super((byte) 0x8 /* Just HeaderForm set */, QUICVersion.NEGOTIATING,
              destConnId, sourceConnId, supportedVersions);
    }
}
