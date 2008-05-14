package suneido;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class SuDate extends SuValue {
	private Date date;
	final public static SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd.HHmmssSSS");
	
	public SuDate() {
		date = new Date();
	}
	public SuDate(String s) {
		if (s.startsWith("#"))
			s = s.substring(1);
		try {
			date = (Date)formatter.parse(s);
		} catch (ParseException e) {
			throw new SuException("can't convert to date");
		}
	}

	@Override
	public String toString() {
		return "#" + formatter.format(date);
	}
	
	@Override
	public int hashCode() {
		return date.hashCode();
	}
	@Override
	public boolean equals(Object value) {
		return value instanceof SuDate
			? date.equals(((SuDate) value).date)
			: false;
	}
	@Override
	public int compareTo(SuValue value) {
		if (value == this)
			return 0;
		int ord = order() - value.order();
		if (ord != 0)
			return ord < 0 ? -1 : +1;
		return -value.compareToDate(this);
	}
	public int compareToDate(SuDate d) {
		return date.compareTo(d.date);
	}
	
	@Override
	public int order() {
		return Order.DATE.ordinal();
	}
}
