/*
 * Copyright © 2019 Cask Data, Inc.
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

package co.cask.wrangler.dataset;

import co.cask.cdap.common.conf.Constants;
import co.cask.cdap.spi.data.transaction.TransactionRunners;
import co.cask.cdap.test.SystemAppTestBase;
import co.cask.cdap.test.TestConfiguration;
import co.cask.wrangler.dataset.workspace.DataType;
import co.cask.wrangler.dataset.workspace.Workspace;
import co.cask.wrangler.dataset.workspace.WorkspaceDataset;
import co.cask.wrangler.dataset.workspace.WorkspaceMeta;
import co.cask.wrangler.dataset.workspace.WorkspaceNotFoundException;
import co.cask.wrangler.proto.Namespace;
import co.cask.wrangler.proto.NamespacedId;
import co.cask.wrangler.proto.Recipe;
import co.cask.wrangler.proto.Request;
import co.cask.wrangler.proto.RequestV1;
import co.cask.wrangler.proto.Sampling;
import co.cask.wrangler.proto.WorkspaceIdentifier;
import com.google.gson.JsonObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.Collections;
import java.util.Map;

/**
 * Tests for the workspace dataset.
 */
public class WorkspaceDatasetTest extends SystemAppTestBase {

  @ClassRule
  public static final TestConfiguration CONFIG = new TestConfiguration(Constants.Explore.EXPLORE_ENABLED, false);
  
  @Before
  public void setupTest() throws Exception {
    getStructuredTableAdmin().create(WorkspaceDataset.TABLE_SPEC);
  }

  @After
  public void cleanupTest() throws Exception {
    getStructuredTableAdmin().drop(WorkspaceDataset.TABLE_SPEC.getTableId());
  }
  
  @Test
  public void testNotFoundExceptions() throws Exception {
    getTransactionRunner().run(context -> {
      WorkspaceDataset ws = WorkspaceDataset.get(context);
      Namespace namespace = new Namespace("c0", 10L);
      try {
        ws.updateWorkspaceData(new NamespacedId(namespace, "id"), DataType.TEXT, new byte[]{0});
        Assert.fail("Updating a non-existing workspace should fail.");
      } catch (WorkspaceNotFoundException e) {
        // expected
      }

      try {
        ws.updateWorkspaceProperties(new NamespacedId(namespace, "id"), Collections.emptyMap());
        Assert.fail("Updating a non-existing workspace should fail.");
      } catch (WorkspaceNotFoundException e) {
        // expected
      }

      try {
        ws.updateWorkspaceRequest(new NamespacedId(namespace, "id"), null);
        Assert.fail("Updating a non-existing workspace should fail.");
      } catch (WorkspaceNotFoundException e) {
        // expected
      }
    });
  }

  @Test
  public void testScopes() {
    WorkspaceIdentifier id1 = new WorkspaceIdentifier("id1", "name1");
    WorkspaceIdentifier id2 = new WorkspaceIdentifier("id2", "name2");

    Namespace namespace = new Namespace("c0", 10L);

    String scope1 = "scope1";
    String scope2 = "scope2";
    WorkspaceMeta meta1 = WorkspaceMeta.builder(new NamespacedId(namespace, id1.getId()), id1.getName())
      .setScope(scope1)
      .build();
    WorkspaceMeta meta2 = WorkspaceMeta.builder(new NamespacedId(namespace, id2.getId()), id2.getName())
      .setScope(scope2)
      .build();

    run(ws -> ws.writeWorkspaceMeta(meta1));
    run(ws -> ws.writeWorkspaceMeta(meta2));

    Assert.assertEquals(Collections.singletonList(id1), call(ws -> ws.listWorkspaces(namespace, scope1)));
    Assert.assertEquals(Collections.singletonList(id2), call(ws -> ws.listWorkspaces(namespace, scope2)));

    run(ws -> ws.deleteScope(namespace, scope1));

    Assert.assertTrue(call(ws -> ws.listWorkspaces(namespace, scope1).isEmpty()));
    Assert.assertEquals(Collections.singletonList(id2), call(ws -> ws.listWorkspaces(namespace, scope2)));

    run(ws -> ws.deleteWorkspace(new NamespacedId(namespace, id2.getId())));
    Assert.assertTrue(call(ws -> ws.listWorkspaces(namespace, scope1).isEmpty()));
    Assert.assertTrue(call(ws -> ws.listWorkspaces(namespace, scope2).isEmpty()));
  }

  @Test
  public void testNamespaceIsolation() {
    NamespacedId id1 = new NamespacedId(new Namespace("n1", 10L), "id1");
    NamespacedId id2 = new NamespacedId(new Namespace("n2", 10L), "id2");

    // test writes in different namespaces don't conflict with each other
    WorkspaceMeta meta1 = WorkspaceMeta.builder(id1, "name1")
      .setType(DataType.BINARY)
      .setProperties(Collections.singletonMap("k1", "v1"))
      .build();
    run(ws -> ws.writeWorkspaceMeta(meta1));
    WorkspaceMeta meta2 = WorkspaceMeta.builder(id2, "name2")
      .setType(DataType.BINARY)
      .setProperties(Collections.singletonMap("k2", "v2"))
      .build();
    run(ws -> ws.writeWorkspaceMeta(meta2));

    Workspace actual1 = call(ws -> ws.getWorkspace(id1));
    Workspace expected1 = Workspace.builder(id1, meta1.getName())
      .setCreated(actual1.getCreated())
      .setUpdated(actual1.getUpdated())
      .setScope(meta1.getScope())
      .setType(meta1.getType())
      .setProperties(meta1.getProperties())
      .build();
    Assert.assertEquals(expected1, actual1);

    Workspace actual2 = call(ws -> ws.getWorkspace(id2));
    Workspace expected2 = Workspace.builder(id2, meta2.getName())
      .setCreated(actual2.getCreated())
      .setUpdated(actual2.getUpdated())
      .setScope(meta2.getScope())
      .setType(meta2.getType())
      .setProperties(meta2.getProperties())
      .build();
    Assert.assertEquals(expected2, actual2);

    // test lists don't include from other namespaces
    Assert.assertEquals(Collections.singletonList(new WorkspaceIdentifier(id1.getId(), meta1.getName())),
                        call(ws -> ws.listWorkspaces(id1.getNamespace(), WorkspaceDataset.DEFAULT_SCOPE)));
    Assert.assertEquals(Collections.singletonList(new WorkspaceIdentifier(id2.getId(), meta2.getName())),
                        call(ws -> ws.listWorkspaces(id2.getNamespace(), WorkspaceDataset.DEFAULT_SCOPE)));

    // test delete is within the correct namespace
    run(ws -> ws.deleteWorkspace(id2));

    Assert.assertEquals(Collections.singletonList(new WorkspaceIdentifier(id1.getId(), meta1.getName())),
                        call(ws -> ws.listWorkspaces(id1.getNamespace(), WorkspaceDataset.DEFAULT_SCOPE)));
    Assert.assertTrue(call(ws -> ws.listWorkspaces(id2.getNamespace(), WorkspaceDataset.DEFAULT_SCOPE).isEmpty()));
    Assert.assertEquals(expected1, call(ws -> ws.getWorkspace(id1)));
  }

  @Test
  public void testNamespaceGenerations() {
    Namespace nsGen1 = new Namespace("ns1", 1L);
    Namespace nsGen2 = new Namespace("ns1", 2L);

    NamespacedId id1 = new NamespacedId(nsGen1, "id0");
    // test creation in different namespaces
    WorkspaceMeta meta1 = WorkspaceMeta.builder(id1, "name1")
      .setType(DataType.BINARY)
      .setProperties(Collections.singletonMap("k1", "v1"))
      .build();
    run(ws -> ws.writeWorkspaceMeta(meta1));

    // test that fetching with a different generation doesn't include the connection
    try {
      run(ws -> ws.getWorkspace(new NamespacedId(nsGen2, id1.getId())));
      Assert.fail("workspace with a different generation should not be visible.");
    } catch (WorkspaceNotFoundException e) {
      // expected
    }

    // test that listing with a different generation doesn't include the connection
    Assert.assertTrue(call(ws -> ws.listWorkspaces(nsGen2, WorkspaceDataset.DEFAULT_SCOPE)).isEmpty());
  }

  @Test
  public void testCRUD() {
    NamespacedId id = new NamespacedId(new Namespace("c0", 10L), "id0");
    Assert.assertTrue(call(ws -> ws.listWorkspaces(id.getNamespace(), "default").isEmpty()));
    Assert.assertFalse(call(ws -> ws.hasWorkspace(id)));
    WorkspaceIdentifier workspaceId = new WorkspaceIdentifier(id.getId(), "name");

    // test write and get
    WorkspaceMeta meta1 = WorkspaceMeta.builder(id, workspaceId.getName())
      .setScope("default")
      .setType(DataType.BINARY)
      .setProperties(Collections.singletonMap("k1", "v1"))
      .build();
    run(ws -> ws.writeWorkspaceMeta(meta1));

    Workspace actual = call(ws -> ws.getWorkspace(id));
    Workspace expected = Workspace.builder(id, meta1.getName())
      .setCreated(actual.getCreated())
      .setUpdated(actual.getUpdated())
      .setType(meta1.getType())
      .setScope(meta1.getScope())
      .setProperties(meta1.getProperties())
      .build();
    Assert.assertEquals(expected, actual);
    Assert.assertEquals(Collections.singletonList(workspaceId),
                        call(ws -> ws.listWorkspaces(id.getNamespace(), "default")));

    // test updating properties
    Map<String, String> properties = Collections.singletonMap("k2", "v2");
    run(ws -> ws.updateWorkspaceProperties(id, properties));
    actual = call(ws -> ws.getWorkspace(id));
    expected = Workspace.builder(expected)
      .setUpdated(actual.getUpdated())
      .setProperties(properties)
      .build();
    Assert.assertEquals(expected, actual);

    // test updating request
    co.cask.wrangler.proto.Workspace requestWorkspace = new co.cask.wrangler.proto.Workspace(meta1.getName(), 10);
    Recipe recipe = new Recipe(Collections.singletonList("parse-as-csv body"), true, "recipeName");
    Sampling sampling = new Sampling("random", 0, 10);
    Request request = new RequestV1(requestWorkspace, recipe, sampling, new JsonObject());
    run(ws -> ws.updateWorkspaceRequest(id, request));
    actual = call(ws -> ws.getWorkspace(id));
    expected = Workspace.builder(expected)
      .setUpdated(actual.getUpdated())
      .setRequest(request)
      .build();
    Assert.assertEquals(expected, actual);

    // test updating data
    byte[] data = new byte[]{0, 1, 2};
    run(ws -> ws.updateWorkspaceData(id, DataType.RECORDS, data));
    actual = call(ws -> ws.getWorkspace(id));
    expected = Workspace.builder(expected)
      .setType(DataType.RECORDS)
      .setUpdated(actual.getUpdated())
      .setData(data)
      .build();
    Assert.assertEquals(expected, actual);

    // test updating meta
    WorkspaceMeta meta2 = WorkspaceMeta.builder(id, meta1.getName())
      .setType(DataType.TEXT)
      .setProperties(Collections.singletonMap("k3", "v3"))
      .build();
    run(ws -> ws.writeWorkspaceMeta(meta2));
    actual = call(ws -> ws.getWorkspace(id));
    expected = Workspace.builder(expected)
      .setUpdated(actual.getUpdated())
      .setProperties(meta2.getProperties())
      .setType(meta2.getType())
      .build();
    Assert.assertEquals(expected, actual);
    Assert.assertEquals(Collections.singletonList(workspaceId),
                        call(ws -> ws.listWorkspaces(id.getNamespace(), "default")));

    // delete workspace
    run(ws -> ws.deleteWorkspace(id));
    Assert.assertTrue(call(ws -> ws.listWorkspaces(id.getNamespace(), "default").isEmpty()));
    try {
      call(ws -> ws.getWorkspace(id));
      Assert.fail("Workspace was not deleted.");
    } catch (WorkspaceNotFoundException e) {
      // expected
    }
  }
  
  private <T> T call(WorkspaceCallable<T> callable) {
    return TransactionRunners.run(getTransactionRunner(), context -> {
      WorkspaceDataset ws = WorkspaceDataset.get(context);
      return callable.run(ws);
    }, WorkspaceNotFoundException.class);
  }

  private void run(WorkspaceRunnable runnable) {
    TransactionRunners.run(getTransactionRunner(), context -> {
      WorkspaceDataset ws = WorkspaceDataset.get(context);
      runnable.run(ws);
    }, WorkspaceNotFoundException.class);
  }

  private interface WorkspaceRunnable {
    void run(WorkspaceDataset ws) throws Exception;
  }

  private interface WorkspaceCallable<T> {
    T run(WorkspaceDataset ws) throws Exception;
  }
}
