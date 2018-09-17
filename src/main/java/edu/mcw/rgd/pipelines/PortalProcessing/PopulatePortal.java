package edu.mcw.rgd.pipelines.PortalProcessing;

import edu.mcw.rgd.datamodel.*;
import edu.mcw.rgd.datamodel.ontology.Annotation;
import edu.mcw.rgd.datamodel.ontologyx.Term;
import edu.mcw.rgd.process.Utils;
import edu.mcw.rgd.reporting.Link;
import org.apache.log4j.Logger;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author mtutaj
 * @since 12/27/11
 * populates a portal
 */
public class PopulatePortal {

    private String portal;
    private Logger log;
    private Dao dao;
    private int skippedLinesWithNullChr = 0;
    private Logger logStatus = Logger.getLogger("status");

    public PopulatePortal(String portal) {
        this.portal = portal;
    }

    public void runPortal() throws Exception {
        long startTimeSec = System.currentTimeMillis();
        skippedLinesWithNullChr = 0;

        log = Logger.getLogger(portal);

        logStatus.info("Starting portal "+portal);

        logMsg("Started "+portal+" Portal Disease Ontology Update...");
        populatePortal(portal);

        logMsg("Started "+portal+" Portal Phenotype Ontology Update...");
        populatePortal(portal + "ph");

        logMsg("Started "+portal+" Portal Pathway Ontology Update...");
        populatePortal(portal+"pw");

        logMsg("Started "+portal+" Portal Biological Process Ontology Update...");
        populatePortal(portal+"bp");

        printSummary(startTimeSec);
    }

    public void runOntology() throws Exception {
        long startTimeSec = System.currentTimeMillis();
        skippedLinesWithNullChr = 0;

        log = Logger.getLogger(portal);

        logMsg("Started "+portal+" Ontology Update...");
        populatePortal(portal);

        printSummary(startTimeSec);
    }

    private void logMsg(String msg) {
        log.info(msg);
        logStatus.info(msg);
    }

    private void printSummary(long startTimeSec) {
        logStatus.info("Finished portal "+portal+" - elapsed: "+ Utils.formatElapsedTime(startTimeSec, System.currentTimeMillis()));
        if( skippedLinesWithNullChr>0 ) {
            logStatus.info("  skipped genes with null chromosome: " + skippedLinesWithNullChr);
        }
        logStatus.info("");
    }

    void populatePortal(String portalUrl) throws Exception {

        long startTimeSec = System.currentTimeMillis();
        String currentBuildNum = new SimpleDateFormat("yyyyMMddhhmm").format(startTimeSec);
        log.debug("Using Build # " + currentBuildNum);

        Portal portal = dao.getPortalByUrl(portalUrl);
        if( portal==null ) {
            log.warn("No portal object found for "+portalUrl);
            return;
        }

	    PortalVersion portalVer = dao.createPortalVersion( portal.getKey(), currentBuildNum);

        // do processing
        AnnotRecordSet portalSet = updatePortal2( portal, portalUrl, portalVer );

        // Update entry with slim terms
        if( !portal.getPortalType().equals("Ontology") ) {
            updatePortalVersion( portalVer, portalSet.ratGenes );
        }

        archiveOldPortalVersions( portalVer );
    }

    private AnnotRecordSet updatePortal2( Portal portal, String url, PortalVersion pver ) throws Exception {

        log.debug("-------------------------- \n Processing :"+portal.getFullName()+":"+url+":"+portal.getPortalType()+"\n----------------------\n");
        log.debug("Portal version id: "+pver.getPortalVerId());

        log.debug("INFO: !ONLY NON-ZERO COUNTS ARE EMITTED!");
        log.debug("INFO: rat: annot_obj_cnt_rat");
        log.debug("INFO: rat*: annot_obj_cnt_rat_w_children");
        log.debug("INFO: mouse*: annot_obj_cnt_mouse_w_children");
        log.debug("INFO: human*: annot_obj_cnt_human_w_children");

        // all annotations merged for all top-level termsets
        AnnotRecordSet annotsForPortal = new AnnotRecordSet();

        int catCount = 0;

        Integer parentCatId;
        List<PortalTermSet> parentTopLevelTermSetIDArray = dao.getTopLevelTermSetIds(portal.getKey());
	    // Loop through all top level terms for this portal
	    for( PortalTermSet childTermSet: parentTopLevelTermSetIDArray ) {

            parentCatId = 0; // must be set to 0 for top-level term sets
		    AnnotRecordSet annotsForTermSet = populatePortalCatTable(childTermSet, pver.getPortalVerId(), parentCatId, portal.getPortalType());
            annotsForPortal.merge(annotsForTermSet);
            catCount++;

            printCounts("Top-level termset "+childTermSet.getTermAcc(), annotsForTermSet.cat);

            // process children of top level term sets
            parentCatId = annotsForTermSet.cat.getPortalCatId(); // must be set to top-level portal cat id

            List<PortalTermSet> grandchildTermSets;
            if( portal.getPortalType().equals("Ontology") )
                grandchildTermSets = getChildTermsFor(childTermSet.getTermAcc(), portal.getKey());
            else
                grandchildTermSets = dao.getPortalTermSet(childTermSet.getPortalTermSetId(), portal.getKey());

            for( PortalTermSet grandchildTermSet: grandchildTermSets ) {

                annotsForTermSet = populatePortalCatTable(grandchildTermSet, pver.getPortalVerId(), parentCatId, portal.getPortalType());
                catCount++;

                printCounts("  Grandchild termset "+grandchildTermSet.getTermAcc(), annotsForTermSet.cat);
            }
        }

	    // Create top level category leaving XML and other data empty until the end when we have a full result set
        log.debug("creating top-level category ...");
        PortalTermSet rootTermSet = new PortalTermSet();
        rootTermSet.setOntTermName(portal.getFullName());
        parentCatId = null; // must be set to null for top level category
        PortalCat cat = saveCategoriesToDatabase(annotsForPortal, rootTermSet, pver.getPortalVerId(), parentCatId, portal.getPortalType());
        printCounts("top-level category created", cat);
        catCount++;

        logMsg("  rows written to PORTAL_CAT1 table: "+catCount);
        return annotsForPortal;
    }

    private void printCounts(String label, PortalCat cat) {
        String msg = "";
        if( cat.getAnnotObjCntRat()!=0 )
            msg += " rat="+cat.getAnnotObjCntRat();
        if( cat.getAnnotObjCntWithChildrenRat()!=0 )
            msg += " rat*="+cat.getAnnotObjCntWithChildrenRat();
        if( cat.getAnnotObjCntWithChildrenMouse()!=0 )
            msg += " mouse*="+cat.getAnnotObjCntWithChildrenMouse();
        if( cat.getAnnotObjCntWithChildrenHuman()!=0 )
            msg += " human*="+cat.getAnnotObjCntWithChildrenHuman();
        log.debug(label+msg);
    }

    private AnnotRecordSet populatePortalCatTable(PortalTermSet term, int portalVerId, Integer parentCatId, String portalType) throws Exception {

        AnnotRecordSet set = new AnnotRecordSet();

        for( Annotation annot: dao.getAnnotations(term.getTermAcc(), true, SpeciesType.RAT) ) {
            AnnotRecord ar = new AnnotRecord(annot, SpeciesType.RAT);
            if( ar.getObjectKey()== RgdId.OBJECT_KEY_GENES ) {
                if( !set.ratGenes.contains(ar) ) {
                    ar.loadPosition(dao);
                    set.ratGenes.add(ar);
                }
            }
            else if( ar.getObjectKey()== RgdId.OBJECT_KEY_QTLS ) {
                if( !set.ratQtls.contains(ar) ) {
                    ar.loadPosition(dao);
                    set.ratQtls.add(ar);
                }
            }
            else if( ar.getObjectKey()== RgdId.OBJECT_KEY_STRAINS ) {
                if( !set.ratStrains.contains(ar) ) {
                    ar.loadPosition(dao);
                    set.ratStrains.add(ar);
                }
            }
        }

        for( Annotation annot: dao.getAnnotations(term.getTermAcc(), true, SpeciesType.MOUSE) ) {
            AnnotRecord ar = new AnnotRecord(annot, SpeciesType.MOUSE);
            if( ar.getObjectKey()== RgdId.OBJECT_KEY_GENES ) {
                if( !set.mouseGenes.contains(ar) ) {
                    set.mouseGenes.add(ar);
                    ar.loadPosition(dao);
                }
            }
            else if( ar.getObjectKey()== RgdId.OBJECT_KEY_QTLS ) {
                if( !set.mouseQtls.contains(ar) ) {
                    set.mouseQtls.add(ar);
                    ar.loadPosition(dao);
                }
            }
        }

        for( Annotation annot: dao.getAnnotations(term.getTermAcc(), true, SpeciesType.HUMAN) ) {
            AnnotRecord ar = new AnnotRecord(annot, SpeciesType.HUMAN);
            if( ar.getObjectKey()== RgdId.OBJECT_KEY_GENES ) {
                if( !set.humanGenes.contains(ar) ) {
                    set.humanGenes.add(ar);
                    ar.loadPosition(dao);
                }
            }
            else if( ar.getObjectKey()== RgdId.OBJECT_KEY_QTLS ) {
                if( !set.humanQtls.contains(ar) ) {
                    set.humanQtls.add(ar);
                    ar.loadPosition(dao);
                }
            }
        }

        set.cat = saveCategoriesToDatabase(set, term, portalVerId, parentCatId, portalType);

        return set;
    }

    private PortalCat saveCategoriesToDatabase(AnnotRecordSet set, PortalTermSet term, int portalVerId, Integer parentCatId, String portalType) throws Exception {

        PortalCat cat = new PortalCat();

        cat.setParentCatId(parentCatId);
        cat.setPortalVerId(portalVerId);

        cat.setSummaryTableHtml(getSummaryTableHTML(set));
        cat.setGeneInfoHtml(getGeneTableHTML(set));
        cat.setgViewerXmlRat(getGviewerXML(set.ratGenes, set.ratQtls));
        cat.setgViewerXmlMouse(getGviewerXML(set.mouseGenes, set.mouseQtls));
        cat.setgViewerXmlHuman(getGviewerXML(set.humanGenes, set.humanQtls));
        cat.setAnnotObjCntWithChildrenRat(set.ratGenes.size()   + set.ratQtls.size());
        cat.setAnnotObjCntWithChildrenHuman(set.humanGenes.size() + set.humanQtls.size());
        cat.setAnnotObjCntWithChildrenMouse(set.mouseGenes.size() + set.mouseQtls.size());
        cat.setQtlInfoHtml(getQTLTableHTML(set));
        cat.setStrainInfoHtml(getStrainTableHTML(set));

        // Calculate the GO Slim terms for this disease portal
        if( !portalType.equals("Ontology") ) {
            GoSlimData goslim = doGOSlimCalc(set.ratGenes);
            goslim.chartXmlCcData = getCCChartXML(goslim.slimCCs);
            goslim.chartXmlMfData = getMFChartXML(goslim.slimMFs);
            goslim.chartXmlBpData = getBPChartXML(goslim.slimBPs);

            cat.setChartXmlCc(goslim.chartXmlCcData);
            cat.setChartXmlBp(goslim.chartXmlBpData);
            cat.setChartXmlMp(goslim.chartXmlMfData);
        }

        // get count of annotations to only this particular term for rat
        cat.setAnnotObjCntRat(dao.getAnnotatedObjectCount(term.getTermAcc(), false, SpeciesType.RAT));
        cat.setCategoryTermAcc(term.getTermAcc());
        cat.setCategoryName(term.getOntTermName());

        saveSummaryTable(cat);

        return cat;
    }

    String getSummaryTableHTML(AnnotRecordSet set) {

        return
        "<table width=\"100%\" border=\"0\" cellpadding=\"1\" cellspacing=\"1\" bgcolor=\"#CCCCCC\">\n"+
        "<tr bgcolor=\"#EEEEEE\">\n"+
        "    <td><p>&nbsp;<strong>Summary</strong><br>\n"+
        "    <img src=\"../../common/images/shim.gif\" width=\"160\" height=\"1\"></p></td>\n"+
        "    <td align=\"right\"><p>Rat&nbsp;&nbsp;<br>\n"+
        "    <img src=\"../../common/images/shim.gif\" width=\"60\" height=\"1\"></p></td>\n"+
        "    <td align=\"right\"><p>Human&nbsp;&nbsp;<br>\n"+
        "    <img src=\"../../common/images/shim.gif\" width=\"70\" height=\"1\"></p></td>\n"+
        "    <td align=\"right\"><p>Mouse&nbsp;&nbsp;<br>\n"+
        "    <img src=\"../../common/images/shim.gif\" width=\"70\" height=\"1\"></p></td>\n"+
        "    <td width=\"100%\"><p>&nbsp;<strong>&nbsp;</strong></p></td>\n"+
        "</tr>\n"+
        "  <tr align=\"right\">\n"+
        "    <td bgcolor=\"#FFFFFF\"><p>Genes&nbsp;</p></td>\n"+
        "    <td bgcolor=\"#FFFFFF\"><span id=\"genes-sum\">"+set.ratGenes.size()+"</span>&nbsp;&nbsp;</td>\n"+
        "    <td bgcolor=\"#FFFFFF\"><span id=\"human-genes-sum\">"+set.humanGenes.size()+"</span>&nbsp;&nbsp;</td>\n"+
        "    <td bgcolor=\"#FFFFFF\"><span id=\"mouse-genes-sum\">"+set.mouseGenes.size()+"</span>&nbsp;&nbsp;</td>\n"+
        "    <td rowspan=\"3\" bgcolor=\"#FFFFFF\">   </td>\n"+
        "  </tr>\n"+
        "  <tr align=\"right\">\n"+
        "    <td bgcolor=\"#FFFFFF\"><p>QTLs&nbsp;</p></td>\n"+
        "    <td bgcolor=\"#FFFFFF\"><span id=\"qtls-sum\">"+set.ratQtls.size()+"</span>&nbsp;&nbsp;</td>\n"+
        "    <td bgcolor=\"#FFFFFF\"><span id=\"human-qtls-sum\">"+set.humanQtls.size()+"</span>&nbsp;&nbsp;</td>\n"+
        "    <td bgcolor=\"#FFFFFF\"><span id=\"mouse-qtls-sum\">"+set.mouseQtls.size()+"</span>&nbsp;&nbsp;</td>\n"+
        "  </tr>\n"+
        "  <tr align=\"right\">\n"+
        "    <td bgcolor=\"#FFFFFF\"><p>Strains&nbsp;</p></td>\n"+
        "    <td bgcolor=\"#FFFFFF\"><span id=\"strains-sum\">"+set.ratStrains.size()+"</span>&nbsp;&nbsp;</td>\n"+
        "    <td bgcolor=\"#EEEEEE\"><span id=\"human-strains-sum\"></span>&nbsp;&nbsp;</td>\n"+
        "    <td bgcolor=\"#EEEEEE\"><span id=\"mouse-strains-sum\"></span>&nbsp;&nbsp;</td>\n"+
        "  </tr>\n"+
        "</table>\n";
    }

    List<AnnotRecord> sortBySymbol(Collection<AnnotRecord> list) {

        List<AnnotRecord> list2 = new ArrayList<AnnotRecord>(list);
        Collections.sort(list2, new Comparator<AnnotRecord>() {
            public int compare(AnnotRecord o1, AnnotRecord o2) {
                return o1.getSymbol().compareToIgnoreCase(o2.getSymbol());
            }
        });
        return list2;
    }

    // returns the Gene table HTML given the array of objects
    String getGeneTableHTML( AnnotRecordSet set ) {

        if( set.ratGenes.isEmpty() && set.humanGenes.isEmpty() && set.mouseGenes.isEmpty() ) {
            return "No Genes's Found";
        }

        // sort genes by symbol
        List<AnnotRecord> ratGenes = sortBySymbol(set.ratGenes);
        List<AnnotRecord> humanGenes = sortBySymbol(set.humanGenes);
        List<AnnotRecord> mouseGenes = sortBySymbol(set.mouseGenes);

        // Build HTML to put in geneInfo div area of page
        StringBuilder buf = new StringBuilder(8000); // avg clob length for gene_info_html
        buf.append("<table width=\"100%\" border=\"0\" cellpadding=\"2\" cellspacing=\"1\">\n")
        .append("<colgroup>\n")
        .append("  <col style='width:33%'>\n")
        .append("  <col style='width:34%'>\n")
        .append("  <col style='width:33%'>\n")
        .append("</colgroup>\n")
        .append("<tr>\n");

        // Start Rat Genes column
        buf.append("<td valign='top' id='geneInfoRatData'>\n");
        for( AnnotRecord ar: ratGenes ) {
            buf.append("<a href=\"").append(Link.gene(ar.getRgdId())).append("\" target='new'>").append(ar.getSymbol()).append("</a>");
            buf.append("<br/>\n");
        }
        buf.append("</td>\n");
        // End Rat Genes column

        // Start Human Genes column
        buf.append("<td valign='top' id='geneInfoHumanData'>\n");
        for( AnnotRecord ar: humanGenes ) {
            buf.append("<a href=\"").append(Link.gene(ar.getRgdId())).append("\" target='new'>").append(ar.getSymbol()).append("</a>");
            buf.append("<br/>\n");
        }
        buf.append("</td>\n");
        // End Human Genes column

        // Start Mouse Genes column
        buf.append("<td valign='top' id='geneInfoMouseData'>\n");
        for( AnnotRecord ar: mouseGenes ) {
            buf.append("<a href=\"").append(Link.gene(ar.getRgdId())).append("\" target='new'>").append(ar.getSymbol()).append("</a>");
            buf.append("<br/>\n");
        }
        buf.append("</td>\n");
        // End Mouse Genes column

        buf.append("</tr>\n");
        buf.append("</table>\n");
        return buf.toString();
    }

    // Return HTML representation of QTL Table based on the rat and human ATL arrays passed in.
    String getQTLTableHTML( AnnotRecordSet set ) {

        if( set.ratQtls.isEmpty() && set.mouseQtls.isEmpty() && set.humanQtls.isEmpty() ) {
            return "No QTL's Found";
        }

        // sort qtls by symbol
        List<AnnotRecord> ratQtls = sortBySymbol(set.ratQtls);
        List<AnnotRecord> humanQtls = sortBySymbol(set.humanQtls);
        List<AnnotRecord> mouseQtls = sortBySymbol(set.mouseQtls);

        // Build HTML to put in qtlInfo div area of page
        StringBuilder buf = new StringBuilder(8000); // avg clob length for gene_info_html
        buf.append("<table width=\"100%\" border=\"0\" cellpadding=\"2\" cellspacing=\"1\">\n")
        .append("<colgroup>\n")
        .append("  <col style='width:33%'>\n")
        .append("  <col style='width:34%'>\n")
        .append("  <col style='width:33%'>\n")
        .append("</colgroup>\n")
        .append("<tr>\n");

        // Start Rat QTLS column
        buf.append("<td valign='top' id='qtlInfoRatData'>\n");
        for( AnnotRecord ar: ratQtls ) {
            buf.append("<a href=\"").append(Link.qtl(ar.getRgdId())).append("\" target='new'>").append(ar.getSymbol()).append("</a>");
            buf.append("<br/>\n");
        }
        buf.append("</td>\n");
        // End Rat QTLS column

        // Start Human QTLS column
        buf.append("<td valign='top' id='qtlInfoHumanData'>\n");
        for( AnnotRecord ar: humanQtls ) {
            buf.append("<a href=\"").append(Link.qtl(ar.getRgdId())).append("\" target='new'>").append(ar.getSymbol()).append("</a>");
            buf.append("<br/>\n");
        }
        buf.append("</td>\n");
        // End Human QTLS column

        // Start Mouse QTLS column
        buf.append("<td valign='top' id='qtlInfoMouseData'>\n");
        for( AnnotRecord ar: mouseQtls ) {
            buf.append("<a href=\"").append(Link.qtl(ar.getRgdId())).append("\" target='new'>").append(ar.getSymbol()).append("</a>");
            buf.append("<br/>\n");
        }
        buf.append("</td>\n");
        // End Mouse QTLS column

        buf.append("</tr>\n");
        buf.append("</table>\n");
        return buf.toString();
    }

    // Return HTML representation of Strain Table based on the rat strain arrays passed in.
    String getStrainTableHTML( AnnotRecordSet set ) {

        if( set.ratStrains.isEmpty() ) {
            return "No Strains Found";
        }

        // sort strains by symbol
        List<AnnotRecord> ratStrains = sortBySymbol(set.ratStrains);

        // Build HTML to put in qtlInfo div area of page
        StringBuilder buf = new StringBuilder(8000); // avg clob length for strain_info_html
        buf.append("<table width=\"100%\" border=\"0\" cellpadding=\"2\" cellspacing=\"1\">\n")
        .append("<tr>\n");

        // Start Rat Strains column
        buf.append("<td valign='top' id='strainInfoRatData'>\n");
        for( AnnotRecord ar: ratStrains ) {
            buf.append("<a href=\"").append(Link.strain(ar.getRgdId())).append("\" target='new'>").append(ar.getSymbol()).append("</a>");
            buf.append("<br/>\n");
        }
        buf.append("</td>\n");
        // End Rat Strains column

        buf.append("</tr>\n");
        buf.append("</table>\n");
        return buf.toString();
    }

    // generate Gviewer XML that gets put into the gview via the PORTAL_CAT.gvewer_XML table
    String getGviewerXML( Collection<AnnotRecord> genes, Collection<AnnotRecord> qtls ) {

        // Header information
        StringBuilder buf = new StringBuilder(8000); // avg size of gviewer_xml column is 4.5k - 6.5k
        buf.append("<?xml version='1.0' standalone='yes' ?><genome>");
        for( AnnotRecord a: genes ) {
            if( a.getChromosome()!=null && !a.getChromosome().equals("null") ) {
                buf.append("<feature>\n");
                buf.append("<chromosome>").append(a.getChromosome()).append("</chromosome>\n");
                buf.append("<start>").append(a.getStartPos()).append("</start>\n");
                buf.append("<end>").append(a.getStopPos()).append("</end>\n");
                buf.append("<type>gene</type>\n");
                buf.append("<label>").append(a.getSymbol()).append("</label>\n");
                buf.append("<link>").append(Link.gene(a.getRgdId())).append("</link>\n");
                buf.append("<color>0x79CC3D</color>\n");
                buf.append("</feature>\n");
            } else {
                skippedLinesWithNullChr++;
            }
        }

        for( AnnotRecord a: qtls ) {
            if( a.getChromosome()!=null && !a.getChromosome().equals("null") ) {
                buf.append("<feature>\n");
                buf.append("<chromosome>").append(a.getChromosome()).append("</chromosome>\n");
                buf.append("<start>").append(a.getStartPos()).append("</start>\n");
                buf.append("<end>").append(a.getStopPos()).append("</end>\n");
                buf.append("<type>qtl</type>\n");
                buf.append("<label>").append(a.getSymbol()).append("</label>\n");
                buf.append("<link>").append(Link.qtl(a.getRgdId())).append("</link>\n");
                buf.append("<color>0xCCCCCC</color>\n");
                buf.append("</feature>\n");
            } else {
                skippedLinesWithNullChr++;
            }
        }
        buf.append( "</genome>" );
        return buf.toString();
    }

    // Update / save summary table information in database for the PORTAL_CAT.PORTAL_CAT_ID specified.
    void saveSummaryTable(PortalCat cat) throws Exception {

        dao.insertPortalCat(cat);
    }

    // Update entry with slim terms
    private void updatePortalVersion( PortalVersion pver, Collection<AnnotRecord> ratGenes ) throws Exception {

        // Update portal with final XML for graphs

        // caclutae go slim terms for entire portal  and update portal version with the XML needed for graphs
        GoSlimData goslim = doGOSlimCalc(ratGenes);
        goslim.chartXmlCcData = getCCChartXML(goslim.slimCCs);
        goslim.chartXmlMfData = getMFChartXML(goslim.slimMFs);
        goslim.chartXmlBpData = getBPChartXML(goslim.slimBPs);

        pver.setChartXmlCc(goslim.chartXmlCcData);
        pver.setChartXmlBp(goslim.chartXmlBpData);
        pver.setChartXmlMp(goslim.chartXmlMfData);

        dao.updatePortalVersion(pver);
    }

    private void archiveOldPortalVersions( PortalVersion pver ) throws Exception {

	    // Mark last portal as archives and new one as active
        int archived = dao.archiveOldPortalVersions(pver);
	    log.debug("archived "+archived+" old portal versions; version "+pver.getPortalVerId()+" for portal_key="+pver.getPortalKey()+" is ACTIVE now");
    }


    String generateChartXML(String title, String[] colors, List<Term> slimArray) {

        StringBuilder buf = new StringBuilder(1024);
        buf.append("<graph caption='").append(title).append("' decimalPrecision='0' xAxisName='Ontology Term' yAxisName='Number of annotations' showNames='0' showValues = '0' pieRadius='70'>\n");

        for( int i=0; i<slimArray.size(); i++ ) {
            Term term = slimArray.get(i);            
            buf.append("<set name='").append(term.getTerm())
                  .append("' value='").append(term.getComment())
                  .append("' color='").append(colors[i])
                  .append("' />\n");
        }
        buf.append("</graph>");
        return buf.toString();
    }

    String getCCChartXML(List<Term> slimArray) {

        String title = "Cellular Component";
        String[] colors = {"66CC00","66CCFF","CC6699","66FF99","CC33FF", "666666","00CCCC","996699","990000","666699"};
        return generateChartXML(title , colors, slimArray);
    }

    String getMFChartXML(List<Term> slimArray) {

        String title = "Molecular Function";
        String[] colors = {"CC9900","CCFF99","FF3399","99CCCC","CCCC33", "66CC00","66CCFF","CC6699","66FF99","CC33FF"};
        return generateChartXML(title , colors, slimArray);
    }

    String getBPChartXML(List<Term> slimArray) {

        String title = "Biological Process";
        String[] colors = {"666666","00CCCC","996699","990000","666699", "006666","336600","333366","000099","999933"};
        return generateChartXML(title , colors, slimArray);
    }

    private GoSlimData doGOSlimCalc(Collection<AnnotRecord> records) throws Exception {

        log.debug("Doing GOSlim grouping");

        GoSlimData goslim = new GoSlimData();

        // trick: we use isObsolete field of term object to keep track of term occurrences;
        // with first occurrence we set isObsolete to 1, with every occurrence we increment this value
        for( AnnotRecord a: records ) {
            //log.debug(a.getRgdId()+" "+a.getSymbol());

            for( Term term: dao.getGoSlimTerms(a.getRgdId()) ) {

                if( term.getOntologyId().equals("BP") ) {
                    processSlimTerm(term, goslim.slimBPs);
                }
                else if( term.getOntologyId().equals("CC") ) {
                    processSlimTerm(term, goslim.slimCCs);
                }
                else if( term.getOntologyId().equals("MF") ) {
                    processSlimTerm(term, goslim.slimMFs);
                }
                else {
                    log.error("Error in ont id: "+term.getOntologyId());
                }
            }
        }

        log.debug("Found # CC: " + goslim.slimCCs.size());
        log.debug("Found # MF: " + goslim.slimMFs.size());
        log.debug("Found # BP: " + goslim.slimBPs.size());

        cleanupGoSlim(goslim.slimCCs, "CC ");
        cleanupGoSlim(goslim.slimBPs, "BP ");
        cleanupGoSlim(goslim.slimMFs, "MF ");

        return goslim;
    }

    void processSlimTerm(Term slimTerm, List<Term> terms) {

        for (Term term : terms) {
            if (term.getAccId().equals(slimTerm.getAccId())) {
                // we found a matching term - increment its frequency count
                term.setComment(Integer.toString(Integer.parseInt(term.getComment()) + 1));
                return;
            }
        }
        // no matching terms -- add a new term with frequency of 1
        slimTerm.setComment("1");
        terms.add(slimTerm);
    }

    // Sort and populate goSlim hash that is passed in returning Array of sorted objects
    // Key to array returned is an array where tmpArray[0] element is GO Slim Name and tmpArray[1]
    // is the count of that slim term
    private void cleanupGoSlim(List<Term> goSlimList, String label) {

        // sort in reverse by frequency count held in 'comment' property of Term
        Collections.sort(goSlimList, new Comparator<Term>(){
            public int compare(Term o1, Term o2) {
                int i1 = Integer.parseInt(o1.getComment());
                int i2 = Integer.parseInt(o2.getComment());
                return i2-i1;
            }
        });
        // Determine top 10 list
        for( int i=goSlimList.size()-1; i>=10; i-- ) {
            goSlimList.remove(i);
        }

        // print out the array
        for( Term term: goSlimList ) {
             log.debug(label + term.getAccId()+" ["+term.getComment()+"] "+term.getTerm());
        }
    }

    List<PortalTermSet> getChildTermsFor(String termAcc, int portalKey) throws Exception {

        // convert Terms into PortalTermSets
        List<Term> terms = dao.getAllActiveTermDescendants(termAcc);
        List<PortalTermSet> termsets = new ArrayList<PortalTermSet>(terms.size());
        for( Term term: terms ) {
            PortalTermSet termset = new PortalTermSet();
            termset.setTermAcc(term.getAccId());
            termset.setOntTermName(term.getTerm());
            termset.setPortalKey(portalKey);
            termsets.add(termset);
        }
        return termsets;
    }

    public Dao getDao() {
        return dao;
    }

    public void setDao(Dao dao) {
        this.dao = dao;
    }
}
