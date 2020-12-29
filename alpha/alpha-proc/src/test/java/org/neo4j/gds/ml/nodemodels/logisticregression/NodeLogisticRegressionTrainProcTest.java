/*
 * Copyright (c) 2017-2020 "Neo4j,"
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
package org.neo4j.gds.ml.nodemodels.logisticregression;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.gds.ml.nodemodels.logisticregression.NodeLogisticRegressionTrainProc;
import org.neo4j.graphalgo.BaseProcTest;
import org.neo4j.graphalgo.GdsCypher;
import org.neo4j.graphalgo.api.DefaultValue;

import java.util.List;

class NodeLogisticRegressionTrainProcTest extends BaseProcTest {

    @BeforeEach
    void setUp() throws Exception {
        registerProcedures(NodeLogisticRegressionTrainProc.class);
        runQuery(createQuery());
    }

    @Test
    void shouldNotCrash() {
        String query = GdsCypher.call()
            .withAnyLabel()
            .withNodeProperties(List.of("a", "b", "t"), DefaultValue.of(0D))
            .withAnyRelationshipType()
            .algo("gds.alpha.ml.nodeLogisticRegression")
            .trainMode()
            .addParameter("featureProperties", List.of("a", "b"))
            .addParameter("targetProperty", "t")
            .yields();
       runQueryWithRowConsumer(query, row -> System.out.println(row.getString("modelDescription")));
    }

    public String createQuery() {
        return "CREATE " +
               "(n1:N {a: 2.0, b: 1.2, t: 1.0})," +
               "(n2:N {a: 1.3, b: 0.5, t: 0.0})," +
               "(n3:N {a: 0.0, b: 2.8, t: 0.0})," +
               "(n4:N {a: 1.0, b: 0.9, t: 1.0})";
    }

}