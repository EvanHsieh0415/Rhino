package dev.latvian.mods.unit.operator;

import dev.latvian.mods.unit.EmptyVariableSet;
import dev.latvian.mods.unit.FixedNumberUnit;
import dev.latvian.mods.unit.Unit;
import dev.latvian.mods.unit.UnitVariables;

public class BitNotOpUnit extends UnaryOpUnit {
	@Override
	public double get(UnitVariables variables) {
		return getInt(variables);
	}

	@Override
	public int getInt(UnitVariables variables) {
		return ~unit.getInt(variables);
	}

	@Override
	public boolean getBoolean(UnitVariables variables) {
		return !unit.getBoolean(variables);
	}

	@Override
	public Unit optimize() {
		unit = unit.optimize();

		if (unit instanceof FixedNumberUnit) {
			return Unit.of(getInt(EmptyVariableSet.INSTANCE));
		}

		return this;
	}
}
