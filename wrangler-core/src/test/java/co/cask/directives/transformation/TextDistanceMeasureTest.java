/*
 *  Copyright © 2017 Cask Data, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not
 *  use this file except in compliance with the License. You may obtain a copy of
 *  the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *  License for the specific language governing permissions and limitations under
 *  the License.
 */

package co.cask.directives.transformation;

import co.cask.wrangler.TestingRig;
import co.cask.wrangler.api.Row;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * Tests {@link TextDistanceMeasure}
 */
public class TextDistanceMeasureTest {
  private static final List<Row> ROWS = Arrays.asList(
    // Correct Row.
    new Row("string1", "This is an example for distance measure.").add("string2", "This test is made of works that " +
      "are similar. This is an example for distance measure."),

    // Row that has one string empty.
    new Row("string1", "This is an example for distance measure.").add("string2", ""),

    // Row that has one string as different type.
    new Row("string1", "This is an example for distance measure.").add("string2", 1L),

    // Row that has only one column.
    new Row("string1", "This is an example for distance measure.")
  );


  @Test
  public void testTextDistanceMeasure() throws Exception {
    String[] directives = new String[] {
      "text-distance cosine string1 string2 cosine",
      "text-distance euclidean string1 string2 euclidean",
      "text-distance block-distance string1 string2 block_distance",
      "text-distance identity string1 string2 identity",
      "text-distance block string1 string2 block",
      "text-distance dice string1 string2 dice",
      "text-distance jaro string1 string2 jaro",
      "text-distance longest-common-subsequence string1 string2 lcs1",
      "text-distance longest-common-substring string1 string2 lcs2",
      "text-distance overlap-cofficient string1 string2 oc",
      "text-distance damerau-levenshtein string1 string2 dl",
      "text-distance simon-white string1 string2 sw",
      "text-distance levenshtein string1 string2 levenshtein",
    };

    List<Row> results = TestingRig.execute(directives, ROWS);
    Assert.assertTrue(results.size() == 4);
    Assert.assertEquals(15, results.get(0).length());
    Assert.assertEquals(15, results.get(1).length());
    Assert.assertEquals(15, results.get(2).length());
    Assert.assertEquals(14, results.get(3).length());
  }

}
