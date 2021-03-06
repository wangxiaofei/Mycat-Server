/*
 * Copyright (c) 2013, OpenCloudDB/MyCAT and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software;Designed and Developed mainly by many Chinese 
 * opensource volunteers. you can redistribute it and/or modify it under the 
 * terms of the GNU General Public License version 2 only, as published by the
 * Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 * 
 * Any questions about this component can be directed to it's project Web address 
 * https://code.google.com/p/opencloudb/.
 *
 */
package io.mycat.server.response;

import io.mycat.MycatServer;
import io.mycat.backend.PhysicalDBPool;
import io.mycat.backend.PhysicalDatasource;
import io.mycat.backend.heartbeat.DBHeartbeat;
import io.mycat.net.BufferArray;
import io.mycat.net.NetSystem;
import io.mycat.server.Fields;
import io.mycat.server.MySQLFrontConnection;
import io.mycat.server.config.node.MycatConfig;
import io.mycat.server.packet.EOFPacket;
import io.mycat.server.packet.FieldPacket;
import io.mycat.server.packet.ResultSetHeaderPacket;
import io.mycat.server.packet.RowDataPacket;
import io.mycat.server.packet.util.PacketUtil;
import io.mycat.util.IntegerUtil;
import io.mycat.util.LongUtil;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * @author mycat
 */
public class ShowHeartbeat {

	private static final int FIELD_COUNT = 11;
	private static final ResultSetHeaderPacket header = PacketUtil
			.getHeader(FIELD_COUNT);
	private static final FieldPacket[] fields = new FieldPacket[FIELD_COUNT];
	private static final EOFPacket eof = new EOFPacket();
	static {
		int i = 0;
		byte packetId = 0;
		header.packetId = ++packetId;

		fields[i] = PacketUtil.getField("NAME", Fields.FIELD_TYPE_VAR_STRING);
		fields[i++].packetId = ++packetId;

		fields[i] = PacketUtil.getField("TYPE", Fields.FIELD_TYPE_VAR_STRING);
		fields[i++].packetId = ++packetId;

		fields[i] = PacketUtil.getField("HOST", Fields.FIELD_TYPE_VAR_STRING);
		fields[i++].packetId = ++packetId;

		fields[i] = PacketUtil.getField("PORT", Fields.FIELD_TYPE_LONG);
		fields[i++].packetId = ++packetId;

		fields[i] = PacketUtil.getField("RS_CODE", Fields.FIELD_TYPE_LONG);
		fields[i++].packetId = ++packetId;

		fields[i] = PacketUtil.getField("RETRY", Fields.FIELD_TYPE_LONG);
		fields[i++].packetId = ++packetId;

		fields[i] = PacketUtil.getField("STATUS", Fields.FIELD_TYPE_VAR_STRING);
		fields[i++].packetId = ++packetId;

		fields[i] = PacketUtil.getField("TIMEOUT", Fields.FIELD_TYPE_LONGLONG);
		fields[i++].packetId = ++packetId;

		fields[i] = PacketUtil.getField("EXECUTE_TIME",
				Fields.FIELD_TYPE_VAR_STRING);
		fields[i++].packetId = ++packetId;

		fields[i] = PacketUtil.getField("LAST_ACTIVE_TIME",
				Fields.FIELD_TYPE_DATETIME);
		fields[i++].packetId = ++packetId;

		fields[i] = PacketUtil.getField("STOP", Fields.FIELD_TYPE_VAR_STRING);
		fields[i++].packetId = ++packetId;

		eof.packetId = ++packetId;
	}

	public static void execute(MySQLFrontConnection c) {
		BufferArray bufferArray = NetSystem.getInstance().getBufferPool()
				.allocateArray();
		// write header
		header.write(bufferArray);

		// write fields
		for (FieldPacket field : fields) {
			field.write(bufferArray);
		}

		// write eof
		eof.write(bufferArray);

		// write rows
		byte packetId = eof.packetId;
		for (RowDataPacket row : getRows()) {
			row.packetId = ++packetId;
			row.write(bufferArray);
		}

		// write last eof
		EOFPacket lastEof = new EOFPacket();
		lastEof.packetId = ++packetId;
		lastEof.write(bufferArray);

		// post write
		c.write(bufferArray);
	}

	private static List<RowDataPacket> getRows() {
		List<RowDataPacket> list = new LinkedList<RowDataPacket>();
		MycatConfig conf = MycatServer.getInstance().getConfig();
		// host nodes
		Map<String, PhysicalDBPool> dataHosts = conf.getDataHosts();
		for (PhysicalDBPool pool : dataHosts.values()) {
			for (PhysicalDatasource ds : pool.getAllDataSources()) {
				DBHeartbeat hb = ds.getHeartbeat();
				RowDataPacket row = new RowDataPacket(FIELD_COUNT);
				row.add(ds.getName().getBytes());
				row.add(ds.getConfig().getDbType().getBytes());
				if (hb != null) {
					row.add(ds.getConfig().getIp().getBytes());
					row.add(IntegerUtil.toBytes(ds.getConfig().getPort()));
					row.add(IntegerUtil.toBytes(hb.getStatus()));
					row.add(IntegerUtil.toBytes(hb.getErrorCount()));
					row.add(hb.isChecking() ? "checking".getBytes() : "idle"
							.getBytes());
					row.add(LongUtil.toBytes(hb.getTimeout()));
					row.add(hb.getRecorder().get().getBytes());
					String lat = hb.getLastActiveTime();
					row.add(lat == null ? null : lat.getBytes());
					row.add(hb.isStop() ? "true".getBytes() : "false"
							.getBytes());
				} else {
					row.add(null);
					row.add(null);
					row.add(null);
					row.add(null);
					row.add(null);
					row.add(null);
					row.add(null);
					row.add(null);
					row.add(null);
				}
				list.add(row);
			}
		}
		return list;
	}

}