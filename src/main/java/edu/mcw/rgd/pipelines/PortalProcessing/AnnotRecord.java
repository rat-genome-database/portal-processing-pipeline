package edu.mcw.rgd.pipelines.PortalProcessing;

import edu.mcw.rgd.datamodel.ontology.Annotation;
import edu.mcw.rgd.process.Utils;

/**
 * Created by IntelliJ IDEA.
 * User: mtutaj
 * Date: 12/27/11
 * Time: 5:49 PM
 * represents a single annotation
 */
public class AnnotRecord implements Comparable {

    private int speciesTypeKey;
    private int objectKey;
    private int rgdId;
    private String symbol;
    private String chromosome;
    private int startPos;
    private int stopPos;

    public AnnotRecord(Annotation annot, int speciesTypeKey) {

        this.speciesTypeKey = speciesTypeKey;
        this.objectKey = annot.getRgdObjectKey();
        this.rgdId = annot.getAnnotatedObjectRgdId();
        this.symbol = annot.getObjectSymbol();
        if( this.symbol==null ) // make sure symbol is never null
            this.symbol = "";
    }

    @Override
    public boolean equals(Object obj) {
        AnnotRecord a = (AnnotRecord) obj;
        return a.speciesTypeKey==this.speciesTypeKey
            && a.objectKey==this.objectKey
            && a.rgdId==this.rgdId;
    }

    @Override
    public int hashCode() {
        return (speciesTypeKey+12345) ^ (-objectKey) ^ rgdId;
    }

    public boolean loadPosition(Dao dao) throws Exception {
        Object[] md = dao.getMapDataCached(rgdId, speciesTypeKey);
        if( md!=null ) {
            chromosome = (String)md[0];
            startPos = (Integer)md[1];
            stopPos = (Integer)md[2];
            return true;
        }
        return false;
    }

    public int getSpeciesTypeKey() {
        return speciesTypeKey;
    }

    public void setSpeciesTypeKey(int speciesTypeKey) {
        this.speciesTypeKey = speciesTypeKey;
    }

    public int getObjectKey() {
        return objectKey;
    }

    public void setObjectKey(int objectKey) {
        this.objectKey = objectKey;
    }

    public int getRgdId() {
        return rgdId;
    }

    public void setRgdId(int rgdId) {
        this.rgdId = rgdId;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getChromosome() {
        return chromosome;
    }

    public void setChromosome(String chromosome) {
        this.chromosome = chromosome;
    }

    public int getStartPos() {
        return startPos;
    }

    public void setStartPos(int startPos) {
        this.startPos = startPos;
    }

    public int getStopPos() {
        return stopPos;
    }

    public void setStopPos(int stopPos) {
        this.stopPos = stopPos;
    }

    public int compareTo(Object o) {
        AnnotRecord r = (AnnotRecord) o;
        return Utils.stringsCompareToIgnoreCase(this.getSymbol(), r.getSymbol());
    }
}
