package edu.mcw.rgd.pipelines.PortalProcessing;

import edu.mcw.rgd.process.Utils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.core.io.FileSystemResource;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * @author mtutaj
 * @since 12/22/11
 */
public class Manager {

    private Dao dao = new Dao();
    private List<String> ontologiesProcessed;
    private String version;
    private Logger log = Logger.getLogger("status");

    public static void main(String[] args) throws Exception {

        boolean processPortals = true;
        boolean processOntologies = true;
        boolean processObjects = true;

        for( String arg: args ) {
            switch(arg) {
                case "-skipPortals":
                    processPortals = false;
                    break;
                case "-skipOntologies":
                    processOntologies = false;
                    break;
                case "-skipObjects":
                    processObjects = false;
                    break;
            }
        }

        DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
        new XmlBeanDefinitionReader(bf).loadBeanDefinitions(new FileSystemResource("properties/AppConfigure.xml"));
        Manager manager = (Manager) (bf.getBean("manager"));
        try {
            manager.run(processPortals, processOntologies, processObjects);
        } catch(Exception e) {
            Utils.printStackTrace(e, manager.log);
            throw e;
        }
    }

    void run(boolean processPortals, boolean processOntologies, boolean processObjects) throws Exception {

        long startTime = System.currentTimeMillis();

        log.info(getVersion());
        log.info("   started at: "+getCurrentTimestamp());
        log.info("   " + dao.getConnectionInfo());
        log.info("");

        if( processPortals ) {
            // populate all portals
            for (String portal : dao.getPortalsProcessed()) {
                populatePortal(portal);
            }
        }

        if( processOntologies ) {
            // populate all ontologies
            for (String ontology : getOntologiesProcessed()) {
                populateOnt(ontology);
            }
        }

        if( processPortals || processOntologies ) {
            // cleanup old portal versions
            log.info("cleaning up old portal versions ...");
            int rowsAffected = dao.cleanupOldPortalVersions();
            log.info("old portal versions cleaned - deleted " + rowsAffected + " rows");
        }

        if( processOntologies ) {
            // post processing for ontology portals
            // Post process the "gomp" and "gocc" ontologies as they need to be merged into the gobp ( aka "go" ontology)
            log.info("post processing for ontology portals ...");
            int rowsAffected = dao.mergePortalVersions("gomp", "go");
            log.info("merged 'gomp' with 'go' - " + rowsAffected + " rows.");
            rowsAffected = dao.mergePortalVersions("gocc", "go");
            log.info("merged 'gocc' with 'go' - " + rowsAffected + " rows.");
        }

        if( processObjects ) {
            // update portal objects
            log.info("populating PORTAL_OBJECTS table");
            PopulatePortalObjects loader = new PopulatePortalObjects();
            loader.setDao(dao);
            loader.run();
            log.info("OK. Populated PORTAL_OBJECTS table");
        }

        log.info("");
        log.info("   finished at: "+getCurrentTimestamp());
        log.info("=== OK === elapsed: "+ Utils.formatElapsedTime(startTime, System.currentTimeMillis()));
        log.info("");
        log.info("==========");
    }

    void populatePortal(String portal) throws Exception {

        PopulatePortal pp = new PopulatePortal(portal);
        pp.setDao(dao);
        pp.runPortal();
    }

    void populateOnt(String ont) throws Exception {

        log.info("Starting ontology "+ont);

        PopulatePortal pp = new PopulatePortal(ont);
        pp.setDao(dao);
        pp.runOntology();
    }

    String getCurrentTimestamp() {
        SimpleDateFormat sdt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdt.format(new Date());
    }

    public void setOntologiesProcessed(List<String> ontologiesProcessed) {
        this.ontologiesProcessed = ontologiesProcessed;
    }

    public List<String> getOntologiesProcessed() {
        return ontologiesProcessed;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getVersion() {
        return version;
    }
}
