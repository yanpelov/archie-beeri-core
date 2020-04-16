package org.hilel14.archie.beeri.core.migration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrInputDocument;
import org.hilel14.archie.beeri.core.jobs.tools.DcCollectionsTool;

/**
 * Migrate Solr schema from old version. Notable changes: collections replace
 * ISAD hierarchy, Yomonim changed from dcType to collection, new field:
 * access-rights. Also make the new schema more consistent.
 *
 * @author hilel14
 */
public class SchemaMigration {

    static final Logger LOGGER = LoggerFactory.getLogger(SchemaMigration.class);
    private final Set<String> collections = new HashSet<>();

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Mandatory arguments: source-collection target-collection");
            System.out.println("Example: beeri_old archie_beeri");
            System.out.println("");
            System.exit(1);
        }
        SchemaMigration app = new SchemaMigration();
        try {
            app.copyDocuments(args[0], args[1]);
            Set<String> collections = app.getCollections();
            List<String> list = new ArrayList<>(collections);
            Collections.sort(list);
            Path outFile = Paths.get(System.getProperty("java.io.tmpdir")).resolve("collections.txt");
            Files.write(outFile, list);
            LOGGER.info("The operation completed successfully. {} general collections found. Report saved in {}", collections.size(), outFile);
        } catch (Exception ex) {
            LOGGER.error(null, ex);
        }
    }

    public void copyDocuments(String sourceCollection, String targetCollection) throws Exception {
        String sourceUrl = "http://localhost:8983/solr/" + sourceCollection;
        String targetUrl = "http://localhost:8983/solr/" + targetCollection;
        LOGGER.info("Migrating from {} to {}", sourceUrl, targetUrl);
        try (
                SolrClient sourceClient = new HttpSolrClient.Builder(sourceUrl).build();
                SolrClient targetClient = new HttpSolrClient.Builder(targetUrl).build();) {
            SolrDocument sourceDoc;
            SolrInputDocument targetDoc;
            SolrQuery query = new SolrQuery();
            query.setQuery("*:*");
            query.setFields("id", "dc_creator", "dc_date", "dc_description",
                    "dc_format", "dc_title", "dc_type",
                    "file_digest", "isad_fonds", "isad_series", "isad_file",
                    "storage_location", "timestamp", "content");
            query.setRows(Integer.MAX_VALUE);
            QueryResponse response = sourceClient.query(query);
            SolrDocumentList results = response.getResults();
            LOGGER.info("{} documents found", results.size());
            for (int i = 0; i < results.size(); ++i) {
                sourceDoc = results.get(i);
                targetDoc = new SolrInputDocument();
                //--
                targetDoc.addField("id", sourceDoc.getFieldValue("id"));
                targetDoc.addField("dcCreator", sourceDoc.getFieldValue("dc_creator"));
                targetDoc.addField("dcDate", sourceDoc.getFieldValue("dc_date"));
                targetDoc.addField("dcDescription", sourceDoc.getFieldValue("dc_description"));
                targetDoc.addField("dcFormat", sourceDoc.getFieldValue("dc_format"));
                targetDoc.addField("dcTitle", sourceDoc.getFieldValue("dc_title"));
                targetDoc.addField("fileDigest", sourceDoc.getFieldValue("file_digest"));
                targetDoc.addField("importTime", sourceDoc.getFieldValue("timestamp"));
                targetDoc.addField("content", sourceDoc.getFieldValue("content"));
                targetDoc.addField("storageLocation", sourceDoc.getFieldValue("storage_location"));

                targetDoc.addField("dcAccessRights",
                        sourceDoc.getFieldValue("dcAccessRights") == null
                        ? "private"
                        : sourceDoc.getFieldValue("dcAccessRights"));

                String dcType = getFieldValue(sourceDoc.getFieldValue("dc_type"));
                targetDoc.addField("dcType", dcType.equals("yomon") ? "text" : dcType);

                processCollections(sourceDoc, targetDoc);

                try {
                    targetClient.add(targetDoc);
                } catch (Exception ex) {
                    LOGGER.error(null, ex);
                    System.exit(1);
                }
            }
            targetClient.commit();
        }
    }

    private void processCollections(SolrDocument sourceDoc, SolrInputDocument targetDoc) {
        String fonds = getFieldValue(sourceDoc.getFieldValue("isad_fonds"));
        String series = getFieldValue(sourceDoc.getFieldValue("isad_series"));
        String file = getFieldValue(sourceDoc.getFieldValue("isad_file"));
        String dcType = getFieldValue(sourceDoc.getFieldValue("dc_type"));
        String collection;
        // yomon
        if (dcType.equals("yomon")) {
            targetDoc.addField("dcIsPartOf", "עיתונות מקומית >> יומון בארי");
            return;
        }
        switch (fonds) {
            case "אולפן וידאו":
            case "אולפן בארי":
            case "אולפן סרטי מקור 2":
                targetDoc.addField("dcIsPartOf", "אולפן וידאו");
                break;
            case "אוספים פרטיים של חברים":
                collection = "אוספי חברים";
                switch (series) {
                    case "אוסף מוטק'ה מנור":
                    case "אוסף מוטקה מנור":
                    case "אוסףמוטק'ה מנור":
                        targetDoc.addField("dcIsPartOf", collection + DcCollectionsTool.SEPARATOR + "מוטק'ה מנור");
                        break;
                    case "אוסף משפחת אליהו גת":
                    case "ממשפחת אליהו גת":
                        targetDoc.addField("dcIsPartOf", collection + DcCollectionsTool.SEPARATOR + "אליהו ורותה גת");
                        break;
                    case "אוסף של אתי מורדו":
                        targetDoc.addField("dcIsPartOf", collection + DcCollectionsTool.SEPARATOR + "אתי מורדו");
                }
                break;
            case "מפות ותצא":
            case "תצלומי אויר ומפות":
                targetDoc.addField("dcIsPartOf", "מפות");
                break;
            case "עיתונות  מקומית":
            case "עיתונות מקומית":
                collection = "עיתונות מקומית";
                if (file.equals("יומן בארי")) {
                    collection = collection + DcCollectionsTool.SEPARATOR + file;
                }
                targetDoc.addField("dcIsPartOf", collection);
                break;
            case "עיתונות חוץ":
            case "עתונות חוץ":
            case "מקורות חוץ":
            case "עיתונות":
            case "תקשורת":
            case "תקשורת חוץ":
                targetDoc.addField("dcIsPartOf", "עיתונות חוץ");
                break;
            case "נוסטלגיה":
            case "תערוכת הנוסטלגיה":
                targetDoc.addField("dcIsPartOf", "תערוכת נוסטלגיה");
                break;
            case "קדמה":
                targetDoc.addField("dcIsPartOf", "קיבוץ קדמה");
                break;
            case "תקשורת, פייסבוק":
                targetDoc.addField("dcIsPartOf", "רשתות חברתיות >> פייסבוק");
                break;
            case "תקשורת דיגיטלית":
                targetDoc.addField("dcIsPartOf", "רשתות חברתיות >> מקומי");
                break;
            default:
                targetDoc.addField("dcIsPartOf", "כללי");
                collections.add(fonds);
        }
        //String collection = fonds + DcCollectionsTool.SEPARATOR + series + DcCollectionsTool.SEPARATOR + file;
    }

    private String getFieldValue(Object soursceVal) {
        return soursceVal == null ? "" : soursceVal.toString().trim().replace("\"", "");
    }

    /**
     * @return the collections
     */
    public Set<String> getCollections() {
        return collections;
    }

}
