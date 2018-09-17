package edu.mcw.rgd.pipelines.PortalProcessing;

import edu.mcw.rgd.dao.impl.GeneDAO;
import edu.mcw.rgd.dao.impl.QTLDAO;
import edu.mcw.rgd.dao.impl.SSLPDAO;
import edu.mcw.rgd.dao.impl.StrainDAO;
import edu.mcw.rgd.datamodel.Identifiable;
import edu.mcw.rgd.datamodel.SpeciesType;
import edu.mcw.rgd.pipelines.PipelineManager;
import edu.mcw.rgd.pipelines.PipelineRecord;
import edu.mcw.rgd.pipelines.RecordPreprocessor;
import edu.mcw.rgd.pipelines.RecordProcessor;

import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

/**
 * @author mtutaj
 * @since 6/8/11
 * moved from OntologyLoad pipeline in February 2012
 * <p>
 * refreshes the table PORTAL_OBJECTS -- incremental update
 * <p>
 * <h3> incremental update mechanism</h3>
 * relies on the timestamps of MODIFICATION_DATE field;
 * <ul>
 *   <li>all rows that did not change have their modification date updated to SYSDATE</li>
 *   <li>all new rows have both their creation and modification dates set to SYSDATE</li>
 *   <li>rows that are to be deleted have their modification date unchanged, so it is easy to find them</li>
 * </ul>
 */
public class PopulatePortalObjects {

    private Dao dao;
    private Logger log = Logger.getLogger("status");

    public Dao getDao() {
        return dao;
    }

    public void setDao(Dao dao) {
        this.dao = dao;
    }

    public void run() throws Exception {

        log.info("--refreshing table PORTAL_OBJECTS--");

        // save timestamp of the start
        Date startDate = new Date();

        PipelineManager manager = new PipelineManager();
        final int queueSize = 2000; // maximum number of objects in the processing queues
        // first thread group: read all objects to be refreshed
        manager.addPipelineWorkgroup(new PreProcessor(), "PP", 1, queueSize);
        // second thread group: compute mappings portal<--> object and determine db action
        manager.addPipelineWorkgroup(new QCProcessor(), "QC", 5, queueSize);
        // last thread group: execute db action
        manager.addPipelineWorkgroup(new DLProcessor(), "DL", 3, queueSize);

        // run everything
        manager.run();

        // at this point all rows that have to be inserted, are inserted
        // and all rows that have to be unchanged are unchanged
        // and modification_date for all these rows is set;
        // drop all older records
        int affectedRows = dao.deletePortalObjects(startDate);
        log.info("DELETED: "+affectedRows);

        // dump counter statistics
        manager.dumpCounters();

        log.info("--SUCCESS--");
    }

    // compute nr of annotations for every term
    class PreProcessor extends RecordPreprocessor {

        @Override
        public void process() throws Exception {

            log.info("  --collecting genes--");
            { // add genes
                GeneDAO dao = new GeneDAO();
                addObjectsToQueue(dao.getActiveGenes(SpeciesType.RAT));
                addObjectsToQueue(dao.getActiveGenes(SpeciesType.MOUSE));
                addObjectsToQueue(dao.getActiveGenes(SpeciesType.HUMAN));
            }

            log.info("  --collecting qtls--");
            { // add qtls
                QTLDAO dao = new QTLDAO();
                addObjectsToQueue(dao.getActiveQTLs(SpeciesType.RAT));
                addObjectsToQueue(dao.getActiveQTLs(SpeciesType.MOUSE));
                addObjectsToQueue(dao.getActiveQTLs(SpeciesType.HUMAN));
            }

            log.info("  --collecting sslps--");
            { // add sslps
                SSLPDAO dao = new SSLPDAO();
                addObjectsToQueue(dao.getActiveSSLPs(SpeciesType.RAT));
                addObjectsToQueue(dao.getActiveSSLPs(SpeciesType.MOUSE));
                addObjectsToQueue(dao.getActiveSSLPs(SpeciesType.HUMAN));
            }

            log.info("  --collecting strains--");
            { // add strains
                StrainDAO dao = new StrainDAO();
                addObjectsToQueue(dao.getActiveStrains());
            }
        }

        private void addObjectsToQueue( List<? extends Identifiable> objects ) throws InterruptedException {
            for( Identifiable object: objects ) {
                PortalObjectRecord rec = new PortalObjectRecord();
                rec.setRecNo(object.getRgdId());
                rec.rgdId = object.getRgdId();
                getSession().putRecordToFirstQueue(rec);
            }
        }
    }

    class QCProcessor extends RecordProcessor {

        public void process(PipelineRecord r) throws Exception {
            PortalObjectRecord rec = (PortalObjectRecord) r;

            // compute associations of given term with portals
            List<Integer> portalKeys = dao.computePortalsForObject(rec.rgdId);

            // portal keys present in rgd
            List<Integer> portalsInRgd = dao.getPortalsForObject(rec.rgdId);

            // determine which portal keys match (both present in rgd and computed by the code)
            portalsInRgd.retainAll(portalKeys);
            rec.portalKeysForUpdate = portalsInRgd; // their modification date will be updated

            // determine which portal keys should be inserted
            portalKeys.removeAll(portalsInRgd);
            rec.portalKeysForInsert = portalKeys;
        }
    }

    class DLProcessor extends RecordProcessor {
        public void process(PipelineRecord r) throws Exception {
            PortalObjectRecord rec = (PortalObjectRecord) r;

            int rowsAffected = dao.updatePortalObjects(rec.rgdId, rec.portalKeysForUpdate);
            if( rowsAffected>0 )
                getSession().incrementCounter("UPDATED", rowsAffected);

            rowsAffected = dao.insertPortalObjects(rec.rgdId, rec.portalKeysForInsert);
            if( rowsAffected>0 )
                getSession().incrementCounter("INSERTED", rowsAffected);
        }
    }

    // shared structure to be passed between processing queues
    class PortalObjectRecord extends PipelineRecord {
        public int rgdId; // object rgd id
        public List<Integer> portalKeysForUpdate;
        public List<Integer> portalKeysForInsert;
    }
}

