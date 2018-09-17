package edu.mcw.rgd.pipelines.PortalProcessing;

import edu.mcw.rgd.datamodel.ontologyx.Term;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: mtutaj
 * Date: 12/29/11
 * Time: 9:51 AM
 * To change this template use File | Settings | File Templates.
 */
public class GoSlimData {

    List<Term> slimCCs = new ArrayList<Term>();
    List<Term> slimMFs = new ArrayList<Term>();
    List<Term> slimBPs = new ArrayList<Term>();

    String chartXmlCcData;
    String chartXmlMfData;
    String chartXmlBpData;
}
