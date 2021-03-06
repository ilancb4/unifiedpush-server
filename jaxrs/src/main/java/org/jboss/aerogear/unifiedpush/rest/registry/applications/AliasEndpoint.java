/**
 * JBoss, Home of Professional Open Source
 * Copyright Red Hat, Inc., and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.aerogear.unifiedpush.rest.registry.applications;

import java.util.List;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import org.jboss.aerogear.unifiedpush.api.Alias;
import org.jboss.aerogear.unifiedpush.api.PushApplication;
import org.jboss.aerogear.unifiedpush.cassandra.dao.impl.AliasAlreadyExists;
import org.jboss.aerogear.unifiedpush.rest.AbstractBaseEndpoint;
import org.jboss.aerogear.unifiedpush.rest.EmptyJSON;
import org.jboss.aerogear.unifiedpush.rest.PasswordContainer;
import org.jboss.aerogear.unifiedpush.rest.util.BearerHelper;
import org.jboss.aerogear.unifiedpush.rest.util.PushAppAuthHelper;
import org.jboss.aerogear.unifiedpush.service.AliasService;
import org.jboss.aerogear.unifiedpush.service.PushApplicationService;
import org.jboss.aerogear.unifiedpush.service.impl.ServiceConstraintViolationException;
import org.keycloak.representations.AccessToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import com.qmino.miredot.annotations.ReturnType;

@Controller
@Path("/alias")
public class AliasEndpoint extends AbstractBaseEndpoint {
	private final Logger logger = LoggerFactory.getLogger(AliasEndpoint.class);

	@Autowired
	private PushApplicationService pushAppService;

	@Autowired
	private AliasService aliasService;

	/**
	 * Cross Origin for Alias
	 *
	 * @param headers
	 *            "Origin" header
	 * @param token
	 *            Will match any pattern not matched by a more specific path.
	 * @return "Access-Control-Allow-Origin" header for your response
	 *
	 * @responseheader Access-Control-Allow-Origin With host in your "Origin"
	 *                 header
	 * @responseheader Access-Control-Allow-Methods POST, DELETE, OPTIONS, PUT
	 * @responseheader Access-Control-Allow-Headers accept, origin,
	 *                 content-type, authorization
	 * @responseheader Access-Control-Allow-Credentials true
	 * @responseheader Access-Control-Max-Age 604800
	 *
	 * @statuscode 200 Successful response for your request
	 */
	@OPTIONS
	@Path("{token: .*}")
	@ReturnType("java.lang.Void")
	public Response crossOriginForInstallations(@Context HttpHeaders headers, @PathParam("token") String token) {
		return appendPreflightResponseHeaders(headers, Response.ok()).build();
	}

	/**
	 * RESTful API for validating alias is already registered (Keycloak). The
	 * Endpoint has public access.
	 *
	 * @param alias
	 *            The alias name.
	 * @return {@link Boolean}
	 *
	 * @responseheader Access-Control-Allow-Origin With host in your "Origin"
	 *                 header
	 * @responseheader Access-Control-Allow-Credentials true
	 * @responseheader WWW-Authenticate Basic realm="UnifiedPush Server" (only
	 *                 for 401 response)
	 *
	 * @statuscode 200 True/False String value.
	 * @statuscode 400 The format of the aliases request was incorrect (e.g.
	 *             missing required values).
	 * @statuscode 401 The request requires authentication.
	 *
	 *             TODO - Rename to registered
	 */
	@GET
	@Path("/exists/{alias}")
	@Produces(MediaType.APPLICATION_JSON)
	@ReturnType("java.lang.Boolean")
	public Response registered(@PathParam("alias") String alias, @Context HttpServletRequest request) {
		if (aliasService.registered(extractUsername(), alias))
			return appendAllowOriginHeader(Response.ok().entity(Boolean.TRUE), request);

		return appendAllowOriginHeader(Response.ok().entity(Boolean.FALSE), request);
	}

	/**
	 * RESTful API for validating alias existence (associated) within a team.
	 * The Endpoint has public access.
	 *
	 * <pre>
	 * curl -v -H "Accept: application/json" -H "Content-type: application/json"
	 *   -X GET https://SERVER:PORT/context/rest/alias/associated/{alias}?fqdn=test.aerobase.org
	 * </pre>
	 *
	 * @param alias
	 *            The associated domain / team.
	 * @param fqdn
	 *            The alias domain.
	 *
	 * @return {@link Boolean}
	 *
	 * @responseheader Access-Control-Allow-Origin With host in your "Origin"
	 *                 header
	 * @responseheader Access-Control-Allow-Credentials true
	 * @responseheader WWW-Authenticate Basic realm="UnifiedPush Server" (only
	 *                 for 401 response)
	 *
	 * @statuscode 200 True/False String value.
	 * @statuscode 400 The format of the aliases request was incorrect (e.g.
	 *             missing required values).
	 * @statuscode 401 The request requires authentication.
	 */
	@GET
	@Path("/associated/{alias}")
	@Produces(MediaType.APPLICATION_JSON)
	@ReturnType("java.lang.Boolean")
	public Response associated(@PathParam("alias") String alias, @QueryParam("fqdn") String fqdn,
			@Context HttpServletRequest request) {
		if (aliasService.associated(alias, fqdn))
			return appendAllowOriginHeader(Response.ok().entity(Boolean.TRUE), request);

		return appendAllowOriginHeader(Response.ok().entity(Boolean.FALSE), request);
	}

	/**
	 * RESTful API for updating alias password. The Endpoint is protected using
	 * Bearer token.
	 *
	 * @param alias
	 *            The alias name.
	 * @param passwordContainer
	 *            {@link PasswordContainer}
	 * @return {@link EmptyJSON}
	 *
	 * @responseheader Access-Control-Allow-Origin With host in your "Origin"
	 *                 header
	 * @responseheader Access-Control-Allow-Credentials true
	 * @responseheader WWW-Authenticate Basic realm="UnifiedPush Server" (only
	 *                 for 401 response)
	 *
	 * @statuscode 200 Successful update of the password.
	 * @statuscode 400 The format of the aliases request was incorrect (e.g.
	 *             missing required values).
	 * @statuscode 401 The request requires authentication.
	 */
	@POST
	@Path("/{alias}/password")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@ReturnType("org.jboss.aerogear.unifiedpush.rest.EmptyJSON")
	public Response updateAliasPassword(@PathParam("alias") String alias, PasswordContainer passwordContainer,
			@Context HttpServletRequest request) {

		// Endpoint is not protected by keycloak, we assume Bearer exists.
		// TODO - Find a way to validate current user password.
		try {
			AccessToken accessToken = BearerHelper.getTokenDataFromBearer(request).orNull();
			if (accessToken != null && accessToken.getPreferredUsername().equals(alias)) {
				ResponseBuilder response = Response.notModified();
				if (passwordContainer.isDataValid()) {
					aliasService.updateAliasePassword(extractUsername(), alias, passwordContainer.getCurrentPassword(),
							passwordContainer.getNewPassword());

					response = Response.ok(EmptyJSON.STRING);
				}

				return appendAllowOriginHeader(response, request);
			}

			return create401Response(request);
		} catch (Exception e) {
			logger.error("Cannot update aliases", e);
			return appendAllowOriginHeader(Response.status(Status.INTERNAL_SERVER_ERROR), request);
		}
	}

	/**************************************************************
	 * Admin API Section, protected with application id & password*
	 **************************************************************/
	/**
	 * RESTful API for alias registration. The Endpoint is protected using
	 * <code>HTTP Basic</code> (credentials
	 * <code>ApplicationID:Master Secret</code>).
	 *
	 * <pre>
	 * curl -u "ApplicaitonId:Master Secret"
	 *   -v -H "Accept: application/json" -H "Content-type: application/json"
	 *   -X POST
	 *   -d '{
	 *     "id" : "Optional - Time-based UUIDs",
	 *     "pushApplicationId" : "Push Application ID",
	 *     "email" : "Unique email address",
	 *     "other" : "Optional - Phone number / Any other alias name"
	 *   }'
	 *   https://SERVER:PORT/context/rest/alias?oauth2=true&synchronously=true
	 * </pre>
	 *
	 * Details about JSON format can be found HERE!
	 *
	 * @param alias
	 *            {@link Alias} for registration
	 * @param oauth2
	 *            Also create identity provider (keycloak) user.
	 * @param synchronously
	 *            Synchronously request - Default true.
	 * @param request
	 *            the request object
	 *
	 * @return Registered {@link Alias}
	 *
	 * @responseheader Access-Control-Allow-Origin With host in your "Origin"
	 *                 header
	 * @responseheader Access-Control-Allow-Credentials true
	 * @responseheader WWW-Authenticate Basic realm="AeroBase UnifiedPush
	 *                 Server" (only for 401 response)
	 *
	 * @statuscode 200 Successful storage of the alias.
	 * @statuscode 400 The format of the client request was incorrect (e.g.
	 *             missing required values)
	 * @statuscode 401 The request requires authentication
	 */
	@POST
	@PUT
	@Path("/")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@ReturnType("org.jboss.aerogear.unifiedpush.api.Alias")
	public Response create(Alias alias, @QueryParam("oauth2") @DefaultValue("false") boolean oauth2,
			@DefaultValue("true") @QueryParam("synchronously") boolean synchronously,
			@Context HttpServletRequest request) {

		try {
			final PushApplication pushApplication = PushAppAuthHelper.loadPushApplicationWhenAuthorized(request,
					pushAppService);
			if (pushApplication == null) {
				return Response.status(Status.UNAUTHORIZED)
						.header("WWW-Authenticate", "Basic realm=\"AeroBase UnifiedPush Server\"")
						.entity("Unauthorized Request").build();
			}

			// Support synchronously mode by default
			if (synchronously)
				aliasService.create(extractUsername(), alias);
			else
				aliasService.createAsynchronous(extractUsername(), alias);

			return appendAllowOriginHeader(Response.ok(alias), request);
		} catch (AliasAlreadyExists e) {
			return appendAllowOriginHeader(Response.status(Status.BAD_REQUEST).entity(e.getMessage()), request);
		} catch (Exception e) {
			logger.error("Cannot create alias", e);
			return appendAllowOriginHeader(Response.status(Status.INTERNAL_SERVER_ERROR), request);
		}
	}

	/**
	 * RESTful API for register aliases of the push application. The Endpoint is
	 * protected using <code>HTTP Basic</code> (credentials
	 * <code>ApplicationID:master secret</code>).
	 *
	 * <pre>
	 * curl -u "ApplicationID:secret"
	 *   -v -H "Accept: application/json" -H "Content-type: application/json"
	 *   -X POST
	 *   -d '[
	 *     {
	 *       "id" : "Time-based UUIDs",
	 *       "pushApplicationId" : "Push Application ID",
	 *       "email" : "Unique email address",
	 *       "other" : "Phone number / Any other alias name"
	 *     },
	 *     {
	 *       "id" : "Time-based UUIDs",
	 *       "pushApplicationId" : "Push Application ID",
	 *       "email" : "Unique email address",
	 *       "other" : "Phone number / Any other alias name"
	 *     }
	 *
	 *   ]'
	 *   https://SERVER:PORT/context/rest/alias?oauth2=true
	 * </pre>
	 *
	 * Details about JSON format can be found HERE!
	 *
	 * @param aliases
	 *            List of {@link Alias} related to push application
	 * @param oauth2
	 *            Also create identity provider (keycloak) user.
	 * @return {@link java.util.List<Alias>}
	 *
	 * @responseheader Access-Control-Allow-Origin With host in your "Origin"
	 *                 header
	 * @responseheader Access-Control-Allow-Credentials true
	 * @responseheader WWW-Authenticate Basic realm="UnifiedPush Server" (only
	 *                 for 401 response)
	 *
	 * @statuscode 200 Successful storage of the aliases.
	 * @statuscode 400 The format of the aliases request was incorrect (e.g.
	 *             missing required values).
	 * @statuscode 401 The request requires authentication.
	 */
	@POST
	@PUT
	@Path("/all")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@ReturnType("java.util.List<org.jboss.aerogear.unifiedpush.api.Alias>")
	public Response addAll(List<Alias> aliases, @QueryParam("oauth2") @DefaultValue("false") boolean oauth2,
			@Context HttpServletRequest request) {
		final PushApplication pushApplication = PushAppAuthHelper.loadPushApplicationWhenAuthorized(request,
				pushAppService);
		if (pushApplication == null) {
			return Response.status(Status.UNAUTHORIZED)
					.header("WWW-Authenticate", "Basic realm=\"AeroBase UnifiedPush Server\"")
					.entity("Unauthorized Request").build();
		}

		try {
			List<Alias> aliaesList = aliasService.addAll(extractUsername(), pushApplication, aliases, oauth2);
			return Response.ok(aliaesList).build();
		} catch (ServiceConstraintViolationException e) {
			logger.warn("ConstraintViolationException, alias {} already exists in db.", e.getEntityId());
			return Response.status(Status.INTERNAL_SERVER_ERROR)
					.entity(quote("Error, alias " + e.getEntityId() + " already exists in db.")).build();
		} catch (Exception e) {
			logger.error("Cannot update aliases, {}", e.getCause());
			return Response.status(Status.INTERNAL_SERVER_ERROR).build();
		}
	}

	/**
	 * RESTful API for query alias. The Endpoint is protected using
	 * <code>HTTP Basic</code> (credentials
	 * <code>ApplicationID:Master Secret</code>).
	 *
	 * @param alias
	 *            The alias name.
	 * @return {@link Alias} registered with application
	 *
	 * @responseheader Access-Control-Allow-Origin With host in your "Origin"
	 *                 header
	 * @responseheader Access-Control-Allow-Credentials true
	 * @responseheader WWW-Authenticate Basic realm="UnifiedPush Server" (only
	 *                 for 401 response)
	 *
	 * @statuscode 200 Successful query of the alias.
	 * @statuscode 400 The format of the aliases request was incorrect (e.g.
	 *             missing required values).
	 * @statuscode 401 The request requires authentication.
	 */
	@GET
	@Path("/name/{alias}")
	@Produces(MediaType.APPLICATION_JSON)
	@ReturnType("org.jboss.aerogear.unifiedpush.api.Alias")
	public Response findByAlias(@PathParam("alias") String alias, @Context HttpServletRequest request) {
		try {
			final PushApplication pushApplication = PushAppAuthHelper.loadPushApplicationWhenAuthorized(request,
					pushAppService);
			if (pushApplication == null) {
				return Response.status(Status.UNAUTHORIZED)
						.header("WWW-Authenticate", "Basic realm=\"AeroBase UnifiedPush Server\"")
						.entity("Unauthorized Request").build();
			}

			return Response.ok(aliasService.find(pushApplication.getPushApplicationID(), alias)).build();
		} catch (Exception e) {
			logger.error(String.format("Cannot find alias with alias name %s", alias), e);
			return appendAllowOriginHeader(Response.status(Status.INTERNAL_SERVER_ERROR), request);
		}

	}

	/**
	 * RESTful API for query alias. The Endpoint is protected using
	 * <code>HTTP Basic</code> (credentials
	 * <code>ApplicationID:Master Secret</code>).
	 *
	 * @param id
	 *            The alias UUID
	 * @return {@link Alias} registered with application
	 *
	 * @responseheader Access-Control-Allow-Origin With host in your "Origin"
	 *                 header
	 * @responseheader Access-Control-Allow-Credentials true
	 * @responseheader WWW-Authenticate Basic realm="UnifiedPush Server" (only
	 *                 for 401 response)
	 *
	 * @statuscode 200 Successful query of the alias.
	 * @statuscode 400 The format of the aliases request was incorrect (e.g.
	 *             missing required values).
	 * @statuscode 401 The request requires authentication.
	 */
	@GET
	@Path("/{id}")
	@Produces(MediaType.APPLICATION_JSON)
	@ReturnType("org.jboss.aerogear.unifiedpush.api.Alias")
	public Response get(@PathParam("id") String id, @Context HttpServletRequest request) {
		try {
			final PushApplication pushApplication = PushAppAuthHelper.loadPushApplicationWhenAuthorized(request,
					pushAppService);
			if (pushApplication == null) {
				return Response.status(Status.UNAUTHORIZED)
						.header("WWW-Authenticate", "Basic realm=\"AeroBase UnifiedPush Server\"")
						.entity("Unauthorized Request").build();
			}

			return Response.ok(aliasService.find(UUID.fromString(pushApplication.getPushApplicationID()), //
					UUID.fromString(id))).build();
		} catch (Exception e) {
			logger.error(String.format("Cannot find alias with alias id %s", id), e);
			return appendAllowOriginHeader(Response.status(Status.INTERNAL_SERVER_ERROR), request);
		}

	}

	/**
	 * RESTful API for delete alias. The Endpoint is protected using
	 * <code>HTTP Basic</code> (credentials
	 * <code>ApplicationID:Master Secret</code>).
	 *
	 * @param id
	 *            The alias UUID.
	 * @return {@link EmptyJSON}
	 *
	 * @responseheader Access-Control-Allow-Origin With host in your "Origin"
	 *                 header
	 * @responseheader Access-Control-Allow-Credentials true
	 * @responseheader WWW-Authenticate Basic realm="UnifiedPush Server" (only
	 *                 for 401 response)
	 *
	 * @statuscode 200 Successful delete of the alias.
	 * @statuscode 400 The format of the aliases request was incorrect (e.g.
	 *             missing required values).
	 * @statuscode 401 The request requires authentication.
	 */
	@DELETE
	@Path("/{id}")
	@Produces(MediaType.APPLICATION_JSON)
	@ReturnType("org.jboss.aerogear.unifiedpush.rest.EmptyJSON")
	public Response delete(@PathParam("id") String id, @Context HttpServletRequest request) {
		try {
			final PushApplication pushApplication = PushAppAuthHelper.loadPushApplicationWhenAuthorized(request,
					pushAppService);
			if (pushApplication == null) {
				return Response.status(Status.UNAUTHORIZED)
						.header("WWW-Authenticate", "Basic realm=\"AeroBase UnifiedPush Server\"")
						.entity("Unauthorized Request").build();
			}

			aliasService.remove(extractUsername(), UUID.fromString(pushApplication.getPushApplicationID()), //
					UUID.fromString(id));

			return Response.ok().build();
		} catch (Exception e) {
			logger.error(String.format("Cannot delete alias by alias id %s", id), e);
			return appendAllowOriginHeader(Response.status(Status.INTERNAL_SERVER_ERROR), request);
		}
	}

	/**
	 * RESTful API for delete alias. The Endpoint is protected using
	 * <code>HTTP Basic</code> (credentials
	 * <code>ApplicationID:Master Secret</code>).
	 *
	 * @param alias
	 *            The alias name.
	 * @return {@link EmptyJSON}
	 *
	 * @responseheader Access-Control-Allow-Origin With host in your "Origin"
	 *                 header
	 * @responseheader Access-Control-Allow-Credentials true
	 * @responseheader WWW-Authenticate Basic realm="UnifiedPush Server" (only
	 *                 for 401 response)
	 *
	 * @statuscode 200 Successful delete of the alias.
	 * @statuscode 400 The format of the aliases request was incorrect (e.g.
	 *             missing required values).
	 * @statuscode 401 The request requires authentication.
	 */
	@DELETE
	@Path("/name/{alias}")
	@Produces(MediaType.APPLICATION_JSON)
	@ReturnType("org.jboss.aerogear.unifiedpush.rest.EmptyJSON")
	public Response deleteByName(@PathParam("alias") String alias, @Context HttpServletRequest request) {
		try {
			final PushApplication pushApplication = PushAppAuthHelper.loadPushApplicationWhenAuthorized(request,
					pushAppService);
			if (pushApplication == null) {
				return Response.status(Status.UNAUTHORIZED)
						.header("WWW-Authenticate", "Basic realm=\"AeroBase UnifiedPush Server\"")
						.entity("Unauthorized Request").build();
			}

			aliasService.remove(extractUsername(), UUID.fromString(pushApplication.getPushApplicationID()), //
					alias);

			return Response.ok().build();
		} catch (Exception e) {
			logger.error(String.format("Cannot delete alias by alias name %s", alias), e);
			return appendAllowOriginHeader(Response.status(Status.INTERNAL_SERVER_ERROR), request);
		}
	}

	/**
	 * RESTful API for delete alias. in addition existing documents and identity
	 * user. The Endpoint is protected using <code>HTTP Basic</code>
	 * (credentials <code>ApplicationID:Master Secret</code>).
	 *
	 * @param id
	 *            The alias UUID
	 * @return {@link EmptyJSON}
	 *
	 * @responseheader Access-Control-Allow-Origin With host in your "Origin"
	 *                 header
	 * @responseheader Access-Control-Allow-Credentials true
	 * @responseheader WWW-Authenticate Basic realm="UnifiedPush Server" (only
	 *                 for 401 response)
	 *
	 * @statuscode 200 Successful delete of the alias.
	 * @statuscode 400 The format of the aliases request was incorrect (e.g.
	 *             missing required values).
	 * @statuscode 401 The request requires authentication.
	 */
	@DELETE
	@Path("/hard/{id}")
	@Produces(MediaType.APPLICATION_JSON)
	@ReturnType("org.jboss.aerogear.unifiedpush.rest.EmptyJSON")
	public Response deleteDestructive(@PathParam("id") String id, @Context HttpServletRequest request) {
		try {
			final PushApplication pushApplication = PushAppAuthHelper.loadPushApplicationWhenAuthorized(request,
					pushAppService);
			if (pushApplication == null) {
				return Response.status(Status.UNAUTHORIZED)
						.header("WWW-Authenticate", "Basic realm=\"AeroBase UnifiedPush Server\"")
						.entity("Unauthorized Request").build();
			}

			aliasService.remove(extractUsername(), UUID.fromString(pushApplication.getPushApplicationID()), UUID.fromString(id), true);

			return Response.ok().build();
		} catch (Exception e) {
			logger.error(String.format("Cannot destructively delete alias by alias id %s", id), e);
			return appendAllowOriginHeader(Response.status(Status.INTERNAL_SERVER_ERROR), request);
		}
	}
}