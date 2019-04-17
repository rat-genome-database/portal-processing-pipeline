package edu.mcw.rgd.pipelines.PortalProcessing;

import edu.mcw.rgd.datamodel.RgdId;
import edu.mcw.rgd.datamodel.SpeciesType;
import edu.mcw.rgd.process.CounterPool;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

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
    private CounterPool counters;

    public Dao getDao() {
        return dao;
    }

    public void setDao(Dao dao) {
        this.dao = dao;
    }

    public void run() throws Exception {

        log.info("--refreshing table PORTAL_OBJECTS--");

        // save timestamp of the start
        Date startDate = edu.mcw.rgd.process.Utils.addDaysToDate(new Date(), -1);

        counters = new CounterPool();

        // load all the records
        List<Integer> records = loadObjectsForProcessing();

        // shuffle rows at random to ensure even load for the parallel stream processing
        Collections.shuffle(records);

        records.parallelStream().forEach( rgdId -> {

            try {
                // compute associations of given term with portals
                List<Integer> portalKeys = dao.computePortalsForObject(rgdId);

                // portal keys present in rgd
                List<Integer> portalsInRgd = dao.getPortalsForObject(rgdId);

                // determine which portal keys match (both present in rgd and computed by the code)
                portalsInRgd.retainAll(portalKeys);
                List<Integer> portalKeysForUpdate = portalsInRgd; // their modification date will be updated

                // determine which portal keys should be inserted
                portalKeys.removeAll(portalsInRgd);
                List<Integer> portalKeysForInsert = portalKeys;

                int rowsAffected = dao.updatePortalObjects(rgdId, portalKeysForUpdate);
                if (rowsAffected > 0) {
                    counters.add("UPDATED", rowsAffected);
                }

                rowsAffected = dao.insertPortalObjects(rgdId, portalKeysForInsert);
                if (rowsAffected > 0) {
                    counters.add("INSERTED", rowsAffected);
                }

                int incoming = counters.increment("INCOMING");
                if( incoming%10000==0 ) {
                    log.debug("--progress: "+incoming+" / "+records.size());
                }
            }catch(Exception e) {
                throw new RuntimeException(e);
            }
        });

        // at this point all rows that have to be inserted, are inserted
        // and all rows that have to be unchanged are unchanged
        // and modification_date for all these rows is set;
        // drop all older records
        int affectedRows = dao.deletePortalObjects(startDate);
        log.info("DELETED: "+affectedRows);

        // dump counter statistics
        counters.dumpAlphabetically();

        log.info("--SUCCESS--");
    }

    List<Integer> loadObjectsForProcessing() throws Exception {

        List<Integer> objects = new ArrayList<>(100000);

        log.debug("  --collecting genes--");
        int count1 = objects.size();
        addObjectsToQueue(objects, dao.getRgdIds(RgdId.OBJECT_KEY_GENES, SpeciesType.RAT));
        addObjectsToQueue(objects, dao.getRgdIds(RgdId.OBJECT_KEY_GENES, SpeciesType.MOUSE));
        addObjectsToQueue(objects, dao.getRgdIds(RgdId.OBJECT_KEY_GENES, SpeciesType.HUMAN));
        int count2 = objects.size();
        log.debug("    -- "+(count2-count1));

        log.debug("  --collecting qtls--");
        addObjectsToQueue(objects, dao.getRgdIds(RgdId.OBJECT_KEY_QTLS, SpeciesType.RAT));
        addObjectsToQueue(objects, dao.getRgdIds(RgdId.OBJECT_KEY_QTLS, SpeciesType.MOUSE));
        addObjectsToQueue(objects, dao.getRgdIds(RgdId.OBJECT_KEY_QTLS, SpeciesType.HUMAN));
        int count3 = objects.size();
        log.debug("    -- "+(count3-count2));

        log.debug("  --collecting sslps--");
        addObjectsToQueue(objects, dao.getRgdIds(RgdId.OBJECT_KEY_SSLPS, SpeciesType.RAT));
        addObjectsToQueue(objects, dao.getRgdIds(RgdId.OBJECT_KEY_SSLPS, SpeciesType.MOUSE));
        addObjectsToQueue(objects, dao.getRgdIds(RgdId.OBJECT_KEY_SSLPS, SpeciesType.HUMAN));
        int count4 = objects.size();
        log.debug("    -- "+(count4-count3));

        log.debug("  --collecting strains--");
        addObjectsToQueue(objects, dao.getRgdIds(RgdId.OBJECT_KEY_STRAINS, SpeciesType.RAT));
        int count5 = objects.size();
        log.debug("    -- "+(count5-count4));

        log.debug("  --objects for processing: "+objects.size());

        return objects;
    }

    void addObjectsToQueue( List<Integer> rgdIdsToProcess, List<RgdId> objects ) throws InterruptedException {
        for( RgdId id: objects ) {
            if( id.getObjectStatus().equals("ACTIVE") ) {
                rgdIdsToProcess.add(id.getRgdId());
            }
        }
    }
}
