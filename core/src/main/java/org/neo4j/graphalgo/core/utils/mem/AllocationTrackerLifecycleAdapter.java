/*
 * Copyright (c) 2017-2021 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.graphalgo.core.utils.mem;

import org.neo4j.graphalgo.compat.Neo4jProxy;
import org.neo4j.kernel.api.KernelTransaction;
import org.neo4j.kernel.api.procedure.Context;
import org.neo4j.kernel.api.procedure.GlobalProcedures;
import org.neo4j.kernel.impl.coreapi.InternalTransaction;
import org.neo4j.kernel.lifecycle.LifecycleAdapter;

import java.util.Optional;

public class AllocationTrackerLifecycleAdapter extends LifecycleAdapter {

    private final GlobalProcedures globalProcedures;

    AllocationTrackerLifecycleAdapter(GlobalProcedures globalProcedures) {
        this.globalProcedures = globalProcedures;
    }

    @Override
    public void init() {
        globalProcedures.registerComponent(AllocationTracker.class, AllocationTrackerLifecycleAdapter::allocationTracker, true);
    }

    static AllocationTracker allocationTracker(Context ctx) {
        Optional<KernelTransaction> kernelTransaction = Optional.ofNullable(ctx.internalTransactionOrNull())
            .map(InternalTransaction::kernelTransaction);
        return kernelTransaction
            .map(tx -> AllocationTracker.create(Neo4jProxy.memoryTrackerProxy(Neo4jProxy.memoryTracker(tx))))
            .orElse(AllocationTracker.create());
    }

}
