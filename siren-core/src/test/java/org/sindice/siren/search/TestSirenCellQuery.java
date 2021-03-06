/**
 * Copyright (c) 2009-2011 National University of Ireland, Galway. All Rights Reserved.
 *
 * Project and contact information: http://www.siren.sindice.com/
 *
 * This file is part of the SIREn project.
 *
 * SIREn is a free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * SIREn is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with SIREn. If not, see <http://www.gnu.org/licenses/>.
 */
/**
 * @project siren
 * @author Renaud Delbru [ 6 Jul 2009 ]
 * @link http://renaud.delbru.fr/
 * @copyright Copyright (C) 2009 by Renaud Delbru, All rights reserved.
 */
package org.sindice.siren.search;

import java.io.IOException;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.util.LuceneTestCase;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.sindice.siren.analysis.AnyURIAnalyzer;
import org.sindice.siren.analysis.TupleAnalyzer;

public class TestSirenCellQuery extends LuceneTestCase {

  private final SirenTermQuery aaa = new SirenTermQuery(new Term(QueryTestingHelper.DEFAULT_FIELD, "aaa"));
  private final SirenTermQuery bbb = new SirenTermQuery(new Term(QueryTestingHelper.DEFAULT_FIELD, "bbb"));
  private final SirenTermQuery ccc = new SirenTermQuery(new Term(QueryTestingHelper.DEFAULT_FIELD, "ccc"));
  private final SirenTermQuery ddd = new SirenTermQuery(new Term(QueryTestingHelper.DEFAULT_FIELD, "ddd"));
  private final SirenTermQuery eee = new SirenTermQuery(new Term(QueryTestingHelper.DEFAULT_FIELD, "eee"));

  private QueryTestingHelper _helper = null;

  @Before
  public void setUp()
  throws Exception {
    super.setUp();
    _helper = new QueryTestingHelper(new TupleAnalyzer(TEST_VERSION_CURRENT,
      new StandardAnalyzer(TEST_VERSION_CURRENT),
      new AnyURIAnalyzer(TEST_VERSION_CURRENT)));
  }

  @After
  public void tearDown()
  throws Exception {
    super.tearDown();
    _helper.close();
  }

  @Test
  public void testEquality() throws Exception {
    final String fieldName = QueryTestingHelper.DEFAULT_FIELD;
    final SirenBooleanQuery bq1 = new SirenBooleanQuery();
    bq1.add(new SirenTermQuery(new Term(fieldName, "value1")), SirenBooleanClause.Occur.SHOULD);
    bq1.add(new SirenTermQuery(new Term(fieldName, "value2")), SirenBooleanClause.Occur.SHOULD);
    final SirenCellQuery cq1 = new SirenCellQuery(bq1);

    final SirenBooleanQuery bq2 = new SirenBooleanQuery();
    bq2.add(new SirenTermQuery(new Term(fieldName, "value1")), SirenBooleanClause.Occur.SHOULD);
    bq2.add(new SirenTermQuery(new Term(fieldName, "value2")), SirenBooleanClause.Occur.SHOULD);
    final SirenCellQuery cq2 = new SirenCellQuery(bq2);

    assertEquals(cq1, cq2);
  }

  @Test
  public void testUnaryClause() throws IOException {
    _helper.addDocument("\"aaa ccc\" .");
    _helper.addDocument("\"bbb\" . \"ddd eee\" . ");
    _helper.addDocument("\"ccc ccc\" . \"ccc ccc\" . ");

    SirenCellQuery q = this.toUnaryClause(aaa);
    assertEquals(1, _helper.search(q).length);

    q = this.toUnaryClause(bbb);
    assertEquals(1, _helper.search(q).length);

    q = this.toUnaryClause(ccc);
    assertEquals(2, _helper.search(q).length);

    q = this.toUnaryClause(ddd);
    assertEquals(1, _helper.search(q).length);

    q = this.toUnaryClause(eee);
    assertEquals(1, _helper.search(q).length);
  }

  @Test
  public void testUnaryClauseWithIndexConstraint()
  throws Exception {
    _helper.addDocument("\"aaa\" \"bbb\" \"ccc\" .");
    _helper.addDocument("\"ccc\" \"bbb\" \"aaa\" .");


    final SirenCellQuery q = this.toUnaryClause(aaa);
    q.setConstraint(0);
    assertEquals(1, _helper.search(q).length);
  }

  private SirenCellQuery toUnaryClause(final SirenTermQuery term) {
    final SirenBooleanQuery bq = new SirenBooleanQuery();
    bq.add(term, SirenBooleanClause.Occur.SHOULD);
    final SirenCellQuery q = new SirenCellQuery(bq);
    return q;
  }

  /**
   * <code>cell(aaa bbb ccc ddd eee)</code>
   */
  @Test
  public void testFlat() throws IOException {
    _helper.addDocument("\"aaa ccc\" .");
    _helper.addDocument("\"bbb\" . \"ddd eee\" . ");
    _helper.addDocument("\"ccc ccc\" . \"ccc ccc\" . ");

    final SirenBooleanQuery bq = new SirenBooleanQuery();
    bq.add(aaa, SirenBooleanClause.Occur.SHOULD);
    bq.add(bbb, SirenBooleanClause.Occur.SHOULD);
    bq.add(ccc, SirenBooleanClause.Occur.SHOULD);
    bq.add(ddd, SirenBooleanClause.Occur.SHOULD);
    bq.add(eee, SirenBooleanClause.Occur.SHOULD);
    final SirenCellQuery q = new SirenCellQuery(bq);

    assertEquals(3, _helper.search(q).length);
  }

  /**
   * <code>bbb cell(+ddd +eee)</code>
   */
  @Test
  public void testParenthesisMust() throws IOException {
    _helper.addDocument("\"bbb\" . \"ddd eee\" . ");

    final SirenBooleanQuery bq = new SirenBooleanQuery();
    bq.add(ddd, SirenBooleanClause.Occur.MUST);
    bq.add(eee, SirenBooleanClause.Occur.MUST);
    final SirenCellQuery nested = new SirenCellQuery(bq);
    final BooleanQuery q = new BooleanQuery();
    q.add(aaa, BooleanClause.Occur.SHOULD);
    q.add(nested, BooleanClause.Occur.SHOULD);
    assertEquals(1, _helper.search(q).length);
  }

  /**
   * <code>aaa +cell(ddd eee)</code>
   */
  @Test
  public void testParenthesisMust2() throws IOException {
    _helper.addDocument("\"bbb\" . \"ddd eee\" . ");

    final SirenBooleanQuery bq = new SirenBooleanQuery();
    bq.add(ddd, SirenBooleanClause.Occur.SHOULD);
    bq.add(ccc, SirenBooleanClause.Occur.SHOULD);
    final SirenCellQuery nested = new SirenCellQuery(bq);
    final BooleanQuery q = new BooleanQuery();
    q.add(aaa, BooleanClause.Occur.SHOULD);
    q.add(nested, BooleanClause.Occur.MUST);
    assertEquals(1, _helper.search(q).length);
  }

  /**
   * <code>cell(ddd ccc) cell(eee ccc)</code>
   */
  @Test
  public void testParenthesisShould() throws IOException {
    _helper.addDocument("\"bbb\" . \"ddd eee\" . ");

    final SirenBooleanQuery bq1 = new SirenBooleanQuery();
    bq1.add(ddd, SirenBooleanClause.Occur.SHOULD);
    bq1.add(ccc, SirenBooleanClause.Occur.SHOULD);
    final SirenCellQuery nested1 = new SirenCellQuery(bq1);
    final SirenBooleanQuery bq2 = new SirenBooleanQuery();
    bq2.add(eee, SirenBooleanClause.Occur.SHOULD);
    bq2.add(ccc, SirenBooleanClause.Occur.SHOULD);
    final SirenCellQuery nested2 = new SirenCellQuery(bq2);
    final BooleanQuery q = new BooleanQuery();
    q.add(nested1, BooleanClause.Occur.SHOULD);
    q.add(nested2, BooleanClause.Occur.SHOULD);
    assertEquals(1, _helper.search(q).length);
  }

  /**
   * <code>cell(+ddd +eee)</code>
   */
  @Test
  public void testMust() throws IOException {
    _helper.addDocument("\"eee\" . \"ddd\" . ");
    _helper.addDocument("\"bbb\" . \"ddd eee\" . ");

    final SirenBooleanQuery bq = new SirenBooleanQuery();
    bq.add(ddd, SirenBooleanClause.Occur.MUST);
    bq.add(eee, SirenBooleanClause.Occur.MUST);
    final SirenCellQuery q = new SirenCellQuery(bq);

    assertEquals(1, _helper.search(q).length);
  }
  
  /**
   * <code>cell(+ddd +eee)</code>, same tuple but not the same cell
   */
  @Test
  public void testMust2() throws IOException {
    _helper.addDocument("\"eee\" \"ddd\" . ");
    _helper.addDocument("\"bbb\" \"ddd eee\" . ");

    final SirenBooleanQuery bq = new SirenBooleanQuery();
    bq.add(ddd, SirenBooleanClause.Occur.MUST);
    bq.add(eee, SirenBooleanClause.Occur.MUST);
    final SirenCellQuery q = new SirenCellQuery(bq);

    assertEquals(1, _helper.search(q).length);
  }

  /**
   * <code>cell(+ddd eee)</code>
   */
  @Test
  public void testMustShould() throws IOException {
    _helper.addDocument("\"eee\" . \"ddd\" . ");
    _helper.addDocument("\"bbb\" . \"ddd eee\" . ");

    final SirenBooleanQuery bq = new SirenBooleanQuery();
    bq.add(ddd, SirenBooleanClause.Occur.MUST);
    bq.add(eee, SirenBooleanClause.Occur.SHOULD);
    final SirenCellQuery q = new SirenCellQuery(bq);

    assertEquals(2, _helper.search(q).length);
  }

  /**
   * <code>cell(+ddd -eee)</code>
   */
  @Test
  public void testMustMustNot() throws IOException {
    _helper.addDocument("\"eee\" . \"ddd aaa\" . ");
    _helper.addDocument("\"bbb\" . \"ddd eee\" . ");

    final SirenBooleanQuery bq = new SirenBooleanQuery();
    bq.add(ddd, SirenBooleanClause.Occur.MUST);
    bq.add(eee, SirenBooleanClause.Occur.MUST_NOT);
    final SirenCellQuery q = new SirenCellQuery(bq);

    assertEquals(1, _helper.search(q).length);
  }

  /**
   * <code>cell(eee bbb)</code>
   */
  @Test
  public void testShould() throws IOException {
    _helper.addDocument("\"eee\" . \"ddd\" . ");
    _helper.addDocument("\"bbb\" . \"ddd eee\" . ");

    final SirenBooleanQuery bq = new SirenBooleanQuery();
    bq.add(eee, SirenBooleanClause.Occur.SHOULD);
    bq.add(bbb, SirenBooleanClause.Occur.SHOULD);
    final SirenCellQuery q = new SirenCellQuery(bq);

    assertEquals(2, _helper.search(q).length);
  }

  /**
   * <code>cell(ddd -eee)</code>
   */
  @Test
  public void testShouldMustNot() throws IOException {
    _helper.addDocument("\"eee\" . \"ddd\" . ");
    _helper.addDocument("\"bbb\" . \"ddd eee\" . ");

    final SirenBooleanQuery bq = new SirenBooleanQuery();
    bq.add(ddd, SirenBooleanClause.Occur.SHOULD);
    bq.add(eee, SirenBooleanClause.Occur.MUST_NOT);
    final SirenCellQuery q = new SirenCellQuery(bq);

    assertEquals(1, _helper.search(q).length);
  }

  /**
   * SRN-99
   * <code>cell(+(aaa bbb) +(ccc ddd))</code>
   */
  @Test
  public void testReqNestedCellQuery() throws IOException {
    _helper.addDocument("\"ccc\" . \"aaa ddd\" . ");
    _helper.addDocument("\"bbb\" . \"ddd ccc\" . ");

    final SirenBooleanQuery bq1 = new SirenBooleanQuery();
    bq1.add(aaa, SirenBooleanClause.Occur.SHOULD);
    bq1.add(bbb, SirenBooleanClause.Occur.SHOULD);

    final SirenBooleanQuery bq2 = new SirenBooleanQuery();
    bq2.add(ccc, SirenBooleanClause.Occur.SHOULD);
    bq2.add(ddd, SirenBooleanClause.Occur.SHOULD);

    final SirenBooleanQuery nested = new SirenBooleanQuery();
    nested.add(bq1, SirenBooleanClause.Occur.MUST);
    nested.add(bq2, SirenBooleanClause.Occur.MUST);

    final SirenCellQuery q = new SirenCellQuery(nested);

    assertEquals(1, _helper.search(q).length);
  }

  /**
   * SRN-99
   * <code>cell(+(aaa bbb) -(ccc ddd))</code>
   */
  @Test
  public void testReqExclNestedCellQuery() throws IOException {
    _helper.addDocument("\"ccc\" . \"aaa ddd\" . ");
    _helper.addDocument("\"bbb\" . \"ddd ccc\" . ");

    final SirenBooleanQuery bq1 = new SirenBooleanQuery();
    bq1.add(aaa, SirenBooleanClause.Occur.SHOULD);
    bq1.add(bbb, SirenBooleanClause.Occur.SHOULD);

    final SirenBooleanQuery bq2 = new SirenBooleanQuery();
    bq2.add(ccc, SirenBooleanClause.Occur.SHOULD);
    bq2.add(ddd, SirenBooleanClause.Occur.SHOULD);

    final SirenBooleanQuery nested = new SirenBooleanQuery();
    nested.add(bq1, SirenBooleanClause.Occur.MUST);
    nested.add(bq2, SirenBooleanClause.Occur.MUST_NOT);

    final SirenCellQuery q = new SirenCellQuery(nested);

    assertEquals(1, _helper.search(q).length);
  }

  /**
   * SRN-99
   * <code>cell(+(aaa bbb) (ccc ddd))</code>
   */
  @Test
  public void testReqOptNestedCellQuery() throws IOException {
    _helper.addDocument("\"ccc\" . \"aaa ddd\" . ");
    _helper.addDocument("\"bbb\" . \"ddd ccc\" . ");

    final SirenBooleanQuery bq1 = new SirenBooleanQuery();
    bq1.add(aaa, SirenBooleanClause.Occur.SHOULD);
    bq1.add(bbb, SirenBooleanClause.Occur.SHOULD);

    final SirenBooleanQuery bq2 = new SirenBooleanQuery();
    bq2.add(ccc, SirenBooleanClause.Occur.SHOULD);
    bq2.add(ddd, SirenBooleanClause.Occur.SHOULD);

    final SirenBooleanQuery nested = new SirenBooleanQuery();
    nested.add(bq1, SirenBooleanClause.Occur.MUST);
    nested.add(bq2, SirenBooleanClause.Occur.SHOULD);

    final SirenCellQuery q = new SirenCellQuery(nested);

    final ScoreDoc[] result = _helper.search(q);
    assertEquals(2, result.length);
    // The first document should get a better score
    assertEquals(0, result[0].doc);
    Assert.assertTrue(result[0].score > result[1].score);
  }

}
