/*
 *  This file is part of Waltz.
 *
 *     Waltz is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Waltz is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Waltz.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.khartec.waltz.web.endpoints.api;

import com.khartec.waltz.model.IdSelectionOptions;
import com.khartec.waltz.model.entity_statistic.EntityStatisticDefinition;
import com.khartec.waltz.model.entity_statistic.EntityStatisticValue;
import com.khartec.waltz.model.entity_statistic.RollupKind;
import com.khartec.waltz.model.immediate_hierarchy.ImmediateHierarchy;
import com.khartec.waltz.model.tally.StringTally;
import com.khartec.waltz.model.tally.TallyPack;
import com.khartec.waltz.service.entity_statistic.EntityStatisticService;
import com.khartec.waltz.web.DatumRoute;
import com.khartec.waltz.web.ListRoute;
import com.khartec.waltz.web.endpoints.Endpoint;
import com.khartec.waltz.web.json.EntityStatisticQueryOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import spark.Request;
import spark.Response;

import java.io.IOException;
import java.util.List;

import static com.khartec.waltz.common.Checks.checkNotNull;
import static com.khartec.waltz.web.WebUtilities.*;
import static com.khartec.waltz.web.endpoints.EndpointUtilities.*;


@Service
public class EntityStatisticEndpoint implements Endpoint {

    private static final String BASE_URL = mkPath("api", "entity-statistic");

    private final EntityStatisticService entityStatisticService;

    private static final Logger LOG = LoggerFactory.getLogger(EntityStatisticEndpoint.class);


    @Autowired
    public EntityStatisticEndpoint(EntityStatisticService entityStatisticService) {
        checkNotNull(entityStatisticService, "entityStatisticService cannot be null");
        this.entityStatisticService = entityStatisticService;
    }


    public EntityStatisticQueryOptions readQueryOptionsFromBody(Request request) throws java.io.IOException {
        return readBody(request, EntityStatisticQueryOptions.class);
    }


    private List<TallyPack<String>> findStatTalliesRoute(Request request, Response response) throws IOException {
        EntityStatisticQueryOptions options = readQueryOptionsFromBody(request);
        return entityStatisticService.findStatTallies(options.statisticIds(), options.selector());
    }


    private List<StringTally> calculateStatTallyRoute(Request request, Response response) throws IOException {
        IdSelectionOptions idSelectionOptions = readIdSelectionOptionsFromBody(request);
        RollupKind rollupKind = readEnum(
                request,
                "rollupKind",
                RollupKind.class,
                (s) -> {
                    String msg = String.format("rollupKind cannot be [%s]", s);
                    throw new UnsupportedOperationException(msg);
                });

        Long statisticId = getId(request);
        return entityStatisticService.calculateStatTally(statisticId, rollupKind, idSelectionOptions);
    }


    @Override
    public void register() {

        String findAllActiveDefinitionsPath = mkPath(BASE_URL, "definition");
        String findDefinitionPath = mkPath(BASE_URL, "definition", ":id");
        String findRelatedStatDefinitionsPath = mkPath(BASE_URL, "definition" , ":statId", "related");
        String findStatValuesBySelectorPath = mkPath(BASE_URL, "value", ":statId");
        String findStatTalliesPath = mkPath(BASE_URL, "tally");
        String calculateStatTallyPath = mkPath(BASE_URL, "tally", ":id", ":rollupKind");

        ListRoute<EntityStatisticDefinition> findAllActiveDefinitionsRoute = (request, response)
                -> entityStatisticService.findAllActiveDefinitions();

        DatumRoute<EntityStatisticDefinition> findDefinitionRoute = (request, response)
                -> entityStatisticService.getDefinitionById(getId(request));

        ListRoute<EntityStatisticValue> findStatValuesForAppSelectorRoute = (request, response)
                -> entityStatisticService.getStatisticValuesForAppIdSelector(getLong(request, "statId"), readIdSelectionOptionsFromBody(request));

        DatumRoute<ImmediateHierarchy<EntityStatisticDefinition>> findRelatedStatDefinitionsRoute = (request, response)
                -> entityStatisticService.findRelatedStatDefinitions(getLong(request, "statId"));

        getForList(findAllActiveDefinitionsPath, findAllActiveDefinitionsRoute);
        postForList(findStatValuesBySelectorPath, findStatValuesForAppSelectorRoute);
        postForList(findStatTalliesPath, this::findStatTalliesRoute);
        postForList(calculateStatTallyPath, this::calculateStatTallyRoute);
        getForDatum(findRelatedStatDefinitionsPath, findRelatedStatDefinitionsRoute);
        getForDatum(findDefinitionPath, findDefinitionRoute);
    }

}