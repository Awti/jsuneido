package suneido.language.builtin;

import static suneido.database.server.Command.theDbms;
import suneido.SuContainer;
import suneido.language.*;

public class Libraries extends SuFunction {

	@Override
	public Object call(Object... args) {
		Args.massage(FunctionSpec.noParams, args);
		return new SuContainer(theDbms.libraries());
	}

}
