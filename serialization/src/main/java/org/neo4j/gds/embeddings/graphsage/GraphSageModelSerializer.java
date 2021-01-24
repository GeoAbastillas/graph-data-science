/*
 * Copyright (c) "Neo4j"
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
package org.neo4j.gds.embeddings.graphsage;

import org.neo4j.gds.embeddings.graphsage.algo.GraphSageTrainConfig;
import org.neo4j.graphalgo.config.GraphSageTrainConfigSerializer;
import org.neo4j.graphalgo.core.model.ImmutableModel;
import org.neo4j.graphalgo.core.model.Model;
import org.neo4j.graphalgo.core.model.ModelSerializer;
import org.neo4j.graphalgo.core.model.proto.GraphSageProto;

import java.io.IOException;

public final class GraphSageModelSerializer {

    private GraphSageModelSerializer() {}

    public static GraphSageProto.GraphSageModel toSerializable(Model<ModelData, GraphSageTrainConfig> model) throws
        IOException {
        var modelDataBuilder = GraphSageProto.ModelData.newBuilder();
        for (int i = 0; i < model.data().layers().length; i++) {
            GraphSageProto.Layer layer = LayerSerializer.toSerializable(model.data().layers()[i]);
            modelDataBuilder.addLayers(i, layer);
        }

        var metadataBuilder = GraphSageProto.GraphSageModelMetadata.newBuilder()
            .setModel(ModelSerializer.toSerializable(model))
            .setTrainConfig(GraphSageTrainConfigSerializer.toSerializable(model.trainConfig()));
        return GraphSageProto.GraphSageModel.newBuilder()
            .setData(modelDataBuilder)
            .setModel(metadataBuilder)
            .setFeatureFunction(FeatureFunctionSerializer.toSerializable(model.data().featureFunction()))
            .build();
    }

    static Model<ModelData, GraphSageTrainConfig> fromSerializable(GraphSageProto.GraphSageModel protoModel) throws
        IOException,
        ClassNotFoundException {
        var protoModelMeta = protoModel.getModel();
        GraphSageTrainConfig graphSageTrainConfig = GraphSageTrainConfigSerializer.fromSerializable(protoModel.getModel().getTrainConfig());
        ImmutableModel.Builder<ModelData, GraphSageTrainConfig> modelBuilder = ModelSerializer.fromSerializable(protoModelMeta.getModel());
        return modelBuilder.data(
            ModelData.of(
                LayerSerializer.fromSerializable(protoModel.getData().getLayersList()),
                FeatureFunctionSerializer.fromSerializable(
                    protoModel.getFeatureFunction(),
                    graphSageTrainConfig
                )
            )
        )
            .customInfo(Model.Mappable.EMPTY)
            .trainConfig(graphSageTrainConfig)
            .build();
    }
}