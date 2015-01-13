package org.opentosca.csarrepo.rest.resource;

import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.opentosca.csarrepo.model.Csar;
import org.opentosca.csarrepo.model.CsarFile;
import org.opentosca.csarrepo.rest.model.CsarEntry;
import org.opentosca.csarrepo.rest.model.SimpleXLink;
import org.opentosca.csarrepo.rest.util.LinkBuilder;
import org.opentosca.csarrepo.service.DeleteCsarService;
import org.opentosca.csarrepo.service.ShowCsarService;
import org.opentosca.csarrepo.service.UploadCsarFileService;

public class CsarResource {

	private static final Logger LOGGER = LogManager.getLogger(CsarResource.class);
	private UriInfo uriInfo;
	private long id;

	public CsarResource(UriInfo uriInfo, long id) {
		this.uriInfo = uriInfo;
		this.id = id;
	}

	@GET
	@Produces(MediaType.APPLICATION_XML)
	public Response getCsar() {
		// TODO: check if csar exists

		// TODO: validate if id is really a long
		List<SimpleXLink> links = new LinkedList<SimpleXLink>();
		links.add(LinkBuilder.selfLink(uriInfo));

		List<SimpleXLink> csarFiles = new LinkedList<SimpleXLink>();
		// TODO: add real UserID
		ShowCsarService showService = new ShowCsarService(0L, id);

		if (showService.hasErrors()) {
			// TODO: move to helper
			// TODO: don't only fetch first error
			return Response.status(Status.INTERNAL_SERVER_ERROR).entity(showService.getErrors().get(0)).build();
		}

		Csar csar = showService.getResult();

		for (CsarFile csarFile : csar.getCsarFiles()) {
			csarFiles.add(new SimpleXLink(LinkBuilder.linkToCsarFile(uriInfo, id, csarFile.getId()), csarFile.getName()
					+ "-" + csarFile.getId()));
		}

		CsarEntry csarEntry = new CsarEntry(csar, links, csarFiles);
		LOGGER.debug("Accessing csar<id:{},name:{}>", csar.getId(), csar.getName());
		return Response.ok(csarEntry).build();
	}

	@DELETE
	public Response deleteCsar() {
		DeleteCsarService service = new DeleteCsarService(0, this.id);

		if (service.hasErrors()) {
			return Response.status(Status.INTERNAL_SERVER_ERROR).entity(service.getErrors().get(0)).build();
		}

		return Response.ok().build();
	}

	// TODO: move id to constant class
	@Path("/{" + "id" + "}")
	public Object getCsarFile(@PathParam("id") long csarfileID, @Context UriInfo uriInfo) {
		// TODO: add warning if longID = -1;
		return new CsarFileResource(uriInfo, this.id, csarfileID);
	}

	@POST
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	public Response uploadFile(@FormDataParam("file") InputStream uploadedInputStream,
			@FormDataParam("file") FormDataContentDisposition fileDetail) {

		if (null == uploadedInputStream) {
			// TODO: logger
			return Response.serverError().entity("The stream is null.").build();
		}

		if (null == fileDetail) {
			return Response.serverError().entity("The file details are null.").build();
		}

		String csarName = fileDetail.getFileName();
		UploadCsarFileService upService = new UploadCsarFileService(0L, this.id, uploadedInputStream, csarName);
		// TODO, think about better Exceptionhandling (currently we
		// just take first Exception)
		if (upService.hasErrors()) {
			return Response.serverError().entity("UploadCsarService has Errors: " + upService.getErrors().get(0))
					.build();
		}

		CsarFile csarFile = upService.getResult();

		LOGGER.info("Post for uploading a new CSAR as file with name \"" + fileDetail.getFileName() + "\" with size ");

		return Response.ok().entity(LinkBuilder.linkToCsarFile(uriInfo, this.id, csarFile.getId())).build();

	}
}
