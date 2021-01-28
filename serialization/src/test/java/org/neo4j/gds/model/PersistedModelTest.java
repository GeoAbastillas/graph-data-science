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
package org.neo4j.gds.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.neo4j.gds.embeddings.graphsage.Layer;
import org.neo4j.gds.embeddings.graphsage.ModelData;
import org.neo4j.gds.embeddings.graphsage.SingleLabelFeatureFunction;
import org.neo4j.gds.embeddings.graphsage.algo.GraphSage;
import org.neo4j.gds.embeddings.graphsage.algo.GraphSageTrainConfig;
import org.neo4j.gds.embeddings.graphsage.algo.ImmutableGraphSageTrainConfig;
import org.neo4j.gds.model.storage.ModelToFileExporter;
import org.neo4j.graphalgo.api.schema.GraphSchema;
import org.neo4j.graphalgo.core.model.Model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.neo4j.gds.model.storage.ModelToFileExporter.META_DATA_FILE;

class PersistedModelTest {

    private static final String MODEL = "model";
    private static final String USER = "user";

    @TempDir
    Path tempDir;

    private Model<ModelData, GraphSageTrainConfig> model;

    @BeforeEach
    void persistModel() throws IOException {
        GraphSageTrainConfig trainConfig = ImmutableGraphSageTrainConfig.builder()
            .modelName(MODEL)
            .relationshipWeightProperty("weight")
            .degreeAsProperty(true)
            .build();

        var modelData = ModelData.of(new Layer[]{}, new SingleLabelFeatureFunction());

        model = Model.of(
            USER,
            MODEL,
            GraphSage.MODEL_TYPE,
            GraphSchema.empty(),
            modelData,
            trainConfig
        );

        ModelToFileExporter.toFile(tempDir, model);
    }

    @Test
    void testLoadingMetaData() throws IOException {
        var persistedModel = new PersistedModel(tempDir);

        assertThat(persistedModel.creator()).isEqualTo(model.creator());
        assertThat(persistedModel.name()).isEqualTo(model.name());
        assertThat(persistedModel.trainConfig()).isEqualTo(model.trainConfig());
        assertThat(persistedModel.creationTime()).isEqualTo(model.creationTime());
        assertThat(persistedModel.graphSchema()).isEqualTo(model.graphSchema());
        assertThat(persistedModel.algoType()).isEqualTo(model.algoType());
        assertThat(persistedModel.persisted()).isTrue();
        assertThat(persistedModel.loaded()).isFalse();
    }

    @Test
    void testLoadingData() throws IOException {
        var persistedModel = new PersistedModel(tempDir);

        persistedModel.load();

        assertThat(persistedModel.loaded()).isTrue();
        assertThat(persistedModel.data()).isInstanceOf(ModelData.class);
        var loadedModelData = (ModelData) persistedModel.data();
        assertThat(loadedModelData.layers()).isEmpty();
        assertThat(loadedModelData.featureFunction()).isExactlyInstanceOf(SingleLabelFeatureFunction.class);
    }

    @Test
    void testUnLoadingData() throws IOException {
        var persistedModel = new PersistedModel(tempDir);

        persistedModel.load();
        persistedModel.unload();

        assertThat(persistedModel.loaded()).isFalse();
        assertThatThrownBy(persistedModel::data).hasMessage("The model 'model' is currently not loaded.");
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void testPublishPersistedModel(boolean loadData) throws IOException {
        var persistedModel = new PersistedModel(tempDir);
        if (loadData) {
            persistedModel.load();
        }
        persistedModel.publish();
        assertTrue(Files.exists(tempDir.resolve(META_DATA_FILE)));

        PersistedModel publishedModel = new PersistedModel(tempDir);
        assertEquals(model.name() + "_public", publishedModel.name());
        assertThat(publishedModel.sharedWith()).containsExactlyInAnyOrder(Model.ALL_USERS);

        if (loadData) {
            publishedModel.load();
        }
        assertThat(publishedModel)
            .usingRecursiveComparison()
            .ignoringFields("sharedWith", "name", "metaData")
            .isEqualTo(persistedModel);
    }
}