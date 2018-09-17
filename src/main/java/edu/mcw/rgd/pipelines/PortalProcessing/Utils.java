package edu.mcw.rgd.pipelines.PortalProcessing;

import edu.mcw.rgd.dao.DataSourceFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Created by mtutaj on 10/24/2017.
 */
public class Utils {

    static void fixGViewerXmlRat() throws Exception {
        Connection conn = DataSourceFactory.getInstance().getDataSource().getConnection();
        String sql = "SELECT portal_cat_id,gviewer_xml_rat FROM portal_cat1";
        PreparedStatement ps = conn.prepareStatement(sql);

        String sql2 = "UPDATE portal_cat1 SET gviewer_xml_rat=? WHERE portal_cat_id=?";
        PreparedStatement ps2 = conn.prepareStatement(sql2);

        int rowsUpdated = 0;

        ResultSet rs = ps.executeQuery();
        while( rs.next() ) {
            String xml = rs.getString(2);
            int pos = xml.indexOf("<label>Adamts16<sup>em1Bj</label>");
            if( pos>=0 ) {
                long id = rs.getLong(1);
                xml = xml.replace("<label>Adamts16<sup>em1Bj</label>", "<label>Adamts16<sup>em1Bj</sup></label>");

                ps2.setString(1, xml);
                ps2.setLong(2, id);
                rowsUpdated += ps2.executeUpdate();
                System.out.println(" updating portal_cat_id="+id);
            }
        }

        ps2.close();
        ps.close();
        conn.close();

        System.out.println("rows updated "+rowsUpdated);
        System.exit(-1);
    }
}
