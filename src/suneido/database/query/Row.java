package suneido.database.query;

import java.nio.ByteBuffer;

import suneido.database.Record;

public class Row {
	private final Record[] records;
	public static final Row Eof = null;

	Row(Record... records) {
		this.records = records;
	}

	public ByteBuffer getraw(Header header, String f) {
		// TODO Auto-generated method stub
		return null;
	}
}
