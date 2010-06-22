package suneido.language;

import suneido.SuValue;

import com.google.common.base.Objects;

/**
 * SuMethod makes methods first class values.
 * It binds the method and the instance it "came from".
 * Also used for nested anonymous functions.
 * @author Andrew McKinlay
 * <p><small>Copyright 2008 Suneido Software Corp. All rights reserved.
 * Licensed under GPLv2.</small></p>
 */
public class SuMethod extends SuValue {
	/** not private final because instance is filled in later
	 *  @see CompileGenerator.linkConstants */
	public SuValue instance;
	public final String method;

	public SuMethod(SuValue instance, String method) {
		this.instance = instance;
		this.method = method;
	}

	@Override
	public Object call(Object... args) {
		return instance.invoke(instance, method, args);
	}

	@Override
	public Object eval(Object self, Object... args) {
		return instance.invoke(self, method, args);
	}

	@Override
	public String toString() {
		return instance + "." + method;
	}

	@Override
	public boolean equals(Object other) {
		if (other == this)
			return true;
		if (! (other instanceof SuMethod))
			return false;
		SuMethod that = (SuMethod) other;
		return Objects.equal(method, that.method) &&
				Objects.equal(instance, that.instance);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(instance, method);
	}

	@Override
	public String typeName() {
		return "Method";
	}

}
