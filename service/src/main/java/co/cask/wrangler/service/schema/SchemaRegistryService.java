/*
 * Copyright © 2017 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package co.cask.wrangler.service.schema;

import co.cask.cdap.api.annotation.UseDataSet;
import co.cask.cdap.api.common.Bytes;
import co.cask.cdap.api.service.http.AbstractHttpServiceHandler;
import co.cask.cdap.api.service.http.HttpServiceRequest;
import co.cask.cdap.api.service.http.HttpServiceResponder;
import co.cask.wrangler.dataset.schema.SchemaDescriptorType;
import co.cask.wrangler.dataset.schema.SchemaRegistry;
import co.cask.wrangler.dataset.schema.SchemaRegistryException;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpURLConnection;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Set;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

import static co.cask.wrangler.service.ServiceUtils.error;
import static co.cask.wrangler.service.ServiceUtils.notFound;
import static co.cask.wrangler.service.ServiceUtils.sendJson;
import static co.cask.wrangler.service.ServiceUtils.success;

/**
 * This class {@link SchemaRegistryService} provides schema management service.
 */
public class SchemaRegistryService extends AbstractHttpServiceHandler {
  private static final Logger LOG = LoggerFactory.getLogger(SchemaRegistryService.class);
  public static final String SCHEMA_REGISTRY_NAME = "schema";

  @UseDataSet("schema")
  private SchemaRegistry registry;

  @PUT
  @Path("schemas")
  public void create(HttpServiceRequest request, HttpServiceResponder responder,
                     @QueryParam("id") String id, @QueryParam("name") String name,
                     @QueryParam("description") String description, @QueryParam("type") String type) {
    try {
      if (id == null || id.isEmpty()) {
        error(responder, "id field is empty. id cannot be null");
        return;
      }
      if (name == null || name.isEmpty()) {
        error(responder,"name field is empty. name cannot be null");
        return;
      }
      if (type == null || SchemaDescriptorType.fromString(type) == null) {
        error(responder, "Not valid schema type specified.");
        return;
      }
      if (description == null || description.isEmpty()) {
        error(responder, "Description cannot be empty");
        return;
      }
      registry.create(id, name, description, SchemaDescriptorType.fromString(type));
      success(responder,
                           String.format("Successfully created schema entry with id '%s', name '%s'", id, name)
      );
    } catch (IllegalArgumentException e) {
      error(responder, e.getMessage());
    } catch (SchemaRegistryException e) {
      error(responder, e.getMessage());
    }
  }

  @POST
  @Path("schemas/{id}")
  public void upload(HttpServiceRequest request, HttpServiceResponder responder,
                     @PathParam("id") String id) {

    byte[] bytes = null;
    ByteBuffer content = request.getContent();
    if (content != null && content.hasRemaining()) {
      bytes = new byte[content.remaining()];
      content.get(bytes);
    }

    if (bytes == null) {
      error(responder, "Schema does not exist.");
      return;
    }

    try {
      if (bytes != null) {
        long version = registry.add(id, bytes);
        JsonObject response = new JsonObject();
        JsonArray array = new JsonArray();
        JsonObject object = new JsonObject();
        object.addProperty("id", id);
        object.addProperty("version", version);
        array.add(object);
        response.addProperty("status", HttpURLConnection.HTTP_OK);
        response.addProperty("message", "Success");
        response.addProperty("count", array.size());
        response.add("values", array);
        sendJson(responder, HttpURLConnection.HTTP_OK, response.toString());
      }
    } catch (SchemaRegistryException e) {
      error(responder, e.getMessage());
    }
  }

  @DELETE
  @Path("schemas/{id}")
  public void delete(HttpServiceRequest request, HttpServiceResponder responder,
                     @PathParam("id") String id) {
    try {
      if (registry.hasSchema(id)) {
        notFound(responder, "Id " + id + " not found.");
        return;
      }
      registry.delete(id);
      success(responder, "Successfully deleted schema " + id);
    } catch (SchemaRegistryException e) {
      error(responder, e.getMessage());
    }
  }

  @DELETE
  @Path("schemas/{id}/versions/{version}")
  public void delete(HttpServiceRequest request, HttpServiceResponder responder,
                     @PathParam("id") String id, @PathParam("version") long version) {
    try {
      if (registry.hasSchema(id, version)) {
        notFound(responder, "Id " + id + " version " + version + " not found.");
        return;
      }
      registry.remove(id, version);
      success(responder, "Successfully deleted version '" + version + "' of schema " + id);
    } catch (SchemaRegistryException e) {
      error(responder, e.getMessage());
    }
  }

  @GET
  @Path("schemas/{id}/versions/{version}")
  public void get(HttpServiceRequest request, HttpServiceResponder responder,
                  @PathParam("id") String id, @PathParam("version") long version) {
    try {
      if (!registry.hasSchema(id, version)) {
        notFound(responder, "Id " + id + " version " + version + " not found.");
        return;
      }
      SchemaRegistry.SchemaEntry entry = registry.get(id, version);
      JsonObject response = new JsonObject();
      JsonArray array = new JsonArray();
      JsonObject object = new JsonObject();
      object.addProperty("id", id);
      object.addProperty("name", entry.getName());
      object.addProperty("version", version);
      object.addProperty("description", entry.getDescription());
      object.addProperty("type", entry.getType().getType());
      object.addProperty("current", entry.getCurrent());
      object.addProperty("specification", Bytes.toString(entry.getSpecification()));
      JsonArray versions = new JsonArray();
      Iterator<Long> it = entry.getVersions().iterator();
      while(it.hasNext()) {
        versions.add(new JsonPrimitive(it.next()));
      }
      object.add("versions", versions);
      array.add(object);
      response.addProperty("status", HttpURLConnection.HTTP_OK);
      response.addProperty("message", "Success");
      response.addProperty("count", array.size());
      response.add("values", array);
      sendJson(responder, HttpURLConnection.HTTP_OK, response.toString());
    } catch (SchemaRegistryException e) {
      error(responder, e.getMessage());
    }
  }

  @GET
  @Path("schemas/{id}")
  public void get(HttpServiceRequest request, HttpServiceResponder responder,
                  @PathParam("id") String id) {
    try {
      if (!registry.hasSchema(id)) {
        notFound(responder, "Id " + id + " not found.");
        return;
      }
      SchemaRegistry.SchemaEntry entry = registry.get(id);
      JsonObject response = new JsonObject();
      JsonArray array = new JsonArray();
      JsonObject object = new JsonObject();
      object.addProperty("id", id);
      object.addProperty("name", entry.getName());
      object.addProperty("version", entry.getCurrent());
      object.addProperty("description", entry.getDescription());
      object.addProperty("type", entry.getType().getType());
      object.addProperty("current", entry.getCurrent());
      object.addProperty("specification", Bytes.toString(entry.getSpecification()));
      JsonArray versions = new JsonArray();
      Iterator<Long> it = entry.getVersions().iterator();
      while(it.hasNext()) {
        versions.add(new JsonPrimitive(it.next()));
      }
      object.add("versions", versions);
      array.add(object);
      response.addProperty("status", HttpURLConnection.HTTP_OK);
      response.addProperty("message", "Success");
      response.addProperty("count", array.size());
      response.add("values", array);
      sendJson(responder, HttpURLConnection.HTTP_OK, response.toString());
    } catch (SchemaRegistryException e) {
      error(responder, e.getMessage());
    }
  }

  @GET
  @Path("schemas/{id}/versions")
  public void versions(HttpServiceRequest request, HttpServiceResponder responder,
                  @PathParam("id") String id) {
    try {
      Set<Long> versions = registry.getVersions(id);
      JsonObject response = new JsonObject();
      JsonArray array = new JsonArray();
      Iterator<Long> it = versions.iterator();
      while(it.hasNext()) {
        array.add(new JsonPrimitive(it.next()));
      }
      response.addProperty("status", HttpURLConnection.HTTP_OK);
      response.addProperty("message", "Success");
      response.addProperty("count", array.size());
      response.add("values", array);
      sendJson(responder, HttpURLConnection.HTTP_OK, response.toString());
    } catch (SchemaRegistryException e) {
      error(responder, e.getMessage());
    }
  }
}