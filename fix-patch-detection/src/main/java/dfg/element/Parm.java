/* Qilin - a Java Pointer Analysis Framework
 * Copyright (C) 2021-2030 Qilin developers
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3.0 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <https://www.gnu.org/licenses/lgpl-3.0.en.html>.
 */

package dfg.element;

import soot.RefType;
import soot.SootMethod;
import soot.Type;
import utils.Constant;

import java.util.Objects;

/**
 * Represents a method parameter.
 *
 * @author Ondrej Lhotak
 */
public class Parm {
    private final int index;
    private final SootMethod method;

    public Parm(SootMethod m, int i) {
        index = i;
        method = m;
    }

    public String toString() {
        if (index == Constant.THIS_NODE) {
            return "Parm THIS_NODE to " + method;
        } else if (index == Constant.RETURN_NODE) {
            return "Parm RETURN to " + method;
        } else {
            return "Parm " + index + " to " + method;
        }
    }

    public int getIndex() {
        return index;
    }

    public boolean isThis() {
        return index == Constant.THIS_NODE;
    }

    public boolean isReturn() {
        return index == Constant.RETURN_NODE;
    }

    public boolean isThrowRet() {
        return index == Constant.THROW_NODE;
    }

    public Type getType() {
        if (index == Constant.RETURN_NODE) {
            return method.getReturnType();
        } else if (index == Constant.THIS_NODE) {
            return method.isStatic() ? RefType.v("java.lang.Object") : method.getDeclaringClass().getType();
        }

        return method.getParameterType(index);
    }

    public SootMethod method() {
        return method;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Parm parm = (Parm) o;
        return index == parm.index &&
                method.equals(parm.method);
    }

    @Override
    public int hashCode() {
        return Objects.hash(index, method);
    }
}
