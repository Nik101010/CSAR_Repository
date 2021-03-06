/**
 * 
 */
package org.opentosca.csarrepo.util;

import java.io.File;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.opentosca.csarrepo.exception.DeploymentException;
import org.opentosca.csarrepo.model.OpenToscaServer;
import org.opentosca.csarrepo.util.jaxb.DeployedCsars;
import org.opentosca.csarrepo.util.jaxb.ServiceInstanceEntry;
import org.opentosca.csarrepo.util.jaxb.ServiceInstanceList;
import org.opentosca.csarrepo.util.jaxb.SimpleXLink;

/**
 * This class establishes a connection to a given ContainerAPI URL
 * 
 * It enables a User of this class to upload a CSAR and trigger its deployment
 * 
 * @author Marcus Eisele (marcus.eisele@gmail.com), Dennis Przytarski, Thomas
 *         Kosch
 *
 */
public class ContainerApiClient {

	private WebTarget baseWebTarget;
	private Client client;

	private static final Logger LOGGER = LogManager.getLogger(ContainerApiClient.class);

	/**
	 * Creates a ContainerApiClient which connects to the given URI
	 * 
	 * @param address
	 * @throws URISyntaxException
	 */
	public ContainerApiClient(OpenToscaServer openToscaServer) throws URISyntaxException {
		ClientConfig clientConfig = new ClientConfig();
		clientConfig.register(MultiPartFeature.class);
		clientConfig.property(ClientProperties.CHUNKED_ENCODING_SIZE, 1024);
		this.client = ClientBuilder.newClient(clientConfig);
		// TODO: check if it possible to store address as URI instead of URL
		baseWebTarget = client.target(openToscaServer.getAddress().toURI());
	}

	/**
	 * Uploads a CsarFile and triggers its processing
	 * 
	 * @param csarFile
	 * @return the location where the instance was created
	 */
	public String uploadFileToOpenTOSCA(File file, String fileName) throws DeploymentException {

		try {

			if (!file.exists()) {
				throw new DeploymentException(String.format("File %s doesn't exist", file.getAbsolutePath()));
			}

			// build the message
			FormDataMultiPart multiPart = new FormDataMultiPart();
			FormDataContentDisposition.FormDataContentDispositionBuilder dispositionBuilder = FormDataContentDisposition
					.name("file");
			dispositionBuilder.fileName(fileName);
			dispositionBuilder.size(file.getTotalSpace());
			FormDataContentDisposition formDataContentDisposition = dispositionBuilder.build();

			multiPart.bodyPart(new FormDataBodyPart("file", file, MediaType.APPLICATION_OCTET_STREAM_TYPE)
					.contentDisposition(formDataContentDisposition));

			Entity<FormDataMultiPart> entity = Entity.entity(multiPart, MediaType.MULTIPART_FORM_DATA_TYPE);

			// submit the request
			WebTarget path = baseWebTarget.path("CSARs");
			Builder request = path.request();
			Response response = request.post(entity);

			// handle response
			if (Status.CREATED.getStatusCode() == response.getStatus()) {
				return response.getHeaderString("location");
			} else {
				LOGGER.warn("Failed to deploy: " + file.getAbsolutePath() + " to " + path);
				throw new DeploymentException("Deployment failed - OpenTOSCA Server returned " + response.getStatus());
			}
		} catch (ProcessingException e) {
			LOGGER.warn("Failed to upload CSAR: Server - server was not reachable", e);
			throw new DeploymentException("Deletion failed - OpenTOSCA Server was not reachable");
		}
	}

	/**
	 * Submits a Delete at the given location
	 * 
	 * @param location
	 *            where the DELETE will be submitted
	 * @throws DeploymentException
	 *             when the deletion fails
	 */
	public void deleteCsarAtLocation(String location) throws DeploymentException {
		try {
			WebTarget deleteTarget = client.target(location);
			Builder request = deleteTarget.request();
			Response response = request.delete();
			if (Status.OK.getStatusCode() == response.getStatus()) {
				return;
			} else {
				LOGGER.warn("Failed to delete CSAR at: " + location);
				throw new DeploymentException("Deletion failed - OpenTOSCA Server returned " + response.getStatus());
			}
		} catch (ProcessingException e) {
			LOGGER.warn("Failed to delete CSAR at: " + location + " Server was not reachable.", e);
			throw new DeploymentException("Deletion failed - OpenTOSCA Server was not reachable");
		}

	}

	/**
	 * Submits a GET on the instancedata/serviceInstances Path of the given
	 * openToscaServer
	 * 
	 * @param openToscaServer
	 * @return
	 * @throws DeploymentException
	 * 
	 */

	// TODO: maybe we can extend the containerAPI to supply all needed
	// attributes inside the serviceInstances resource directly
	public List<ServiceInstanceEntry> getServiceInstances() throws DeploymentException {
		try {
			WebTarget path = baseWebTarget.path("instancedata/serviceInstances");
			Builder request = path.request().accept(MediaType.APPLICATION_XML_TYPE);
			ServiceInstanceList serviceInstanceList = request.get().readEntity(ServiceInstanceList.class);

			List<ServiceInstanceEntry> results = new ArrayList<ServiceInstanceEntry>();
			for (SimpleXLink link : serviceInstanceList.getLinks()) {
				WebTarget target = client.target(link.getHref());
				ServiceInstanceEntry serviceInstanceEntry = target.request().accept(MediaType.APPLICATION_XML_TYPE)
						.get(ServiceInstanceEntry.class);
				results.add(serviceInstanceEntry);
			}
			return results;
		} catch (ProcessingException e) {
			LOGGER.warn("Failed to get running InstancesLiveList - Server was not reachable.", e);
			throw new DeploymentException(
					"Failed to get running InstancesLiveList - OpenTOSCA Server was not reachable");
		}
	}

	/**
	 * Gets all deployed CSARs
	 * 
	 * @return list of deployed csars
	 * @throws DeploymentException
	 */
	public List<SimpleXLink> getDeployedCsars() throws DeploymentException {
		try {
			WebTarget path = baseWebTarget.path("CSARs");
			Builder request = path.request();
			DeployedCsars deployedCsars = request.get().readEntity(DeployedCsars.class);

			List<SimpleXLink> results = new ArrayList<SimpleXLink>();
			for (SimpleXLink link : deployedCsars.getLinks()) {
				if ("Self".equals(link.getTitle())) {
					continue;
				}
				results.add(link);
			}
			return results;
		} catch (ProcessingException e) {
			LOGGER.warn("Failed to get deployed CSARs - Server was not reachable.", e);
			throw new DeploymentException("Failed to get deployed CSARs - OpenTOSCA Server was not reachable");
		}
	}

	/**
	 * Returns the CSAR file id for the given CSAR filename
	 * 
	 * @param csarFileName
	 * @return null, if csarFileId not found.
	 * @throws DeploymentException
	 */
	public Long getRepositoryCsarFileId(String csarFileName) throws DeploymentException {
		try {
			WebTarget path = baseWebTarget.path(String.format("CSARs/%s/Content/CSAR-REPOSITORY.txt", csarFileName));
			Builder request = path.request().accept(MediaType.APPLICATION_OCTET_STREAM_TYPE);
			Response response = request.get();
			if (200 == response.getStatus()) {
				String data = response.readEntity(String.class);
				Long csarFileId = Long.valueOf(data);
				LOGGER.debug("CSAR file id for {} found: {}", csarFileName, csarFileId);
				return csarFileId;
			} else {
				LOGGER.debug("CSAR file id for {} not found: Status code was not 200.", csarFileName);
			}
		} catch (ProcessingException e) {
			LOGGER.warn("Failed to get CSAR file id - Server was not reachable.", e);
			throw new DeploymentException("Failed to get CSAR file id - OpenTOSCA Server was not reachable");
		}
		return null;
	}
}
