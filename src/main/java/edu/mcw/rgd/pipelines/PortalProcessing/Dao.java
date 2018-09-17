package edu.mcw.rgd.pipelines.PortalProcessing;

import edu.mcw.rgd.dao.impl.AnnotationDAO;
import edu.mcw.rgd.dao.impl.MapDAO;
import edu.mcw.rgd.dao.impl.OntologyXDAO;
import edu.mcw.rgd.dao.impl.PortalDAO;
import edu.mcw.rgd.datamodel.*;
import edu.mcw.rgd.datamodel.ontology.Annotation;
import edu.mcw.rgd.datamodel.ontologyx.Term;
import edu.mcw.rgd.process.Utils;
import edu.mcw.rgd.process.mapping.MapManager;

import java.util.*;

/**
 * @author mtutaj
 * @since 12/27/11
 * all dao code is here - usually wrapper for rgdcore methods
 */
public class Dao {

    private AnnotationDAO annotDAO = new AnnotationDAO();
    private MapDAO mapDAO = new MapDAO();
    private OntologyXDAO ontologyDAO = new OntologyXDAO();
    private PortalDAO portalDAO = new PortalDAO();

    /**
     * get a portal given url name; null if there is no portal with such an url
     * @param urlName url name
     * @return Portal object or null
     * @throws Exception when unexpected error in spring framework occurs
     */
    public Portal getPortalByUrl(String urlName) throws Exception {
        return portalDAO.getPortalByUrl(urlName);
    }

    /**
     * insert a new portal version for given portal
     * @param portalKey portal key
     * @param currentBuildNum portal build number
     * @return PortalVersion object
     * @throws Exception when unexpected error in spring framework occurs
     */
    public PortalVersion createPortalVersion( int portalKey, String currentBuildNum ) throws Exception {

        return portalDAO.insertPortalVersion(portalKey, Long.parseLong(currentBuildNum));
    }

    /**
     * update a portal version for given portal; date_last_updated will be set to SYSDATE
     * @param pver PortalVersion object
     * @return count of rows affected
     * @throws Exception when unexpected error in spring framework occurs
     */
    public int updatePortalVersion(PortalVersion pver) throws Exception {

        return portalDAO.updatePortalVersion(pver);
    }

    /**
     * Mark last portals as archived and new one as active
     * @param portalVer PortalVersion object that must be made active; all other versions must be made archived
     * @return count of portals made as archived
     * @throws Exception when unexpected error in spring framework occurs
     */
    public int archiveOldPortalVersions( PortalVersion portalVer ) throws Exception {

        return portalDAO.archiveOldPortalVersions(portalVer);
    }

    /**
     * get a list of top level terms (top level term set) for given portal
     * @param portalKey portal key
     * @return List of PortalTermSet objects
     * @throws Exception when unexpected error in spring framework occurs
     */
    public List<PortalTermSet> getTopLevelTermSetIds(int portalKey) throws Exception {

        return portalDAO.getTopLevelPortalTermSet(portalKey);
    }

    /**
     * get a list of child term sets for a given top-level term set for a portal
     * @param parentTermSetId parent term set id
     * @param portalKey portal key
     * @return List of PortalTermSet objects
     * @throws Exception when unexpected error in spring framework occurs
     */
    public List<PortalTermSet> getPortalTermSet(int parentTermSetId, int portalKey) throws Exception {

        return portalDAO.getPortalTermSet(parentTermSetId, portalKey);
    }

    /**
     * insert a new portal category
     * @param cat PortalCat object to be inserted
     * @return new portal cat id
     * @throws Exception when unexpected error in spring framework occurs
     */
    public int insertPortalCat(PortalCat cat) throws Exception {
        return portalDAO.insertPortalCat(cat);
    }

    /**
     * get annotations for term identified by term accession id;
     * if 'withChildren' parameter is true, annotations for child terms are also returned;
     * you can also limit the results to given species
     * @param accId ontology term accession id
     * @param withChildren if true then gene annotations for all child terms are also returned;
     *        if false, only gene annotations for given term are returned
     * @param speciesTypeKey species type key -- if not zero, only annotations for given species are returned
     * @return list of Annotation objects
     * @throws Exception on spring framework dao failure
     */
    public List<Annotation> getAnnotations(String accId, boolean withChildren, int speciesTypeKey) throws Exception {
        return annotDAO.getAnnotations(accId, withChildren, speciesTypeKey);
    }

    public int getAnnotatedObjectCount(String accId, boolean withChildren, int speciesTypeKey) throws Exception {
        return annotDAO.getAnnotatedObjectCount(accId, withChildren, speciesTypeKey, 0);
    }

    /**
     * get list of all synonyms for given term
     * @param rgdId id
     * @return list of all GO SLIM terms for given rgd id; could be empty list
     * @throws Exception if something wrong happens in spring framework
     */
    public List<Term> getGoSlimTerms(int rgdId) throws Exception {

        // this method called thousand times in major bottleneck in the pipeline performance
        // to improve performance, we cache goslim lists
        List<Term> list = _goslimCache.get(rgdId);
        if( list==null ) {
            list = ontologyDAO.getGoSlimTerms(rgdId);
            _goslimCache.put(rgdId, list);
        }
        return list;
    }
    private Hashtable<Integer, List<Term>> _goslimCache = new Hashtable<>();

    /**
     * get active (non-obsolete) descendant (child) terms of given term, recursively
     * @param termAcc term accession id
     * @return list of descendant terms
     * @throws Exception if something wrong happens in spring framework
     */
    public List<Term> getAllActiveTermDescendants(String termAcc) throws Exception {
        return ontologyDAO.getAllActiveTermDescendants(termAcc);
    }

    public MapData getMapData(int rgdId, int speciesTypeKey) throws Exception {

        int mapKey = MapManager.getInstance().getReferenceAssembly(speciesTypeKey).getKey();
        List<MapData> mds = mapDAO.getMapData(rgdId, mapKey);
        if( mds!=null && !mds.isEmpty() ) {
            return mds.get(0);
        }
        return null;
    }

    public Object[] getMapDataCached(int rgdId, int speciesTypeKey) throws Exception {

        String key = rgdId+"|"+speciesTypeKey;
        Object[] mapDataCached = _mapDataCached.get(key);
        if( mapDataCached==null ) {
            MapData md = getMapData(rgdId, speciesTypeKey);
            if( md!=null ) {
                mapDataCached = new Object[3];
                mapDataCached[0] = md.getChromosome();
                mapDataCached[1] = md.getStartPos();
                mapDataCached[2] = md.getStopPos();
                _mapDataCached.put(key, mapDataCached);
            }
        }
        return mapDataCached;
    }
    java.util.Map<String,Object[]> _mapDataCached = new HashMap<>();
    
    /**
     # Cleanup the data in tables PORTAL_CAT1 and PORTAL_VER1 from old versions.
     #
     # Should be run after all portal processing is complete to get rid of old, no longer active,
     # versions of the data to save up space in database (if not cleaned up, it could take up
     # many gigabytes of space in the database, what will make Stacy and Kent unhappy).
     * @return count of rows affected
     * @throws Exception when unexpected error in spring framework occurs
     */
    public int cleanupOldPortalVersions() throws Exception {
        return portalDAO.cleanupOldPortalVersions();
    }

    /**
     * merge versions of two portals; active version of source portal;
     * for example active version of 'gomp' portal should be merged with 'go' portal
     * (fromUrl='gomp', toUrl='go')
     * @param fromUrl url of from portal
     * @param toUrl url of to portal
     * @return count of affected rows
     * @throws Exception when unexpected error in spring framework occurs
     */
    public int mergePortalVersions(String fromUrl, String toUrl) throws Exception {
        return portalDAO.mergePortalVersions(fromUrl, toUrl);
    }

    /**
     * delete rows from PORTAL_OBJECTS table that were not touched by the pipeline
     * @param cutoffDate cut-off date: records older than the cutoff date are deleted
     * @return count of rows affected
     * @throws Exception if something wrong happens in spring framework
     */
    int deletePortalObjects(Date cutoffDate) throws Exception {
        return portalDAO.deleteStalePortalObjects(cutoffDate);
    }

    /**
     * update modification_date to SYSDATE for a bunch of rows in PORTAL_OBJECTS table
     * @param rgdId rgd id
     * @param portalKeys list of portal keys
     * @return count of rows affected
     * @throws Exception if something wrong happens in spring framework
     */
    int updatePortalObjects(int rgdId, List<Integer> portalKeys) throws Exception {

        if( portalKeys==null || portalKeys.isEmpty() ) {
            return 0;
        }

        String sql = "UPDATE portal_objects SET modification_date=SYSDATE WHERE rgd_id=? AND portal_key in("+ Utils.buildInPhrase(portalKeys)+")";
        return portalDAO.update(sql, rgdId);
    }

    /**
     * insert a bunch of (RGD_ID,PORTAL_KEY) rows into RGD_OBJECTS table
     * @param rgdId rgd id
     * @param portalKeys list of portal keys
     * @return count of rows affected
     * @throws Exception if something wrong happens in spring framework
     */
    int insertPortalObjects(int rgdId, List<Integer> portalKeys) throws Exception {

        if( portalKeys==null || portalKeys.isEmpty() ) {
            return 0;
        }

        StringBuilder sql = new StringBuilder("INSERT ALL\n");
        for( Integer portalKey: portalKeys ) {
            sql.append("INTO portal_objects (rgd_id,portal_key) VALUES(").append(rgdId).append(",").append(portalKey).append(")\n");
        }
        sql.append("SELECT * FROM dual");

        return portalDAO.update(sql.toString());
    }

    /**
     * compute portals associated with given rgd object (genes, qtls, ...)
     * @param rgdId object rgd id (genes, qtls, ...)
     * @return list of portal keys
     * @throws Exception if something wrong happens in spring framework
     */
    public List<Integer> computePortalsForObject(int rgdId) throws Exception {

        List<Portal> portals = portalDAO.getPortalsForObject(rgdId);
        List<Integer> portalKeys = new ArrayList<>(portals.size());
        for( Portal portal: portals ) {
            portalKeys.add(portal.getKey());
        }
        return portalKeys;
    }

    /**
     * return portals associated with given rgd object (genes, qtls, ...)
     * @param rgdId object rgd id (genes, qtls, ...)
     * @return list of portal keys
     * @throws Exception if something wrong happens in spring framework
     */
    public List<Integer> getPortalsForObject(int rgdId) throws Exception {
        return portalDAO.getPortalKeysForObject(rgdId);
    }

    public Collection<String> getPortalsProcessed() throws Exception {
        Set<String> portalsProcessed = new HashSet<>();
        List<Portal> portals = portalDAO.getPortals();
        for( Portal portal: portals ) {
            if( Utils.intsAreEqual(portal.getKey(), portal.getMasterPortalKey()) &&
                    Utils.stringsAreEqual(portal.getPortalType(), "Standard") ) {
                portalsProcessed.add(portal.getUrlName());
            }
        }
        return portalsProcessed;
    }
}
