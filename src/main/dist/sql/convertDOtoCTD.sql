-- January 2012
-- written by Marek Tutaj
--
-- conversion script to convert majority of old disease ontology term sets into new disease ontology
-- should be run once BEFORE the new PortalProcessing pipeline is run
-- after running the script, curator in charge should review termsets for disease portals

update portal_termset1 p
set (term_acc,ont_term_name) = (select t.term_acc,t.term from ont_terms t, ont_synonyms s
  where t.term_acc=s.term_acc and 'MESH:'||p.term_acc=s.synonym_name and s.synonym_type='primary_id')
where exists
 (
 select 1 from ont_terms t, ont_synonyms s
  where t.term_acc=s.term_acc and 'MESH:'||p.term_acc=s.synonym_name and s.synonym_type='primary_id'
  );

update portal_termset1 set term_acc='CTD:0003457' where term_acc='D001144';
update portal_termset1 set term_acc='CTD:0004350' where term_acc='D019171';
update portal_termset1 set term_acc='CTD:0000001' where term_acc='D900001';
update portal_termset1 set term_acc='NBO:0000000' where term_acc='D900002';
update portal_termset1 set term_acc='CTD:0000553' where term_acc='D009766';

