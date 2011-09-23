package org.sindice.siren.search;

/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.Version;
import org.junit.Test;
import org.sindice.siren.analysis.TupleAnalyzer;
import org.sindice.siren.search.SirenMultiTermQuery.TopTermsBoostOnlySirenBooleanQueryRewrite;

/**
 * Tests {@link SirenSirenFuzzyQuery}.
 *
 */
public class TestSirenFuzzyQuery {

  @Test
  public void testFuzziness() throws Exception {
    Directory directory = new RAMDirectory();
    final IndexWriterConfig conf = new IndexWriterConfig(Version.LUCENE_31, new TupleAnalyzer(new StandardAnalyzer(Version.LUCENE_31)));
    IndexWriter writer = new IndexWriter(directory, conf);
    
    addDoc("aaaaa", writer);
    addDoc("aaaab", writer);
    addDoc("aaabb", writer);
    addDoc("aabbb", writer);
    addDoc("abbbb", writer);
    addDoc("bbbbb", writer);
    addDoc("ddddd", writer);

    IndexReader reader = IndexReader.open(directory);
    IndexSearcher searcher = new IndexSearcher(directory);
    writer.close();

    SirenFuzzyQuery query = new SirenFuzzyQuery(new Term("field", "aaaaa"), SirenFuzzyQuery.defaultMinSimilarity, 0);   
    ScoreDoc[] hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(3, hits.length);
    
    // same with prefix
    query = new SirenFuzzyQuery(new Term("field", "aaaaa"), SirenFuzzyQuery.defaultMinSimilarity, 1);   
    hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(3, hits.length);
    query = new SirenFuzzyQuery(new Term("field", "aaaaa"), SirenFuzzyQuery.defaultMinSimilarity, 2);   
    hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(3, hits.length);
    query = new SirenFuzzyQuery(new Term("field", "aaaaa"), SirenFuzzyQuery.defaultMinSimilarity, 3);   
    hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(3, hits.length);
    query = new SirenFuzzyQuery(new Term("field", "aaaaa"), SirenFuzzyQuery.defaultMinSimilarity, 4);   
    hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(2, hits.length);
    query = new SirenFuzzyQuery(new Term("field", "aaaaa"), SirenFuzzyQuery.defaultMinSimilarity, 5);   
    hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(1, hits.length);
    query = new SirenFuzzyQuery(new Term("field", "aaaaa"), SirenFuzzyQuery.defaultMinSimilarity, 6);   
    hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(1, hits.length);
    
    // test scoring
    query = new SirenFuzzyQuery(new Term("field", "bbbbb"), SirenFuzzyQuery.defaultMinSimilarity, 0);   
    hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals("3 documents should match", 3, hits.length);
    List<String> order = Arrays.asList("bbbbb","abbbb","aabbb");
    for (int i = 0; i < hits.length; i++) {
      final String term = searcher.doc(hits[i].doc).get("field");
      //System.out.println(hits[i].score);
      assertEquals(getTriple(order.get(i)), term);
    }

    // test pq size by supplying maxExpansions=2
    // This query would normally return 3 documents, because 3 terms match (see above):
    query = new SirenFuzzyQuery(new Term("field", "bbbbb"), SirenFuzzyQuery.defaultMinSimilarity, 0, 2); 
    hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals("only 2 documents should match", 2, hits.length);
    order = Arrays.asList("bbbbb","abbbb");
    for (int i = 0; i < hits.length; i++) {
      final String term = searcher.doc(hits[i].doc).get("field");
      //System.out.println(hits[i].score);
      assertEquals(getTriple(order.get(i)), term);
    }

    // not similar enough:
    query = new SirenFuzzyQuery(new Term("field", "xxxxx"), SirenFuzzyQuery.defaultMinSimilarity, 0);  	
    hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(0, hits.length);
    query = new SirenFuzzyQuery(new Term("field", "aaccc"), SirenFuzzyQuery.defaultMinSimilarity, 0);   // edit distance to "aaaaa" = 3
    hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(0, hits.length);

    // query identical to a word in the index:
    query = new SirenFuzzyQuery(new Term("field", "aaaaa"), SirenFuzzyQuery.defaultMinSimilarity, 0);   
    hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(3, hits.length);
    assertEquals(getTriple("aaaaa"), searcher.doc(hits[0].doc).get("field"));
    // default allows for up to two edits:
    assertEquals(getTriple("aaaab"), searcher.doc(hits[1].doc).get("field"));
    assertEquals(getTriple("aaabb"), searcher.doc(hits[2].doc).get("field"));

    // query similar to a word in the index:
    query = new SirenFuzzyQuery(new Term("field", "aaaac"), SirenFuzzyQuery.defaultMinSimilarity, 0);   
    hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(3, hits.length);
    assertEquals(getTriple("aaaaa"), searcher.doc(hits[0].doc).get("field"));
    assertEquals(getTriple("aaaab"), searcher.doc(hits[1].doc).get("field"));
    assertEquals(getTriple("aaabb"), searcher.doc(hits[2].doc).get("field"));
    
    // now with prefix
    query = new SirenFuzzyQuery(new Term("field", "aaaac"), SirenFuzzyQuery.defaultMinSimilarity, 1);   
    hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(3, hits.length);
    assertEquals(getTriple("aaaaa"), searcher.doc(hits[0].doc).get("field"));
    assertEquals(getTriple("aaaab"), searcher.doc(hits[1].doc).get("field"));
    assertEquals(getTriple("aaabb"), searcher.doc(hits[2].doc).get("field"));
    query = new SirenFuzzyQuery(new Term("field", "aaaac"), SirenFuzzyQuery.defaultMinSimilarity, 2);   
    hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(3, hits.length);
    assertEquals(getTriple("aaaaa"), searcher.doc(hits[0].doc).get("field"));
    assertEquals(getTriple("aaaab"), searcher.doc(hits[1].doc).get("field"));
    assertEquals(getTriple("aaabb"), searcher.doc(hits[2].doc).get("field"));
    query = new SirenFuzzyQuery(new Term("field", "aaaac"), SirenFuzzyQuery.defaultMinSimilarity, 3);   
    hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(3, hits.length);
    assertEquals(getTriple("aaaaa"), searcher.doc(hits[0].doc).get("field"));
    assertEquals(getTriple("aaaab"), searcher.doc(hits[1].doc).get("field"));
    assertEquals(getTriple("aaabb"), searcher.doc(hits[2].doc).get("field"));
    query = new SirenFuzzyQuery(new Term("field", "aaaac"), SirenFuzzyQuery.defaultMinSimilarity, 4);   
    hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(2, hits.length);
    assertEquals(getTriple("aaaaa"), searcher.doc(hits[0].doc).get("field"));
    assertEquals(getTriple("aaaab"), searcher.doc(hits[1].doc).get("field"));
    query = new SirenFuzzyQuery(new Term("field", "aaaac"), SirenFuzzyQuery.defaultMinSimilarity, 5);   
    hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(0, hits.length);
    

    query = new SirenFuzzyQuery(new Term("field", "ddddX"), SirenFuzzyQuery.defaultMinSimilarity, 0);   
    hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(1, hits.length);
    assertEquals(getTriple("ddddd"), searcher.doc(hits[0].doc).get("field"));
    
    // now with prefix
    query = new SirenFuzzyQuery(new Term("field", "ddddX"), SirenFuzzyQuery.defaultMinSimilarity, 1);   
    hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(1, hits.length);
    assertEquals(getTriple("ddddd"), searcher.doc(hits[0].doc).get("field"));
    query = new SirenFuzzyQuery(new Term("field", "ddddX"), SirenFuzzyQuery.defaultMinSimilarity, 2);   
    hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(1, hits.length);
    assertEquals(getTriple("ddddd"), searcher.doc(hits[0].doc).get("field"));
    query = new SirenFuzzyQuery(new Term("field", "ddddX"), SirenFuzzyQuery.defaultMinSimilarity, 3);   
    hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(1, hits.length);
    assertEquals(getTriple("ddddd"), searcher.doc(hits[0].doc).get("field"));
    query = new SirenFuzzyQuery(new Term("field", "ddddX"), SirenFuzzyQuery.defaultMinSimilarity, 4);   
    hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(1, hits.length);
    assertEquals(getTriple("ddddd"), searcher.doc(hits[0].doc).get("field"));
    query = new SirenFuzzyQuery(new Term("field", "ddddX"), SirenFuzzyQuery.defaultMinSimilarity, 5);   
    hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(0, hits.length);
    

    // different field = no match:
    query = new SirenFuzzyQuery(new Term("anotherfield", "ddddX"), SirenFuzzyQuery.defaultMinSimilarity, 0);   
    hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(0, hits.length);

    searcher.close();
    reader.close();
    directory.close();
  }

  @Test
  public void testFuzzinessLong() throws Exception {
    Directory directory = new RAMDirectory();
    final IndexWriterConfig conf = new IndexWriterConfig(Version.LUCENE_31, new StandardAnalyzer(Version.LUCENE_31));
    IndexWriter writer = new IndexWriter(directory, conf);
    addDoc("aaaaaaa", writer);
    addDoc("segment", writer);

    IndexReader reader = IndexReader.open(directory);
    IndexSearcher searcher = new IndexSearcher(directory);
    writer.close();

    SirenFuzzyQuery query;
    // not similar enough:
    query = new SirenFuzzyQuery(new Term("field", "xxxxx"), SirenFuzzyQuery.defaultMinSimilarity, 0);   
    ScoreDoc[] hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(0, hits.length);
    // edit distance to "aaaaaaa" = 3, this matches because the string is longer than
    // in testDefaultFuzziness so a bigger difference is allowed:
    query = new SirenFuzzyQuery(new Term("field", "aaaaccc"), SirenFuzzyQuery.defaultMinSimilarity, 0);   
    hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(1, hits.length);
    assertEquals(getTriple("aaaaaaa"), searcher.doc(hits[0].doc).get("field"));
    
    // now with prefix
    query = new SirenFuzzyQuery(new Term("field", "aaaaccc"), SirenFuzzyQuery.defaultMinSimilarity, 1);   
    hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(1, hits.length);
    assertEquals(getTriple("aaaaaaa"), searcher.doc(hits[0].doc).get("field"));
    query = new SirenFuzzyQuery(new Term("field", "aaaaccc"), SirenFuzzyQuery.defaultMinSimilarity, 4);   
    hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(1, hits.length);
    assertEquals(getTriple("aaaaaaa"), searcher.doc(hits[0].doc).get("field"));
    query = new SirenFuzzyQuery(new Term("field", "aaaaccc"), SirenFuzzyQuery.defaultMinSimilarity, 5);   
    hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(0, hits.length);

    // no match, more than half of the characters is wrong:
    query = new SirenFuzzyQuery(new Term("field", "aaacccc"), SirenFuzzyQuery.defaultMinSimilarity, 0);   
    hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(0, hits.length);
    
    // now with prefix
    query = new SirenFuzzyQuery(new Term("field", "aaacccc"), SirenFuzzyQuery.defaultMinSimilarity, 2);   
    hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(0, hits.length);

    // "student" and "stellent" are indeed similar to "segment" by default:
    query = new SirenFuzzyQuery(new Term("field", "student"), SirenFuzzyQuery.defaultMinSimilarity, 0);   
    hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(1, hits.length);
    query = new SirenFuzzyQuery(new Term("field", "stellent"), SirenFuzzyQuery.defaultMinSimilarity, 0);   
    hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(1, hits.length);
    
    // now with prefix
    query = new SirenFuzzyQuery(new Term("field", "student"), SirenFuzzyQuery.defaultMinSimilarity, 1);   
    hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(1, hits.length);
    query = new SirenFuzzyQuery(new Term("field", "stellent"), SirenFuzzyQuery.defaultMinSimilarity, 1);   
    hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(1, hits.length);
    query = new SirenFuzzyQuery(new Term("field", "student"), SirenFuzzyQuery.defaultMinSimilarity, 2);   
    hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(0, hits.length);
    query = new SirenFuzzyQuery(new Term("field", "stellent"), SirenFuzzyQuery.defaultMinSimilarity, 2);   
    hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(0, hits.length);
    
    // "student" doesn't match anymore thanks to increased minimum similarity:
    query = new SirenFuzzyQuery(new Term("field", "student"), 0.6f, 0);   
    hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(0, hits.length);

    try {
      query = new SirenFuzzyQuery(new Term("field", "student"), 1.1f);
      fail("Expected IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      // expecting exception
    }
    try {
      query = new SirenFuzzyQuery(new Term("field", "student"), -0.1f);
      fail("Expected IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      // expecting exception
    }

    searcher.close();
    reader.close();
    directory.close();
  }

  @Test
  public void testTokenLengthOpt() throws IOException {
    Directory directory = new RAMDirectory();
    final IndexWriterConfig conf = new IndexWriterConfig(Version.LUCENE_31, new StandardAnalyzer(Version.LUCENE_31));
    IndexWriter writer = new IndexWriter(directory, conf);
    addDoc("12345678911", writer);
    addDoc("segment", writer);

    IndexReader reader = IndexReader.open(directory);
    IndexSearcher searcher = new IndexSearcher(directory);
    writer.close();

    Query query;
    // term not over 10 chars, so optimization shortcuts
    query = new SirenFuzzyQuery(new Term("field", "1234569"), 0.9f);
    ScoreDoc[] hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(0, hits.length);

    // 10 chars, so no optimization
    query = new SirenFuzzyQuery(new Term("field", "1234567891"), 0.9f);
    hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(0, hits.length);
    
    // over 10 chars, so no optimization
    query = new SirenFuzzyQuery(new Term("field", "12345678911"), 0.9f);
    hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(1, hits.length);

    // over 10 chars, no match
    query = new SirenFuzzyQuery(new Term("field", "sdfsdfsdfsdf"), 0.9f);
    hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(0, hits.length);
    
    searcher.close();
    reader.close();
    directory.close();
  }
  
  /** Test the {@link TopTermsBoostOnlySirenBooleanQueryRewrite} rewrite method. */
  @Test
  public void testBoostOnlyRewrite() throws Exception {
    Directory directory = new RAMDirectory();
    final IndexWriterConfig conf = new IndexWriterConfig(Version.LUCENE_31, new StandardAnalyzer(Version.LUCENE_31));
    IndexWriter writer = new IndexWriter(directory, conf);
    addDoc("Lucene", writer);
    addDoc("Lucene", writer);
    addDoc("Lucenne", writer);

    IndexReader reader = IndexReader.open(directory);
    IndexSearcher searcher = new IndexSearcher(directory);
    writer.close();
    
    SirenFuzzyQuery query = new SirenFuzzyQuery(new Term("field", "Lucene"));
    query.setRewriteMethod(new SirenMultiTermQuery.TopTermsBoostOnlySirenBooleanQueryRewrite(50));
    ScoreDoc[] hits = searcher.search(query, null, 1000).scoreDocs;
    assertEquals(3, hits.length);
    // normally, 'Lucenne' would be the first result as IDF will skew the score.
    assertEquals(getTriple("Lucene"), reader.document(hits[0].doc).get("field"));
    assertEquals(getTriple("Lucene"), reader.document(hits[1].doc).get("field"));
    assertEquals(getTriple("Lucenne"), reader.document(hits[2].doc).get("field"));
    searcher.close();
    reader.close();
    directory.close();
  }
  
  @Test
  public void testGiga() throws Exception {
    
    StandardAnalyzer analyzer = new StandardAnalyzer(Version.LUCENE_31);
    Directory directory = new RAMDirectory();
    final IndexWriterConfig conf = new IndexWriterConfig(Version.LUCENE_31, new StandardAnalyzer(Version.LUCENE_31));
    IndexWriter w = new IndexWriter(directory, conf);

    addDoc("Lucene in Action", w);
    addDoc("Lucene for Dummies", w);

    //addDoc("Giga", w);
    addDoc("Giga byte", w);

    addDoc("ManagingGigabytesManagingGigabyte", w);
    addDoc("ManagingGigabytesManagingGigabytes", w);

    addDoc("The Art of Computer Science", w);
    addDoc("J. K. Rowling", w);
    addDoc("JK Rowling", w);
    addDoc("Joanne K Roling", w);
    addDoc("Bruce Willis", w);
    addDoc("Willis bruce", w);
    addDoc("Brute willis", w);
    addDoc("B. willis", w);
    IndexReader r = IndexReader.open(directory);
    w.close();

    Query q = new QueryParser(Version.LUCENE_31, "field", analyzer).parse( "giga~0.9" );

    // 3. search
    IndexSearcher searcher = new IndexSearcher(directory);
    ScoreDoc[] hits = searcher.search(q, 10).scoreDocs;
    assertEquals(1, hits.length);
    assertEquals(getTriple("Giga byte"), searcher.doc(hits[0].doc).get("field"));
    searcher.close();
    r.close();
    directory.close();
  }

  private String getTriple(String text) {
    return "<http://fake.doc> <http://foaf.fake> \"" + text + "\" .\n";
  }
  
  private void addDoc(String text, IndexWriter writer)
  throws IOException {
    Document doc = new Document();
    doc.add(new Field("field", getTriple(text), Store.YES, Index.ANALYZED));
    writer.addDocument(doc);
    writer.commit();
  }

}
