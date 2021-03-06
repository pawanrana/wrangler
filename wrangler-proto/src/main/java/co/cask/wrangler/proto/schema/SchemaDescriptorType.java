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

package co.cask.wrangler.proto.schema;

import com.google.gson.annotations.SerializedName;

/**
 * This class {@link SchemaDescriptorType} defines types of Schema supported by the Schema registry
 */
public enum SchemaDescriptorType {
  @SerializedName("avro")
  // Represents an AVRO schema.
  AVRO,

  @SerializedName("protobuf-desc")
  // Represents a protobuf descriptor schema.
  PROTOBUF_DESC,

  @SerializedName("protobuf-binary")
  // Represents schema is of type protobuf-binary which is compiled classes on protobuf.
  PROTOBUF_BINARY,

  @SerializedName("copybook")
  // Defines copybook for COBOL EBCDIC data.
  COPYBOOK;
}
