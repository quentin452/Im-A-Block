///////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2001, Eric D. Friedman All Rights Reserved.
//
// This library is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either
// version 2.1 of the License, or (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
///////////////////////////////////////////////////////////////////////////////

package de.labystudio.game.libraries.trove.procedure.array;

import de.labystudio.game.libraries.trove.procedure.TObjectProcedure;

/**
 * A procedure which stores each value it receives into a target array.
 * <p/>
 * Created: Sat Jan 12 10:13:42 2002
 *
 * @author Eric D. Friedman
 * @version $Id: ToObjectArrayProceedure.java,v 1.1.2.1 2009/09/02 21:52:33 upholderoftruth Exp $
 */

public final class ToObjectArrayProceedure<T> implements TObjectProcedure<T> {

    private final T[] target;
    private int pos = 0;

    public ToObjectArrayProceedure(final T[] target) {
        this.target = target;
    }

    @Override
    public final boolean execute(T value) {
        target[pos++] = value;
        return true;
    }
} // ToObjectArrayProcedure
