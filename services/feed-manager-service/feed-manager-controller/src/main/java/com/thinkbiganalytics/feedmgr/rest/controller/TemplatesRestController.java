package com.thinkbiganalytics.feedmgr.rest.controller;

/*-
 * #%L
 * thinkbig-feed-manager-controller
 * %%
 * Copyright (C) 2017 ThinkBig Analytics
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.thinkbiganalytics.feedmgr.nifi.NifiFlowCache;
import com.thinkbiganalytics.feedmgr.rest.model.FeedCategory;
import com.thinkbiganalytics.feedmgr.rest.model.FeedMetadata;
import com.thinkbiganalytics.feedmgr.rest.model.NiFiTemplateFlowRequest;
import com.thinkbiganalytics.feedmgr.rest.model.NiFiTemplateFlowResponse;
import com.thinkbiganalytics.feedmgr.rest.model.RegisteredTemplate;
import com.thinkbiganalytics.feedmgr.rest.model.RegisteredTemplateRequest;
import com.thinkbiganalytics.feedmgr.rest.model.ReusableTemplateConnectionInfo;
import com.thinkbiganalytics.feedmgr.rest.model.TemplateDtoWrapper;
import com.thinkbiganalytics.feedmgr.rest.model.TemplateOrder;
import com.thinkbiganalytics.feedmgr.rest.model.TemplateProcessorDatasourceDefinition;
import com.thinkbiganalytics.feedmgr.rest.support.SystemNamingService;
import com.thinkbiganalytics.feedmgr.service.MetadataService;
import com.thinkbiganalytics.feedmgr.service.datasource.DatasourceService;
import com.thinkbiganalytics.feedmgr.service.template.FeedManagerTemplateService;
import com.thinkbiganalytics.feedmgr.service.template.RegisteredTemplateService;
import com.thinkbiganalytics.metadata.rest.model.data.DatasourceDefinition;
import com.thinkbiganalytics.nifi.feedmgr.TemplateCreationHelper;
import com.thinkbiganalytics.nifi.rest.client.LegacyNifiRestClient;
import com.thinkbiganalytics.nifi.rest.client.NifiComponentNotFoundException;
import com.thinkbiganalytics.nifi.rest.model.NifiProperty;
import com.thinkbiganalytics.nifi.rest.support.NifiConstants;
import com.thinkbiganalytics.nifi.rest.support.NifiPropertyUtil;
import com.thinkbiganalytics.rest.model.RestResponseStatus;

import org.apache.commons.lang3.StringUtils;
import org.apache.nifi.web.api.dto.PortDTO;
import org.apache.nifi.web.api.dto.TemplateDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.SwaggerDefinition;
import io.swagger.annotations.Tag;

@Api(tags = "Feed Manager - Templates", produces = "application/json")
@Path(TemplatesRestController.BASE)
@SwaggerDefinition(tags = @Tag(name = "Feed Manager - Templates", description = "manages templates"))
public class TemplatesRestController {

    private static final Logger log = LoggerFactory.getLogger(TemplatesRestController.class);
    public static final String BASE = "/v1/feedmgr/templates";
    public static final String REGISTERED = "/registered";

    @Autowired
    LegacyNifiRestClient nifiRestClient;

    @Autowired
    MetadataService metadataService;

    @Autowired
    FeedManagerTemplateService feedManagerTemplateService;

    @Autowired
    DatasourceService datasourceService;

    @Inject
    RegisteredTemplateService registeredTemplateService;

    @Inject
    NifiFlowCache nifiFlowCache;

    private MetadataService getMetadataService() {
        return metadataService;
    }


    /**
     * This will list all the templates registered in Kylo
     * @param includeDetails
     * @return
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation("Gets the list of all templates.")
    @ApiResponses({
                      @ApiResponse(code = 200, message = "Returns the templates.", response = TemplateDtoWrapper.class, responseContainer = "Set"),
                      @ApiResponse(code = 500, message = "NiFi is unavailable.", response = RestResponseStatus.class)
                  })
    public Response getTemplates(@QueryParam("includeDetails") boolean includeDetails) {
        Set<TemplateDTO> nifiTemplates = nifiRestClient.getTemplates(includeDetails);
        Set<TemplateDtoWrapper> dtos = new HashSet<>();
        List<RegisteredTemplate> registeredTemplates = registeredTemplateService.getRegisteredTemplates();
        for (final TemplateDTO dto : nifiTemplates) {

            RegisteredTemplate match =registeredTemplates.stream().filter(template -> template.getNifiTemplateId().equalsIgnoreCase(dto.getId()) || template.getTemplateName().equalsIgnoreCase(dto.getName())).findFirst().orElse(null);

            TemplateDtoWrapper wrapper = new TemplateDtoWrapper(dto);
            if (match != null) {
                wrapper.setRegisteredTemplateId(match.getId());
            }
            dtos.add(wrapper);
        }
        return Response.ok(dtos).build();
    }

    /**
     * This will populate the select drop down when a user asks to register a new template
     *
     * @param includeDetails
     * @return
     */
    @GET
    @Path("/unregistered")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation("Gets the list of unregistered templates.")
    @ApiResponses({
                      @ApiResponse(code = 200, message = "Returns the templates.", response = TemplateDtoWrapper.class, responseContainer = "Set"),
                      @ApiResponse(code = 500, message = "NiFi is unavailable.", response = RestResponseStatus.class)
                  })
    public Response getUnregisteredTemplates(@QueryParam("includeDetails") boolean includeDetails) {
        Set<TemplateDTO> nifiTemplates = nifiRestClient.getTemplates(includeDetails);
        //List<RegisteredTemplate> registeredTemplates = metadataService.getRegisteredTemplates();

        Set<TemplateDtoWrapper> dtos = new HashSet<>();
        for (final TemplateDTO dto : nifiTemplates) {
            RegisteredTemplate match = registeredTemplateService.findRegisteredTemplate(
                RegisteredTemplateRequest.requestByNiFiTemplateProperties(dto.getId(), dto.getName()));
            if (match == null) {
                dtos.add(new TemplateDtoWrapper(dto));
            }
        }
        return Response.ok(dtos).build();
    }


    @GET
    @Path("/nifi/{templateId}/ports")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation("Gets the ports in the specified template.")
    @ApiResponses({
                      @ApiResponse(code = 200, message = "Returns the ports.", response = PortDTO.class, responseContainer = "Set"),
                      @ApiResponse(code = 500, message = "NiFi is unavailable.", response = RestResponseStatus.class)
                  })
    public Response getPortsForNifiTemplate(@PathParam("templateId") String nifiTemplateId) {
        Set<PortDTO> ports = nifiRestClient.getPortsForTemplate(nifiTemplateId);
        return Response.ok(ports).build();
    }

    @GET
    @Path("/nifi/{templateId}/input-ports")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation("Gets the input ports in the specified template.")
    @ApiResponses({
                      @ApiResponse(code = 200, message = "Returns the input ports.", response = PortDTO.class, responseContainer = "Set"),
                      @ApiResponse(code = 500, message = "NiFi is unavailable.", response = RestResponseStatus.class)
                  })
    public Response getInputPortsForNifiTemplate(@PathParam("templateId") String nifiTemplateId) {
        Set<PortDTO> ports = nifiRestClient.getPortsForTemplate(nifiTemplateId);
        List<PortDTO> list = Lists.newArrayList(Iterables.filter(ports, new Predicate<PortDTO>() {
            @Override
            public boolean apply(PortDTO portDTO) {
                return portDTO.getType().equalsIgnoreCase(NifiConstants.NIFI_PORT_TYPE.INPUT_PORT.name());
            }
        }));
        return Response.ok(list).build();
    }


    @GET
    @Path("/nifi/reusable-input-ports-processors")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation("Gets the processors connected to the specified input ports.")
    @ApiResponses({
                      @ApiResponse(code = 200, message = "Returns the processors.", response = RegisteredTemplate.Processor.class, responseContainer = "List"),
                      @ApiResponse(code = 500, message = "NiFi is unavailable.", response = RestResponseStatus.class)
                  })
    public List<RegisteredTemplate.Processor> getReusableTemplateProcessorsForInputPorts(@QueryParam("inputPorts") String inputPortIds) {
        List<RegisteredTemplate.Processor> processorProperties = new ArrayList<>();
        if (StringUtils.isNotBlank(inputPortIds)) {
            List<String> inputPortIdsList = Arrays.asList(StringUtils.split(inputPortIds, ","));
            processorProperties = feedManagerTemplateService.getReusableTemplateProcessorsForInputPorts(inputPortIdsList);
        }
        return processorProperties;
    }


    @GET
    @Path("/nifi/{templateId}/processors")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation("Gets the processors in the specified template.")
    @ApiResponses({
                      @ApiResponse(code = 200, message = "Returns the processors.", response = RegisteredTemplate.Processor.class, responseContainer = "List"),
                      @ApiResponse(code = 500, message = "NiFi is unavailable.", response = RestResponseStatus.class)
                  })
    public Response getNiFiTemplateProcessors(@PathParam("templateId") String templateId) {
        List<RegisteredTemplate.Processor> processorProperties = feedManagerTemplateService.getNiFiTemplateProcessorsWithProperties(templateId);
        return Response.ok(processorProperties).build();
    }


    /**
     * Returns data about the NiFiTemplate and its processors related to the input connections, along with the Datasources in the flow
     */
    @POST
    @Path("/nifi/{templateId}/flow-info")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation("Gets the flow for the specified template.")
    @ApiResponses({
                      @ApiResponse(code = 200, message = "Returns the flow.", response = NiFiTemplateFlowResponse.class),
                      @ApiResponse(code = 500, message = "NiFi is unavailable.", response = RestResponseStatus.class)
                  })
    public Response getNiFiTemplateFlowInfo(@PathParam("templateId") String templateId, NiFiTemplateFlowRequest flowRequest) {
        List<TemplateProcessorDatasourceDefinition> templateProcessorDatasourceDefinitions = new ArrayList<>();
        NiFiTemplateFlowResponse response = new NiFiTemplateFlowResponse();
        response.setRequest(flowRequest);
        if (StringUtils.isNotBlank(templateId)) {

            List<RegisteredTemplate.FlowProcessor> processors = feedManagerTemplateService.getNiFiTemplateFlowProcessors(templateId, flowRequest.getConnectionInfo());

            Set<DatasourceDefinition> defs = datasourceService.getDatasourceDefinitions();
            Map<String, DatasourceDefinition> datasourceDefinitionMap = new HashMap<>();
            if (defs != null) {
                defs.stream().forEach(def -> datasourceDefinitionMap.put(def.getProcessorType(), def));
            }

            templateProcessorDatasourceDefinitions = processors.stream().filter(processor -> datasourceDefinitionMap.containsKey(processor.getType())).map(p -> {
                TemplateProcessorDatasourceDefinition definition = new TemplateProcessorDatasourceDefinition();
                definition.setProcessorType(p.getType());
                definition.setProcessorName(p.getName());
                definition.setProcessorId(p.getId());
                definition.setDatasourceDefinition(datasourceDefinitionMap.get(p.getType()));
                return definition;
            }).collect(Collectors.toList());

            response.setProcessors(processors);
            response.setTemplateProcessorDatasourceDefinitions(templateProcessorDatasourceDefinitions);
        }
        return Response.ok(response).build();


    }


    @GET
    @Path("/reload-data-source-definitions")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation("Reloads the datasource definitions file.")
    @ApiResponses(
        @ApiResponse(code = 200, message = "The datasource definitions were reloaded.", response = RestResponseStatus.class)
    )
    public Response reloadDatasources() {
        datasourceService.loadDefinitionsFromFile();
        return Response.ok(RestResponseStatus.SUCCESS).build();
    }


    @GET
    @Path("/nifi/{templateId}/out-ports")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation("Gets the output ports for the specified template.")
    @ApiResponses({
                      @ApiResponse(code = 200, message = "Returns the output ports.", response = PortDTO.class, responseContainer = "List"),
                      @ApiResponse(code = 500, message = "NiFi is unavailable.", response = RestResponseStatus.class)
                  })
    public Response getOutputPortsForNifiTemplate(@PathParam("templateId") String nifiTemplateId) {
        Set<PortDTO> ports = nifiRestClient.getPortsForTemplate(nifiTemplateId);
        List<PortDTO> list = Lists.newArrayList(Iterables.filter(ports, new Predicate<PortDTO>() {
            @Override
            public boolean apply(PortDTO portDTO) {
                return portDTO.getType().equalsIgnoreCase(NifiConstants.NIFI_PORT_TYPE.OUTPUT_PORT.name());
            }
        }));
        return Response.ok(list).build();
    }


    /**
     * get all the registered Templates
     *
     * @
     */
    @GET
    @Path(REGISTERED)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation("Gets the list of registered templates.")
    @ApiResponses(
        @ApiResponse(code = 200, message = "Returns the templates.", response = RegisteredTemplate.class, responseContainer = "List")
    )
    public Response getRegisteredTemplates() {
        List<RegisteredTemplate> templates = getMetadataService().getRegisteredTemplates();
        return Response.ok(templates).build();

    }


    /**
     * Gets the template and optionally all reusable flow processors and properties
     */
    @GET
    @Path("/registered/{templateId}/processor-properties")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation("Gets the processors of a registered template for input ports.")
    @ApiResponses(
        @ApiResponse(code = 200, message = "Returns the processors.", response = RegisteredTemplate.Processor.class, responseContainer = "List")
    )
    public List<RegisteredTemplate.Processor> getReusableTemplateProcessorsForInputPorts(@PathParam("templateId") String templateId,
                                                                                         @QueryParam("includeReusableTemplates") boolean includeReusableTemplates) {
        List<RegisteredTemplate.Processor> processorProperties = feedManagerTemplateService.getRegisteredTemplateProcessors(templateId, includeReusableTemplates);

        return processorProperties;
    }


    /**
     * get a given Registered Templates properties
     *
     * @
     */
    @GET
    @Path("/registered/{templateId}/properties")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation("Gets the properties of a registered template.")
    @ApiResponses(
        @ApiResponse(code = 200, message = "Returns the properties.", response = NifiProperty.class, responseContainer = "List")
    )
    public Response getRegisteredTemplateProperties(@PathParam("templateId") String templateId) {
        return Response.ok(getMetadataService().getTemplateProperties(templateId)).build();
    }

    /**
     * get a registeredTemplate for updating
     *
     * 
     */
    @GET
    @Path("/registered/{templateId}")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation("Gets the specified registered template.")
    @ApiResponses({
                      @ApiResponse(code = 200, message = "Returns the template.", response = RegisteredTemplate.class),
                      @ApiResponse(code = 500, message = "NiFi is unavailable.", response = RestResponseStatus.class)
                  })
    public Response getRegisteredTemplate(@PathParam("templateId") String templateId, @QueryParam("allProperties") boolean allProperties, @QueryParam("feedName") String feedName,
                                          @QueryParam("templateName") String templateName) {

        RegisteredTemplateRequest registeredTemplateRequest = new RegisteredTemplateRequest.Builder().templateId(templateId).templateName(templateName).nifiTemplateId(templateId).includeAllProperties(allProperties).includePropertyDescriptors(true).isTemplateEdit(true).build();
        RegisteredTemplate registeredTemplate = registeredTemplateService.getRegisteredTemplateForUpdate(registeredTemplateRequest);
        return Response.ok(registeredTemplate).build();
    }

    @POST
    @Path("/registered/{templateId}/enable")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_FORM_URLENCODED})
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation("Enables the specified registered template.")
    @ApiResponses(
        @ApiResponse(code = 200, message = "Returns the template.", response = RegisteredTemplate.class)
    )
    public Response enableTemplate(@PathParam("templateId") String templateId) {
        RegisteredTemplate enabledTemplate = feedManagerTemplateService.enableTemplate(templateId);
        return Response.ok(enabledTemplate).build();
    }

    @POST
    @Path("/registered/{templateId}/disable")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_FORM_URLENCODED})
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation("Disables the specified registered template.")
    @ApiResponses(
        @ApiResponse(code = 200, message = "Returns the template.", response = RegisteredTemplate.class)
    )
    public Response disableTemplate(@PathParam("templateId") String templateId) {
        RegisteredTemplate disabledTemplate = feedManagerTemplateService.disableTemplate(templateId);
        return Response.ok(disabledTemplate).build();
    }

    @DELETE
    @Path("/registered/{templateId}/delete")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation("Deletes the specified registered template.")
    @ApiResponses(
        @ApiResponse(code = 200, message = "Returns the result.", response = RestResponseStatus.class)
    )
    public Response deleteTemplate(@PathParam("templateId") String templateId) {
        boolean deleted = feedManagerTemplateService.deleteRegisteredTemplate(templateId);
        return Response.ok(deleted ? new RestResponseStatus.ResponseStatusBuilder().buildSuccess() : new RestResponseStatus.ResponseStatusBuilder().buildError()).build();
    }

    @POST
    @Path("/order")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_FORM_URLENCODED})
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation("Modifies the order of the registered templates.")
    @ApiResponses(
        @ApiResponse(code = 200, message = "Returns the result.", response = RestResponseStatus.class)
    )
    public Response orderTemplates(TemplateOrder templateOrder) {
        feedManagerTemplateService.orderTemplates(templateOrder.getTemplateIds(), null);
        return Response.ok(new RestResponseStatus.ResponseStatusBuilder().buildSuccess()).build();
    }

    /**
     * Register and save a given template and its properties
     *
     * @
     */
    @POST
    @Path("/register")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_FORM_URLENCODED})
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation("Registers the specified template.")
    @ApiResponses(
        @ApiResponse(code = 200, message = "The template was registered.", response = RegisteredTemplate.class)
    )
    public Response registerTemplate(RegisteredTemplate registeredTemplate) {

        RegisteredTemplate saved = feedManagerTemplateService.registerTemplate(registeredTemplate);
        return Response.ok(saved).build();
    }
}
