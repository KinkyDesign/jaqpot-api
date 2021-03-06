package org.jaqpot.core.service.resource;

import io.swagger.annotations.*;
import org.apache.commons.validator.routines.UrlValidator;
import org.jaqpot.core.annotations.Jackson;
import org.jaqpot.core.data.AlgorithmHandler;
import org.jaqpot.core.data.ModelHandler;
import org.jaqpot.core.data.UserHandler;
import org.jaqpot.core.data.serialize.JSONSerializer;
import org.jaqpot.core.model.Algorithm;
import org.jaqpot.core.model.Task;
import org.jaqpot.core.model.User;
import org.jaqpot.core.model.facades.UserFacade;
import org.jaqpot.core.service.annotations.Authorize;
import org.jaqpot.core.service.data.AAService;
import org.jaqpot.core.service.data.TrainingService;
import org.jaqpot.core.service.exceptions.QuotaExceededException;
import org.jaqpot.core.service.exceptions.parameter.*;
import org.jaqpot.core.service.validator.ParameterValidator;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;

import javax.ejb.EJB;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.io.*;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.logging.Level;
import org.jaqpot.core.data.DatasetHandler;
import org.jaqpot.core.model.ErrorReport;
import org.jaqpot.core.model.Model;
import org.jaqpot.core.model.dto.dataset.Dataset;
import org.jaqpot.core.service.data.PredictionService;

/**
 * Created by Angelos Valsamis on 23/10/2017.
 */
@Path("biokinetics")
@Api(value = "/biokinetics", description = "Biokinetics API")
@Produces({"application/json", "text/uri-list"})
@Authorize

public class BiokineticsResource {

    @EJB
    AAService aaService;

    @Context
    SecurityContext securityContext;

    @EJB
    AlgorithmHandler algorithmHandler;

    @EJB
    UserHandler userHandler;

    @EJB
    ModelHandler modelHandler;

    @Inject
    ParameterValidator parameterValidator;

    @EJB
    DatasetHandler datasetHandler;

    @EJB
    PredictionService predictionService;

    @EJB
    TrainingService trainingService;

    @Context
    UriInfo uriInfo;

    @Inject
    @Jackson
    JSONSerializer serializer;

    private static final Logger LOG = Logger.getLogger(BiokineticsResource.class.getName());

    @POST
    @Produces({MediaType.APPLICATION_JSON, "text/uri-list"})
    @Path("/pksim/createmodel")
    @ApiImplicitParams({
        @ApiImplicitParam(name = "file", value = "xml[m,x] file", required = true, dataType = "file", paramType = "formData")
        ,
            @ApiImplicitParam(name = "dataset-uri", value = "Dataset uri to be trained upon", required = true, dataType = "string", paramType = "formData")
        ,
            @ApiImplicitParam(name = "title", value = "Title of model", required = true, dataType = "string", paramType = "formData")
        ,
            @ApiImplicitParam(name = "description", value = "Description of model", required = true, dataType = "string", paramType = "formData")
        ,
            @ApiImplicitParam(name = "algorithm-uri", value = "Algorithm URI", required = true, dataType = "string", paramType = "formData")
        ,
            @ApiImplicitParam(name = "parameters", value = "Parameters for algorithm", required = false, dataType = "string", paramType = "formData")

    })
    @ApiOperation(value = "Creates Biokinetics model with PkSim",
            notes = "Creates a biokinetics model given a pksim .xml file and demographic data",
            response = Task.class
    )
    @org.jaqpot.core.service.annotations.Task
    public Response trainBiokineticsModel(
            @HeaderParam("subjectid") String subjectId,
            @ApiParam(value = "multipartFormData input", hidden = true) MultipartFormDataInput input)
            throws ParameterIsNullException, ParameterInvalidURIException, QuotaExceededException, IOException, ParameterScopeException, ParameterRangeException, ParameterTypeException {

        UrlValidator urlValidator = new UrlValidator(UrlValidator.ALLOW_LOCAL_URLS);

        byte[] bytes;
        Map<String, List<InputPart>> uploadForm = input.getFormDataMap();
        List<InputPart> inputParts = uploadForm.get("file");
        String title = uploadForm.get("title").get(0).getBody(String.class, null);
        String description = uploadForm.get("description").get(0).getBody(String.class, null);
        String algorithmURI = uploadForm.get("algorithm-uri").get(0).getBody(String.class, null);
        String datasetUri = uploadForm.get("dataset-uri").get(0).getBody(String.class, null);

        User user = userHandler.find(securityContext.getUserPrincipal().getName());
        long modelCount = modelHandler.countAllOfCreator(user.getId());
        int maxAllowedModels = new UserFacade(user).getMaxModels();

        if (modelCount > maxAllowedModels) {
            LOG.info(String.format("User %s has %d model while maximum is %d",
                    user.getId(), modelCount, maxAllowedModels));
            throw new QuotaExceededException("Dear " + user.getId()
                    + ", your quota has been exceeded; you already have " + modelCount + " models. "
                    + "No more than " + maxAllowedModels + " are allowed with your subscription.");
        }

        String parameters = null;
        if (uploadForm.get("parameters") != null) {
            parameters = uploadForm.get("parameters").get(0).getBody(String.class, null);
        }

        if (algorithmURI == null) {
            throw new ParameterIsNullException("algorithmURI");
        }

        if (!urlValidator.isValid(algorithmURI)) {
            throw new ParameterInvalidURIException("Not valid Algorithm URI.");
        }
        String algorithmId = algorithmURI.split("algorithm/")[1];

        Algorithm algorithm = algorithmHandler.find(algorithmId);
        if (algorithm == null) {
            throw new NotFoundException("Could not find Algorithm with id:" + algorithmId);
        }

        parameterValidator.validate(parameters, algorithm.getParameters());

        String encodedString = "";
        for (InputPart inputPart : inputParts) {
            try {
                //Convert the uploaded file to inputstream
                InputStream inputStream = inputPart.getBody(InputStream.class, null);
                bytes = getBytesFromInputStream(inputStream);

                //Base64 encode
                byte[] encoded = java.util.Base64.getEncoder().encode(bytes);
                encodedString = new String(encoded);
                System.out.println(encodedString);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        //Append XML file (Base64 string) in parameters
        parameters = parameters.substring(0, parameters.length() - 1);
        parameters += ",\"xml_file\":[\"" + encodedString + "\"]}";

        Map<String, Object> options = new HashMap<>();
        options.put("title", title);
        options.put("description", description);
        options.put("subjectid", subjectId);
        options.put("parameters", parameters);
        options.put("base_uri", uriInfo.getBaseUri().toString());
        options.put("dataset_uri", datasetUri);
        options.put("algorithmId", algorithmId);
        options.put("creator", securityContext.getUserPrincipal().getName());
        Task task = trainingService.initiateTraining(options, securityContext.getUserPrincipal().getName());
        return Response.ok(task).build();
    }

    @POST
    @Produces({MediaType.APPLICATION_JSON, "text/uri-list"})
    @Path("httk/createmodel")
    @ApiOperation(value = "Creates an httk biocinetics Model",
            notes = "Creates an httk biocinetics Model ",
            extensions = {
                @Extension(properties = {
            @ExtensionProperty(name = "orn-@type", value = "x-orn:Model"),}
                )
                ,
                @Extension(name = "orn:expects", properties = {
            @ExtensionProperty(name = "x-orn-@id", value = "x-orn:OperrationParameters")
        })
                ,
                @Extension(name = "orn:returns", properties = {
            @ExtensionProperty(name = "x-orn-@id", value = "x-orn:JaqpotModelingTaskId")
        })
            }
    )
    @ApiResponses(value = {
        @ApiResponse(code = 400, response = ErrorReport.class, message = "Bad request. More info can be found in details of Error Report.")
        ,
            @ApiResponse(code = 401, response = ErrorReport.class, message = "Wrong, missing or insufficient credentials. Error report is produced.")
        ,
            @ApiResponse(code = 404, response = ErrorReport.class, message = "Algorithm was not found.")
        ,
            @ApiResponse(code = 200, response = Task.class, message = "The process has successfully been started. A task URI is returned.")
        ,
            @ApiResponse(code = 500, response = ErrorReport.class, message = "Internal server error - this request cannot be served.")

    })
    @org.jaqpot.core.service.annotations.Task
    public Response trainHttk(
            @ApiParam(name = "title", required = true) @FormParam("title") String title,
            @ApiParam(name = "description", required = true) @FormParam("description") String description,
            @FormParam("parameters") String parameters,
//            @ApiParam(name = "algorithmId", required = true) @FormParam("algorithmId") String algorithmId,
            @HeaderParam("subjectid") String subjectId) throws QuotaExceededException, ParameterIsNullException, ParameterInvalidURIException, ParameterTypeException, ParameterRangeException, ParameterScopeException {
        UrlValidator urlValidator = new UrlValidator(UrlValidator.ALLOW_LOCAL_URLS);

        String algorithmId = "httk";
        Algorithm algorithm = algorithmHandler.find(algorithmId);
        if (algorithm == null) {
            throw new NotFoundException("Could not find Algorithm with id:" + algorithmId);
        }

        if (title == null) {
            throw new ParameterIsNullException("title");
        }
        if (description == null) {
            throw new ParameterIsNullException("description");
        }

        User user = userHandler.find(securityContext.getUserPrincipal().getName());
        long modelCount = modelHandler.countAllOfCreator(user.getId());
        int maxAllowedModels = new UserFacade(user).getMaxModels();

        if (modelCount > maxAllowedModels) {
            LOG.info(String.format("User %s has %d models while maximum is %d",
                    user.getId(), modelCount, maxAllowedModels));
            throw new QuotaExceededException("Dear " + user.getId()
                    + ", your quota has been exceeded; you already have " + modelCount + " models. "
                    + "No more than " + maxAllowedModels + " are allowed with your subscription.");
        }

        Map<String, Object> options = new HashMap<>();
        options.put("title", title);
        options.put("description", description);
        options.put("dataset_uri", null);
        options.put("prediction_feature", null);
        options.put("subjectid", subjectId);
        options.put("algorithmId", algorithmId);
        options.put("parameters", parameters);
        options.put("base_uri", uriInfo.getBaseUri().toString());
        options.put("creator", securityContext.getUserPrincipal().getName());

        Map<String, String> transformationAlgorithms = new LinkedHashMap<>();

        if (!transformationAlgorithms.isEmpty()) {
            String transformationAlgorithmsString = serializer.write(transformationAlgorithms);
            LOG.log(Level.INFO, "Transformations:{0}", transformationAlgorithmsString);
            options.put("transformations", transformationAlgorithmsString);
        }

        parameterValidator.validate(parameters, algorithm.getParameters());

        //return Response.ok().build();
        Task task = trainingService.initiateTraining(options, securityContext.getUserPrincipal().getName());

        return Response.ok(task).build();
    }

    @POST
    @Consumes({MediaType.APPLICATION_FORM_URLENCODED})
    @Produces({MediaType.APPLICATION_JSON})
    @Path("httk/model/{id}")
    @ApiOperation(value = "Creates prediction with httk model",
            notes = "Creates prediction with Httk model",
            response = Task.class,
            extensions = {
                @Extension(properties = {
            @ExtensionProperty(name = "orn-@type", value = "x-orn:JaqpotPredictionTaskId"),}
                )
                ,
                @Extension(name = "orn:expects", properties = {
            @ExtensionProperty(name = "x-orn-@id", value = "x-orn:AcessToken")
            ,
                    @ExtensionProperty(name = "x-orn-@id", value = "x-orn:JaqpotModelId")
        })
                ,
                @Extension(name = "orn:returns", properties = {
            @ExtensionProperty(name = "x-orn-@id", value = "x-orn:JaqpotHttkPredictionTaskId")
        })
            }
    )
    @org.jaqpot.core.service.annotations.Task
    public Response makeHttkPrediction(
            @FormParam("visible") Boolean visible,
            @PathParam("id") String id,
            @HeaderParam("subjectid") String subjectId) throws GeneralSecurityException, QuotaExceededException, ParameterIsNullException, ParameterInvalidURIException {

        if (id == null) {
            throw new ParameterIsNullException("id");
        }

        UrlValidator urlValidator = new UrlValidator(UrlValidator.ALLOW_LOCAL_URLS);

        User user = userHandler.find(securityContext.getUserPrincipal().getName());
        long datasetCount = datasetHandler.countAllOfCreator(user.getId());
        int maxAllowedDatasets = new UserFacade(user).getMaxDatasets();

        if (datasetCount > maxAllowedDatasets) {
            LOG.info(String.format("User %s has %d datasets while maximum is %d",
                    user.getId(), datasetCount, maxAllowedDatasets));
            throw new QuotaExceededException("Dear " + user.getId()
                    + ", your quota has been exceeded; you already have " + datasetCount + " datasets. "
                    + "No more than " + maxAllowedDatasets + " are allowed with your subscription.");
        }

        Model model = modelHandler.find(id);
        if (model == null) {
            throw new NotFoundException("Model not found.");
        }
        if (!model.getAlgorithm().getId().equals("httk")){
            throw new NotFoundException("Model is not created from httk");
        }

        List<String> requiredFeatures = retrieveRequiredFeatures(model);

        Map<String, Object> options = new HashMap<>();
        options.put("dataset_uri", null);
        options.put("subjectid", subjectId);
        options.put("modelId", id);
        options.put("creator", securityContext.getUserPrincipal().getName());
        options.put("base_uri", uriInfo.getBaseUri().toString());
        Task task = predictionService.initiatePrediction(options);
        return Response.ok(task).build();
    }

    private List<String> retrieveRequiredFeatures(Model model) {
        if (model.getTransformationModels() != null && !model.getTransformationModels().isEmpty()) {
            String transModelId = model.getTransformationModels().get(0).split("model/")[1];
            Model transformationModel = modelHandler.findModelIndependentFeatures(transModelId);
            if (transformationModel != null && transformationModel.getIndependentFeatures() != null) {
                return transformationModel.getIndependentFeatures();
            }
        }
        return model.getIndependentFeatures();
    }

    private static byte[] getBytesFromInputStream(InputStream is) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        byte[] buffer = new byte[0xFFFF];
        for (int len; (len = is.read(buffer)) != -1;) {
            os.write(buffer, 0, len);
        }
        os.flush();
        return os.toByteArray();
    }
}
