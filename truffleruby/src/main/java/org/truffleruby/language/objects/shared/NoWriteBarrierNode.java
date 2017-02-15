/*
 * Copyright (c) 2017 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.language.objects.shared;

import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeInfo;

@NodeInfo(cost=NodeCost.NONE)
public class NoWriteBarrierNode extends WriteBarrier {

    @Override
    public void executeWriteBarrier(Object value) {
        // no-op
    }

}
