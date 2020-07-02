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

import com.google.common.collect.Lists;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.codec.quic.QUICVersionPacket.Payload;
import org.junit.Test;

import java.nio.charset.Charset;
import java.util.List;

import static org.junit.Assert.*;

public class QUICVersionPacketTest {

    @Test
    public void fromSupportedVersions() {
        // NOTE: Version packets, even with QUIC v1 may have connection ids larger than 20 bytes per 17.2.1
        final ByteBuf destConnId = Unpooled.copiedBuffer("destination-connection-id".getBytes(Charset.defaultCharset()));
        final ByteBuf sourceConnId = Unpooled.copiedBuffer("source-connection-id".getBytes(Charset.defaultCharset()));
        final List<QUICVersion> supportedVersions = Lists.newArrayList(QUICVersion.DRAFT_29, QUICVersion.ONE);
        final QUICVersionPacket packet = QUICVersionPacket.from(destConnId, sourceConnId, supportedVersions);

        assertEquals(0x8, packet.header);
        assertEquals(QUICVersion.NEGOTIATING, packet.version);

        assertEquals(destConnId.readableBytes(), packet.destConnIdLength);
        assertEquals(destConnId.slice(), packet.destConnId.slice());
        assertEquals(sourceConnId.readableBytes(), packet.sourceConnIdLength);
        assertEquals(sourceConnId.slice(), packet.sourceConnId.slice());

        assertEquals(supportedVersions, packet.payload.supportedVersions);
    }

    @Test
    public void PayloadToByteBuf() {
        final List<QUICVersion> supportedVersions = Lists.newArrayList(QUICVersion.DRAFT_29, QUICVersion.ONE);
        Payload payload = new Payload(supportedVersions);

        final ByteBuf buf = payload.toByteBuf();

        assertEquals(QUICVersion.DRAFT_29.value, buf.readInt());
        assertEquals(QUICVersion.ONE.value, buf.readInt());
        assertFalse(buf.isReadable());
    }
}