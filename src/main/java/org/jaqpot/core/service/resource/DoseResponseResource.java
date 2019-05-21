/*
 *
 * JAQPOT Quattro
 *
 * JAQPOT Quattro and the components shipped with it (web applications and beans)
 * are licensed by GPL v3 as specified hereafter. Additional components may ship
 * with some other licence as will be specified therein.
 *
 * Copyright (C) 2014-2015 KinkyDesign (Charalampos Chomenidis, Pantelis Sopasakis)
 *
 * This program is free software: you can redistribute it and/or modify
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
 * 
 * Source code:
 * The source code of JAQPOT Quattro is available on github at:
 * https://github.com/KinkyDesign/JaqpotQuattro
 * All source files of JAQPOT Quattro that are stored on github are licensed
 * with the aforementioned licence. 
 */
package org.jaqpot.core.service.resource;

//import io.swagger.annotations.Api;
//import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import org.jaqpot.core.annotations.Jackson;
import org.jaqpot.core.data.ReportHandler;
import org.jaqpot.core.data.UserHandler;
import org.jaqpot.core.data.serialize.JSONSerializer;
import org.jaqpot.core.model.Report;
import org.jaqpot.core.model.User;
import org.jaqpot.core.model.builder.MetaInfoBuilder;
import org.jaqpot.core.model.dto.dataset.Dataset;
import org.jaqpot.core.model.dto.jpdi.TrainingRequest;
import org.jaqpot.core.model.facades.UserFacade;
import org.jaqpot.core.model.util.ROG;
import org.jaqpot.core.service.annotations.Authorize;
import org.jaqpot.core.service.annotations.UnSecure;
import org.jaqpot.core.service.exceptions.JaqpotDocumentSizeExceededException;
import org.jaqpot.core.service.exceptions.QuotaExceededException;

import javax.ejb.EJB;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import java.util.HashMap;
import java.util.logging.Logger;
import org.jaqpot.core.service.annotations.TokenSecured;
import org.jaqpot.core.service.authentication.RoleEnum;

/**
 *
 * @author Charalampos Chomenidis
 * @author Pantelis Sopasakis
 */
@Path("doseresponse")
//@Api(value = "/doseresponse", description = "Dose Response API")
@Produces(MediaType.APPLICATION_JSON)
public class DoseResponseResource {

    private static final Logger LOG = Logger.getLogger(DoseResponseResource.class.getName());

    @Inject
    @UnSecure
    Client client;

    @Inject
    @Jackson
    JSONSerializer jsonSerializer;

    @EJB
    ReportHandler reportHandler;

    @EJB
    UserHandler userHandler;

    @Context
    SecurityContext securityContext;

    @POST
    @TokenSecured({RoleEnum.DEFAULT_USER})
//    @Path("/test")
    /*@ApiOperation(value = "Creates Dose Response Report",
            notes = "Creates Dose Response Report",
            response = Report.class
    )*/
    @Operation(summary = "Creates Dose Response Report",
               description = "Creates Dose Response Report",
               responses = {
                   @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = Report.class))),
               })
    @Produces({MediaType.APPLICATION_JSON, "text/uri-list"})
    public Response doseResponse(
            @FormParam("title") String title,
            @FormParam("description") String description,
            @FormParam("dataset_uri") String datasetURI,
            @FormParam("prediction_feature") String predictionFeature,
            @FormParam("parameters") String parameters,
            @HeaderParam("Authorization") String api_key
    ) throws QuotaExceededException,JaqpotDocumentSizeExceededException {

        User user = userHandler.find(securityContext.getUserPrincipal().getName());
        long reportCount = reportHandler.countAllOfCreator(user.getId());
        int maxAllowedReports = new UserFacade(user).getMaxReports();

        if (reportCount > maxAllowedReports) {
            LOG.info(String.format("User %s has %d reports while maximum is %d",
                    user.getId(), reportCount, maxAllowedReports));
            throw new QuotaExceededException("Dear " + user.getId()
                    + ", your quota has been exceeded; you already have " + reportCount + " reports. "
                    + "No more than " + maxAllowedReports + " are allowed with your subscription.");
        }
        String[] apiA = api_key.split("\\s+");
        String apiKey = apiA[1];
        Dataset dataset = client.target(datasetURI)
                .request()
                .header("Authorization", "Bearer " + apiKey)
                .accept(MediaType.APPLICATION_JSON)
                .get(Dataset.class);
        dataset.setDatasetURI(datasetURI);

        TrainingRequest trainingRequest = new TrainingRequest();
        trainingRequest.setDataset(dataset);
        trainingRequest.setPredictionFeature(predictionFeature);

        if (parameters != null && !parameters.isEmpty()) {
            HashMap<String, Object> parameterMap = jsonSerializer.parse(parameters, new HashMap<String, Object>().getClass());
            trainingRequest.setParameters(parameterMap);
        }

        Report report = client.target("http://147.102.86.129:8004/ocpu/library/IntPROAST61v5/R/enm.proast61/json")
                .request()
                .header("Content-Type", MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .post(Entity.json(trainingRequest), Report.class);

        report.setMeta(MetaInfoBuilder.builder()
                .addTitles(title)
                .addDescriptions(description)
                .addCreators(securityContext.getUserPrincipal().getName())
                .build()
        );
        report.setId(new ROG(true).nextString(15));
        report.setVisible(Boolean.TRUE);
        reportHandler.create(report);

        return Response.ok(report).build();
    }

}
