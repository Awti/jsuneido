/* Copyright 2013 (c) Suneido Software Corp. All rights reserved.
 * Licensed under GPLv2.
 */

package suneido.jsdi.type;

import javax.annotation.concurrent.Immutable;

import suneido.jsdi.*;
import suneido.jsdi.marshall.MarshallPlanBuilder;
import suneido.jsdi.marshall.Marshaller;
import suneido.jsdi.marshall.NumberConversions;
import suneido.jsdi.marshall.VariableIndirectInstruction;

/**
 * <p>
 * Special type representing a multi-use string pointer, a given value of which
 * is treated by the Windows API in the following way:
 * <ul>
 * <li>
 * when the high-order bits are zero, the value is treated as a 16-bit integer
 * whose value is given by the low-order 16 bits of the pointer;</li>
 * <li>
 * when the high-order bits are non-zero, the value is treated as pointer to a
 * string.</li>
 * </ul>
 * </p>
 * 
 * <p>
 * This type therefore has some characteristics of TypeString
 * </p>
 * 
 * @author Victor Schappert
 * @since 20130731
 */
@DllInterface
@Immutable
public final class ResourceType extends StringIndirect {

	//
	// CONSTRUCTORS
	//

	private ResourceType() {
	}

	//
	// SINGLETON INSTANCE
	//

	/**
	 * Singleton instance of ResourceType.
	 * 
	 * @see #IDENTIFIER
	 */
	public static final ResourceType INSTANCE = new ResourceType();

	//
	// PUBLIC CONSTANTS
	//

	/**
	 * String identifier for ResourceType.
	 * 
	 * @see #INSTANCE
	 */
	public static final String IDENTIFIER = "resource";

	//
	// STATICS
	//

	/**
	 * Method for determining whether a value is a Win32 {@code INTRESOURCE}
	 * value, similar to the {@code IS_INTRESOURCE} macro.
	 * 
	 * @param value
	 *            Value to test
	 * @return If the value is not an {@code INTRESOURCE} value, the return
	 *         value is {@code null}; otherwise, the return value is an instance
	 *         of Short containing the signed 16-bit integer that is bitwise
	 *         equivalent to the unsigned 16-bit {@code INTRESOURCE} value
	 */
	@_64BitIssue
	public static Short AS_INTRESOURCE(Object value) {
		try
		{
			int intValue = NumberConversions.toPointer32(value);
			return 0 == (0xffff0000 & intValue)
				? new Short((short)intValue)
				: null;
		}
		catch (RuntimeException e)
		{ return null; }
	}

	//
	// ANCESTOR CLASS: Type
	//

	@Override
	public String getDisplayName() {
		return IDENTIFIER;
	}


	@Override
	public void addToPlan(MarshallPlanBuilder builder, boolean isCallbackPlan) {
		if (isCallbackPlan) {
			// Unlike with InString ('[in] string') and BufferType ('buffer'),
			// it would make sense for a dll to send a resource to a callback.
			// The reason for throwing here at the moment is just laziness,
			// because it would require sending a variable indirect instruction
			// array to the native-side callback thunk, which isn't implemented
			// as of 20130808. If needed, it is trivial to implement.
			throwNotValidForCallback();
		}
		super.addToPlan(builder, isCallbackPlan);
	}

	@Override
	public void marshallIn(Marshaller marshaller, Object value) {
		if (isNullPointerEquivalent(value)) {
			marshaller.putINTRESOURCE((short)0);
		} else {
			Short intResource = AS_INTRESOURCE(value);
			if (null != intResource)
				marshaller.putINTRESOURCE(intResource.shortValue());
			else
				marshaller.putStringPtr(
					value.toString(),
					VariableIndirectInstruction.RETURN_RESOURCE
				);
		}
	}

	@Override
	public Object marshallOut(Marshaller marshaller, Object oldValue) {
		return marshaller.getResource();
	}

	@Override
	public void putMarshallOutInstruction(Marshaller marshaller) {
		marshaller
				.putViInstructionOnly(VariableIndirectInstruction.RETURN_RESOURCE);
	}
}
