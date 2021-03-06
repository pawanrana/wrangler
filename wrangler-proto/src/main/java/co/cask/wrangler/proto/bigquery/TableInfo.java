/*
 * Copyright © 2018 Cask Data, Inc.
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

package co.cask.wrangler.proto.bigquery;

import com.google.gson.annotations.SerializedName;

/**
 * Information about a BigQuery table.
 */
public class TableInfo {
  private final String id;
  private final String name;
  private final String description;
  private final String etag;
  private final Long created;
  @SerializedName("last-modified")
  private final Long lastModified;
  @SerializedName("expiration-time")
  private final Long expirationTime;

  public TableInfo(String id, String name, String description, String etag, Long created, Long lastModified,
                   Long expirationTime) {
    this.id = id;
    this.name = name;
    this.description = description;
    this.etag = etag;
    this.created = created;
    this.lastModified = lastModified;
    this.expirationTime = expirationTime;
  }
}
