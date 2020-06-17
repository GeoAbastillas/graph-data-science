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
package org.neo4j.gds.core.pagecached;

import org.neo4j.graphalgo.Orientation;
import org.neo4j.graphalgo.core.Aggregation;
import org.neo4j.graphalgo.core.loading.RelationshipsBatchBuffer;
import org.neo4j.graphalgo.core.utils.RawValues;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;
import org.neo4j.graphalgo.utils.StringFormatting;
import org.neo4j.kernel.api.StatementConstants;

class RelationshipImporter {

    private final AllocationTracker tracker;
    private final AdjacencyBuilder adjacencyBuilder;

    RelationshipImporter(AllocationTracker tracker, AdjacencyBuilder adjacencyBuilder) {
        this.tracker = tracker;
        this.adjacencyBuilder = adjacencyBuilder;
    }

    public interface Imports {
        long importRelationships(RelationshipsBatchBuffer batches, PropertyReader propertyReader);
    }

    Imports imports(Orientation orientation, boolean loadProperties) {
        if (orientation == Orientation.UNDIRECTED) {
            return loadProperties
                ? this::importUndirectedWithProperties
                : this::importUndirected;
        } else if (orientation == Orientation.NATURAL) {
            return loadProperties
                ? this::importNaturalWithProperties
                : this::importNatural;
        } else if (orientation == Orientation.REVERSE) {
            return loadProperties
                ? this::importReverseWithProperties
                : this::importReverse;
        } else {
            throw new IllegalArgumentException(StringFormatting.formatWithLocale("Unexpected orientation: %s", orientation));
        }
    }

    private long importUndirected(RelationshipsBatchBuffer buffer, PropertyReader propertyReader) {
        long[] batch = buffer.sortBySource();
        int importedOut = importRelationships(buffer, batch, null, adjacencyBuilder, tracker);
        batch = buffer.sortByTarget();
        int importedIn = importRelationships(buffer, batch, null, adjacencyBuilder, tracker);
        return RawValues.combineIntInt(importedOut + importedIn, 0);
    }

    private long importUndirectedWithProperties(RelationshipsBatchBuffer buffer, PropertyReader reader) {
        int batchLength = buffer.length();
        long[] batch = buffer.sortBySource();
        long[][] outProperties = reader.readProperty(
            batch,
            batchLength,
            adjacencyBuilder.getPropertyKeyIds(),
            adjacencyBuilder.getDefaultValues(),
            adjacencyBuilder.getAggregations(),
            adjacencyBuilder.atLeastOnePropertyToLoad()
        );
        int importedOut = importRelationships(buffer, batch, outProperties, adjacencyBuilder, tracker);
        batch = buffer.sortByTarget();

        long[][] inProperties = reader.readProperty(
            batch,
            batchLength,
            adjacencyBuilder.getPropertyKeyIds(),
            adjacencyBuilder.getDefaultValues(),
            adjacencyBuilder.getAggregations(),
            adjacencyBuilder.atLeastOnePropertyToLoad()
        );
        int importedIn = importRelationships(buffer, batch, inProperties, adjacencyBuilder, tracker);
        return RawValues.combineIntInt(importedOut + importedIn, importedOut + importedIn);
    }

    private long importNatural(RelationshipsBatchBuffer buffer, PropertyReader propertyReader) {
        long[] batch = buffer.sortBySource();
        return RawValues.combineIntInt(importRelationships(buffer, batch, null, adjacencyBuilder, tracker), 0);
    }

    private long importNaturalWithProperties(RelationshipsBatchBuffer buffer, PropertyReader propertyReader) {
        int batchLength = buffer.length();
        long[] batch = buffer.sortBySource();
        long[][] outProperties = propertyReader.readProperty(
            batch,
            batchLength,
            adjacencyBuilder.getPropertyKeyIds(),
            adjacencyBuilder.getDefaultValues(),
            adjacencyBuilder.getAggregations(),
            adjacencyBuilder.atLeastOnePropertyToLoad()
        );
        int importedOut = importRelationships(buffer, batch, outProperties, adjacencyBuilder, tracker);
        return RawValues.combineIntInt(importedOut, importedOut);
    }

    private long importReverse(RelationshipsBatchBuffer buffer, PropertyReader propertyReader) {
        long[] batch = buffer.sortByTarget();
        return RawValues.combineIntInt(importRelationships(buffer, batch, null, adjacencyBuilder, tracker), 0);
    }

    private long importReverseWithProperties(RelationshipsBatchBuffer buffer, PropertyReader propertyReader) {
        int batchLength = buffer.length();
        long[] batch = buffer.sortByTarget();
        long[][] inProperties = propertyReader.readProperty(
            batch,
            batchLength,
            adjacencyBuilder.getPropertyKeyIds(),
            adjacencyBuilder.getDefaultValues(),
            adjacencyBuilder.getAggregations(),
            adjacencyBuilder.atLeastOnePropertyToLoad()
        );
        int importedIn = importRelationships(buffer, batch, inProperties, adjacencyBuilder, tracker);
        return RawValues.combineIntInt(importedIn, importedIn);
    }

    public interface PropertyReader {
        /**
         * Load the relationship properties for the given batch of relationships.
         * Relationships are represented in the format produced by {@link RelationshipsBatchBuffer}.
         *
         * @param batch                    relationship data
         * @param batchLength              number of valid entries in the batch data
         * @param propertyKeyIds           property key ids to load
         * @param defaultValues            default weight for each property key
         * @param aggregations             the aggregation for each property
         * @param atLeastOnePropertyToLoad true iff there is at least one value in {@code propertyKeyIds} that is not {@link StatementConstants#NO_SUCH_PROPERTY_KEY} (-1).
         * @return list of property values per per relationship property id
         */
        long[][] readProperty(
            long[] batch,
            int batchLength,
            int[] propertyKeyIds,
            double[] defaultValues,
            Aggregation[] aggregations,
            boolean atLeastOnePropertyToLoad
        );
    }

    private static int importRelationships(
        RelationshipsBatchBuffer buffer,
        long[] batch,
        long[][] properties,
        AdjacencyBuilder adjacency,
        AllocationTracker tracker
    ) {
        int batchLength = buffer.length();

        int[] offsets = buffer.spareInts();
        long[] targets = buffer.spareLongs();

        long source, target, prevSource = batch[0];
        int offset = 0, nodesLength = 0;

        for (int i = 0; i < batchLength; i += 4) {
            source = batch[i];
            target = batch[1 + i];
            // If rels come in chunks for same source node
            // then we can skip adding an extra offset
            if (source > prevSource) {
                offsets[nodesLength++] = offset;
                prevSource = source;
            }
            targets[offset++] = target;
        }
        offsets[nodesLength++] = offset;

        adjacency.addAll(
            batch,
            targets,
            properties,
            offsets,
            nodesLength,
            tracker
        );

        return batchLength >> 2; // divide by 4
    }
}