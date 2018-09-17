package edu.mcw.rgd.pipelines.PortalProcessing;

import edu.mcw.rgd.datamodel.PortalCat;

import java.util.Set;
import java.util.TreeSet;

/**
 * Created by IntelliJ IDEA.
 * User: mtutaj
 * Date: 12/28/11
 * Time: 10:49 AM
 */
public class AnnotRecordSet {

    Set<AnnotRecord> ratGenes = new TreeSet<>();
    Set<AnnotRecord> mouseGenes = new TreeSet<>();
    Set<AnnotRecord> humanGenes = new TreeSet<>();
    Set<AnnotRecord> ratQtls = new TreeSet<>();
    Set<AnnotRecord> mouseQtls = new TreeSet<>();
    Set<AnnotRecord> humanQtls = new TreeSet<>();
    Set<AnnotRecord> ratStrains = new TreeSet<>();
    PortalCat cat;

    // merge another set with this set
    public void merge(AnnotRecordSet set) {
        ratGenes.addAll(set.ratGenes);
        mouseGenes.addAll(set.mouseGenes);
        humanGenes.addAll(set.humanGenes);

        ratQtls.addAll(set.ratQtls);
        mouseQtls.addAll(set.mouseQtls);
        humanQtls.addAll(set.humanQtls);

        ratStrains.addAll(set.ratStrains);
    }
}
